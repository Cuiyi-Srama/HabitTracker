package com.sister.habits.data.models;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "lottery_records")
public class LotteryRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String prizeName;     // 中奖奖品名
    public String prizeIcon;     // 中奖奖品图标
    public int cost;             // 消耗积分
    public long wonAt;           // 中奖时间戳
    public String deviceId;

    public LotteryRecord() {
        this.wonAt = System.currentTimeMillis();
    }
}
