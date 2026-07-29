package com.sister.habits.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.sister.habits.data.models.DailyGate;

import java.util.List;

@Dao
public interface DailyGateDao {
    @Query("SELECT * FROM daily_gates WHERE date = :date")
    DailyGate getByDate(String date);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DailyGate gate);

    @Update
    void update(DailyGate gate);

    @Query("DELETE FROM daily_gates WHERE date = :date")
    void deleteByDate(String date);

    /** 获取最近N天的记录（用于计算连续状态） */
    @Query("SELECT * FROM daily_gates ORDER BY date DESC LIMIT :limit")
    List<DailyGate> getRecent(int limit);

    @Query("SELECT * FROM daily_gates WHERE synced = 0")
    List<DailyGate> getUnsynced();

    @Query("UPDATE daily_gates SET synced = 1 WHERE date = :date")
    void markSynced(String date);
}
