package com.sister.habits.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.sister.habits.data.models.GateConfig;

import java.util.List;

@Dao
public interface GateConfigDao {
    @Query("SELECT * FROM gate_config WHERE id = 1")
    GateConfig getConfig();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GateConfig config);

    @Update
    void update(GateConfig config);

    @Query("SELECT * FROM gate_config WHERE synced = 0")
    List<GateConfig> getUnsynced();

    @Query("UPDATE gate_config SET synced = 1 WHERE id = 1")
    void markSynced();
}
