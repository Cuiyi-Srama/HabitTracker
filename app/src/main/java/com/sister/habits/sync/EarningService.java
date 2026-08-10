package com.sister.habits.sync;

import android.content.Context;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.dao.CoinEarningDao;
import com.sister.habits.data.dao.EconomyConfigDao;
import com.sister.habits.data.models.EconomyConfig;
import java.util.Calendar;

public class EarningService {

    // ==================== Context 版本（生产环境调用，委托给 DAO 版本） ====================

    public static int calculateTodayEstimate(Context ctx) {
        return calculateTodayEstimate(AppDatabase.getInstance(ctx).coinEarningDao());
    }

    public static int calculateTodayConfirmed(Context ctx) {
        return calculateTodayConfirmed(AppDatabase.getInstance(ctx).coinEarningDao());
    }

    public static int calculateTodayPending(Context ctx) {
        return calculateTodayPending(AppDatabase.getInstance(ctx).coinEarningDao());
    }

    public static int getDailySoftLimit(Context ctx) {
        return getDailySoftLimit(AppDatabase.getInstance(ctx).economyConfigDao());
    }

    public static int getDailyHardLimit(Context ctx) {
        return getDailyHardLimit(AppDatabase.getInstance(ctx).economyConfigDao());
    }

    public static boolean isWithinLimit(Context ctx, int newAmount) {
        return isWithinLimit(AppDatabase.getInstance(ctx).coinEarningDao(),
                AppDatabase.getInstance(ctx).economyConfigDao(), newAmount);
    }

    // ==================== DAO 注入版本（可单测，不依赖 Context） ====================

    public static int calculateTodayEstimate(CoinEarningDao coinDao) {
        long[] range = getTodayRange();
        return coinDao.getTodayEstimate("sister", range[0], range[1]);
    }

    public static int calculateTodayConfirmed(CoinEarningDao coinDao) {
        long[] range = getTodayRange();
        return coinDao.getTodayConfirmed("sister", range[0], range[1]);
    }

    public static int calculateTodayPending(CoinEarningDao coinDao) {
        long[] range = getTodayRange();
        return coinDao.getTodayPending("sister", range[0], range[1]);
    }

    /** 推荐每日目标值（仅供参考，不强制拦截）：平日60/周末100 */
    public static int getDailySoftLimit(EconomyConfigDao configDao) {
        EconomyConfig config = configDao.getConfig();
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
        if (config != null) {
            return isWeekend ? config.softLimitWeekend : config.softLimitWeekday;
        }
        return isWeekend ? 100 : 60;
    }

    public static boolean isWithinLimit(CoinEarningDao coinDao, EconomyConfigDao configDao, int newAmount) {
        int current = calculateTodayConfirmed(coinDao) + calculateTodayPending(coinDao);
        int limit = getDailyHardLimit(configDao);
        return (current + newAmount) <= limit;
    }

    /** 每日收入硬上限（真正拦截的上限，默认 500，家长端可调） */
    public static int getDailyHardLimit(EconomyConfigDao configDao) {
        EconomyConfig config = configDao.getConfig();
        return config != null ? config.maxDailyCoins : 500;
    }

    static long[] getTodayRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        long end = cal.getTimeInMillis();
        return new long[]{start, end};
    }
}