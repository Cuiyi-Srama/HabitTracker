package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 经济参数配置——可以在家长端动态调整
 * 单例表，只有一行数据
 * 默认值基于设计方案：每天典型收入 40~60，娱乐 15分钟=10、半小时=18
 */
@Entity(tableName = "economy_config")
public class EconomyConfig {
    @PrimaryKey
    public int id;  // 固定为 1

    // ===== 签到奖励 =====
    public int checkInBaseReward = 10;        // 基础签到
    public int streak3Bonus = 5;              // 连续3天额外
    public int streak7Bonus = 15;             // 连续7天额外
    public int streak14Bonus = 30;            // 连续14天额外
    public int streak30Bonus = 100;           // 连续30天额外

    // ===== 单词奖励 =====
    public int wordLearnReward = 2;           // 每学一个新词答对
    public int reviewPassReward = 2;          // 复习每个词全对

    // ===== 任务奖励范围 =====
    public int taskDailyMin = 5;              // 日常任务最低
    public int taskDailyMax = 15;             // 日常任务最高
    public int taskChallengeMin = 20;         // 挑战任务最低
    public int taskChallengeMax = 50;         // 挑战任务最高

    // ===== 娱乐时间价格 =====
    public int screenTime15min = 10;          // 15分钟屏幕时间
    public int screenTime30min = 18;          // 30分钟屏幕时间（9折）
    public int screenTime60min = 30;          // 1小时屏幕时间（75折）

    // ===== 防刷限制 =====
    public int maxDailyCoins = 500;           // 每日总收入上限
    public int maxDailyWords = 10;            // 每日新词上限
    public int maxDailyReview = 30;           // 每日复习上限


    // ===== 加速器 =====
    public boolean doublePointsEnabled = false;  // 双倍积分日开关
    public String doublePointDate = null;        // 双倍积分日日期 (yyyy-MM-dd)
    public int boostStreak7 = 15;                // 连续打卡7天奖励
    public int boostWeek = 30;                   // 周勤勉奖
    public int boostMonth = 80;                  // 月度全勤奖
    public int boostBirthday = 100;              // 生日礼包
    public int boostHoliday = 50;                // 节日礼包
    public int softLimitWeekday = 60;            // 平日积分软上限
    public int softLimitWeekend = 100;           // 周末积分软上限

    public EconomyConfig() {
        this.id = 1;
    }
}