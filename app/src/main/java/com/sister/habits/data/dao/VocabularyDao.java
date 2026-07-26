package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.Vocabulary;
import java.util.List;

@Dao
public interface VocabularyDao {
    @Query("SELECT * FROM vocabulary WHERE mastered = 0 ORDER BY level ASC")
    List<Vocabulary> getUnmastered();

    @Query("SELECT * FROM vocabulary WHERE mastered = 1 ORDER BY masteredAt DESC")
    List<Vocabulary> getMastered();

    @Query("SELECT * FROM vocabulary WHERE category = :category AND mastered = 0 ORDER BY level ASC")
    List<Vocabulary> getByCategory(String category);

    @Query("SELECT COUNT(*) FROM vocabulary WHERE mastered = 0")
    int getUnmasteredCount();

    @Query("SELECT COUNT(*) FROM vocabulary WHERE mastered = 1")
    int getMasteredCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Vocabulary word);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Vocabulary> words);

    @Update
    void update(Vocabulary word);

    @Query("UPDATE vocabulary SET mastered = 1, masteredAt = :now WHERE id = :id")
    void markMastered(String id, long now);

    @Query("SELECT * FROM vocabulary ORDER BY RANDOM() LIMIT :count")
    List<Vocabulary> getRandom(int count);
}