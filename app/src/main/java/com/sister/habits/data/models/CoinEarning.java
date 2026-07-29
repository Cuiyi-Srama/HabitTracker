package com.sister.habits.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.UUID;

@Entity(tableName = "coin_earnings")
public class CoinEarning {
    @PrimaryKey
    @NonNull
    public String id;
    public String userId;
    public int amount;
    public String sourceType;  // "task" | "word_learn" | "check_in" | "streak_bonus" | "weekly_bonus" | "monthly_bonus" | "parent_grant"
    public String sourceId;
    public String description;
    public String status;      // "pending" | "confirmed" | "rejected"
    public long requestedAt;
    public long confirmedAt;
    public long rejectedAt;
    public String deviceId;
    public boolean synced;
    public long syncTimestamp;

    public CoinEarning() {
        this.id = UUID.randomUUID().toString();
        this.userId = "sister";
        this.status = "pending";
        this.requestedAt = System.currentTimeMillis();
        this.synced = false;
        this.syncTimestamp = System.currentTimeMillis();
    }
}