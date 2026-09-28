package com.fc.v2.model.custom;

import java.math.BigDecimal;

import com.fc.v2.model.auto.TAmssDiffRow;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一行的进门处理结果。一行进便过一行：本行是正常入账（待核/已核销）还是落挂账、
 * 挂账缘故是什么，全由业务服务层逐行给出，页面不自己数、不就地改。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "AmssRowOutcome", description = "对账逐行处理结果")
public class AmssRowOutcome {

    @ApiModelProperty(value = "对账批次号")
    private String batchNo;

    @ApiModelProperty(value = "原清单行次（系统照原清单顺次落地，查挂账直接报此行号）")
    private Integer rowNo;

    @ApiModelProperty(value = "机具(车牌/机械)号")
    private String machineNo;

    @ApiModelProperty(value = "差异项代号")
    private String itemCode;

    @ApiModelProperty(value = "本行争议差额(元)")
    private BigDecimal qty;

    /** 0待核 1已核销 2挂账 */
    @ApiModelProperty(value = "行进展 0待核 1已核销 2挂账")
    private Integer status;

    @ApiModelProperty(value = "本行并入的原清单行数")
    private Integer mergedCount;

    @ApiModelProperty(value = "挂账缘故（一句话只说一件事）；正常入账为空")
    private String suspendReason;

    @ApiModelProperty(value = "本行是否正常入账（false=本行落挂账，别的行照旧）")
    private boolean accepted;

    public AmssRowOutcome() {
    }

    public static AmssRowOutcome of(TAmssDiffRow row, boolean accepted) {
        AmssRowOutcome o = new AmssRowOutcome();
        o.batchNo = row.getBatchNo();
        o.rowNo = row.getRowNo();
        o.machineNo = row.getMachineNo();
        o.itemCode = row.getItemCode();
        o.qty = row.getQty();
        o.status = row.getStatus();
        o.mergedCount = row.getMergedCount();
        o.suspendReason = row.getSuspendReason();
        o.accepted = accepted;
        return o;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getMachineNo() {
        return machineNo;
    }

    public void setMachineNo(String machineNo) {
        this.machineNo = machineNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getMergedCount() {
        return mergedCount;
    }

    public void setMergedCount(Integer mergedCount) {
        this.mergedCount = mergedCount;
    }

    public String getSuspendReason() {
        return suspendReason;
    }

    public void setSuspendReason(String suspendReason) {
        this.suspendReason = suspendReason;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }
}
