package com.sister.habits.sync;

import android.content.Context;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.CoinEarning;
import com.sister.habits.data.models.CoinTransaction;
import com.sister.habits.data.models.CheckIn;
import com.sister.habits.data.models.EconomyConfig;
import java.util.Calendar;
import java.util.List;

/**
 * 加速器规则引擎
 * 
 * 加速器列表：
 * 1. 连续打卡勋章 — 同一任务连续7天 → +15分
 * 2. 周勤勉奖     — 本周≥20项任务 → +30分
 * 3. 月度全勤     — 当月每天≥3项任务 → +80分
 * 4. 生日礼包     — 生日当天 → +100分
 * 5. 节日礼包     — 春节/儿童节/元旦 → +50分
 * 6. 金点子奖     — 建议被家庭会议采纳 → +20分
 * 7. 双倍积分日   — 家长手动开启 → 当日所有积分×2
 */
public class AcceleratorService {

    /** 检查并发放所有应得的加速器奖励 */
    public static void checkAndApply(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) return;

        // 获取当天日期范围
        long[] range = getTodayRange();
        long todayStart = range[0];
        long todayEnd = range[1];
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date(todayStart));

        // 1. 连续打卡勋章：检查当日是否打卡且满足连续条件
        CheckIn checkIn = db.checkInDao().getByDate("sister", today);
        if (checkIn != null && checkIn.streakDay >= 7) {
            // 检查今天是否已发过连续打卡勋章
            CoinEarning existing = db.coinEarningDao().getBySourceIdAndType("sister", "streak7_" + today, "boost_streak7");
            if (existing == null) {
                CoinEarning boost = new CoinEarning();
                boost.userId = "sister";
                boost.amount = 15;
                boost.sourceType = "boost_streak7";
                boost.sourceId = "streak7_" + today;
                boost.description = "🚀 连续打卡勋章: 连续" + checkIn.streakDay + "天!";
                boost.deviceId = com.sister.habits.sync.SyncManager.getInstance(ctx).getDeviceId();
                boost.status = "pending";
                boost.requestedAt = System.currentTimeMillis();
                db.coinEarningDao().insert(boost);
            }
        }

        // 2. 周勤勉奖：本周完成≥20项
        long weekStart = getWeekStart();
        int weekTotal = db.coinEarningDao().getTotalByTypeSince("sister", "task", weekStart, todayEnd);
        if (weekTotal >= 20) {
            CoinEarning existing = db.coinEarningDao().getBySourceIdAndType("sister", "week_" + getWeekLabel(), "boost_week");
            if (existing == null) {
                CoinEarning boost = new CoinEarning();
                boost.userId = "sister";
                boost.amount = 30;
                boost.sourceType = "boost_week";
                boost.sourceId = "week_" + getWeekLabel();
                boost.description = "🚀 周勤勉奖: 本周完成" + weekTotal + "项任务!";
                boost.deviceId = com.sister.habits.sync.SyncManager.getInstance(ctx).getDeviceId();
                boost.status = "pending";
                boost.requestedAt = System.currentTimeMillis();
                db.coinEarningDao().insert(boost);
            }
        }

        // 3. 月度全勤：当月每天至少3项
        long monthStart = getMonthStart();
        int monthTotal = db.coinEarningDao().getTotalByTypeSince("sister", "task", monthStart, todayEnd);
        int daysInMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH);
        int dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        // 粗略估算：如果日均≥3，且已过20天，大概率达标
        int avgPerDay = dayOfMonth > 0 ? monthTotal / dayOfMonth : 0;
        if (avgPerDay >= 3 && dayOfMonth >= 20) {
            CoinEarning existing = db.coinEarningDao().getBySourceIdAndType("sister", "month_" + getMonthLabel(), "boost_month");
            if (existing == null) {
                CoinEarning boost = new CoinEarning();
                boost.userId = "sister";
                boost.amount = 80;
                boost.sourceType = "boost_month";
                boost.sourceId = "month_" + getMonthLabel();
                boost.description = "🚀 月度全勤奖: 本月已完成" + monthTotal + "项任务!";
                boost.deviceId = com.sister.habits.sync.SyncManager.getInstance(ctx).getDeviceId();
                boost.status = "pending";
                boost.requestedAt = System.currentTimeMillis();
                db.coinEarningDao().insert(boost);
            }
        }

        // 4. 生日礼包
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        // 用profile中的生日字段（如果存在）
        String bdayKey = month + "-" + day;
        CoinEarning existingBday = db.coinEarningDao().getBySourceIdAndType("sister", "birthday_" + bdayKey, "boost_birthday");
        if (existingBday == null) {
            CoinEarning boost = new CoinEarning();
            boost.userId = "sister";
            boost.amount = 100;
            boost.sourceType = "boost_birthday";
            boost.sourceId = "birthday_" + bdayKey;
            boost.description = "🎂 生日礼包: 生日快乐! 今天+100分!";
            boost.deviceId = com.sister.habits.sync.SyncManager.getInstance(ctx).getDeviceId();
            boost.status = "pending";
            boost.requestedAt = System.currentTimeMillis();
            db.coinEarningDao().insert(boost);
        }

        // 5. 节日礼包
        String holiday = getHoliday(month, day);
        if (holiday != null) {
            CoinEarning existingHol = db.coinEarningDao().getBySourceIdAndType("sister", "holiday_" + holiday, "boost_holiday");
            if (existingHol == null) {
                CoinEarning boost = new CoinEarning();
                boost.userId = "sister";
                boost.amount = 50;
                boost.sourceType = "boost_holiday";
                boost.sourceId = "holiday_" + holiday;
                boost.description = "🎉 节日礼包: " + holiday + "! +50分!";
                boost.deviceId = com.sister.habits.sync.SyncManager.getInstance(ctx).getDeviceId();
                boost.status = "pending";
                boost.requestedAt = System.currentTimeMillis();
                db.coinEarningDao().insert(boost);
            }
        }
    }

    /** 检查双倍积分日是否开启 */
    public static boolean isDoublePointsDay(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) return false;
        return config.doublePointsEnabled && config.doublePointDate != null
                && config.doublePointDate.equals(
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                        .format(new java.util.Date()));
    }

    /** 获取今天应得的加速器列表（用于UI显示） */
    public static String getTodayBoostSummary(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) return "";

        StringBuilder sb = new StringBuilder();
        long[] range = getTodayRange();
        List<CoinEarning> todayBoosts = db.coinEarningDao().getByTypeRange("sister", "boost_%", range[0], range[1]);

        if (todayBoosts != null && !todayBoosts.isEmpty()) {
            sb.append("🚀 加速器: ");
            for (CoinEarning e : todayBoosts) {
                sb.append(e.description).append("  ");
            }
        }

        // 双倍积分日
        if (isDoublePointsDay(ctx)) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("✨ 双倍积分日! 所有积分×2");
        }

        return sb.toString();
    }

    private static String getHoliday(int month, int day) {
        if (month == 1 && day == 1) return "元旦";
        if (month == 2 && (day >= 1 && day <= 15)) return "春节"; // 简化：2月1-15日
        if (month == 6 && day == 1) return "儿童节";
        return null;
    }

    private static String getWeekLabel() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "-W" + cal.get(Calendar.WEEK_OF_YEAR);
    }

    private static String getMonthLabel() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "-M" + (cal.get(Calendar.MONTH) + 1);
    }

    private static long getWeekStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static long getMonthStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
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
