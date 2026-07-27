package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 单词复习追踪——艾宾浩斯遗忘曲线
 * 每个单词一条记录，追踪当前复习阶段和下次复习时间
 */
@Entity(tableName = "word_reviews",
        indices = {@Index(value = {"wordId"}, unique = true)})
public class WordReview {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String wordId;           // 关联 Vocabulary.id
    public String bankId;           // 关联词库ID，用于隔离不同词库的学习进度

    public int stage;               // 当前阶段 0=刚学, 1~5=复习轮次
    public long nextReviewAt;       // 下次复习时间（毫秒时间戳）
    public long lastReviewedAt;     // 上次复习时间
    public int correctCount;        // 累计答对
    public int wrongCount;          // 累计答错（仅统计，不惩罚）

    // 艾宾浩斯间隔（单位：毫秒）
    // 0→1: 当天稍后（4小时）, 1→2: 1天, 2→3: 2天, 3→4: 4天, 4→5: 7天, 5→✅: 15天后标记掌握
    public static final long[] INTERVALS = {
            4 * 3600 * 1000L,       // 阶段0→1: 4小时后
            24 * 3600 * 1000L,      // 阶段1→2: 1天后
            2 * 24 * 3600 * 1000L,  // 阶段2→3: 2天后
            4 * 24 * 3600 * 1000L,  // 阶段3→4: 4天后
            7 * 24 * 3600 * 1000L,  // 阶段4→5: 7天后
            15 * 24 * 3600 * 1000L, // 阶段5→✅: 15天后
    };

    public static final int MAX_STAGE = 5; // 通过第5轮即掌握

    public WordReview() {}

    /**
     * 创建一个新记录（刚学）
     */
    public static WordReview createNew(String wordId, String bankId) {
        WordReview r = new WordReview();
        r.wordId = wordId;
        r.bankId = bankId;
        r.stage = 0;
        r.nextReviewAt = System.currentTimeMillis() + INTERVALS[0];
        r.lastReviewedAt = System.currentTimeMillis();
        r.correctCount = 0;
        r.wrongCount = 0;
        return r;
    }

    /**
     * 答对→前进到下一阶段
     */
    public void advanceStage() {
        correctCount++;
        if (stage < MAX_STAGE) {
            stage++;
            nextReviewAt = System.currentTimeMillis() + INTERVALS[stage];
        } else {
            // 已到最高阶段，不再需要复习
            nextReviewAt = Long.MAX_VALUE;
        }
        lastReviewedAt = System.currentTimeMillis();
    }

    /**
     * 答错（不惩罚模式🌸）→ 推到明天同一阶段再试
     */
    public void failStage() {
        wrongCount++;
        // 不降级，推到明天再试
        nextReviewAt = System.currentTimeMillis() + 24 * 3600 * 1000L;
        lastReviewedAt = System.currentTimeMillis();
    }

    /**
     * 是否已到掌握标准（通过所有阶段）
     */
    public boolean isMastered() {
        return stage > MAX_STAGE || nextReviewAt == Long.MAX_VALUE;
    }

    /**
     * 当前是否到了该复习的时间
     */
    public boolean isDue() {
        return System.currentTimeMillis() >= nextReviewAt;
    }

    /**
     * 计算错误率 (0.0 ~ 1.0)
     * 用于加权复习间隔：错误率越高 → 间隔越短 → 出现越频繁
     */
    public double getErrorRate() {
        int total = correctCount + wrongCount;
        return total == 0 ? 0.0 : (double) wrongCount / (total + 1);
    }

    /**
     * 获取加权后的复习间隔（基于错误率缩短间隔）
     * @param stageIndex 阶段索引（0~5）
     * @return 加权后的间隔毫秒数
     */
    public long getWeightedInterval(int stageIndex) {
        long baseInterval = INTERVALS[stageIndex];
        if (stageIndex >= INTERVALS.length) return baseInterval;
        double errorRate = getErrorRate();
        // 错误率越高，因子越小，间隔越短
        // 错误率80% → 因子 1-0.48=0.52 → 间隔缩短到52%
        // 错误率20% → 因子 1-0.12=0.88 → 间隔缩短到88%
        double factor = 1.0 - errorRate * 0.6;
        // 最小保留30%，防止间隔过短
        return (long) (baseInterval * Math.max(factor, 0.3));
    }
}