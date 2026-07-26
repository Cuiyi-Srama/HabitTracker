package com.sister.habits.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * 商城商品
 */
@Entity(tableName = "shop_items")
public class ShopItem {
    @PrimaryKey
    @NonNull
    public String id;

    public String name;            // 商品名
    public String description;     // 描述
    public int priceCoins;         // 所需金币
    public String iconUrl;         // 图标（可选）
    public String category;        // "snack" | "toy" | "game_time" | "outing" | "other"
    public boolean active;         // 是否上架
    public long createdAt;

    public ShopItem() {
        this.id = UUID.randomUUID().toString();
        this.active = true;
        this.createdAt = System.currentTimeMillis();
    }
}