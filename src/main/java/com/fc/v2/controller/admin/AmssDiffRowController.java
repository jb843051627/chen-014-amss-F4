package com.fc.v2.controller.admin;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAmssDiffBatch;
import com.fc.v2.model.auto.TAmssDiffRow;
import com.fc.v2.model.custom.DiffBatchReceipt;
import com.fc.v2.service.ITAmssDiffRowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 通行费免收与作业证发放对账 Controller（batch-process 形状：整批逐行处理，无单条增改）。
 * 页面只能递报送册、领回执、按行核销、拉账，没有手工补一行、就地改一行的把手；
 * 核了几行、挂了几行、差额余下多少，一律以服务层回执为准，页面不自己数。
 *
 * @author fuce
 * @date 2026-09-28
 */
@Api(value = "通行费免收与作业证发放对账")
@Controller
@RequestMapping("/amssDiffRow")
public class AmssDiffRowController extends BaseController {

    private final String prefix = "admin/amssDiffRow";

    @Autowired
    private ITAmssDiffRowService amssDiffRowService;

    @ApiOperation(value = "对账台账跳转", notes = "对账台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("amssDiffRow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "对账批次报送", action = "submit")
    @ApiOperation(value = "整批报送（逐行处理）", notes = "批次统头+原清单行；超上限整批退回，同号只留前一遍")
    @PostMapping("/submit")
    @RequiresPermissions("amssDiffRow:submit")
    @ResponseBody
    public AjaxResult submit(TAmssDiffBatch batch, @RequestParam(value = "rows", required = false)
                             List<TAmssDiffRow> rows) {
        DiffBatchReceipt receipt = amssDiffRowService.submitBatch(batch, rows);
        return AjaxResult.successData(receipt.isAccepted() ? 200 : 400, receipt);
    }

    @ApiOperation(value = "批次回执", notes = "回执合计与库内合计同一口径同一回算")
    @GetMapping("/receipt")
    @RequiresPermissions("amssDiffRow:receipt")
    @ResponseBody
    public AjaxResult receipt(String batchNo) {
        DiffBatchReceipt receipt = amssDiffRowService.getReceipt(batchNo);
        if (receipt == null) {
            return AjaxResult.error("批次不存在");
        }
        return AjaxResult.successData(200, receipt);
    }

    @Log(title = "对账行核销", action = "writeOff")
    @ApiOperation(value = "按行核销", notes = "只针对这一行；核销额不得大于差额，凭据号缺一不可")
    @PostMapping("/writeOff")
    @RequiresPermissions("amssDiffRow:writeOff")
    @ResponseBody
    public AjaxResult writeOff(Long id, BigDecimal amount, String voucher) {
        return AjaxResult.successData(200, amssDiffRowService.writeOff(id, amount, voucher));
    }

    @ApiOperation(value = "现行台账", notes = "满两年度转历史后不露；可按批次号/差异代号过滤")
    @GetMapping("/current")
    @RequiresPermissions("amssDiffRow:list")
    @ResponseBody
    public List<TAmssDiffRow> current(String batchNo, String itemCode) {
        return amssDiffRowService.listCurrent(batchNo, itemCode);
    }

    @ApiOperation(value = "挂账行明细", notes = "按原清单行次升序，行上写明出自原清单第几行与缘故")
    @GetMapping("/hang")
    @RequiresPermissions("amssDiffRow:list")
    @ResponseBody
    public List<TAmssDiffRow> hang(String batchNo) {
        return amssDiffRowService.listHangRows(batchNo);
    }

    @ApiOperation(value = "凭批次号拉账", notes = "含已转历史行")
    @GetMapping("/byBatch")
    @RequiresPermissions("amssDiffRow:list")
    @ResponseBody
    public List<TAmssDiffRow> byBatch(String batchNo) {
        return amssDiffRowService.listByBatchNo(batchNo);
    }

    @ApiOperation(value = "凭差异代号拉账", notes = "含已转历史行")
    @GetMapping("/byItem")
    @RequiresPermissions("amssDiffRow:list")
    @ResponseBody
    public List<TAmssDiffRow> byItem(String itemCode) {
        return amssDiffRowService.listByItemCode(itemCode);
    }

    @Log(title = "对账行转历史", action = "archive")
    @ApiOperation(value = "满两个年度转历史", notes = "现行页不再露，只留批次号可查")
    @PostMapping("/archive")
    @RequiresPermissions("amssDiffRow:archive")
    @ResponseBody
    public AjaxResult archive(@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date at) {
        return AjaxResult.success("转档行数：" + amssDiffRowService.archiveMatured(at));
    }
}
