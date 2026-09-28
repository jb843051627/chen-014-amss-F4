package com.fc.v2.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.common.exception.AmssBizException;
import com.fc.v2.mapper.auto.TAmssDiffBatchMapper;
import com.fc.v2.mapper.auto.TAmssDiffRowMapper;
import com.fc.v2.model.auto.TAmssDiffBatch;
import com.fc.v2.model.auto.TAmssDiffRow;
import com.fc.v2.model.custom.DiffBatchReceipt;
import com.fc.v2.service.impl.TAmssDiffRowServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账逐行处理离线单测：H2(MySQL 兼容) 内存库跑真 SQL，不起 Spring、不连外网。
 * 一条断言对一句站规。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class TAmssDiffRowServiceTest {

    private static SqlSession session;
    private static TAmssDiffBatchMapper batchMapper;
    private static TAmssDiffRowMapper rowMapper;
    private static DataSource dataSource;

    private TAmssDiffRowServiceImpl service;

    private static final int PENDING = 0;
    private static final int WRITEOFF = 1;
    private static final int HANG = 2;

    @BeforeAll
    static void initFactory() throws Exception {
        org.springframework.jdbc.datasource.SimpleDriverDataSource ds =
                new org.springframework.jdbc.datasource.SimpleDriverDataSource();
        ds.setDriver(new org.h2.Driver());
        ds.setUrl("jdbc:h2:mem:amss_diff;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        dataSource = ds;

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("create table t_amss_diff_batch ("
                    + "id bigint primary key, batch_no varchar(64) not null, period_no varchar(64), "
                    + "org_code varchar(64), original_rows int, stored_rows int, "
                    + "archived int default 0, archived_at datetime, del_flag int default 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), "
                    + "update_time datetime, remark varchar(500), unique (batch_no))");
            st.execute("create table t_amss_diff_row ("
                    + "id bigint primary key, batch_no varchar(64), row_no int, item_code varchar(64), "
                    + "machine_no varchar(64), qty decimal(12,2), writeoff_amt decimal(12,2) default 0, "
                    + "writeoff_voucher varchar(255), merged_rows int default 1, status int, "
                    + "hang_reason varchar(500), archived int default 0, del_flag int default 0, "
                    + "create_by varchar(64), create_time datetime, update_by varchar(64), "
                    + "update_time datetime, remark varchar(500))");
        }

        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(ds);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.addMapper(TAmssDiffBatchMapper.class);
        cfg.addMapper(TAmssDiffRowMapper.class);
        fb.setConfiguration(cfg);
        GlobalConfig gc = new GlobalConfig().setMetaObjectHandler(new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createBy", String.class, "tester");
                this.strictInsertFill(metaObject, "createTime", java.util.Date.class, new java.util.Date());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateBy", String.class, "tester");
                this.strictUpdateFill(metaObject, "updateTime", java.util.Date.class, new java.util.Date());
            }
        });
        fb.setGlobalConfig(gc);
        session = fb.getObject().openSession(true);
        batchMapper = session.getMapper(TAmssDiffBatchMapper.class);
        rowMapper = session.getMapper(TAmssDiffRowMapper.class);
    }

    @AfterAll
    static void close() {
        if (session != null) {
            session.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("delete from t_amss_diff_row");
            st.execute("delete from t_amss_diff_batch");
        }
        service = new TAmssDiffRowServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "amssDiffBatchMapper", batchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "amssDiffRowMapper", rowMapper);
    }

    private TAmssDiffRow row(String machine, String code, String qty) {
        TAmssDiffRow r = new TAmssDiffRow();
        r.setMachineNo(machine);
        r.setItemCode(code);
        r.setQty(new BigDecimal(qty));
        return r;
    }

    private TAmssDiffBatch head(String no) {
        TAmssDiffBatch h = new TAmssDiffBatch();
        h.setBatchNo(no);
        h.setPeriodNo("2026-08");
        h.setOrgCode("LCX");
        return h;
    }

    @Test
    void 行次系统顺次抄入_坏行只挂本行_别的行照旧进账() {
        List<TAmssDiffRow> rows = new ArrayList<>();
        rows.add(row("M1", "NO_CERT", "100"));
        rows.add(row("M2", "NO_CERT", "-50"));
        rows.add(row("M3", "NOT_RESERVED", "200"));

        DiffBatchReceipt rc = service.submitBatch(head("B001"), rows);

        assertTrue(rc.isAccepted());
        assertEquals(3, rc.getOriginalRows());
        List<TAmssDiffRow> stored = service.listByBatchNo("B001");
        assertEquals(3, stored.size());
        // 行次照原清单顺次，系统抄入，不信用报送册
        assertEquals(1, stored.get(0).getRowNo().intValue());
        assertEquals(2, stored.get(1).getRowNo().intValue());
        assertEquals(3, stored.get(2).getRowNo().intValue());
        assertEquals(PENDING, stored.get(0).getStatus().intValue());
        assertEquals(HANG, stored.get(1).getStatus().intValue());
        assertEquals(PENDING, stored.get(2).getStatus().intValue());
        // 缘故一句话只说一件事
        assertTrue(stored.get(1).getHangReason().contains("负数"));
        // 挂账数+核销数+待核数=原清单行数；挂账差额不进合计
        assertEquals(2, rc.getPendingRows());
        assertEquals(0, rc.getWriteoffRows());
        assertEquals(1, rc.getHangRows());
        assertEquals(0, new BigDecimal("300.00").compareTo(rc.getTotalQty()));
        assertFalse(rc.isSettled());
        assertFalse(rc.isAllWrittenOff());
    }

    @Test
    void 差异代号不在五种情形只挂本行() {
        List<TAmssDiffRow> rows = new ArrayList<>();
        rows.add(row("M1", "WHATEVER", "100"));
        DiffBatchReceipt rc = service.submitBatch(head("B_CODE"), rows);
        assertEquals(HANG, service.listByBatchNo("B_CODE").get(0).getStatus().intValue());
        assertEquals(1, rc.getHangRows());
    }

    @Test
    void 冲过上限整批退回一行不落() {
        List<TAmssDiffRow> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("M" + i, "NO_CERT", "1"));
        }
        DiffBatchReceipt rc = service.submitBatch(head("B_BIG"), rows);
        assertFalse(rc.isAccepted());
        // 旧签名：超上限 -1
        assertEquals(-1, service.submitBatch("B_BIG2", rows));
        assertNull(service.getReceipt("B_BIG"));
        assertEquals(0, service.listByBatchNo("B_BIG").size());
    }

    @Test
    void 白批回执不许写已核讫() {
        DiffBatchReceipt rc = service.submitBatch(head("B_EMPTY"), new ArrayList<>());
        assertEquals(0, rc.getOriginalRows());
        assertFalse(rc.isAllWrittenOff());
        assertFalse(rc.isSettled());
        assertEquals(0, service.submitBatch("B_EMPTY2", new ArrayList<>()));
        assertNull(service.getReceipt("B_EMPTY"));
    }

    @Test
    void 同批次号重发只留前一遍不盖行不追缘故() {
        service.submitBatch(head("B_DUP"), java.util.Collections.singletonList(row("M1", "NO_CERT", "100")));

        List<TAmssDiffRow> again = new ArrayList<>();
        again.add(row("M1", "NO_CERT", "999"));
        again.add(row("M2", "NO_CERT", "100"));
        DiffBatchReceipt rc = service.submitBatch(head("B_DUP"), again);

        assertTrue(rc.isDuplicate());
        List<TAmssDiffRow> stored = service.listByBatchNo("B_DUP");
        assertEquals(1, stored.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(stored.get(0).getQty()));
        assertEquals(1, rc.getOriginalRows());
    }

    @Test
    void 同机同类两行并一条差额只计一次行次认前() {
        List<TAmssDiffRow> rows = new ArrayList<>();
        rows.add(row("M1", "NO_CERT", "100"));
        rows.add(row("M2", "NOT_RESERVED", "50"));
        rows.add(row("M1", "NO_CERT", "100"));

        DiffBatchReceipt rc = service.submitBatch(head("B_MERGE"), rows);
        List<TAmssDiffRow> stored = service.listByBatchNo("B_MERGE");
        assertEquals(2, stored.size());
        TAmssDiffRow first = stored.get(0);
        assertEquals(1, first.getRowNo().intValue());
        assertEquals(2, first.getMergedRows().intValue());
        assertEquals(0, new BigDecimal("100.00").compareTo(first.getQty()));
        // 合计只计一次
        assertEquals(0, new BigDecimal("150.00").compareTo(rc.getTotalQty()));
        // 加权后仍是原清单三行
        assertEquals(3, rc.getPendingRows());
    }

    @Test
    void 并笔先于校验_第二笔写坏不单独挂账() {
        List<TAmssDiffRow> rows = new ArrayList<>();
        rows.add(row("M1", "NO_CERT", "100"));
        rows.add(row("M2", "NOT_RESERVED", "50"));
        rows.add(row("M1", "NO_CERT", "-9"));

        DiffBatchReceipt rc = service.submitBatch(head("B_MERGE2"), rows);
        List<TAmssDiffRow> stored = service.listByBatchNo("B_MERGE2");
        assertEquals(2, stored.size());
        assertEquals(2, stored.get(0).getMergedRows().intValue());
        assertEquals(3, rc.getPendingRows());
        assertEquals(0, rc.getHangRows());
    }

    @Test
    void 核销只对这一行_额不越差_凭据齐全() {
        service.submitBatch(head("B_WO"), java.util.Collections.singletonList(row("M1", "NO_CERT", "100")));
        Long id = service.listByBatchNo("B_WO").get(0).getId();

        assertThrows(AmssBizException.class, () -> service.writeOff(id, new BigDecimal("120"), "V1"));
        assertThrows(AmssBizException.class, () -> service.writeOff(id, new BigDecimal("100"), "  "));
        assertThrows(AmssBizException.class, () -> service.writeOff(id, new BigDecimal("0"), "V1"));

        TAmssDiffRow partial = service.writeOff(id, new BigDecimal("60"), "V1");
        assertEquals(PENDING, partial.getStatus().intValue());
        assertEquals(0, new BigDecimal("60.00").compareTo(partial.getWriteoffAmt()));

        TAmssDiffRow full = service.writeOff(id, new BigDecimal("40"), "V2");
        assertEquals(WRITEOFF, full.getStatus().intValue());
        assertTrue(full.getWriteoffVoucher().contains("V1"));
        assertTrue(full.getWriteoffVoucher().contains("V2"));

        // 已核满再核，哪怕一元也不许
        assertThrows(AmssBizException.class, () -> service.writeOff(id, new BigDecimal("1"), "V3"));

        DiffBatchReceipt rc = service.getReceipt("B_WO");
        assertEquals(1, rc.getWriteoffRows());
        assertEquals(0, rc.getPendingRows());
        assertEquals(0, new BigDecimal("0.00").compareTo(rc.getRemainingAmt()));
        assertTrue(rc.isSettled());
        assertTrue(rc.isAllWrittenOff());
    }

    @Test
    void 挂账行不许就地核销() {
        service.submitBatch(head("B_HG"), java.util.Collections.singletonList(row("M1", "NO_CERT", "-1")));
        Long id = service.listByBatchNo("B_HG").get(0).getId();
        assertThrows(AmssBizException.class, () -> service.writeOff(id, new BigDecimal("1"), "V1"));
    }

    @Test
    void 较上期多出逾五十倍只挂本行() {
        service.submitBatch(head("B_P1"), java.util.Collections.singletonList(row("M1", "NO_CERT", "100")));

        // 恰好五十倍不算走样
        DiffBatchReceipt ok = service.submitBatch(head("B_P2"),
                java.util.Collections.singletonList(row("M1", "NO_CERT", "5000")));
        assertEquals(PENDING, service.listByBatchNo("B_P2").get(0).getStatus().intValue());

        // 五十一倍挂账，只挂这一行
        DiffBatchReceipt bad = service.submitBatch(head("B_P3"),
                java.util.Collections.singletonList(row("M1", "NO_CERT", "5100")));
        TAmssDiffRow hang = service.listByBatchNo("B_P3").get(0);
        assertEquals(HANG, hang.getStatus().intValue());
        assertTrue(hang.getHangReason().contains("五十倍"));
        assertEquals(1, bad.getHangRows());
    }

    @Test
    void 满两年度转历史_现行页不露只留批次号可查() {
        service.submitBatch(head("B_AR"), java.util.Collections.singletonList(row("M1", "NO_CERT", "100")));

        // 未满两年：一行不转
        int moved = service.archiveMatured(new java.util.Date());
        assertEquals(0, moved);
        assertEquals(1, service.listCurrent("B_AR", null).size());

        // 把账龄拨到三年前
        TAmssDiffRow r = service.listByBatchNo("B_AR").get(0);
        rowMapper.update(null, new UpdateWrapper<TAmssDiffRow>().eq("id", r.getId())
                .set("create_time", Timestamp.valueOf("2023-01-01 00:00:00")));

        int moved2 = service.archiveMatured(Timestamp.valueOf("2026-09-28 00:00:00"));
        assertEquals(1, moved2);
        // 现行页不再露
        assertEquals(0, service.listCurrent("B_AR", null).size());
        // 凭批次号、差异代号仍可拉
        assertEquals(1, service.listByBatchNo("B_AR").size());
        assertEquals(1, service.listByItemCode("NO_CERT").size());
        // 回执口径不变，仍闭合
        DiffBatchReceipt rc = service.getReceipt("B_AR");
        assertNotNull(rc);
        assertEquals(1, rc.getPendingRows());
    }

    @Test
    void 旧签名返回原清单行数_重复批次照头一遍() {
        int n = service.submitBatch("B_OLD", java.util.Collections.singletonList(row("M1", "NO_CERT", "100")));
        assertEquals(1, n);
        int again = service.submitBatch("B_OLD",
                java.util.Arrays.asList(row("M1", "NO_CERT", "1"), row("M2", "NO_CERT", "1")));
        assertEquals(1, again);
    }

    @Test
    void 挂账明细按行次升序且带缘故() {
        List<TAmssDiffRow> rows = new ArrayList<>();
        rows.add(row("M1", "NO_CERT", "100"));
        rows.add(row("M2", "NO_CERT", "-1"));
        rows.add(row("M3", "NO_CERT", "-2"));
        service.submitBatch(head("B_LIST"), rows);
        List<TAmssDiffRow> hangs = service.listHangRows("B_LIST");
        assertEquals(2, hangs.size());
        assertEquals(2, hangs.get(0).getRowNo().intValue());
        assertEquals(3, hangs.get(1).getRowNo().intValue());
        assertNotNull(hangs.get(0).getHangReason());
    }
}
