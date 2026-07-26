package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * 任务系统
 */
@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey
@androidx.annotation.NonNull
        public String id;

    public String title;           // 任务标题
    public String description;     // 任务描述
    public int rewardCoins;        // 奖励金币
    public String type;            // "daily" | "challenge" | "custom"
    public String status;          // "active" | "completed" | "expired"
    public long deadline;          // 截止时间（0=无期限）
    public long createdAt;
    public long completedAt;

    // 同步
    public String deviceId;
    public boolean synced;
    public long syncTimestamp;

    public Task() {
        this.id = UUID.randomUUID().toString();
        this.status = "active";
        this.createdAt = System.currentTimeMillis();
        this.synced = false;
        this.syncTimestamp = System.currentTimeMillis();
    }
}