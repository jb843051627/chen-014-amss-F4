package com.fc.v2.model.custom;

import java.util.List;

import com.fc.v2.model.auto.TAmssDiffRow;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一批对账行的报送封装：一个对账批次号统头，往下每一行带差异项代号与本行争议差额。
 * 行次、进展一律由系统落地，报送方不往里填。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "AmssDiffBatch", description = "通行费免收对账批次报送封装")
public class AmssDiffBatch {

    @ApiModelProperty(value = "对账批次号")
    private String batchNo;

    @ApiModelProperty(value = "本批各行（进门按清单顺次）")
    private List<TAmssDiffRow> rows;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public List<TAmssDiffRow> getRows() {
        return rows;
    }

    public void setRows(List<TAmssDiffRow> rows) {
        this.rows = rows;
    }
}
