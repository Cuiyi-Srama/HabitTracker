package com.sister.habits.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * 单词记录
 */
@Entity(tableName = "vocabulary")
public class Vocabulary {
    @PrimaryKey
    @NonNull
    public String id;

    public String word;            // 英文单词
    public String meaning;         // 中文释义
    public String phonetic;        // 音标（可选）
    public String category;        // 分类标签，如 "animal" | "food" | "school" | ...
    public String gradeLevel;      // 年级标签 "grade1" | "grade2" | ... | "junior1" | "senior1" | ...
    public int level;              // 难度 1-5
    public boolean mastered;       // 是否已掌握
    public long masteredAt;        // 掌握时间
    public boolean active;         // 家长是否启用（用于控制哪些词出现在妹妹的列表中）

    public Vocabulary() {
        this.id = UUID.randomUUID().toString();
        this.mastered = false;
        this.active = true;
    }
}