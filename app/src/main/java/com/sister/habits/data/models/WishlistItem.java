package com.sister.habits.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * 愿望清单——孩子收藏的商品
 */
@Entity(tableName = "wishlist_items")
public class WishlistItem {
    @PrimaryKey
    @NonNull
    public String id;
    public String shopItemId;     // 关联的商品ID
    public long addedAt;          // 收藏时间
    public boolean isTarget;      // 是否为当前攒分目标（愿望进度条）
    public int targetPoints;      // 目标积分（默认取商品价格，可调整）
    public WishlistItem() {
        this.id = UUID.randomUUID().toString();
        this.addedAt = System.currentTimeMillis();
        this.isTarget = false;
        this.targetPoints = 0;
    }
    public static WishlistItem create(String shopItemId) {
        WishlistItem item = new WishlistItem();
        item.shopItemId = shopItemId;
        return item;
    }
}
