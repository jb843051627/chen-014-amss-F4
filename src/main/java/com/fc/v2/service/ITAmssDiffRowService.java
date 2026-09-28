package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TAmssDiffBatch;
import com.fc.v2.model.auto.TAmssDiffRow;
import com.fc.v2.model.custom.DiffBatchReceipt;

/**
 * 通行费免收与作业证发放对账行 Service接口。
 *
 * 形状：一批账进出的唯一出口在业务服务层的逐行处理上——一行进便过一行；
 * 核了几行、挂了几行、差额余下多少，一律以提交方法返回的回执为准，页面不自己数，
 * 也不开放手工补一行、就地改一行的把手。差额要改，走差异清单那一路，不在报送册上动。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAmssDiffRowService {

    /** 按主键回查单行 */
    TAmssDiffRow selectTAmssDiffRowById(Long id);

    /**
     * 旧送法（签名不动）：只给批次号 + 行清单。
     * 返回原清单行数（一行进便过一行）；空批返回 0；超过单批上限整批退回返回 -1；
     * 同批次号重发只留前一遍，返回值与头一遍一致。
     */
    int submitBatch(String batchNo, List<TAmssDiffRow> rows);

    /**
     * 新送法（同名重载）：批次统头（期号、报送县站）+ 行清单，
     * 返回的回执与库内台账同一口径、同一回算。
     */
    DiffBatchReceipt submitBatch(TAmssDiffBatch batch, List<TAmssDiffRow> rows);

    /**
     * 回执重算（与报送当时同一个算法）：挂账数 + 核销数 + 待核数 = 原清单行数。
     * 批次不存在返回 null。
     */
    DiffBatchReceipt getReceipt(String batchNo);

    /**
     * 核销只针对这一行：额须大于 0 且不得大于本行争议差额，凭据号缺一不可；
     * 待核行方可核销，挂账行不得就地核销（走差异清单），已核销行不得重复核销。
     */
    TAmssDiffRow writeOff(Long id, BigDecimal amount, String voucher);

    /** 该批次挂账行（按原清单行次升序），行上直接写明出自原清单第几行、挂账缘故 */
    List<TAmssDiffRow> listHangRows(String batchNo);

    /** 现行台账（满两年度转历史后不在此页），可按批次号/差异代号过滤 */
    List<TAmssDiffRow> listCurrent(String batchNo, String itemCode);

    /** 凭批次号拉账（含已转历史行），按原清单行次升序 */
    List<TAmssDiffRow> listByBatchNo(String batchNo);

    /** 凭差异项代号拉账（含已转历史行），按批次号、原清单行次升序 */
    List<TAmssDiffRow> listByItemCode(String itemCode);

    /**
     * 满两个年度转进历史：到 at 之日已满两年的现行行置已归档，
     * 行已全部归档的批次统头一并转历史。返回转档行数。
     */
    int archiveMatured(Date at);
}
