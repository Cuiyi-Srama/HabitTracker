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

    public WishlistItem() {
        this.id = UUID.randomUUID().toString();
        this.addedAt = System.currentTimeMillis();
    }

    public static WishlistItem create(String shopItemId) {
        WishlistItem item = new WishlistItem();
        item.shopItemId = shopItemId;
        return item;
    }
}
