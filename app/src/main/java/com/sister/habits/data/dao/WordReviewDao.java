package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.WordReview;
import java.util.List;

@Dao
public interface WordReviewDao {

    @Query("SELECT * FROM word_reviews WHERE wordId = :wordId AND bankId = :bankId LIMIT 1")
    WordReview getByWordId(String wordId, String bankId);

    @Query("SELECT * FROM word_reviews WHERE nextReviewAt <= :now AND stage >= 0 AND bankId = :bankId ORDER BY nextReviewAt ASC")
    List<WordReview> getDueReviews(long now, String bankId);

    @Query("SELECT * FROM word_reviews WHERE stage >= 0 AND nextReviewAt > :now AND bankId = :bankId ORDER BY nextReviewAt ASC")
    List<WordReview> getUpcomingReviews(long now, String bankId);

    @Query("SELECT COUNT(*) FROM word_reviews WHERE nextReviewAt <= :now AND bankId = :bankId")
    int getDueCount(long now, String bankId);

    @Query("SELECT COUNT(*) FROM word_reviews WHERE stage >= 0 AND bankId = :bankId")
    int getTotalLearningCount(String bankId);

    @Query("SELECT COUNT(*) FROM word_reviews WHERE stage > :maxStage OR nextReviewAt = :mastered AND bankId = :bankId")
    int getMasteredCount(int maxStage, long mastered, String bankId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WordReview review);

    @Update
    void update(WordReview review);

    @Query("SELECT COUNT(*) FROM word_reviews WHERE lastReviewedAt >= :todayStart AND bankId = :bankId")
    int getTodayCount(long todayStart, String bankId);

    @Query("DELETE FROM word_reviews WHERE wordId = :wordId")
    void deleteByWordId(String wordId);

    @Query("DELETE FROM word_reviews")
    void deleteAll();

    @Query("DELETE FROM word_reviews WHERE bankId = :bankId")
    void deleteByBankId(String bankId);
}