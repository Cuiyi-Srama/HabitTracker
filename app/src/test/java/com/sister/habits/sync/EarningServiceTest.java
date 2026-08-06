package com.sister.habits.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sister.habits.data.dao.CoinEarningDao;
import com.sister.habits.data.dao.EconomyConfigDao;
import com.sister.habits.data.models.EconomyConfig;

import org.junit.Test;

/**
 * EarningService 单元测试 —— 覆盖积分估算/确认/待审批/软限额/限额判断
 * 使用 DAO 注入重载版本，不依赖 Android Context
 */
public class EarningServiceTest {

    @Test
    public void calculateTodayEstimate_委托DAO查询并返回汇总() {
        CoinEarningDao dao = mock(CoinEarningDao.class);
        when(dao.getTodayEstimate(eq("sister"), anyLong(), anyLong())).thenReturn(55);

        int result = EarningService.calculateTodayEstimate(dao);

        assertEquals(55, result);
        verify(dao).getTodayEstimate(eq("sister"), anyLong(), anyLong());
    }

    @Test
    public void calculateTodayConfirmed_委托DAO查询并返回汇总() {
        CoinEarningDao dao = mock(CoinEarningDao.class);
        when(dao.getTodayConfirmed(eq("sister"), anyLong(), anyLong())).thenReturn(30);

        int result = EarningService.calculateTodayConfirmed(dao);

        assertEquals(30, result);
        verify(dao).getTodayConfirmed(eq("sister"), anyLong(), anyLong());
    }

    @Test
    public void calculateTodayPending_委托DAO查询并返回汇总() {
        CoinEarningDao dao = mock(CoinEarningDao.class);
        when(dao.getTodayPending(eq("sister"), anyLong(), anyLong())).thenReturn(20);

        int result = EarningService.calculateTodayPending(dao);

        assertEquals(20, result);
        verify(dao).getTodayPending(eq("sister"), anyLong(), anyLong());
    }

    @Test
    public void getDailySoftLimit_有配置时返回工作日或周末限额() {
        EconomyConfigDao dao = mock(EconomyConfigDao.class);
        EconomyConfig cfg = new EconomyConfig();
        cfg.softLimitWeekday = 80;
        cfg.softLimitWeekend = 150;
        when(dao.getConfig()).thenReturn(cfg);

        int limit = EarningService.getDailySoftLimit(dao);

        // 具体是 80 还是 150 取决于当天星期，断言二者之一
        assertTrue("应返回配置的工作日或周末限额", limit == 80 || limit == 150);
    }

    @Test
    public void getDailySoftLimit_无配置时返回默认值() {
        EconomyConfigDao dao = mock(EconomyConfigDao.class);
        when(dao.getConfig()).thenReturn(null);

        int limit = EarningService.getDailySoftLimit(dao);

        assertTrue("无配置时默认值应为 60(工作日)或 100(周末)", limit == 60 || limit == 100);
    }

    @Test
    public void isWithinLimit_未超限返回true_超限返回false() {
        CoinEarningDao coinDao = mock(CoinEarningDao.class);
        EconomyConfigDao cfgDao = mock(EconomyConfigDao.class);
        // 已确认 30 + 待审批 20 = 50；限额设 100（工作日/周末一致，消除星期依赖）
        when(coinDao.getTodayConfirmed(anyString(), anyLong(), anyLong())).thenReturn(30);
        when(coinDao.getTodayPending(anyString(), anyLong(), anyLong())).thenReturn(20);
        EconomyConfig cfg = new EconomyConfig();
        cfg.softLimitWeekday = 100;
        cfg.softLimitWeekend = 100;
        when(cfgDao.getConfig()).thenReturn(cfg);

        // 50 + 50 = 100 <= 100 → 允许
        assertTrue(EarningService.isWithinLimit(coinDao, cfgDao, 50));
        // 50 + 51 = 101 > 100 → 拒绝
        assertFalse(EarningService.isWithinLimit(coinDao, cfgDao, 51));
    }
}