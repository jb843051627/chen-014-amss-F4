package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAmssOutCardMapper;
import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.service.ITAmssOutCardService;

/**
 * 变型拖拉机牌证缴回事务卡 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAmssOutCardServiceImpl implements ITAmssOutCardService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TAmssOutCardMapper amssOutCardMapper;

    @Override
    public TAmssOutCard selectTAmssOutCardById(Long id) {
        return this.amssOutCardMapper.selectById(id);
    }

    @Override
    public List<TAmssOutCard> selectTAmssOutCardList(QueryWrapper<TAmssOutCard> queryWrapper) {
        return this.amssOutCardMapper.selectList(queryWrapper);
    }

    @Override
    public TAmssOutCard advance(Long id, String remark) {
        TAmssOutCard r = this.amssOutCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.amssOutCardMapper.updateById(r);
        return r;
    }

    @Override
    public TAmssOutCard rollback(Long id, String remark) {
        TAmssOutCard r = this.amssOutCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.amssOutCardMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TAmssOutCard r = this.amssOutCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.amssOutCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TAmssOutCard r = this.amssOutCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.amssOutCardMapper.deleteById(id) > 0;
    }

}
