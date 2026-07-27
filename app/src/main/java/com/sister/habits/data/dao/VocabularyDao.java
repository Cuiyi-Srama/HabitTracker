package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.Vocabulary;
import java.util.List;

@Dao
public interface VocabularyDao {
    @Query("SELECT * FROM vocabulary WHERE active = 1 AND mastered = 0 AND bankId = :bankId ORDER BY level ASC, gradeLevel ASC")
    List<Vocabulary> getActiveUnmastered(String bankId);

    @Query("SELECT * FROM vocabulary WHERE mastered = 0 AND bankId = :bankId ORDER BY level ASC")
    List<Vocabulary> getUnmastered(String bankId);

    @Query("SELECT * FROM vocabulary WHERE mastered = 1 AND bankId = :bankId ORDER BY masteredAt DESC")
    List<Vocabulary> getMastered(String bankId);

    @Query("SELECT * FROM vocabulary WHERE category = :category AND active = 1 AND mastered = 0 AND bankId = :bankId ORDER BY level ASC")
    List<Vocabulary> getByCategory(String category, String bankId);

    @Query("SELECT * FROM vocabulary WHERE gradeLevel = :grade AND active = 1 AND bankId = :bankId ORDER BY category ASC")
    List<Vocabulary> getByGradeLevel(String grade, String bankId);

    @Query("SELECT * FROM vocabulary WHERE active = 1 AND bankId = :bankId ORDER BY RANDOM() LIMIT :count")
    List<Vocabulary> getRandomActive(int count, String bankId);

    @Query("SELECT COUNT(*) FROM vocabulary WHERE active = 1 AND mastered = 0 AND bankId = :bankId")
    int getActiveUnmasteredCount(String bankId);

    @Query("SELECT COUNT(*) FROM vocabulary WHERE mastered = 0 AND bankId = :bankId")
    int getUnmasteredCount(String bankId);

    @Query("SELECT COUNT(*) FROM vocabulary WHERE mastered = 1 AND bankId = :bankId")
    int getMasteredCount(String bankId);

    @Query("SELECT COUNT(*) FROM vocabulary WHERE active = 1 AND bankId = :bankId")
    int getActiveCount(String bankId);

    @Query("SELECT DISTINCT gradeLevel FROM vocabulary WHERE active = 1 AND bankId = :bankId ORDER BY gradeLevel ASC")
    List<String> getActiveGradeLevels(String bankId);

    @Query("SELECT DISTINCT category FROM vocabulary WHERE gradeLevel = :grade AND active = 1 AND bankId = :bankId ORDER BY category ASC")
    List<String> getCategoriesByGrade(String grade, String bankId);

    @Query("UPDATE vocabulary SET active = :active WHERE gradeLevel = :grade AND bankId = :bankId")
    void setGradeActive(String grade, boolean active, String bankId);

    @Query("UPDATE vocabulary SET active = :active WHERE category = :category AND gradeLevel = :grade AND bankId = :bankId")
    void setCategoryActive(String grade, String category, boolean active, String bankId);

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

    @Query("SELECT * FROM vocabulary WHERE bankId = :bankId ORDER BY RANDOM() LIMIT :count")
    List<Vocabulary> getRandom(int count, String bankId);

    @Query("SELECT * FROM vocabulary")
    List<Vocabulary> getAll();

    @Query("SELECT * FROM vocabulary WHERE bankId = :bankId")
    List<Vocabulary> getByBankId(String bankId);

    @Delete
    void delete(Vocabulary word);

    @Query("DELETE FROM vocabulary")
    void deleteAll();

    @Query("DELETE FROM vocabulary WHERE bankId = :bankId")
    void deleteByBankId(String bankId);
}