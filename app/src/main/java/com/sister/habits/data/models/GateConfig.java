package com.sister.habits.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 作业关卡配置（家长端单例配置）
 * 存储在 gate_config 表，全局仅一行 (id=1)
 */
@Entity(tableName = "gate_config")
public class GateConfig {
    @PrimaryKey
    public int id = 1;

    /** 假期日期范围 JSON: [{"start":"2026-07-01","end":"2026-08-31"}, ...] */
    public String holidayRanges;

    /** 是否启用周末打折制（行课期间的周六日也打折） */
    public boolean weekendMode;

    /** 作业完成奖励分（默认5分） */
    public int completionReward;

    /** 未完成打折乘数百分比（默认50，即五折） */
    public int defaultPenaltyPercent;

    /** 补交减免乘数百分比（默认80，即八折） */
    public int makeupPercent;

    /** 截止时间（HH:mm格式，如"12:00"，超过算"拖到下午"） */
    public String deadlineTime;

    /** 是否启用整个打折系统 */
    public boolean enabled;

    /** 最后修改时间戳 */
    public long updatedAt;

    /** 同步字段 */
    public String deviceId;
    public boolean synced;
    public long syncTimestamp;

    public GateConfig() {
        this.id = 1;
        this.holidayRanges = "[]";
        this.weekendMode = true;
        this.completionReward = 5;
        this.defaultPenaltyPercent = 50;
        this.makeupPercent = 80;
        this.deadlineTime = "12:00";
        this.enabled = true;
        this.updatedAt = System.currentTimeMillis();
        this.deviceId = "";
        this.synced = false;
        this.syncTimestamp = System.currentTimeMillis();
    }
}
