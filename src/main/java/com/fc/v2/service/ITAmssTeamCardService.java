package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TAmssTeamCard;

import java.util.List;

/**
 * 跨区作业队在机手名录建档卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITAmssTeamCardService {

    /** 按主键查询 */
    TAmssTeamCard selectTAmssTeamCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TAmssTeamCard> selectTAmssTeamCardList(Wrapper<TAmssTeamCard> queryWrapper);

    /** 新增 */
    int insertTAmssTeamCard(TAmssTeamCard record);

    /** 修改 */
    int updateTAmssTeamCard(TAmssTeamCard record);

    /** 批量删除 */
    int deleteTAmssTeamCardByIds(String ids);

    /** 按主键删除 */
    int deleteTAmssTeamCardById(Long id);
}
