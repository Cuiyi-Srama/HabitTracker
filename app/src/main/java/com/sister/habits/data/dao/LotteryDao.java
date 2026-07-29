package com.sister.habits.data.dao;
import androidx.room.*;
import com.sister.habits.data.models.LotteryPrize;
import com.sister.habits.data.models.LotteryRecord;
import java.util.List;

@Dao
public interface LotteryDao {
    // 奖品管理
    @Insert
    void insertPrize(LotteryPrize prize);
    @Update
    void updatePrize(LotteryPrize prize);
    @Delete
    void deletePrize(LotteryPrize prize);
    @Query("SELECT * FROM lottery_prizes WHERE enabled = 1 ORDER BY id ASC")
    List<LotteryPrize> getEnabledPrizes();
    @Query("SELECT * FROM lottery_prizes ORDER BY id ASC")
    List<LotteryPrize> getAllPrizes();
    
    // 抽奖记录
    @Insert
    void insertRecord(LotteryRecord record);
    @Query("SELECT * FROM lottery_records ORDER BY wonAt DESC LIMIT 20")
    List<LotteryRecord> getRecentRecords();
    @Query("SELECT COUNT(*) FROM lottery_records")
    int getTotalDraws();
}
