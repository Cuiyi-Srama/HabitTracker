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
        CoinEarning.class
    },
    version = 5,
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

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "habit_tracker.db"
                    )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
}