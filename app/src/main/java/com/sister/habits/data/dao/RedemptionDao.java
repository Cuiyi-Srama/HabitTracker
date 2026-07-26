package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.Redemption;
import java.util.List;

@Dao
public interface RedemptionDao {
    @Query("SELECT * FROM redemptions ORDER BY requestedAt DESC")
    List<Redemption> getAll();

    @Query("SELECT * FROM redemptions WHERE status = :status ORDER BY requestedAt ASC")
    List<Redemption> getByStatus(String status);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Redemption redemption);

    @Update
    void update(Redemption redemption);

    @Query("UPDATE redemptions SET status = :status, processedAt = :now, note = :note WHERE id = :id")
    void process(String id, String status, long now, String note);

    @Query("SELECT * FROM redemptions WHERE synced = 0 ORDER BY syncTimestamp ASC")
    List<Redemption> getUnsynced();

    @Query("UPDATE redemptions SET synced = 1 WHERE id = :id")
    void markSynced(String id);
}