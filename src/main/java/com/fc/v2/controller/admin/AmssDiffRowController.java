package com.fc.v2.controller.admin;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAmssDiffRow;
import com.fc.v2.model.custom.AmssDiffBatch;
import com.fc.v2.service.ITAmssDiffRowService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 通行费免收与作业证发放对账行 Controller（batch-process 形状）。
 *
 * 这批账进出的唯一出口在业务服务层的逐行处理方法上：这里只整批送、凭号查、
 * 逐行核销与回执，没有手工补一行、就地改一行的把手。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Api(value = "通行费免收与作业证发放对账行")
@Controller
@RequestMapping("/amssDiffRow")
public class AmssDiffRowController extends BaseController {

    @Autowired
    private ITAmssDiffRowService amssDiffRowService;

    @Log(title = "通行费免收对账整批提交", action = "submit")
    @ApiOperation(value = "整批提交（逐行处理，返回执）", notes = "一批一个批次号；重复批次只留前一遍，超上限整批退回，白批不写已核讫")
    @PostMapping("/submit")
    @ResponseBody
    public AjaxResult submit(@RequestBody AmssDiffBatch batch) {
        return AjaxResult.success(200, "操作成功", amssDiffRowService.submitBatch(batch));
    }

    @Log(title = "通行费免收对账逐行进门", action = "processRow")
    @ApiOperation(value = "逐行进门（唯一逐行出口）", notes = "行次、进展系统落地；走样行只落本行挂账")
    @PostMapping("/processRow")
    @ResponseBody
    public AjaxResult processRow(String batchNo, TAmssDiffRow row, Integer rowNo) {
        if (rowNo == null) {
            return AjaxResult.error("原清单行次不能为空");
        }
        return AjaxResult.success(200, "操作成功",
                amssDiffRowService.processRow(batchNo, row, rowNo.intValue()));
    }

    @Log(title = "通行费免收对账逐行核销", action = "writeOff")
    @ApiOperation(value = "逐行核销", notes = "只核这一行；必带凭据；累计核销不得大过差异额")
    @PostMapping("/writeOff")
    @ResponseBody
    public AjaxResult writeOff(Long id, BigDecimal writeoffQty, String voucher) {
        TAmssDiffRow row = amssDiffRowService.writeOff(id, writeoffQty, voucher);
        return AjaxResult.success(200, "操作成功", row);
    }

    @ApiOperation(value = "批次回执", notes = "回执合计与库内合计同一口径同次回算；白批不写已核讫")
    @GetMapping("/receipt")
    @ResponseBody
    public AjaxResult receipt(String batchNo) {
        return AjaxResult.success(200, "操作成功", amssDiffRowService.receipt(batchNo));
    }

    @ApiOperation(value = "凭批次号拉账", notes = "含已转历史行，按原清单行次升序")
    @GetMapping("/listByBatch")
    @ResponseBody
    public AjaxResult listByBatch(String batchNo) {
        return AjaxResult.success(200, "操作成功", amssDiffRowService.listByBatch(batchNo));
    }

    @ApiOperation(value = "凭差异项代号拉账", notes = "五种代号之一，按批次号、原清单行次升序")
    @GetMapping("/listByItem")
    @ResponseBody
    public AjaxResult listByItem(String itemCode) {
        return AjaxResult.success(200, "操作成功", amssDiffRowService.listByItem(itemCode));
    }

    @ApiOperation(value = "现行账", notes = "批次内未转历史的行，转过之后现行页不再露")
    @GetMapping("/current")
    @ResponseBody
    public AjaxResult current(String batchNo) {
        return AjaxResult.success(200, "操作成功", amssDiffRowService.listCurrent(batchNo));
    }
}
