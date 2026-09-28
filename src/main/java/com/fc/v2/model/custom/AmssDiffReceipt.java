package com.fc.v2.model.custom;

import java.math.BigDecimal;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一批对账的回执。回执合计与库内合计是同一口径、同一次回算：
 * 原清单行数 = 待核数 + 挂账数 + 已核销数（按合并前原清单行数计，合并行按并入行数展开）；
 * 一批结清（没有待核）时挂账数 + 已核销数 = 原清单行数，此时才算“已核讫”。
 * 白批一行都没有，绝不写已核讫。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "AmssDiffReceipt", description = "对账批次回执")
public class AmssDiffReceipt {

    @ApiModelProperty(value = "对账批次号")
    private String batchNo;

    @ApiModelProperty(value = "原清单行数（合并行按并入行数展开）")
    private int totalRows;

    @ApiModelProperty(value = "库内实际行数（同机同类合并后）")
    private int storedRows;

    @ApiModelProperty(value = "待核行数（按原清单行数）")
    private int pendingCount;

    @ApiModelProperty(value = "挂账行数（按原清单行数）")
    private int suspendedCount;

    @ApiModelProperty(value = "已核销行数（按原清单行数）")
    private int writtenOffCount;

    @ApiModelProperty(value = "本批争议差额合计(元)")
    private BigDecimal totalQty;

    @ApiModelProperty(value = "本批已核销额合计(元)")
    private BigDecimal writtenOffQty;

    @ApiModelProperty(value = "差额余下(元)=争议差额合计-已核销额合计")
    private BigDecimal remainQty;

    /**
     * 三方口径是否对得齐：待核+挂账+核销=原清单行数。
     * 与“已核讫”不是一回事：有待核时口径仍可平衡，但不算核讫。
     */
    @ApiModelProperty(value = "回执与库内口径是否一致")
    private boolean balanced;

    /** 已核讫：非空批且已无待核（挂账+核销=原清单行数）。白批永不核讫。 */
    @ApiModelProperty(value = "是否已核讫（白批永不核讫）")
    private boolean settled;

    @ApiModelProperty(value = "本批是否整批退回（超行数上限，一行未落）")
    private boolean rejected;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getStoredRows() {
        return storedRows;
    }

    public void setStoredRows(int storedRows) {
        this.storedRows = storedRows;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public int getSuspendedCount() {
        return suspendedCount;
    }

    public void setSuspendedCount(int suspendedCount) {
        this.suspendedCount = suspendedCount;
    }

    public int getWrittenOffCount() {
        return writtenOffCount;
    }

    public void setWrittenOffCount(int writtenOffCount) {
        this.writtenOffCount = writtenOffCount;
    }

    public BigDecimal getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(BigDecimal totalQty) {
        this.totalQty = totalQty;
    }

    public BigDecimal getWrittenOffQty() {
        return writtenOffQty;
    }

    public void setWrittenOffQty(BigDecimal writtenOffQty) {
        this.writtenOffQty = writtenOffQty;
    }

    public BigDecimal getRemainQty() {
        return remainQty;
    }

    public void setRemainQty(BigDecimal remainQty) {
        this.remainQty = remainQty;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public boolean isSettled() {
        return settled;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    public boolean isRejected() {
        return rejected;
    }

    public void setRejected(boolean rejected) {
        this.rejected = rejected;
    }
}
