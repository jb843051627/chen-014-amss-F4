package com.fc.v2.common.quartz.task;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITAmssDiffRowService;
import com.fc.v2.service.ITSysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 *测试类
 * @CLASSNAME   V2Task
 * @Description 定时调度具体工作类
 * @Auther Jan  橙寂
 * @DATE 2019/9/2 0002 15:33
 */
@Component("v2Task")
public class V2Task {

	@Autowired
    private ITSysUserService userService;

    @Autowired
    private ITAmssDiffRowService amssDiffRowService;
    /**
     * 无参的任务
     */
    public void runTask1()
    {
        System.out.println("正在执行定时任务，无参方法");
    }

    /**
     * 有参任务
     * 目前仅执行常见的数据类型  Integer Long  带L  string  带 ''  bool Double 带 d
     * @param a
     * @param b
     */
    public void runTask2(Integer a,Long b,String c,Boolean d,Double e)
    {
    	List<TSysUser> list=  userService.selectTSysUserList(new QueryWrapper<TSysUser>());
    	System.err.println("用户查询num："+list.size());
        System.out.println("正在执行定时任务，带多个参数的方法"+a+"   "+b+" "+c+"  "+d+" "+e+"执行时间:"+DateUtil.now());
    }

    /**
     * 对账行满两个年度转进历史：转过之后现行页不再露，只留批次号可查。
     * 调度串：v2Task.archiveAmssDiffRows()
     */
    public void archiveAmssDiffRows()
    {
        int n = amssDiffRowService.archiveMaturedRows();
        System.out.println("对账行转历史完成，本次转进"+n+"行，执行时间:"+DateUtil.now());
    }
}
