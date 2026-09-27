package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAmssDiffRowMapper;
import com.fc.v2.model.auto.TAmssDiffRow;
import com.fc.v2.service.ITAmssDiffRowService;

/**
 * 通行费免收与作业证发放对账行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAmssDiffRowServiceImpl implements ITAmssDiffRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TAmssDiffRowMapper amssDiffRowMapper;

    @Override
    public TAmssDiffRow selectTAmssDiffRowById(Long id) {
        return this.amssDiffRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TAmssDiffRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TAmssDiffRow> errors = new java.util.ArrayList<TAmssDiffRow>();
        int seq = 0;
        for (TAmssDiffRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.amssDiffRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TAmssDiffRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.amssDiffRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TAmssDiffRow> listErrors(String batchNo) {
        return this.amssDiffRowMapper.selectList(new QueryWrapper<TAmssDiffRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
