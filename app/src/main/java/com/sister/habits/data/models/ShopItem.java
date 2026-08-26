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
    public String itemType;        // "normal"(常驻/可反复购买) | "limited"(限量/一次性)
    public int stock;              // 库存，-1=无限，仅限量商品使用
    public boolean active;         // 是否上架
    public int deleted;            // 删除标记（tombstone，v3.0.69）：1=已删除（同步传播下架），0=正常
    public long createdAt;
    public long updatedAt;         // 最后修改时间（LWW 合并依据，v3.0.62）
    public ShopItem() {
        this.id = UUID.randomUUID().toString();
        this.itemType = "limited";
        this.stock = -1;
        this.active = true;
        this.deleted = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
}