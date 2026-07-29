package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 每日作业关卡记录
 * 每天一条记录，家长审核后更新状态
 */
@Entity(tableName = "daily_gates")
public class DailyGate {
    @PrimaryKey
    public String date;           // 日期 yyyy-MM-dd

    /**
     * 状态枚举:
     * PENDING      - 待审核（孩子已提交/未到截止时间）
     * COMPLETED    - 已完成（家长确认，+completionReward分）
     * INCOMPLETE   - 未完成（触发次日打折）
     * AI_DETECTED  - AI作弊（触发次日打折，且不获得作业分）
     * SKIPPED      - 免检（生病/外出，次日正常）
     */
    public String status;

    /** 是否为补交（前一天未完成，今天补上了，触发减免8折） */
    public boolean isLateSubmission;

    /** 家长审核时间戳 */
    public long reviewedAt;

    /** 孩子提交时间戳 */
    public long submittedAt;

    /** 备注（家长可选填） */
    public String note;

    /** 同步字段 */
    public String deviceId;
    public boolean synced;
    public long syncTimestamp;

    public DailyGate() {
        this.status = "PENDING";
        this.isLateSubmission = false;
        this.reviewedAt = 0;
        this.submittedAt = 0;
        this.note = "";
        this.deviceId = "";
        this.synced = false;
        this.syncTimestamp = System.currentTimeMillis();
    }

    // ===== 状态常量 =====
    public static final String STATUS_PENDING     = "PENDING";
    public static final String STATUS_COMPLETED   = "COMPLETED";
    public static final String STATUS_INCOMPLETE  = "INCOMPLETE";
    public static final String STATUS_AI_DETECTED = "AI_DETECTED";
    public static final String STATUS_SKIPPED     = "SKIPPED";
}
