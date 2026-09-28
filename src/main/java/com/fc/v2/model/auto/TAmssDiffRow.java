package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 通行费免收与作业证发放对账行对象 t_amss_diff_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_amss_diff_row")
@ApiModel(value = "TAmssDiffRow", description = "通行费免收与作业证发放对账行")
public class TAmssDiffRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 通行费免收对账批次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "通行费免收对账批次号")
    private String batchNo;

    /** 原清单行次（进门照原清单顺次由系统抄入，并笔后认在前头那一个） */
    @TableField("row_no")
    @ApiModelProperty(value = "原清单行次")
    private Integer rowNo;

    /** 差异项代号（无证通行/证号与机主对不上/未预约通行/一证多车/跨省归属不符，一项一码） */
    @TableField("item_code")
    @ApiModelProperty(value = "差异项代号")
    private String itemCode;

    /** 机台号（同机台同差异并笔的归并依据） */
    @TableField("machine_no")
    @ApiModelProperty(value = "机台号")
    private String machineNo;

    /** 本行争议差额(元) */
    @TableField("qty")
    @ApiModelProperty(value = "本行争议差额(元)")
    private BigDecimal qty;

    /** 已核销额(元)，不得大于本行争议差额 */
    @TableField("writeoff_amt")
    @ApiModelProperty(value = "已核销额(元)")
    private BigDecimal writeoffAmt;

    /** 核销凭据号（无凭据不得核销） */
    @TableField("writeoff_voucher")
    @ApiModelProperty(value = "核销凭据号")
    private String writeoffVoucher;

    /** 本行并入的原清单行数（1 表示未并笔） */
    @TableField("merged_rows")
    @ApiModelProperty(value = "本行并入的原清单行数")
    private Integer mergedRows;

    /** 行进展 0待核 1已核销 2挂账 */
    @TableField("status")
    @ApiModelProperty(value = "行进展 0待核 1已核销 2挂账")
    private Integer status;

    /** 挂账缘故（一句话只说一件事；挂账后不得再追第二句） */
    @TableField("hang_reason")
    @ApiModelProperty(value = "挂账缘故")
    private String hangReason;

    /** 是否已转历史 0现行 1已转历史（满两个年度转进，现行页不再露） */
    @TableField("archived")
    @ApiModelProperty(value = "是否已转历史 0现行 1已转历史")
    private Integer archived;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getMachineNo() {
        return machineNo;
    }

    public void setMachineNo(String machineNo) {
        this.machineNo = machineNo;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getWriteoffAmt() {
        return writeoffAmt;
    }

    public void setWriteoffAmt(BigDecimal writeoffAmt) {
        this.writeoffAmt = writeoffAmt;
    }

    public String getWriteoffVoucher() {
        return writeoffVoucher;
    }

    public void setWriteoffVoucher(String writeoffVoucher) {
        this.writeoffVoucher = writeoffVoucher;
    }

    public Integer getMergedRows() {
        return mergedRows;
    }

    public void setMergedRows(Integer mergedRows) {
        this.mergedRows = mergedRows;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getHangReason() {
        return hangReason;
    }

    public void setHangReason(String hangReason) {
        this.hangReason = hangReason;
    }

    public Integer getArchived() {
        return archived;
    }

    public void setArchived(Integer archived) {
        this.archived = archived;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
