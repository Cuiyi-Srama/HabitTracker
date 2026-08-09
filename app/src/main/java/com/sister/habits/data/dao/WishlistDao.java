package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.WishlistItem;
import java.util.List;

@Dao
public interface WishlistDao {

    @Query("SELECT * FROM wishlist_items ORDER BY addedAt DESC")
    List<WishlistItem> getAll();

    @Query("SELECT * FROM wishlist_items WHERE shopItemId = :shopItemId LIMIT 1")
    WishlistItem getByShopItemId(String shopItemId);

    @Query("SELECT COUNT(*) FROM wishlist_items")
    int getCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WishlistItem item);

    @Delete
    void delete(WishlistItem item);

    @Query("DELETE FROM wishlist_items WHERE shopItemId = :shopItemId")
    void deleteByShopItemId(String shopItemId);

    // ==================== 攒分目标（愿望进度条） ====================
    @Query("SELECT * FROM wishlist_items WHERE isTarget = 1 LIMIT 1")
    WishlistItem getTarget();

    @Query("UPDATE wishlist_items SET isTarget = 0")
    void clearTarget();

    @Query("UPDATE wishlist_items SET isTarget = 1 WHERE id = :id")
    void setTarget(String id);

    @Query("UPDATE wishlist_items SET targetPoints = :points WHERE id = :id")
    void updateTargetPoints(String id, int points);
}