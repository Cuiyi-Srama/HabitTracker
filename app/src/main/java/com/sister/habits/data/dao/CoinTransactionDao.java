package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.CoinTransaction;
import java.util.List;

@Dao
public interface CoinTransactionDao {
    @Query("SELECT * FROM coin_transactions WHERE userId = :userId ORDER BY createdAt DESC")
    List<CoinTransaction> getByUser(String userId);

    @Query("SELECT SUM(amount) FROM coin_transactions WHERE userId = :userId")
    Integer getBalance(String userId);

    @Query("SELECT * FROM coin_transactions WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    List<CoinTransaction> getRecent(String userId, int limit);

    @Query("SELECT SUM(amount) FROM coin_transactions WHERE userId = :userId AND type = :type AND createdAt >= :since")
    Integer getTotalByTypeSince(String userId, String type, long since);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CoinTransaction transaction);

    @Query("SELECT * FROM coin_transactions WHERE synced = 0 ORDER BY syncTimestamp ASC")
    List<CoinTransaction> getUnsynced();

    @Query("UPDATE coin_transactions SET synced = 1 WHERE id = :id")
    void markSynced(String id);
}