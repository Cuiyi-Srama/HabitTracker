package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.EconomyConfig;

@Dao
public interface EconomyConfigDao {
    @Query("SELECT * FROM economy_config WHERE id = 1 LIMIT 1")
    EconomyConfig getConfig();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void setConfig(EconomyConfig config);

    @Query("UPDATE economy_config SET maxDailyWords = :maxDailyWords WHERE id = 1")
    void updateMaxDailyWords(int maxDailyWords);

    @Query("UPDATE economy_config SET " +
            "checkInBaseReward = :checkInBaseReward, " +
            "streak3Bonus = :streak3Bonus, " +
            "streak7Bonus = :streak7Bonus, " +
            "streak14Bonus = :streak14Bonus, " +
            "streak30Bonus = :streak30Bonus, " +
            "wordLearnReward = :wordLearnReward, " +
            "wordBatchBonus10 = :wordBatchBonus10, " +
            "wordBatchBonus20 = :wordBatchBonus20, " +
            "taskDailyMin = :taskDailyMin, " +
            "taskDailyMax = :taskDailyMax, " +
            "taskChallengeMin = :taskChallengeMin, " +
            "taskChallengeMax = :taskChallengeMax, " +
            "maxDailyCoins = :maxDailyCoins, " +
            "maxDailyWords = :maxDailyWords, " +
            "maxDailyReview = :maxDailyReview " +
            "WHERE id = 1")
    void updateAll(int checkInBaseReward, int streak3Bonus, int streak7Bonus,
                   int streak14Bonus, int streak30Bonus, int wordLearnReward,
                   int wordBatchBonus10, int wordBatchBonus20,
                   int taskDailyMin, int taskDailyMax,
                   int taskChallengeMin, int taskChallengeMax,
                   int maxDailyCoins, int maxDailyWords, int maxDailyReview);
}