package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * 兑换申请/记录
 * 孩子提交申请 → 家长审批（confirm/reject）
 */
@Entity(tableName = "redemptions")
public class Redemption {
    @PrimaryKey
    public String id;

    public String shopItemId;      // 商品ID
    public String itemName;        // 商品名（冗余，防止商品信息变更后历史记录丢失）
    public int coinsCost;          // 消耗金币
    public int coinsBalanceBefore; // 兑换前余额
    public int coinsBalanceAfter;  // 兑换后余额
    public String status;          // "pending" | "confirmed" | "rejected"
    public long requestedAt;       // 申请时间
    public long processedAt;       // 审批时间
    public String note;            // 家长备注

    // 同步
    public String deviceId;
    public boolean synced;
    public long syncTimestamp;

    public Redemption() {
        this.id = UUID.randomUUID().toString();
        this.status = "pending";
        this.requestedAt = System.currentTimeMillis();
        this.synced = false;
        this.syncTimestamp = System.currentTimeMillis();
    }
}