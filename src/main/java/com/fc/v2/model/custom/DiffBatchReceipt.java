package com.fc.v2.model.custom;

import java.math.BigDecimal;

/**
 * 对账回执：报送出口与库内台账两个口径都由本对象同一回算出，
 * 页面不许自己数。挂账/核销/待核均按并入行数加权，恒等式：
 * 待核数 + 核销数 + 挂账数 = 原清单行数；结清（待核为 0）后，核销数 + 挂账数 = 原清单行数。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class DiffBatchReceipt {

    /** 对账批次号 */
    private String batchNo;

    /** 本次报送是否被收下（超上限整批退回时 false，一行不落） */
    private boolean accepted;

    /** 是否为同批次号第二次报送（只留前一遍，本回执照前一遍重算） */
    private boolean duplicate;

    /** 原清单行数（进门物理行，并笔前） */
    private int originalRows;

    /** 账内行数（同机同类并笔后落下的行目数） */
    private int storedRows;

    /** 待核行数（按并入行数加权） */
    private int pendingRows;

    /** 已核销行数（按并入行数加权） */
    private int writeoffRows;

    /** 挂账行数（按并入行数加权） */
    private int hangRows;

    /** 争议差额合计 */
    private BigDecimal totalQty = BigDecimal.ZERO;

    /** 已核销额合计 */
    private BigDecimal writeoffAmt = BigDecimal.ZERO;

    /** 差额余下（争议合计减已核销） */
    private BigDecimal remainingAmt = BigDecimal.ZERO;

    /** 是否已结清：待核为 0，此时核销数 + 挂账数 = 原清单行数 */
    private boolean settled;

    /**
     * 是否可写"已核讫"：批内至少一行，且全部核销。
     * 白批（一行没有）恒为 false——不许凭空批回执充当全辖清完。
     */
    private boolean allWrittenOff;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public int getOriginalRows() {
        return originalRows;
    }

    public void setOriginalRows(int originalRows) {
        this.originalRows = originalRows;
    }

    public int getStoredRows() {
        return storedRows;
    }

    public void setStoredRows(int storedRows) {
        this.storedRows = storedRows;
    }

    public int getPendingRows() {
        return pendingRows;
    }

    public void setPendingRows(int pendingRows) {
        this.pendingRows = pendingRows;
    }

    public int getWriteoffRows() {
        return writeoffRows;
    }

    public void setWriteoffRows(int writeoffRows) {
        this.writeoffRows = writeoffRows;
    }

    public int getHangRows() {
        return hangRows;
    }

    public void setHangRows(int hangRows) {
        this.hangRows = hangRows;
    }

    public BigDecimal getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(BigDecimal totalQty) {
        this.totalQty = totalQty == null ? BigDecimal.ZERO : totalQty;
    }

    public BigDecimal getWriteoffAmt() {
        return writeoffAmt;
    }

    public void setWriteoffAmt(BigDecimal writeoffAmt) {
        this.writeoffAmt = writeoffAmt == null ? BigDecimal.ZERO : writeoffAmt;
    }

    public BigDecimal getRemainingAmt() {
        return remainingAmt;
    }

    public void setRemainingAmt(BigDecimal remainingAmt) {
        this.remainingAmt = remainingAmt == null ? BigDecimal.ZERO : remainingAmt;
    }

    public boolean isSettled() {
        return settled;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    public boolean isAllWrittenOff() {
        return allWrittenOff;
    }

    public void setAllWrittenOff(boolean allWrittenOff) {
        this.allWrittenOff = allWrittenOff;
    }
}
