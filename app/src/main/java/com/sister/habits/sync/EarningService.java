package com.sister.habits.sync;

import android.content.Context;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.CoinEarning;
import java.util.Calendar;
import java.util.List;

public class EarningService {
    public static int calculateTodayEstimate(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        long[] range = getTodayRange();
        return db.coinEarningDao().getTodayEstimate("sister", range[0], range[1]);
    }

    public static int calculateTodayConfirmed(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        long[] range = getTodayRange();
        return db.coinEarningDao().getTodayConfirmed("sister", range[0], range[1]);
    }

    public static int calculateTodayPending(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        long[] range = getTodayRange();
        return db.coinEarningDao().getTodayPending("sister", range[0], range[1]);
    }

    public static int getDailySoftLimit(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        com.sister.habits.data.models.EconomyConfig config = db.economyConfigDao().getConfig();
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
        if (config != null) {
            return isWeekend ? config.softLimitWeekend : config.softLimitWeekday;
        }
        return isWeekend ? 100 : 60;
    }

    public static boolean isWithinLimit(Context ctx, int newAmount) {
        int current = calculateTodayConfirmed(ctx) + calculateTodayPending(ctx);
        int limit = getDailySoftLimit(ctx);
        return (current + newAmount) <= limit;
    }

    private static long[] getTodayRange() {
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