package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.CheckIn;
import java.util.List;

@Dao
public interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE userId = :userId ORDER BY date DESC")
    List<CheckIn> getByUser(String userId);

    @Query("SELECT * FROM check_ins WHERE userId = :userId AND date = :date LIMIT 1")
    CheckIn getByDate(String userId, String date);

    @Query("SELECT * FROM check_ins WHERE userId = :userId ORDER BY date DESC LIMIT 1")
    CheckIn getLatest(String userId);

    @Query("SELECT COUNT(*) FROM check_ins WHERE userId = :userId")
    int getTotalCheckIns(String userId);

    @Query("SELECT MAX(streakDay) FROM check_ins WHERE userId = :userId")
    int getMaxStreak(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CheckIn checkIn);

    @Update
    void update(CheckIn checkIn);

    @Query("SELECT * FROM check_ins WHERE synced = 0 ORDER BY syncTimestamp ASC")
    List<CheckIn> getUnsynced();

    @Query("UPDATE check_ins SET synced = 1 WHERE id = :id")
    void markSynced(String id);
}