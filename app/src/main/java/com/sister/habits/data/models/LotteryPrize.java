package com.sister.habits.data.models;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "lottery_prizes")
public class LotteryPrize {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;          // 奖品名称
    public String icon;          // emoji图标
    public int cost;             // 单次抽奖消耗积分（统一价格，家长端设置）
    public int probability;      // 中奖概率（百分比 0-100，所有奖品总和<=100）
    public int stock;            // 库存（-1=无限）
    public boolean enabled;      // 是否启用
    public String prizeType;     // "points"=积分奖品 | "gift"=礼物奖品 | "other"=其他
    public int pointsValue;      // 积分奖品的中奖积分数（type=points时有效）
    public String shopItemId;    // 关联商城商品ID（type=gift时有效，库存联动）
    public long createdAt;
    public LotteryPrize() {
        this.cost = 10;
        this.probability = 50;
        this.stock = -1;
        this.enabled = true;
        this.prizeType = "points";
        this.pointsValue = 10;
        this.shopItemId = null;
        this.createdAt = System.currentTimeMillis();
    }
}