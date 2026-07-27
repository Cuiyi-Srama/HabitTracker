package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.ShopItem;
import java.util.List;

@Dao
public interface ShopItemDao {
    @Query("SELECT * FROM shop_items WHERE active = 1 ORDER BY priceCoins ASC")
    List<ShopItem> getActive();

    @Query("SELECT * FROM shop_items ORDER BY createdAt DESC")
    List<ShopItem> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ShopItem item);

    @Update
    void update(ShopItem item);

    @Query("UPDATE shop_items SET active = :active WHERE id = :id")
    void setActive(String id, boolean active);

    @Query("SELECT * FROM shop_items WHERE id = :id")
    ShopItem getById(String id);

    @Delete
    void delete(ShopItem item);
}