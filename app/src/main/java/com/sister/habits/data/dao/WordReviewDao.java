package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.WordReview;
import java.util.List;

@Dao
public interface WordReviewDao {

    @Query("SELECT * FROM word_reviews WHERE wordId = :wordId LIMIT 1")
    WordReview getByWordId(String wordId);

    @Query("SELECT * FROM word_reviews WHERE nextReviewAt <= :now AND stage >= 0 ORDER BY nextReviewAt ASC")
    List<WordReview> getDueReviews(long now);

    @Query("SELECT * FROM word_reviews WHERE stage >= 0 AND nextReviewAt > :now ORDER BY nextReviewAt ASC")
    List<WordReview> getUpcomingReviews(long now);

    @Query("SELECT COUNT(*) FROM word_reviews WHERE nextReviewAt <= :now")
    int getDueCount(long now);

    @Query("SELECT COUNT(*) FROM word_reviews WHERE stage >= 0")
    int getTotalLearningCount();

    @Query("SELECT COUNT(*) FROM word_reviews WHERE stage > :maxStage OR nextReviewAt = :mastered")
    int getMasteredCount(int maxStage, long mastered);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WordReview review);

    @Update
    void update(WordReview review);

    @Query("SELECT COUNT(*) FROM word_reviews WHERE lastReviewedAt >= :todayStart")
    int getTodayCount(long todayStart);

    @Query("DELETE FROM word_reviews WHERE wordId = :wordId")
    void deleteByWordId(String wordId);

    @Query("DELETE FROM word_reviews")
    void deleteAll();
}