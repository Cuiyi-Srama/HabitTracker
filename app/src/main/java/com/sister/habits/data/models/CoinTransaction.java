package com.sister.habits.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

import java.util.UUID;

/**
 * 金币流水——每一枚金币的进出都要记账
 */
@Entity(tableName = "coin_transactions",
        indices = {@Index(value = "createdAt")})
public class CoinTransaction {
    @PrimaryKey
    @NonNull
    public String id;

    public String userId;        // 默认 "sister"
    public int amount;           // 正=获得，负=消费
    public int balanceAfter;     // 变动后余额
    public String type;          // "check_in" | "word_learn" | "task_reward" | "shop_spend" | "parent_adjust"
    public String description;   // 备注
    public long createdAt;

    // 同步字段
    public String deviceId;
    public boolean synced;
    public long syncTimestamp;

    public CoinTransaction() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.synced = false;
        this.syncTimestamp = System.currentTimeMillis();
    }

    public CoinTransaction(String userId, int amount, int balanceAfter,
                           String type, String description, String deviceId) {
        this();
        this.userId = userId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.type = type;
        this.description = description;
        this.deviceId = deviceId;
    }
}