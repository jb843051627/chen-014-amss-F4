package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TAmssDiffRow;
import com.fc.v2.model.custom.AmssDiffBatch;
import com.fc.v2.model.custom.AmssDiffReceipt;
import com.fc.v2.model.custom.AmssRowOutcome;

/**
 * 通行费免收与作业证发放对账行 Service。
 *
 * 这批账进出的唯一出口是 {@link #processRow} 这一逐行处理方法：一行进便过一行，
 * 行次、进展一律系统落地，页面不自己数，也不留手工增改行的把手。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAmssDiffRowService {

    /** 单批行数上限，冲过上限整批退回、一行不落 */
    int MAX_ROWS = 500;

    /** 行进展：待核 */
    int STATUS_PENDING = 0;
    /** 行进展：已核销 */
    int STATUS_WRITTEN_OFF = 1;
    /** 行进展：挂账 */
    int STATUS_SUSPENDED = 2;

    /** 按主键回查单行 */
    TAmssDiffRow selectTAmssDiffRowById(Long id);

    /**
     * 唯一的逐行进门出口。行次（原清单第几行）由系统照清单顺次落地，报送册填的不认；
     * 初始进展系统定为待核。差额走样（未填/负数/比同机同类上旬一笔大出几十倍）或
     * 差异项代号不在五种规定之内的，只把这一行落挂账并写一句只说一件事的缘故，别的行照旧。
     * 同机同类的第二笔正常行并到前头那一条：差额合计只计一次、行次认前头那一个。
     *
     * @param batchNo 对账批次号
     * @param row     进门一行（machineNo/itemCode/qty 为准，其余系统字段忽略）
     * @param rowNo   本行在原清单上的顺次（进门那一刻抄定）
     */
    AmssRowOutcome processRow(String batchNo, TAmssDiffRow row, int rowNo);

    /**
     * 整批提交（原签名不动）。逐行走 {@link #processRow}，返回**库内行数**（合并后）。
     * 同一批次号第二次送进来库里只留前一遍、一个行不盖，返回前一遍的库内行数；
     * 行数超过 {@link #MAX_ROWS} 整批退回返回 -1、一行不落；空批返回 0。
     */
    int submitBatch(String batchNo, List<TAmssDiffRow> rows);

    /**
     * 换一种送法的同名重载：批次封装进门，返回回执。
     * 回执合计与库内合计同一口径、同一次回算；白批回执不写“已核讫”。
     */
    AmssDiffReceipt submitBatch(AmssDiffBatch batch);

    /**
     * 只核销这一行。核销必带凭据号；本次核销额必须为正且累计不超过本行争议差额，
     * 多出的部分既不塞本行也不挪邻行——超额整笔拒，本行原样不动。
     * 挂账行不许就报送册核销（差额真要改，走差异清单那一路）。
     */
    TAmssDiffRow writeOff(Long id, java.math.BigDecimal writeoffQty, String voucher);

    /**
     * 回执：与库内同一口径、同一次回算。挂账数+核销数在无待核时等于原清单行数；
     * 非空批且已无待核才算已核讫，白批永不核讫。
     */
    AmssDiffReceipt receipt(String batchNo);

    /** 凭批次号拉账（含已转历史行，按原清单行次升序） */
    List<TAmssDiffRow> listByBatch(String batchNo);

    /** 凭差异项代号拉账（含已转历史行，按批次号、原清单行次升序） */
    List<TAmssDiffRow> listByItem(String itemCode);

    /** 现行账：批次内未转历史的行（转过之后现行页不再露），按原清单行次升序 */
    List<TAmssDiffRow> listCurrent(String batchNo);

    /**
     * 收下的行满两个年度转进历史：置 archive_time，现行页不再露、批次号仍可查。
     * @return 本次转进历史的行数
     */
    int archiveMaturedRows();
}
