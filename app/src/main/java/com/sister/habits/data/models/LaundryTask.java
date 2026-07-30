package com.sister.habits.data.models;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 洗衣任务记录
 * 孩子选择衣物类型+件数提交，家长审核积分
 */
@Entity(tableName = "laundry_tasks")
public class LaundryTask {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String date;           // yyyy-MM-dd
    public String clothingType;   // 衣物类型
    public int quantity;          // 件数
    public int points;            // 单品积分
    public int totalPoints;       // 总积分 = points * quantity
    public String status;         // pending/approved/rejected
    public long submittedAt;
    public long reviewedAt;
    public String deviceId;
    public boolean synced;
    public long syncTimestamp;

    public LaundryTask() {
        this.status = STATUS_PENDING;
        this.submittedAt = System.currentTimeMillis();
        this.reviewedAt = 0;
        this.deviceId = "";
        this.synced = false;
        this.syncTimestamp = System.currentTimeMillis();
        this.quantity = 1;
    }

    // ===== 衣物类型常量 =====
    public static final String TYPE_TSHIRT = "T恤";
    public static final String TYPE_SKIRT = "裙子";
    public static final String TYPE_SHORTS = "短裤";
    public static final String TYPE_THIN_PANTS = "薄长裤";
    public static final String TYPE_THICK_PANTS = "厚长裤";
    public static final String TYPE_JACKET = "外套";
    public static final String TYPE_UNDERWEAR = "内裤";
    public static final String TYPE_SOCKS = "袜子";
    public static final String TYPE_TOWEL = "毛巾";
    public static final String TYPE_SHEET = "床单";
    public static final String TYPE_PILLOWCASE = "枕套";
    public static final String TYPE_OTHER = "其他";

    public static final String[][] CLOTHING_TYPES = {
        {TYPE_TSHIRT, "6分"},
        {TYPE_SKIRT, "6分"},
        {TYPE_SHORTS, "6分"},
        {TYPE_THIN_PANTS, "7分"},
        {TYPE_THICK_PANTS, "8分"},
        {TYPE_JACKET, "8分"},
        {TYPE_UNDERWEAR, "4分"},
        {TYPE_SOCKS, "3分"},
        {TYPE_TOWEL, "5分"},
        {TYPE_SHEET, "10分"},
        {TYPE_PILLOWCASE, "5分"},
        {TYPE_OTHER, "4分"},
    };

    // ===== 状态常量 =====
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    /** 获取衣物类型对应的积分 */
    public static int getPointsForType(String type) {
        switch (type) {
            case TYPE_TSHIRT: return 6;
            case TYPE_SKIRT: return 6;
            case TYPE_SHORTS: return 6;
            case TYPE_THIN_PANTS: return 7;
            case TYPE_THICK_PANTS: return 8;
            case TYPE_JACKET: return 8;
            case TYPE_UNDERWEAR: return 4;
            case TYPE_SOCKS: return 3;
            case TYPE_TOWEL: return 5;
            case TYPE_SHEET: return 10;
            case TYPE_PILLOWCASE: return 5;
            case TYPE_OTHER: return 4;
            default: return 4;
        }
    }
}
