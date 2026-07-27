package com.sister.habits.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * 词库元数据
 * 每个词库一条记录，用于管理多个词库的独立学习进度
 */
@Entity(tableName = "word_banks")
public class WordBank {
    @PrimaryKey
    @NonNull
    public String id;

    public String name;           // 显示名称，如 "KyleBing 初中"
    public String sourceUrl;      // 来源URL（外部下载时）
    public String sourceType;     // "builtin" | "import" | "external"
    public String gradeLabel;     // 年级标签，如 "junior"
    public int wordCount;         // 单词数量
    public long downloadedAt;     // 下载/创建时间
    public boolean active;        // 当前是否被选中使用

    public WordBank() {
        this.id = UUID.randomUUID().toString();
        this.downloadedAt = System.currentTimeMillis();
        this.active = false;
    }

    /** 创建内置词库 */
    public static WordBank createBuiltin() {
        WordBank bank = new WordBank();
        bank.id = "builtin";
        bank.name = "内置词库（小学）";
        bank.sourceType = "builtin";
        bank.gradeLabel = "primary";
        bank.active = true;
        return bank;
    }

    /** 从外部源创建词库记录 */
    public static WordBank fromExternal(String sourceId, String name, String url, String gradeLabel, int wordCount) {
        WordBank bank = new WordBank();
        bank.id = "ext_" + sourceId;
        bank.name = name;
        bank.sourceUrl = url;
        bank.sourceType = "external";
        bank.gradeLabel = gradeLabel;
        bank.wordCount = wordCount;
        return bank;
    }

    /** 从导入创建词库记录 */
    public static WordBank fromImport(String fileName, int wordCount) {
        WordBank bank = new WordBank();
        bank.id = "import_" + System.currentTimeMillis();
        bank.name = "导入: " + fileName;
        bank.sourceType = "import";
        bank.gradeLabel = "external";
        bank.wordCount = wordCount;
        return bank;
    }
}
