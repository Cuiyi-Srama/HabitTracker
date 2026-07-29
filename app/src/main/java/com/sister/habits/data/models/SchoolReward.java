package com.sister.habits.data.models;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "school_rewards")
public class SchoolReward {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;          // 奖励名称
    public String date;          // 日期 yyyy-MM-dd
    public int points;           // 奖励积分
    public String note;          // 备注
    public String badge;         // 徽章emoji
    public long createdAt;
    public String deviceId;

    public SchoolReward() {
        this.points = 5;
        this.badge = "🏆";
        this.createdAt = System.currentTimeMillis();
    }
}
