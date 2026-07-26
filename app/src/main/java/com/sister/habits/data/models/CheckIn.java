package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

import java.util.UUID;

/**
 * 每日打卡记录
 * 复合索引 [userId, date] 防止同一天重复打卡
 */
@Entity(tableName = "check_ins",
        indices = {@Index(value = {"userId", "date"}, unique = true)})
public class CheckIn {
    @PrimaryKey
    public String id;

    public String userId;      // 默认 "sister"
    public String date;        // 格式 "yyyy-MM-dd"
    public long timestamp;     // 打卡时刻
    public int streakDay;      // 连续打卡第几天
    public int coinsEarned;    // 本次获得金币
    public boolean synced;     // 是否已同步

    // 设备标识 + UUID 用于合并冲突
    public String deviceId;
    public long syncTimestamp;

    public CheckIn() {
        this.id = UUID.randomUUID().toString();
        this.synced = false;
        this.syncTimestamp = System.currentTimeMillis();
    }

    public CheckIn(String userId, String date, int streakDay, int coinsEarned, String deviceId) {
        this();
        this.userId = userId;
        this.date = date;
        this.timestamp = System.currentTimeMillis();
        this.streakDay = streakDay;
        this.coinsEarned = coinsEarned;
        this.deviceId = deviceId;
    }
}