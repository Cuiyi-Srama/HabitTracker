package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.Vocabulary;
import java.util.List;

@Dao
public interface VocabularyDao {
    @Query("SELECT * FROM vocabulary WHERE active = 1 AND mastered = 0 ORDER BY level ASC, gradeLevel ASC")
    List<Vocabulary> getActiveUnmastered();

    @Query("SELECT * FROM vocabulary WHERE mastered = 0 ORDER BY level ASC")
    List<Vocabulary> getUnmastered();

    @Query("SELECT * FROM vocabulary WHERE mastered = 1 ORDER BY masteredAt DESC")
    List<Vocabulary> getMastered();

    @Query("SELECT * FROM vocabulary WHERE category = :category AND active = 1 AND mastered = 0 ORDER BY level ASC")
    List<Vocabulary> getByCategory(String category);

    @Query("SELECT * FROM vocabulary WHERE gradeLevel = :grade AND active = 1 ORDER BY category ASC")
    List<Vocabulary> getByGradeLevel(String grade);

    @Query("SELECT * FROM vocabulary WHERE active = 1 ORDER BY RANDOM() LIMIT :count")
    List<Vocabulary> getRandomActive(int count);

    @Query("SELECT COUNT(*) FROM vocabulary WHERE active = 1 AND mastered = 0")
    int getActiveUnmasteredCount();

    @Query("SELECT COUNT(*) FROM vocabulary WHERE mastered = 0")
    int getUnmasteredCount();

    @Query("SELECT COUNT(*) FROM vocabulary WHERE mastered = 1")
    int getMasteredCount();

    @Query("SELECT COUNT(*) FROM vocabulary WHERE active = 1")
    int getActiveCount();

    @Query("SELECT DISTINCT gradeLevel FROM vocabulary WHERE active = 1 ORDER BY gradeLevel ASC")
    List<String> getActiveGradeLevels();

    @Query("SELECT DISTINCT category FROM vocabulary WHERE gradeLevel = :grade AND active = 1 ORDER BY category ASC")
    List<String> getCategoriesByGrade(String grade);

    @Query("UPDATE vocabulary SET active = :active WHERE gradeLevel = :grade")
    void setGradeActive(String grade, boolean active);

    @Query("UPDATE vocabulary SET active = :active WHERE category = :category AND gradeLevel = :grade")
    void setCategoryActive(String grade, String category, boolean active);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Vocabulary word);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Vocabulary> words);

    @Update
    void update(Vocabulary word);

    @Query("UPDATE vocabulary SET mastered = 1, masteredAt = :now WHERE id = :id")
    void markMastered(String id, long now);

    @Query("SELECT * FROM vocabulary WHERE id = :id LIMIT 1")
    Vocabulary getById(String id);

    @Query("SELECT * FROM vocabulary ORDER BY RANDOM() LIMIT :count")
    List<Vocabulary> getRandom(int count);

    @Delete
    void delete(Vocabulary word);

    @Query("DELETE FROM vocabulary")
    void deleteAll();
}