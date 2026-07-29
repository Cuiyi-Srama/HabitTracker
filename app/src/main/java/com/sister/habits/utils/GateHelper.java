package com.sister.habits.utils;

import android.content.Context;

import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.DailyGate;
import com.sister.habits.data.models.GateConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 作业打折系统核心计算工具
 *
 * 规则：
 * - 行课期间（非假期非周末）→ 不打折，乘数 1.0
 * - 假期/周末 → 启用打折制
 * - 昨天 COMPLETED → 今天乘数 1.0（满分）
 * - 昨天 INCOMPLETE 或 AI_DETECTED → 今天乘数 = defaultPenaltyPercent/100
 * - 昨天 INCOMPLETE 但今天补交 → 乘数 = makeupPercent/100（减免）
 * - 昨天 SKIPPED（免检）→ 今天乘数 1.0
 * - 系统 disabled → 乘数 1.0
 */
public class GateHelper {
    private static final String TAG = "GateHelper";
    private static final Gson gson = new Gson();

    /**
     * 计算今日任务积分乘数
     * @return 0.0 ~ 1.0，默认 1.0
     */
    public static double getTodayMultiplier(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        GateConfig config = db.gateConfigDao().getConfig();
        if (config == null || !config.enabled) return 1.0;

        // 检查今天是否处于打折模式（假期或周末）
        if (!isDiscountMode(config)) return 1.0;

        // 查昨天状态
        String yesterday = getDateOffset(-1);
        DailyGate yesterdayGate = db.dailyGateDao().getByDate(yesterday);

        if (yesterdayGate == null) {
            // 昨天没有记录 → 默认视为正常（第一天启动系统）
            return 1.0;
        }

        switch (yesterdayGate.status) {
            case DailyGate.STATUS_COMPLETED:
            case DailyGate.STATUS_SKIPPED:
                return 1.0;

            case DailyGate.STATUS_INCOMPLETE:
            case DailyGate.STATUS_AI_DETECTED:
                // 检查今天是否补交
                String today = getToday();
                DailyGate todayGate = db.dailyGateDao().getByDate(today);
                if (todayGate != null && todayGate.isLateSubmission
                        && DailyGate.STATUS_COMPLETED.equals(todayGate.status)) {
                    return config.makeupPercent / 100.0;
                }
                return config.defaultPenaltyPercent / 100.0;

            case DailyGate.STATUS_PENDING:
                // 昨天待审核（家长还没处理）→ 默认不惩罚
                return 1.0;

            default:
                return 1.0;
        }
    }

    /**
     * 今日是否启用打折模式
     * 条件：enabled=true 且（今天在假期范围内 或（weekendMode=true 且 今天是周六日））
     */
    public static boolean isDiscountMode(GateConfig config) {
        if (config == null || !config.enabled) return false;

        Calendar cal = Calendar.getInstance();
        String today = getToday();

        // 检查假期范围
        if (isInHolidayRange(config.holidayRanges, today)) return true;

        // 检查周末
        if (config.weekendMode) {
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return true;
            }
        }
        return false;
    }

    /** 检查日期是否在JSON配置的假期范围内 */
    private static boolean isInHolidayRange(String holidayRangesJson, String dateStr) {
        if (holidayRangesJson == null || holidayRangesJson.isEmpty()
                || "[]".equals(holidayRangesJson)) return false;
        try {
            Type listType = new TypeToken<List<HolidayRange>>(){}.getType();
            List<HolidayRange> ranges = gson.fromJson(holidayRangesJson, listType);
            if (ranges == null) return false;
            for (HolidayRange range : ranges) {
                if (dateStr.compareTo(range.start) >= 0 && dateStr.compareTo(range.end) <= 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "解析假期范围失败", e);
        }
        return false;
    }

    /** 今日是否处于打折模式（便捷方法） */
    public static boolean isDiscountModeToday(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        GateConfig config = db.gateConfigDao().getConfig();
        return isDiscountMode(config);
    }

    /** 获取今天的乘数描述文本（给孩子端显示） */
    public static String getMultiplierLabel(Context context) {
        double m = getTodayMultiplier(context);
        if (m >= 1.0) return "";
        if (m <= 0.5) return " ⚠️ 今日积分打五折";
        if (m <= 0.8) return " 🔧 今日积分打八折（补交减免）";
        return " ⚠️ 今日积分 ×" + String.format("%.0f%%", m * 100);
    }

    private static String getToday() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    private static String getDateOffset(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.getTime());
    }

    /** 假期范围内部类 */
    public static class HolidayRange {
        public String start;
        public String end;
        public HolidayRange() {}
        public HolidayRange(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }
}
