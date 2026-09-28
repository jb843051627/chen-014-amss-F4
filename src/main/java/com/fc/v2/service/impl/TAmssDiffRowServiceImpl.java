package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.AmssBizException;
import com.fc.v2.mapper.auto.TAmssDiffBatchMapper;
import com.fc.v2.mapper.auto.TAmssDiffRowMapper;
import com.fc.v2.model.auto.TAmssDiffBatch;
import com.fc.v2.model.auto.TAmssDiffRow;
import com.fc.v2.model.custom.DiffBatchReceipt;
import com.fc.v2.service.ITAmssDiffRowService;

/**
 * 通行费免收与作业证发放对账行 Service业务层处理。
 *
 * 形状：一批账进出的唯一出口——{@link #processRows} 一行进便过一行，
 * 合法行落待核、写走样的行只挂本行，绝不整批按住；核了几行、挂了几行、差额余下多少，
 * 全看 {@link DiffBatchReceipt} 这一个回算，页面不自己数。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAmssDiffRowServiceImpl implements ITAmssDiffRowService {

    /** 单批行数上限：冲过上限整批退回，一行都不落 */
    private static final int MAX_ROWS = 500;

    /** 行进展 0待核 1已核销 2挂账 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_WRITEOFF = 1;
    private static final int STATUS_HANG = 2;

    /** 差异项代号：一项一个码，只有这五种情形 */
    public static final String CODE_NO_CERT = "NO_CERT";                    // 无证通行
    public static final String CODE_CERT_OWNER_MISMATCH = "CERT_MISMATCH"; // 证号与机主对不上
    public static final String CODE_NOT_RESERVED = "NOT_RESERVED";         // 未预约通行
    public static final String CODE_ONE_CERT_MULTI_VEH = "ONE_CERT_MULTI"; // 一证多车
    public static final String CODE_CROSS_PROVINCE = "CROSS_PROVINCE";     // 跨省归属不符

    private static final Set<String> ITEM_CODES = new HashSet<String>();
    static {
        ITEM_CODES.add(CODE_NO_CERT);
        ITEM_CODES.add(CODE_CERT_OWNER_MISMATCH);
        ITEM_CODES.add(CODE_NOT_RESERVED);
        ITEM_CODES.add(CODE_ONE_CERT_MULTI_VEH);
        ITEM_CODES.add(CODE_CROSS_PROVINCE);
    }

    /** "比上一期多出几十倍"的认定线：本行差额达到上期记录的五十倍即判写走样 */
    private static final BigDecimal SPIKE_FACTOR = new BigDecimal("50");

    @javax.annotation.Resource
    private TAmssDiffRowMapper amssDiffRowMapper;

    @javax.annotation.Resource
    private TAmssDiffBatchMapper amssDiffBatchMapper;

    @Override
    public TAmssDiffRow selectTAmssDiffRowById(Long id) {
        return this.amssDiffRowMapper.selectById(id);
    }

    /* ===================== 报送：两个签名，同一个出口 ===================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TAmssDiffRow> rows) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new AmssBizException("对账批次号不得为空");
        }
        TAmssDiffBatch head = new TAmssDiffBatch();
        head.setBatchNo(batchNo.trim());
        DiffBatchReceipt receipt = submitBatch(head, rows);
        if (!receipt.isAccepted()) {
            return -1;
        }
        return receipt.getOriginalRows();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiffBatchReceipt submitBatch(TAmssDiffBatch batch, List<TAmssDiffRow> rows) {
        if (batch == null || batch.getBatchNo() == null || batch.getBatchNo().trim().isEmpty()) {
            throw new AmssBizException("对账批次号不得为空");
        }
        String batchNo = batch.getBatchNo().trim();
        List<TAmssDiffRow> incoming = rows == null ? new ArrayList<TAmssDiffRow>() : rows;

        // 同批次号第二次送进来：库里只留前一遍。后一遍不落一行、不盖一行、不追第二句缘故。
        TAmssDiffBatch existing = findBatch(batchNo);
        if (existing != null) {
            DiffBatchReceipt prior = buildReceipt(existing);
            prior.setDuplicate(true);
            return prior;
        }

        // 白批：一行没有，不建账；回执一切为零，allWrittenOff 恒为 false，不许写"已核讫"。
        if (incoming.isEmpty()) {
            return emptyReceipt(batchNo);
        }

        // 行数上限先于一切：冲过上限整批退回，一行都不落。
        if (incoming.size() > MAX_ROWS) {
            DiffBatchReceipt rejected = emptyReceipt(batchNo);
            rejected.setAccepted(false);
            return rejected;
        }

        TAmssDiffBatch head = new TAmssDiffBatch();
        head.setBatchNo(batchNo);
        head.setPeriodNo(batch.getPeriodNo());
        head.setOrgCode(batch.getOrgCode());
        head.setOriginalRows(incoming.size());
        head.setArchived(0);
        head.setDelFlag(0);

        // 统头先占号，再放行进账：唯一键撞号发生在任何一行落库之前，
        // 后一遍不落一行（不依赖异常回滚），直接照前一遍重算回执。
        try {
            this.amssDiffBatchMapper.insert(head);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            TAmssDiffBatch prior = findBatch(batchNo);
            DiffBatchReceipt receipt = buildReceipt(prior);
            receipt.setDuplicate(true);
            return receipt;
        }

        List<TAmssDiffRow> stored = processRows(head, incoming);
        head.setStoredRows(stored.size());
        UpdateWrapper<TAmssDiffBatch> huw = new UpdateWrapper<TAmssDiffBatch>();
        huw.eq("id", head.getId()).set("stored_rows", stored.size());
        this.amssDiffBatchMapper.update(null, huw);

        // 并笔行的并入行数在进门时合并累加，落库后回写一次（差额本身只计一次，不回写）。
        for (TAmssDiffRow r : stored) {
            if (r.getMergedRows() != null && r.getMergedRows() > 1) {
                UpdateWrapper<TAmssDiffRow> uw = new UpdateWrapper<TAmssDiffRow>();
                uw.eq("id", r.getId()).set("merged_rows", r.getMergedRows());
                this.amssDiffRowMapper.update(null, uw);
            }
        }

        return buildReceipt(head);
    }

    /**
     * 一批账进出的唯一出口：一行进便过一行。
     * 行次照原清单顺次由系统抄入（i+1），人不填；写走样的行只把本行落挂账、写清一句缘故，
     * 别的行照旧进账；同机台同差异的重复旧写法并成一条，差额只计一次，行次认在前头那一个。
     */
    private List<TAmssDiffRow> processRows(TAmssDiffBatch head, List<TAmssDiffRow> incoming) {
        String batchNo = head.getBatchNo();
        List<TAmssDiffRow> stored = new ArrayList<TAmssDiffRow>();
        Map<String, TAmssDiffRow> mergeIndex = new LinkedHashMap<String, TAmssDiffRow>();
        Map<String, BigDecimal> baselineCache = new HashMap<String, BigDecimal>();

        for (int i = 0; i < incoming.size(); i++) {
            TAmssDiffRow in = incoming.get(i);
            // 行次进门那一刻照原清单顺次抄进账，不信用报送册自带的行次。
            int rowNo = i + 1;

            // 并笔先于校验：同机台同差异占了两个行目，是同一条目的旧写法，
            // 第二笔整笔并进来、不再单独立行，差额只认头一笔——故第二笔上的数字不单独挂账。
            String mergeKey = mergeKey(in);
            TAmssDiffRow first = mergeKey == null ? null : mergeIndex.get(mergeKey);
            if (first != null) {
                first.setMergedRows(first.getMergedRows() + 1);
                continue;
            }

            String reason = validate(in, baselineCache);
            if (reason != null) {
                // 只挂这一行：差额写走样不连累同批其他行，更不许整批按住重导。
                TAmssDiffRow hang = newRow(batchNo, in, rowNo);
                hang.setStatus(STATUS_HANG);
                hang.setHangReason(reason);
                hang.setMergedRows(1);
                this.amssDiffRowMapper.insert(hang);
                stored.add(hang);
                if (mergeKey != null) {
                    // 挂账行也占归并位：后头同机同类的旧写法并到它名下加权，不会冒出第二条。
                    mergeIndex.put(mergeKey, hang);
                }
                continue;
            }

            TAmssDiffRow row = newRow(batchNo, in, rowNo);
            row.setStatus(STATUS_PENDING);
            row.setMergedRows(1);
            this.amssDiffRowMapper.insert(row);
            stored.add(row);
            if (mergeKey != null) {
                mergeIndex.put(mergeKey, row);
            }
        }
        return stored;
    }

    /**
     * 逐行校验：返回挂账缘故（一句话只说一件事），合法返回 null。
     * 一行只给头一条毛病，保证缘故上账时只有一件事。
     */
    private String validate(TAmssDiffRow in, Map<String, BigDecimal> baselineCache) {
        if (in == null) {
            return "空行不得入账";
        }
        if (in.getItemCode() == null || in.getItemCode().trim().isEmpty()) {
            return "差异项代号缺失";
        }
        if (!ITEM_CODES.contains(in.getItemCode().trim())) {
            return "差异项代号不在五种规定情形之内";
        }
        BigDecimal qty = in.getQty();
        if (qty == null) {
            return "本行争议差额缺失";
        }
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            return "本行争议差额填成负数";
        }
        BigDecimal baseline = priorPeriodBaseline(in, baselineCache);
        if (baseline != null) {
            BigDecimal limit = baseline.multiply(SPIKE_FACTOR);
            if (qty.compareTo(limit) > 0) {
                return "本行争议差额较上期记录多出逾五十倍";
            }
        }
        return null;
    }

    /** 上一期同机台同差异的差额底数（取库内最近一笔非挂账行）；无记录或无机台号则无底数 */
    private BigDecimal priorPeriodBaseline(TAmssDiffRow in, Map<String, BigDecimal> baselineCache) {
        String machine = in.getMachineNo();
        if (machine == null || machine.trim().isEmpty()) {
            return null;
        }
        String key = machine.trim() + "|" + in.getItemCode().trim();
        if (baselineCache.containsKey(key)) {
            return baselineCache.get(key);
        }
        List<TAmssDiffRow> prior = this.amssDiffRowMapper.selectList(new QueryWrapper<TAmssDiffRow>()
                .eq("del_flag", 0)
                .ne("status", STATUS_HANG)
                .eq("machine_no", machine.trim())
                .eq("item_code", in.getItemCode().trim())
                .orderByDesc("create_time"));
        BigDecimal baseline = null;
        for (TAmssDiffRow r : prior) {
            if (r.getQty() != null && r.getQty().compareTo(BigDecimal.ZERO) > 0) {
                baseline = r.getQty();
                break;
            }
        }
        baselineCache.put(key, baseline);
        return baseline;
    }

    private TAmssDiffRow newRow(String batchNo, TAmssDiffRow in, int rowNo) {
        TAmssDiffRow r = new TAmssDiffRow();
        r.setBatchNo(batchNo);
        r.setRowNo(rowNo);
        r.setItemCode(in.getItemCode() == null ? null : in.getItemCode().trim());
        r.setMachineNo(in.getMachineNo() == null ? null : in.getMachineNo().trim());
        // 挂账行的差额未经认定，照原值留存只供追查，不进合计；进账行统一到分。
        r.setQty(in.getQty() == null ? null : in.getQty().setScale(2, RoundingMode.HALF_UP));
        r.setWriteoffAmt(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        r.setArchived(0);
        r.setDelFlag(0);
        return r;
    }

    private String mergeKey(TAmssDiffRow in) {
        String machine = in.getMachineNo();
        String item = in.getItemCode();
        if (machine == null || machine.trim().isEmpty()
                || item == null || item.trim().isEmpty()) {
            return null;
        }
        return machine.trim() + "|" + item.trim();
    }

    /* ===================== 核销：只针对那一行，额不越差、凭据齐全 ===================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssDiffRow writeOff(Long id, BigDecimal amount, String voucher) {
        if (id == null) {
            throw new AmssBizException("核销行不得为空");
        }
        if (voucher == null || voucher.trim().isEmpty()) {
            throw new AmssBizException("核销须有凭据号");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AmssBizException("核销额须大于零");
        }
        TAmssDiffRow row = this.amssDiffRowMapper.selectById(id);
        if (row == null || (row.getDelFlag() != null && row.getDelFlag() == 1)) {
            throw new AmssBizException("本行不存在");
        }
        if (row.getStatus() != null && row.getStatus() == STATUS_HANG) {
            // 挂账行不许就地核销，差额真要改凭差异清单那一路走。
            throw new AmssBizException("挂账行须先凭差异清单改定后方可核销");
        }
        if (row.getStatus() != null && row.getStatus() == STATUS_WRITEOFF
                && row.getQty().subtract(noNull(row.getWriteoffAmt())).signum() == 0) {
            throw new AmssBizException("本行已核销，不得重复核销");
        }
        BigDecimal already = noNull(row.getWriteoffAmt());
        BigDecimal next = already.add(amount);
        // 差一百、核一百二：多出的二十既不能塞进本行，也不能挪到邻行顶上。
        if (next.compareTo(row.getQty()) > 0) {
            throw new AmssBizException("累计核销额不得大于本行争议差额");
        }
        int targetStatus = next.compareTo(row.getQty()) == 0 ? STATUS_WRITEOFF : STATUS_PENDING;
        String amt = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();

        // 条件更新一道兜底并发：两笔核销一齐办，越额条件由库内按当前余额判定，
        // 不会因两笔同时读过旧余额而各过各的（差额恒为非负的本行才进此口）。
        UpdateWrapper<TAmssDiffRow> uw = new UpdateWrapper<TAmssDiffRow>();
        uw.eq("id", id)
                .ne("status", STATUS_HANG)
                .apply("COALESCE(writeoff_amt,0) + {0} <= qty", amount.setScale(2, RoundingMode.HALF_UP))
                .setSql("writeoff_amt = COALESCE(writeoff_amt,0) + " + amt)
                .set("writeoff_voucher", appendVoucher(row.getWriteoffVoucher(), voucher.trim()))
                .set("status", targetStatus);
        int affected = this.amssDiffRowMapper.update(null, uw);
        if (affected != 1) {
            // 回读一次，把驳回缘故说成一件事。
            TAmssDiffRow now = this.amssDiffRowMapper.selectById(id);
            if (now == null || (now.getDelFlag() != null && now.getDelFlag() == 1)) {
                throw new AmssBizException("本行不存在");
            }
            if (now.getStatus() != null && now.getStatus() == STATUS_HANG) {
                throw new AmssBizException("挂账行须先凭差异清单改定后方可核销");
            }
            throw new AmssBizException("累计核销额不得大于本行争议差额");
        }
        return this.amssDiffRowMapper.selectById(id);
    }

    private String appendVoucher(String oldVoucher, String voucher) {
        if (oldVoucher == null || oldVoucher.trim().isEmpty()) {
            return voucher;
        }
        return oldVoucher + "，" + voucher;
    }

    private BigDecimal noNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /* ===================== 回执：报送合计与库内合计同一个口径、同一回算 ===================== */

    @Override
    public DiffBatchReceipt getReceipt(String batchNo) {
        TAmssDiffBatch head = findBatch(batchNo);
        if (head == null) {
            return null;
        }
        return buildReceipt(head);
    }

    private DiffBatchReceipt buildReceipt(TAmssDiffBatch head) {
        List<TAmssDiffRow> rows = listByBatchNo(head.getBatchNo());
        DiffBatchReceipt receipt = new DiffBatchReceipt();
        receipt.setAccepted(true);
        receipt.setDuplicate(false);
        receipt.setBatchNo(head.getBatchNo());
        receipt.setOriginalRows(head.getOriginalRows() == null ? 0 : head.getOriginalRows());
        receipt.setStoredRows(rows.size());

        int pendingW = 0;
        int writeoffW = 0;
        int hangW = 0;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal writeoffAmt = BigDecimal.ZERO;
        for (TAmssDiffRow r : rows) {
            int weight = r.getMergedRows() == null || r.getMergedRows() < 1 ? 1 : r.getMergedRows();
            int status = r.getStatus() == null ? STATUS_PENDING : r.getStatus();
            if (status == STATUS_HANG) {
                hangW += weight;
            } else if (status == STATUS_WRITEOFF) {
                writeoffW += weight;
            } else {
                pendingW += weight;
            }
            // 挂账行差额未经认定，不进争议合计；待差异清单改定后才计。
            if (status != STATUS_HANG && r.getQty() != null) {
                totalQty = totalQty.add(r.getQty());
            }
            if (r.getWriteoffAmt() != null) {
                writeoffAmt = writeoffAmt.add(r.getWriteoffAmt());
            }
        }
        receipt.setPendingRows(pendingW);
        receipt.setWriteoffRows(writeoffW);
        receipt.setHangRows(hangW);
        receipt.setTotalQty(totalQty.setScale(2, RoundingMode.HALF_UP));
        receipt.setWriteoffAmt(writeoffAmt.setScale(2, RoundingMode.HALF_UP));
        receipt.setRemainingAmt(totalQty.subtract(writeoffAmt).setScale(2, RoundingMode.HALF_UP));

        int original = receipt.getOriginalRows();
        // 挂账数 + 核销数 + 待核数 = 原清单行数（按并入行数加权），同一回算里必须闭合。
        boolean identityClosed = pendingW + writeoffW + hangW == original;
        if (!identityClosed) {
            throw new AmssBizException("回执合计与原清单行数对不齐");
        }
        receipt.setSettled(original > 0 && pendingW == 0);
        receipt.setAllWrittenOff(original > 0 && pendingW == 0 && hangW == 0);
        return receipt;
    }

    private DiffBatchReceipt emptyReceipt(String batchNo) {
        DiffBatchReceipt receipt = new DiffBatchReceipt();
        receipt.setAccepted(true);
        receipt.setDuplicate(false);
        receipt.setBatchNo(batchNo);
        return receipt;
    }

    private TAmssDiffBatch findBatch(String batchNo) {
        List<TAmssDiffBatch> hit = this.amssDiffBatchMapper.selectList(new QueryWrapper<TAmssDiffBatch>()
                .eq("batch_no", batchNo).eq("del_flag", 0));
        return hit.isEmpty() ? null : hit.get(0);
    }

    /* ===================== 查账：批次号、差异代号都能拉；现行页不露历史 ===================== */

    @Override
    public List<TAmssDiffRow> listHangRows(String batchNo) {
        return this.amssDiffRowMapper.selectList(new QueryWrapper<TAmssDiffRow>()
                .eq("del_flag", 0).eq("batch_no", batchNo).eq("status", STATUS_HANG)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TAmssDiffRow> listCurrent(String batchNo, String itemCode) {
        QueryWrapper<TAmssDiffRow> qw = new QueryWrapper<TAmssDiffRow>()
                .eq("del_flag", 0).eq("archived", 0);
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            qw.eq("batch_no", batchNo.trim());
        }
        if (itemCode != null && !itemCode.trim().isEmpty()) {
            qw.eq("item_code", itemCode.trim());
        }
        qw.orderByAsc("batch_no", "row_no");
        return this.amssDiffRowMapper.selectList(qw);
    }

    @Override
    public List<TAmssDiffRow> listByBatchNo(String batchNo) {
        return this.amssDiffRowMapper.selectList(new QueryWrapper<TAmssDiffRow>()
                .eq("del_flag", 0).eq("batch_no", batchNo).orderByAsc("row_no"));
    }

    @Override
    public List<TAmssDiffRow> listByItemCode(String itemCode) {
        return this.amssDiffRowMapper.selectList(new QueryWrapper<TAmssDiffRow>()
                .eq("del_flag", 0).eq("item_code", itemCode)
                .orderByAsc("batch_no", "row_no"));
    }

    /* ===================== 满两个年度转历史 ===================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveMatured(Date at) {
        if (at == null) {
            throw new AmssBizException("转历史的截止之日不得为空");
        }
        Calendar cutoff = Calendar.getInstance();
        cutoff.setTime(at);
        cutoff.add(Calendar.YEAR, -2);

        // 时段比较统一走墙钟字符串绑定（与判据线同一教训：Date 直绑会因时区整体偏移）。
        String cutoffTs = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cutoff.getTime());
        UpdateWrapper<TAmssDiffRow> ruw = new UpdateWrapper<TAmssDiffRow>();
        ruw.eq("archived", 0).eq("del_flag", 0).apply("create_time <= {0}", cutoffTs)
                .set("archived", 1);
        int archivedRows = this.amssDiffRowMapper.update(null, ruw);

        // 行已全部转进历史的批次统头一并转，现行页只留批次号可查。
        List<TAmssDiffBatch> heads = this.amssDiffBatchMapper.selectList(new QueryWrapper<TAmssDiffBatch>()
                .eq("del_flag", 0).eq("archived", 0));
        for (TAmssDiffBatch head : heads) {
            Integer left = this.amssDiffRowMapper.selectCount(new QueryWrapper<TAmssDiffRow>()
                    .eq("del_flag", 0).eq("batch_no", head.getBatchNo()).eq("archived", 0));
            if (left != null && left == 0) {
                UpdateWrapper<TAmssDiffBatch> huw = new UpdateWrapper<TAmssDiffBatch>();
                huw.eq("id", head.getId()).set("archived", 1).set("archived_at", at);
                this.amssDiffBatchMapper.update(null, huw);
            }
        }
        return archivedRows;
    }
}
