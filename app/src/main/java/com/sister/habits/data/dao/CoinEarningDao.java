package com.sister.habits.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.sister.habits.data.models.CoinEarning;
import java.util.List;

@Dao
public interface CoinEarningDao {
    @Query("SELECT * FROM coin_earnings WHERE status = 'pending' ORDER BY requestedAt DESC")
    List<CoinEarning> getPending();

    @Query("SELECT * FROM coin_earnings WHERE userId = :userId ORDER BY requestedAt DESC")
    List<CoinEarning> getByUser(String userId);

    @Query("SELECT * FROM coin_earnings WHERE userId = :userId AND requestedAt >= :dayStart AND requestedAt < :dayEnd ORDER BY requestedAt ASC")
    List<CoinEarning> getByUserAndDate(String userId, long dayStart, long dayEnd);

    @Query("SELECT COUNT(*) FROM coin_earnings WHERE status = 'pending'")
    int getPendingCount();

    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_earnings WHERE userId = :userId AND status = 'confirmed' AND requestedAt >= :dayStart AND requestedAt < :dayEnd")
    int getTodayConfirmed(String userId, long dayStart, long dayEnd);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_earnings WHERE userId = :userId AND status != 'rejected' AND requestedAt >= :dayStart AND requestedAt < :dayEnd")
    int getTodayEstimate(String userId, long dayStart, long dayEnd);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_earnings WHERE userId = :userId AND status = 'pending' AND requestedAt >= :dayStart AND requestedAt < :dayEnd")
    int getTodayPending(String userId, long dayStart, long dayEnd);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CoinEarning earning);

    @Update
    void update(CoinEarning earning);

    @Query("UPDATE coin_earnings SET status = 'confirmed', confirmedAt = :time WHERE id = :id")
    void confirm(String id, long time);

    @Query("UPDATE coin_earnings SET status = 'rejected', rejectedAt = :time WHERE id = :id")
    void reject(String id, long time);

    @Query("SELECT * FROM coin_earnings WHERE synced = 0 ORDER BY syncTimestamp ASC")
    List<CoinEarning> getUnsynced();

    @Query("UPDATE coin_earnings SET synced = 1 WHERE id = :id")
    void markSynced(String id);
}