package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAmssTeamCard;
import com.fc.v2.service.ITAmssTeamCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 跨区作业队在机手名录建档卡 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "跨区作业队在机手名录建档卡")
@Controller
@RequestMapping("/AmssTeamCardController")
public class AmssTeamCardController extends BaseController {

    private final String prefix = "admin/amssTeamCard";

    @Autowired
    private ITAmssTeamCardService amssTeamCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("amss:amssTeamCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "跨区作业队在机手名录建档卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("amss:amssTeamCard:list")
    @ResponseBody
    public ResultTable list(TAmssTeamCard record) {
        QueryWrapper<TAmssTeamCard> queryWrapper = new QueryWrapper<TAmssTeamCard>();
        startPage();
        com.github.pagehelper.PageInfo<TAmssTeamCard> page =
                new com.github.pagehelper.PageInfo<TAmssTeamCard>(amssTeamCardService.selectTAmssTeamCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "跨区作业队在机手名录建档卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("amss:amssTeamCard:add")
    @ResponseBody
    public AjaxResult add(TAmssTeamCard record) {
        return toAjax(amssTeamCardService.insertTAmssTeamCard(record));
    }

    @Log(title = "跨区作业队在机手名录建档卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("amss:amssTeamCard:edit")
    @ResponseBody
    public AjaxResult editSave(TAmssTeamCard record) {
        return toAjax(amssTeamCardService.updateTAmssTeamCard(record));
    }

    @Log(title = "跨区作业队在机手名录建档卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("amss:amssTeamCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(amssTeamCardService.deleteTAmssTeamCardByIds(ids));
    }
}
