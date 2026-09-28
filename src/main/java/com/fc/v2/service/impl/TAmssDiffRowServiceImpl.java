package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TAmssDiffRowMapper;
import com.fc.v2.model.auto.TAmssDiffRow;
import com.fc.v2.model.custom.AmssDiffBatch;
import com.fc.v2.model.custom.AmssDiffItem;
import com.fc.v2.model.custom.AmssDiffReceipt;
import com.fc.v2.model.custom.AmssRowOutcome;
import com.fc.v2.service.ITAmssDiffRowService;

/**
 * 通行费免收与作业证发放对账行 Service业务层处理。
 *
 * 这批账进出的唯一出口是 {@link #processRow}：一行进便过一行。核了几行、挂了几行、
 * 差额余下多少，全看逐行处理与回执的返回，页面自己数的一律不算，也没有手工补一行、
 * 就地改一行的把手。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAmssDiffRowServiceImpl implements ITAmssDiffRowService {

    /** 差额比同机同类上一笔大出该倍数即视为走样（“多出几十倍”取三十倍含以上） */
    private static final BigDecimal SURGE_MULTIPLE = new BigDecimal("30");
    /** 收下的行满两个年度转进历史 */
    private static final int ARCHIVE_YEARS = 2;

    @Resource
    private TAmssDiffRowMapper amssDiffRowMapper;

    @Override
    public TAmssDiffRow selectTAmssDiffRowById(Long id) {
        return this.amssDiffRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmssRowOutcome processRow(String batchNo, TAmssDiffRow row, int rowNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("对账批次号不能为空");
        }
        if (row == null) {
            throw new IllegalArgumentException("进门行不能为空");
        }
        if (rowNo < 1) {
            throw new IllegalArgumentException("原清单行次从1起顺次排定");
        }

        // 系统落地：批次号、行次照原清单顺次抄定，初始进展一律待核；报送册上填的这些一概不认
        row.setId(null);
        row.setBatchNo(batchNo.trim());
        row.setRowNo(rowNo);
        row.setDelFlag(0);
        row.setStatus(STATUS_PENDING);
        row.setWriteoffQty(null);
        row.setWriteoffVoucher(null);
        row.setSuspendReason(null);
        row.setArchiveTime(null);
        row.setMergedCount(1);
        row.setMachineNo(row.getMachineNo() == null ? null : row.getMachineNo().trim());
        row.setItemCode(row.getItemCode() == null ? null : row.getItemCode().trim());

        // 先认代号；代号不认就谈不上同机同类归并
        if (!AmssDiffItem.isValidCode(row.getItemCode())) {
            return suspend(row, "差异项代号不在规定的五种情形之内");
        }

        // 同机同类前头那一条（挂账行不参与归并：既不能往它身上追加，也不许追第二句缘故）
        TAmssDiffRow earlier = findMergeTarget(row.getBatchNo(), row.getMachineNo(), row.getItemCode());

        String reason = amountFault(row, earlier);
        if (reason != null) {
            return suspend(row, reason);
        }

        // 同机同类占了两个行目（上旬一笔、下旬又一笔的旧写法）：并成一条，差额合计只计一次
        if (earlier != null) {
            earlier.setQty(earlier.getQty().add(row.getQty()));
            earlier.setMergedCount(nvl(earlier.getMergedCount()) + 1);
            this.amssDiffRowMapper.updateById(earlier);
            // 并过之后行次认在前头那一个；本行不另落，返回头行
            return AmssRowOutcome.of(earlier, true);
        }

        this.amssDiffRowMapper.insert(row);
        return AmssRowOutcome.of(row, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TAmssDiffRow> rows) {
        String no = batchNo == null ? null : batchNo.trim();
        if (no == null || no.isEmpty()) {
            throw new IllegalArgumentException("对账批次号不能为空");
        }
        if (rows == null || rows.isEmpty()) {
            // 白批：一行都没有，什么也不落
            return 0;
        }

        // 重复批次优先：同一批次号第二次送进来，库里只留前一遍，后一遍一个行也不盖
        Integer existing = this.amssDiffRowMapper.selectCount(batchWrapper(no));
        if (existing != null && existing > 0) {
            return existing.intValue();
        }
        // 冲过行数上限：整批退回去，一行都不落
        if (rows.size() > MAX_ROWS) {
            return -1;
        }

        int seq = 0;
        for (TAmssDiffRow r : rows) {
            seq++;
            processRow(no, r, seq);
        }
        return this.amssDiffRowMapper.selectCount(batchWrapper(no)).intValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmssDiffReceipt submitBatch(AmssDiffBatch batch) {
        if (batch == null) {
            throw new IllegalArgumentException("批次不能为空");
        }
        String no = batch.getBatchNo() == null ? null : batch.getBatchNo().trim();
        if (no == null || no.isEmpty()) {
            throw new IllegalArgumentException("对账批次号不能为空");
        }
        List<TAmssDiffRow> rows = batch.getRows() == null
                ? new ArrayList<TAmssDiffRow>() : batch.getRows();

        int stored = submitBatch(no, rows);
        AmssDiffReceipt receipt = receipt(no);
        if (stored == -1) {
            // 整批退回：回执照库内回算（库里本批一行没有），另打退回记号，不写已核讫
            receipt.setRejected(true);
        }
        return receipt;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssDiffRow writeOff(Long id, BigDecimal writeoffQty, String voucher) {
        if (id == null) {
            throw new IllegalArgumentException("核销行不能为空");
        }
        if (writeoffQty == null || writeoffQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("本次核销额必须大于0");
        }
        if (voucher == null || voucher.trim().isEmpty()) {
            throw new IllegalArgumentException("核销必须带凭据号，凭凭据核销");
        }
        TAmssDiffRow row = this.amssDiffRowMapper.selectById(id);
        if (row == null || (row.getDelFlag() != null && row.getDelFlag() == 1)) {
            throw new IllegalArgumentException("本行不存在");
        }
        if (row.getArchiveTime() != null) {
            throw new IllegalArgumentException("本行已转历史，现行账不再核销");
        }
        if (STATUS_SUSPENDED == statusOf(row)) {
            // 差额真要改，凭差异清单那一路走，别在报送册上动
            throw new IllegalArgumentException("挂账行不许就报送册核销");
        }
        BigDecimal done = row.getWriteoffQty() == null ? BigDecimal.ZERO : row.getWriteoffQty();
        BigDecimal after = done.add(writeoffQty);
        // 核销那笔不能大过差异额：差一百、核一百二，多出的二十整笔拒，本行原样不动
        if (after.compareTo(row.getQty()) > 0) {
            throw new IllegalArgumentException("累计核销额不得大于本行争议差额");
        }
        row.setWriteoffQty(after);
        row.setWriteoffVoucher(voucher.trim());
        // 分笔核销未足额时行仍旧待核（差额余下还在），足额那一刻才落已核销
        if (after.compareTo(row.getQty()) == 0) {
            row.setStatus(STATUS_WRITTEN_OFF);
        }
        this.amssDiffRowMapper.updateById(row);
        return row;
    }

    @Override
    public AmssDiffReceipt receipt(String batchNo) {
        String no = batchNo == null ? null : batchNo.trim();
        AmssDiffReceipt receipt = new AmssDiffReceipt();
        receipt.setBatchNo(no);
        receipt.setTotalQty(BigDecimal.ZERO);
        receipt.setWrittenOffQty(BigDecimal.ZERO);
        receipt.setRemainQty(BigDecimal.ZERO);

        List<TAmssDiffRow> rows;
        if (no == null || no.isEmpty()) {
            rows = Collections.emptyList();
        } else {
            rows = this.amssDiffRowMapper.selectList(batchWrapper(no).orderByAsc("row_no"));
        }

        int total = 0;
        int pending = 0;
        int suspended = 0;
        int writtenOff = 0;
        BigDecimal qtySum = BigDecimal.ZERO;
        BigDecimal offSum = BigDecimal.ZERO;
        for (TAmssDiffRow r : rows) {
            // 合并行按并入的原清单行数展开，回执口径对的是原清单，不是库内条数
            int m = nvl(r.getMergedCount());
            total += m;
            int st = statusOf(r);
            if (st == STATUS_SUSPENDED) {
                suspended += m;
            } else if (st == STATUS_WRITTEN_OFF) {
                writtenOff += m;
            } else {
                pending += m;
            }
            if (r.getQty() != null) {
                qtySum = qtySum.add(r.getQty());
            }
            if (r.getWriteoffQty() != null) {
                offSum = offSum.add(r.getWriteoffQty());
            }
        }

        receipt.setStoredRows(rows.size());
        receipt.setTotalRows(total);
        receipt.setPendingCount(pending);
        receipt.setSuspendedCount(suspended);
        receipt.setWrittenOffCount(writtenOff);
        receipt.setTotalQty(qtySum);
        receipt.setWrittenOffQty(offSum);
        receipt.setRemainQty(qtySum.subtract(offSum));
        // 回执合计与库内合计须是一个口径、同一回算：待核+挂账+核销=原清单行数
        receipt.setBalanced(pending + suspended + writtenOff == total);
        // 非空批且已无待核才算核讫；白批一行都没有，绝不写已核讫
        receipt.setSettled(total > 0 && pending == 0 && receipt.isBalanced());
        return receipt;
    }

    @Override
    public List<TAmssDiffRow> listByBatch(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TAmssDiffRow>();
        }
        return this.amssDiffRowMapper.selectList(batchWrapper(batchNo.trim()).orderByAsc("row_no"));
    }

    @Override
    public List<TAmssDiffRow> listByItem(String itemCode) {
        if (!AmssDiffItem.isValidCode(itemCode)) {
            return new ArrayList<TAmssDiffRow>();
        }
        return this.amssDiffRowMapper.selectList(new QueryWrapper<TAmssDiffRow>()
                .eq("del_flag", 0)
                .eq("item_code", itemCode.trim())
                .orderByAsc("batch_no", "row_no"));
    }

    @Override
    public List<TAmssDiffRow> listCurrent(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TAmssDiffRow>();
        }
        return this.amssDiffRowMapper.selectList(batchWrapper(batchNo.trim())
                .isNull("archive_time")
                .orderByAsc("row_no"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveMaturedRows() {
        // 收下的行满两个年度转进历史；转过之后现行页不露，批次号仍可查
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -ARCHIVE_YEARS);
        Date now = new Date();
        // 时间边界统一走墙钟字符串绑定，避开 JDBC serverTimezone 偏移（与判据线同一教训）
        return this.amssDiffRowMapper.update(null, new UpdateWrapper<TAmssDiffRow>()
                .eq("del_flag", 0)
                .isNull("archive_time")
                .lt("create_time", ts(cal.getTime()))
                .set("archive_time", ts(now)));
    }

    // ------------------------------------------------------------------

    /** 本行只落挂账，写一句只说一件事的缘故；别的行照旧进账，整批不按住 */
    private AmssRowOutcome suspend(TAmssDiffRow row, String reason) {
        row.setStatus(STATUS_SUSPENDED);
        row.setSuspendReason(reason);
        this.amssDiffRowMapper.insert(row);
        return AmssRowOutcome.of(row, false);
    }

    /** 差额走样的缘故；每个分支只说一件事，“数不对”这类要人猜的话不许上账 */
    private String amountFault(TAmssDiffRow row, TAmssDiffRow earlier) {
        BigDecimal q = row.getQty();
        if (q == null) {
            return "本行争议差额未填";
        }
        if (q.compareTo(BigDecimal.ZERO) < 0) {
            return "本行争议差额填成负数";
        }
        if (q.compareTo(BigDecimal.ZERO) == 0) {
            return "本行争议差额为零";
        }
        if (earlier != null && earlier.getQty() != null
                && earlier.getQty().compareTo(BigDecimal.ZERO) > 0
                && q.compareTo(earlier.getQty().multiply(SURGE_MULTIPLE)) > 0) {
            return "本行争议差额比同机同类上旬一笔大出三十倍以上";
        }
        return null;
    }

    /** 同机同类前头那一条（认行次最前的非挂账行）；机具号空缺则无从归并 */
    private TAmssDiffRow findMergeTarget(String batchNo, String machineNo, String itemCode) {
        if (machineNo == null || machineNo.isEmpty()) {
            return null;
        }
        List<TAmssDiffRow> hits = this.amssDiffRowMapper.selectList(new QueryWrapper<TAmssDiffRow>()
                .eq("del_flag", 0)
                .eq("batch_no", batchNo)
                .eq("machine_no", machineNo)
                .eq("item_code", itemCode)
                .eq("status", STATUS_PENDING)
                .orderByAsc("row_no"));
        return hits.isEmpty() ? null : hits.get(0);
    }

    private QueryWrapper<TAmssDiffRow> batchWrapper(String batchNo) {
        return new QueryWrapper<TAmssDiffRow>()
                .eq("del_flag", 0)
                .eq("batch_no", batchNo);
    }

    private static int statusOf(TAmssDiffRow r) {
        return r.getStatus() == null ? STATUS_PENDING : r.getStatus().intValue();
    }

    private static int nvl(Integer v) {
        return v == null ? 1 : v.intValue();
    }

    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }
}
