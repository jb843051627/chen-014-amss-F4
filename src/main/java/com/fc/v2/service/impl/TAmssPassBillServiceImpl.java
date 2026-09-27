package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TAmssPassBillMapper;
import com.fc.v2.model.auto.TAmssPassBill;
import com.fc.v2.service.ITAmssPassBillService;

/**
 * 跨区作业证逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAmssPassBillServiceImpl implements ITAmssPassBillService {

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TAmssPassBillMapper amssPassBillMapper;

    @Override
    public TAmssPassBill selectTAmssPassBillById(Long id) {
        return this.amssPassBillMapper.selectById(id);
    }

    @Override
    public TAmssPassBill approve(Long id, String approver, String comment) {
        TAmssPassBill r = this.amssPassBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        r.setNodeNo(Integer.valueOf((r.getNodeNo() == null ? 0 : r.getNodeNo()) + 1));
        r.setStatus(Integer.valueOf(r.getNodeNo() >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING));
        this.amssPassBillMapper.updateById(r);
        return r;
    }

    @Override
    public TAmssPassBill reject(Long id, String approver, String comment) {
        TAmssPassBill r = this.amssPassBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        this.amssPassBillMapper.updateById(r);
        return r;
    }

    @Override
    public TAmssPassBill rollback(Long id, String comment) {
        TAmssPassBill r = this.amssPassBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        r.setNodeNo(Integer.valueOf(Math.max(0, node - 1)));
        this.amssPassBillMapper.updateById(r);
        return r;
    }
}
