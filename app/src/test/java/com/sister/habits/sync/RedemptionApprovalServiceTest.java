package com.sister.habits.sync;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sister.habits.data.dao.CoinTransactionDao;
import com.sister.habits.data.dao.RedemptionDao;
import com.sister.habits.data.models.CoinTransaction;
import com.sister.habits.data.models.Redemption;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * RedemptionApprovalService 单元测试 —— v3.0.61 防双花核心
 * 覆盖：审批通过扣款 / 余额不足自动拒绝 / 拒绝不退款 / 旧数据迁移退款
 * 使用 DAO 注入重载版本，不依赖 Android Context
 */
public class RedemptionApprovalServiceTest {

    private Redemption makeRedemption(int cost) {
        Redemption r = new Redemption();
        r.coinsCost = cost;
        r.itemName = "测试商品";
        return r;
    }

    @Test
    public void approve_余额充足时扣款并确认() {
        CoinTransactionDao coinDao = mock(CoinTransactionDao.class);
        RedemptionDao redDao = mock(RedemptionDao.class);
        when(coinDao.getBalance(anyString())).thenReturn(100);
        Redemption r = makeRedemption(80);

        RedemptionApprovalService.ApproveResult result =
                RedemptionApprovalService.approve(coinDao, redDao, r, "dev-A");

        assertEquals(RedemptionApprovalService.ApproveResult.APPROVED, result);
        // 扣款流水 -80
        verify(coinDao).insert(any(CoinTransaction.class));
        // 状态 → confirmed
        verify(redDao).process(r.id, "confirmed", any(Long.class), anyString());
    }

    @Test
    public void approve_余额不足时自动拒绝且不产生流水() {
        CoinTransactionDao coinDao = mock(CoinTransactionDao.class);
        RedemptionDao redDao = mock(RedemptionDao.class);
        when(coinDao.getBalance(anyString())).thenReturn(50);
        Redemption r = makeRedemption(80);

        RedemptionApprovalService.ApproveResult result =
                RedemptionApprovalService.approve(coinDao, redDao, r, "dev-A");

        assertEquals(RedemptionApprovalService.ApproveResult.REJECTED_INSUFFICIENT, result);
        // 不产生任何流水（防双花：不引入负积分）
        verify(coinDao, never()).insert(any(CoinTransaction.class));
        // 状态 → rejected，备注含"余额不足"
        verify(redDao).process(r.id, "rejected", any(Long.class), org.mockito.ArgumentMatchers.contains("余额不足"));
    }

    @Test
    public void approve_余额恰好等于成本时允许() {
        CoinTransactionDao coinDao = mock(CoinTransactionDao.class);
        RedemptionDao redDao = mock(RedemptionDao.class);
        when(coinDao.getBalance(anyString())).thenReturn(80);
        Redemption r = makeRedemption(80);

        RedemptionApprovalService.ApproveResult result =
                RedemptionApprovalService.approve(coinDao, redDao, r, "dev-A");

        assertEquals(RedemptionApprovalService.ApproveResult.APPROVED, result);
        verify(redDao).process(r.id, "confirmed", any(Long.class), anyString());
    }

    @Test
    public void reject_直接拒绝且不产生流水() {
        RedemptionDao redDao = mock(RedemptionDao.class);
        Redemption r = makeRedemption(80);

        RedemptionApprovalService.reject(redDao, r);

        verify(redDao).process(r.id, "rejected", any(Long.class), anyString());
    }

    @Test
    public void migratePendingRefunds_存在pending时逐笔退款() {
        CoinTransactionDao coinDao = mock(CoinTransactionDao.class);
        RedemptionDao redDao = mock(RedemptionDao.class);
        when(coinDao.getBalance(anyString())).thenReturn(100);
        Redemption r1 = makeRedemption(80);
        Redemption r2 = makeRedemption(50);
        when(redDao.getByStatus("pending")).thenReturn(Arrays.asList(r1, r2));

        int refunded = RedemptionApprovalService.migratePendingRefunds(coinDao, redDao, "dev-A");

        assertEquals(2, refunded);
        // 两笔退款流水（+80 / +50），余额恢复
        verify(coinDao).insert(any(CoinTransaction.class));
        org.mockito.Mockito.verify(coinDao, org.mockito.Mockito.times(2)).insert(any(CoinTransaction.class));
        // pending 状态不变（等待家长按新规则审批）
        org.mockito.Mockito.verify(redDao, never()).process(anyString(), anyString(), any(Long.class), anyString());
    }

    @Test
    public void migratePendingRefunds_无pending时返回0() {
        CoinTransactionDao coinDao = mock(CoinTransactionDao.class);
        RedemptionDao redDao = mock(RedemptionDao.class);
        when(redDao.getByStatus("pending")).thenReturn(Collections.<Redemption>emptyList());

        int refunded = RedemptionApprovalService.migratePendingRefunds(coinDao, redDao, "dev-A");

        assertEquals(0, refunded);
        verify(coinDao, never()).insert(any(CoinTransaction.class));
    }
}