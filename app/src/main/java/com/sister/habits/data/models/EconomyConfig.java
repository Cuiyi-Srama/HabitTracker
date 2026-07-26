package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 经济参数配置——可以在家长端动态调整
 * 单例表，只有一行数据
 */
@Entity(tableName = "economy_config")
public class EconomyConfig {
    @PrimaryKey
    public int id;  // 固定为 1

    // 打卡奖励
    public int checkInBaseReward = 10;
    public int streak3Bonus = 15;
    public int streak7Bonus = 50;
    public int streak14Bonus = 120;
    public int streak30Bonus = 300;

    // 单词奖励
    public int wordLearnReward = 5;
    public int wordBatchBonus10 = 10;
    public int wordBatchBonus20 = 30;

    // 任务奖励范围
    public int taskDailyMin = 20;
    public int taskDailyMax = 50;
    public int taskChallengeMin = 100;
    public int taskChallengeMax = 300;

    // 防刷限制
    public int maxDailyCoins = 500;
    public int maxDailyWords = 50;

    public EconomyConfig() {
        this.id = 1;
    }
}