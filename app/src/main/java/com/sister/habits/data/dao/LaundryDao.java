package com.sister.habits.data.dao;
import androidx.room.*;
import com.sister.habits.data.models.LaundryTask;
import java.util.List;

@Dao
public interface LaundryDao {
    @Insert
    void insert(LaundryTask task);

    @Update
    void update(LaundryTask task);

    @Query("SELECT * FROM laundry_tasks WHERE date = :date ORDER BY submittedAt DESC")
    List<LaundryTask> getByDate(String date);

    @Query("SELECT * FROM laundry_tasks WHERE date = :date AND clothingType = :type LIMIT1")
    LaundryTask getByDateAndType(String date, String type);

    @Query("SELECT * FROM laundry_tasks ORDER BY submittedAt DESC")
    List<LaundryTask> getAll();

    @Query("SELECT * FROM laundry_tasks WHERE status = 'pending' ORDER BY submittedAt DESC")
    List<LaundryTask> getPending();

    @Query("SELECT COUNT(*) FROM laundry_tasks WHERE date = :date AND status = 'approved'")
    int getApprovedCountForDate(String date);
}
