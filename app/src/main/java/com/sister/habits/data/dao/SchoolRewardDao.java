package com.sister.habits.data.dao;
import androidx.room.*;
import com.sister.habits.data.models.SchoolReward;
import java.util.List;

@Dao
public interface SchoolRewardDao {
    @Insert
    void insert(SchoolReward reward);
    @Update
    void update(SchoolReward reward);
    @Delete
    void delete(SchoolReward reward);
    @Query("SELECT * FROM school_rewards ORDER BY date DESC, createdAt DESC")
    List<SchoolReward> getAll();
    @Query("SELECT SUM(points) FROM school_rewards")
    int getTotalPoints();
}
