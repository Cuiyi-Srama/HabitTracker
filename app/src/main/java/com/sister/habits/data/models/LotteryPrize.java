package com.sister.habits.data.models;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "lottery_prizes")
public class LotteryPrize {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;          // 奖品名称
    public String icon;          // emoji图标
    public int cost;             // 单次抽奖消耗积分
    public int probability;      // 中奖概率权重（1-100，越高越易中）
    public int stock;            // 库存（-1=无限）
    public boolean enabled;      // 是否启用
    public long createdAt;

    public LotteryPrize() {
        this.cost = 10;
        this.probability = 50;
        this.stock = -1;
        this.enabled = true;
        this.createdAt = System.currentTimeMillis();
    }
}
