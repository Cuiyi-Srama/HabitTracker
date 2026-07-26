package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * 单词记录（三年级词库）
 */
@Entity(tableName = "vocabulary")
public class Vocabulary {
    @PrimaryKey
    public String id;

    public String word;            // 英文单词
    public String meaning;         // 中文释义
    public String phonetic;        // 音标（可选）
    public String category;        // "animal" | "food" | "school" | "color" | "number" | ...
    public int level;              // 难度 1-5
    public boolean mastered;       // 是否已掌握
    public long masteredAt;        // 掌握时间

    public Vocabulary() {
        this.id = UUID.randomUUID().toString();
        this.mastered = false;
    }
}