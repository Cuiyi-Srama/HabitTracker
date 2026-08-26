package com.sister.habits.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.sister.habits.data.dao.*;
import com.sister.habits.data.models.*;

@Database(
    entities = {
        CheckIn.class,
        CoinTransaction.class,
        Task.class,
        ShopItem.class,
        Redemption.class,
        Vocabulary.class,
        EconomyConfig.class,
        WordReview.class,
        WordBank.class,
        WishlistItem.class,
        CoinEarning.class,
        GateConfig.class,
        DailyGate.class,
        LaundryTask.class,
        LotteryPrize.class,
        LotteryRecord.class,
        SchoolReward.class
    },
    version = 17,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract CheckInDao checkInDao();
    public abstract CoinTransactionDao coinTransactionDao();
    public abstract TaskDao taskDao();
    public abstract ShopItemDao shopItemDao();
    public abstract RedemptionDao redemptionDao();
    public abstract VocabularyDao vocabularyDao();
    public abstract EconomyConfigDao economyConfigDao();
    public abstract WordReviewDao wordReviewDao();
    public abstract WordBankDao wordBankDao();
    public abstract WishlistDao wishlistDao();
    public abstract CoinEarningDao coinEarningDao();
    public abstract GateConfigDao gateConfigDao();
    public abstract DailyGateDao dailyGateDao();
    public abstract LaundryDao laundryDao();
    public abstract LotteryDao lotteryDao();
    public abstract SchoolRewardDao schoolRewardDao();

    /**
     * 数据库迁移 1→2：
     * - 新增 word_reviews 表（艾宾浩斯复习追踪）
     * - vocabulary 表新增 gradeLevel 和 active 列
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 创建艾宾浩斯复习追踪表
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `word_reviews` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`wordId` TEXT NOT NULL, " +
                "`stage` INTEGER NOT NULL DEFAULT 0, " +
                "`nextReviewAt` INTEGER NOT NULL, " +
                "`lastReviewedAt` INTEGER NOT NULL, " +
                "`correctCount` INTEGER NOT NULL DEFAULT 0, " +
                "`wrongCount` INTEGER NOT NULL DEFAULT 0, " +
                "UNIQUE(`wordId`))"
            );
            // vocabulary 表新增列
            database.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `gradeLevel` TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1");
        }
    };

    /**
     * 数据库迁移 2→3：
     * - 新增 word_banks 表（词库元数据）
     * - 新增 wishlist_items 表（愿望清单）
     * - vocabulary 表新增 bankId 列
     * - word_reviews 表新增 bankId 列
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 创建词库元数据表
            // 规则：原语类型(int/long/boolean) → NOT NULL 不带 DEFAULT，String → 不带 NOT NULL
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `word_banks` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT, " +
                "`sourceUrl` TEXT, " +
                "`sourceType` TEXT, " +
                "`gradeLabel` TEXT, " +
                "`wordCount` INTEGER NOT NULL, " +
                "`downloadedAt` INTEGER NOT NULL, " +
                "`active` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
            );
            // 创建愿望清单表
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `wishlist_items` (" +
                "`id` TEXT NOT NULL, " +
                "`shopItemId` TEXT, " +
                "`addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
            );
            // vocabulary 表新增 bankId 列（String 类型，不带 NOT NULL）
            database.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `bankId` TEXT");
            // word_reviews 表新增 bankId 列
            database.execSQL("ALTER TABLE `word_reviews` ADD COLUMN `bankId` TEXT");
            // 插入默认内置词库（原语类型必须显式赋值）
            database.execSQL("INSERT OR IGNORE INTO `word_banks` (`id`, `name`, `sourceType`, `gradeLabel`, `wordCount`, `downloadedAt`, `active`) " +
                    "VALUES ('builtin', '内置词库（小学）', 'builtin', 'primary', 0, 0, 1)");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `gate_config` (" +
                "`id` INTEGER NOT NULL, " +
                "`holidayRanges` TEXT, " +
                "`weekendMode` INTEGER NOT NULL DEFAULT 1, " +
                "`completionReward` INTEGER NOT NULL DEFAULT 5, " +
                "`defaultPenaltyPercent` INTEGER NOT NULL DEFAULT 50, " +
                "`makeupPercent` INTEGER NOT NULL DEFAULT 80, " +
                "`deadlineTime` TEXT, " +
                "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`deviceId` TEXT, " +
                "`synced` INTEGER NOT NULL DEFAULT 0, " +
                "`syncTimestamp` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
            );
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_gates` (" +
                "`date` TEXT NOT NULL, " +
                "`status` TEXT, " +
                "`isLateSubmission` INTEGER NOT NULL DEFAULT 0, " +
                "`reviewedAt` INTEGER NOT NULL, " +
                "`submittedAt` INTEGER NOT NULL, " +
                "`note` TEXT, " +
                "`deviceId` TEXT, " +
                "`synced` INTEGER NOT NULL DEFAULT 0, " +
                "`syncTimestamp` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`))"
            );
            // 插入默认配置
            database.execSQL("INSERT OR IGNORE INTO gate_config (" +
                "id, holidayRanges, weekendMode, completionReward, " +
                "defaultPenaltyPercent, makeupPercent, deadlineTime, enabled, " +
                "updatedAt, deviceId, synced, syncTimestamp" +
                ") VALUES (1, '[]', 1, 5, 50, 80, '12:00', 1, 0, '', 0, 0)");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `laundry_tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT, " +
                "`clothingType` TEXT, " +
                "`quantity` INTEGER NOT NULL, " +
                "`points` INTEGER NOT NULL, " +
                "`totalPoints` INTEGER NOT NULL, " +
                "`status` TEXT, " +
                "`submittedAt` INTEGER NOT NULL, " +
                "`reviewedAt` INTEGER NOT NULL, " +
                "`deviceId` TEXT, " +
                "`synced` INTEGER NOT NULL, " +
                "`syncTimestamp` INTEGER NOT NULL" +
                ")"
            );
        }
    };
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 修复 v8 的 laundry_tasks 表 schema（MIGRATION_7_8 带了错误的 DEFAULT 值）
            database.execSQL("DROP TABLE IF EXISTS `laundry_tasks`");
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `laundry_tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT, " +
                "`clothingType` TEXT, " +
                "`quantity` INTEGER NOT NULL, " +
                "`points` INTEGER NOT NULL, " +
                "`totalPoints` INTEGER NOT NULL, " +
                "`status` TEXT, " +
                "`submittedAt` INTEGER NOT NULL, " +
                "`reviewedAt` INTEGER NOT NULL, " +
                "`deviceId` TEXT, " +
                "`synced` INTEGER NOT NULL, " +
                "`syncTimestamp` INTEGER NOT NULL" +
                ")"
            );
        }
    };
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `lottery_prizes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, " +
                "`icon` TEXT, " +
                "`cost` INTEGER NOT NULL, " +
                "`probability` INTEGER NOT NULL, " +
                "`stock` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL" +
                ")"
            );
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `lottery_records` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`prizeName` TEXT, " +
                "`prizeIcon` TEXT, " +
                "`cost` INTEGER NOT NULL, " +
                "`wonAt` INTEGER NOT NULL, " +
                "`deviceId` TEXT" +
                ")"
            );
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `school_rewards` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, " +
                "`date` TEXT, " +
                "`points` INTEGER NOT NULL, " +
                "`note` TEXT, " +
                "`badge` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`deviceId` TEXT" +
                ")"
            );
            // 插入默认奖品
            database.execSQL("INSERT OR IGNORE INTO lottery_prizes (id, name, icon, cost, probability, stock, enabled, createdAt) VALUES (1, '⭐ 星星贴纸', '⭐', 10, 40, -1, 1, 0)");
            database.execSQL("INSERT OR IGNORE INTO lottery_prizes (id, name, icon, cost, probability, stock, enabled, createdAt) VALUES (2, '🍬 糖果', '🍬', 10, 30, -1, 1, 0)");
            database.execSQL("INSERT OR IGNORE INTO lottery_prizes (id, name, icon, cost, probability, stock, enabled, createdAt) VALUES (3, '📺 看电视30分钟', '📺', 10, 15, -1, 1, 0)");
            database.execSQL("INSERT OR IGNORE INTO lottery_prizes (id, name, icon, cost, probability, stock, enabled, createdAt) VALUES (4, '🎮 游戏15分钟', '🎮', 10, 10, -1, 1, 0)");
            database.execSQL("INSERT OR IGNORE INTO lottery_prizes (id, name, icon, cost, probability, stock, enabled, createdAt) VALUES (5, '🌟 神秘大奖', '🌟', 10, 5, -1, 1, 0)");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE shop_items ADD COLUMN itemType TEXT DEFAULT 'limited'");
            database.execSQL("ALTER TABLE shop_items ADD COLUMN stock INTEGER DEFAULT -1");
        }
    };

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 安全重建 shop_items（幂等，保留原逻辑）
            database.execSQL("CREATE TABLE IF NOT EXISTS shop_items_new (" +
                "id TEXT PRIMARY KEY NOT NULL, name TEXT, description TEXT, " +
                "priceCoins INTEGER NOT NULL, iconUrl TEXT, category TEXT, " +
                "itemType TEXT DEFAULT 'limited', stock INTEGER DEFAULT -1, " +
                "active INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL DEFAULT 0)");
            // 抽奖奖品表新增字段：类型/积分值/关联商品
            try {
                database.execSQL("ALTER TABLE lottery_prizes ADD COLUMN prizeType TEXT DEFAULT 'points'");
            } catch (Exception ignored) {}
            try {
                database.execSQL("ALTER TABLE lottery_prizes ADD COLUMN pointsValue INTEGER DEFAULT 10");
            } catch (Exception ignored) {}
            try {
                database.execSQL("ALTER TABLE lottery_prizes ADD COLUMN shopItemId TEXT");
            } catch (Exception ignored) {}
        }
    };
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 重建 lottery_prizes 表，修正 schema（v12 的 ALTER 带 DEFAULT 导致校验失败）
            database.execSQL("CREATE TABLE IF NOT EXISTS lottery_prizes_new (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT, icon TEXT, " +
                "cost INTEGER NOT NULL, probability INTEGER NOT NULL, " +
                "stock INTEGER NOT NULL, enabled INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL, " +
                "prizeType TEXT, " +
                "pointsValue INTEGER NOT NULL DEFAULT 10, " +
                "shopItemId TEXT)");
            // 复制数据（旧列可能不存在，用 COALESCE 兜底）
            try {
                database.execSQL("INSERT INTO lottery_prizes_new (id, name, icon, cost, probability, stock, enabled, createdAt, prizeType, pointsValue, shopItemId) " +
                    "SELECT id, name, icon, cost, probability, stock, enabled, createdAt, " +
                    "COALESCE(prizeType, 'points'), COALESCE(pointsValue, 10), shopItemId FROM lottery_prizes");
            } catch (Exception ignored) {}
            database.execSQL("DROP TABLE lottery_prizes");
            database.execSQL("ALTER TABLE lottery_prizes_new RENAME TO lottery_prizes");
        }
    };
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // gate_config 新增赦免日期范围（外出/旅行免检）
            database.execSQL("ALTER TABLE gate_config ADD COLUMN excuseRanges TEXT");
        }
    };
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // wishlist_items 新增攒分目标（愿望进度条）
            database.execSQL("ALTER TABLE wishlist_items ADD COLUMN isTarget INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE wishlist_items ADD COLUMN targetPoints INTEGER NOT NULL DEFAULT 0");
        }
    };
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // v3.0.62：设置类实体新增 LWW 时间戳（最后修改者胜合并依据）
            // 旧数据 updatedAt=0，首次收到远端新配置（updatedAt>0）时会被覆盖，语义正确
            database.execSQL("ALTER TABLE shop_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE economy_config ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // v3.0.69：商品删除标记（tombstone）——删除改为标记删除，同步传播下架
            database.execSQL("ALTER TABLE shop_items ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
        }
    };
    /**
     * 获取单例
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class,
                            "habit_tracker.db")
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase database) {
            // 旧表有 wordBatchBonus10/20 列（已从 entity 中移除），
            // 且没有 reviewPassReward/screenTime15min/30min/60min 列。
            // SQLite 不支持 DROP COLUMN，需完整重建表。
            //
            // 使用 IF EXISTS / IF NOT EXISTS 以兼容之前迁移失败导致的脏状态：
            // #275 的迁移可能在 step1/2/3 后失败，残留 economy_config_temp 表。

            // 1. 清理可能残留的临时表
            database.execSQL("DROP TABLE IF EXISTS economy_config_temp");

            // 2. 保存旧数据（只保存旧表已有的列，旧表可能已被 #275 重建为18列版本）
            //    使用 IF NOT EXISTS 防止 #275 的 DROP TABLE 已删除旧表
            database.execSQL("CREATE TABLE IF NOT EXISTS economy_config_temp AS SELECT " +
                "id, checkInBaseReward, streak3Bonus, streak7Bonus, streak14Bonus, streak30Bonus, " +
                "wordLearnReward, " +
                "taskDailyMin, taskDailyMax, taskChallengeMin, taskChallengeMax, " +
                "maxDailyCoins, maxDailyWords, maxDailyReview " +
                "FROM economy_config");

            // 3. 删除旧表
            database.execSQL("DROP TABLE IF EXISTS economy_config");

            // 4. 创建新表（匹配 EconomyConfig.java 的 18 个字段）
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `economy_config` (" +
                "`id` INTEGER NOT NULL, " +
                "`checkInBaseReward` INTEGER NOT NULL DEFAULT 10, " +
                "`streak3Bonus` INTEGER NOT NULL DEFAULT 5, " +
                "`streak7Bonus` INTEGER NOT NULL DEFAULT 15, " +
                "`streak14Bonus` INTEGER NOT NULL DEFAULT 30, " +
                "`streak30Bonus` INTEGER NOT NULL DEFAULT 100, " +
                "`wordLearnReward` INTEGER NOT NULL DEFAULT 2, " +
                "`reviewPassReward` INTEGER NOT NULL DEFAULT 2, " +
                "`taskDailyMin` INTEGER NOT NULL DEFAULT 5, " +
                "`taskDailyMax` INTEGER NOT NULL DEFAULT 15, " +
                "`taskChallengeMin` INTEGER NOT NULL DEFAULT 20, " +
                "`taskChallengeMax` INTEGER NOT NULL DEFAULT 50, " +
                "`screenTime15min` INTEGER NOT NULL DEFAULT 10, " +
                "`screenTime30min` INTEGER NOT NULL DEFAULT 18, " +
                "`screenTime60min` INTEGER NOT NULL DEFAULT 30, " +
                "`maxDailyCoins` INTEGER NOT NULL DEFAULT 500, " +
                "`maxDailyWords` INTEGER NOT NULL DEFAULT 10, " +
                "`maxDailyReview` INTEGER NOT NULL DEFAULT 30, " +
                "PRIMARY KEY(`id`))"
            );

            // 5. 从临时表复制数据（新列用字面默认值，因为旧表没有这些列）
            database.execSQL(
                "INSERT OR IGNORE INTO economy_config (" +
                "id, checkInBaseReward, streak3Bonus, streak7Bonus, streak14Bonus, streak30Bonus, " +
                "wordLearnReward, reviewPassReward, " +
                "taskDailyMin, taskDailyMax, taskChallengeMin, taskChallengeMax, " +
                "screenTime15min, screenTime30min, screenTime60min, " +
                "maxDailyCoins, maxDailyWords, maxDailyReview" +
                ") SELECT " +
                "id, checkInBaseReward, streak3Bonus, streak7Bonus, streak14Bonus, streak30Bonus, " +
                "wordLearnReward, 2, " +   // reviewPassReward 字面默认值
                "taskDailyMin, taskDailyMax, taskChallengeMin, taskChallengeMax, " +
                "10, 18, 30, " +           // screenTime15/30/60min 字面默认值
                "maxDailyCoins, maxDailyWords, maxDailyReview " +
                "FROM economy_config_temp"
            );

            // 6. 清理临时表
            database.execSQL("DROP TABLE IF EXISTS economy_config_temp");

            // 7. 确保默认行存在（如果旧表已经被 #275 清空/损坏）
            database.execSQL("INSERT OR IGNORE INTO economy_config (" +
                "id, checkInBaseReward, streak3Bonus, streak7Bonus, streak14Bonus, streak30Bonus, " +
                "wordLearnReward, reviewPassReward, " +
                "taskDailyMin, taskDailyMax, taskChallengeMin, taskChallengeMax, " +
                "screenTime15min, screenTime30min, screenTime60min, " +
                "maxDailyCoins, maxDailyWords, maxDailyReview" +
                ") VALUES (" +
                "1, 10, 5, 15, 30, 100, " +
                "2, 2, " +
                "5, 15, 20, 50, " +
                "10, 18, 30, " +
                "500, 10, 30" +
                ")");
        }
    };


    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `coin_earnings` (" +
                "`id` TEXT NOT NULL, " +
                "`userId` TEXT NOT NULL DEFAULT 'sister', " +
                "`amount` INTEGER NOT NULL, " +
                "`sourceType` TEXT, " +
                "`sourceId` TEXT, " +
                "`description` TEXT, " +
                "`status` TEXT NOT NULL DEFAULT 'pending', " +
                "`requestedAt` INTEGER NOT NULL, " +
                "`confirmedAt` INTEGER NOT NULL DEFAULT 0, " +
                "`rejectedAt` INTEGER NOT NULL DEFAULT 0, " +
                "`deviceId` TEXT, " +
                "`synced` INTEGER NOT NULL DEFAULT 0, " +
                "`syncTimestamp` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
            );
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `doublePointsEnabled` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `doublePointDate` TEXT");
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `boostStreak7` INTEGER NOT NULL DEFAULT 15");
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `boostWeek` INTEGER NOT NULL DEFAULT 30");
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `boostMonth` INTEGER NOT NULL DEFAULT 80");
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `boostBirthday` INTEGER NOT NULL DEFAULT 100");
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `boostHoliday` INTEGER NOT NULL DEFAULT 50");
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `softLimitWeekday` INTEGER NOT NULL DEFAULT 60");
            database.execSQL("ALTER TABLE `economy_config` ADD COLUMN `softLimitWeekend` INTEGER NOT NULL DEFAULT 100");
        }
    };
}