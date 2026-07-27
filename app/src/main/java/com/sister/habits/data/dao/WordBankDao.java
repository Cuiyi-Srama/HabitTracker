package com.sister.habits.data.dao;

import androidx.room.*;
import com.sister.habits.data.models.WordBank;
import java.util.List;

@Dao
public interface WordBankDao {

    @Query("SELECT * FROM word_banks ORDER BY downloadedAt DESC")
    List<WordBank> getAll();

    @Query("SELECT * FROM word_banks WHERE active = 1 LIMIT 1")
    WordBank getActive();

    @Query("SELECT * FROM word_banks WHERE id = :id LIMIT 1")
    WordBank getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WordBank bank);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<WordBank> banks);

    @Update
    void update(WordBank bank);

    @Query("UPDATE word_banks SET active = 0")
    void deactivateAll();

    @Query("UPDATE word_banks SET active = 1 WHERE id = :id")
    void setActive(String id);

    @Query("UPDATE word_banks SET wordCount = :count WHERE id = :id")
    void updateWordCount(String id, int count);

    @Delete
    void delete(WordBank bank);

    @Query("DELETE FROM word_banks WHERE id = :id")
    void deleteById(String id);
}