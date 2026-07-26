package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.Task;
import java.util.List;

@Dao
public interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    List<Task> getAll();

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY createdAt DESC")
    List<Task> getByStatus(String status);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Task task);

    @Update
    void update(Task task);

    @Query("UPDATE tasks SET status = 'completed', completedAt = :completedAt WHERE id = :id")
    void complete(String id, long completedAt);

    @Query("SELECT * FROM tasks WHERE synced = 0 ORDER BY syncTimestamp ASC")
    List<Task> getUnsynced();

    @Query("UPDATE tasks SET synced = 1 WHERE id = :id")
    void markSynced(String id);
}