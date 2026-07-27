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
        WishlistItem.class
    },
    version = 3,
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
                "`stage` INTEGER DEFAULT 0, " +
                "`nextReviewAt` INTEGER NOT NULL, " +
                "`lastReviewedAt` INTEGER NOT NULL, " +
                "`correctCount` INTEGER DEFAULT 0, " +
                "`wrongCount` INTEGER DEFAULT 0, " +
                "UNIQUE(`wordId`))"
            );
            // vocabulary 表新增列
            database.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `gradeLevel` TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `active` INTEGER DEFAULT 1");
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
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `word_banks` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT DEFAULT '', " +
                "`sourceUrl` TEXT DEFAULT '', " +
                "`sourceType` TEXT DEFAULT 'builtin', " +
                "`gradeLabel` TEXT DEFAULT '', " +
                "`wordCount` INTEGER DEFAULT 0, " +
                "`downloadedAt` INTEGER DEFAULT 0, " +
                "`active` INTEGER DEFAULT 0, " +
                "PRIMARY KEY(`id`))"
            );
            // 创建愿望清单表
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `wishlist_items` (" +
                "`id` TEXT NOT NULL, " +
                "`shopItemId` TEXT NOT NULL, " +
                "`addedAt` INTEGER DEFAULT 0, " +
                "PRIMARY KEY(`id`))"
            );
            // vocabulary 表新增 bankId 列
            database.execSQL("ALTER TABLE `vocabulary` ADD COLUMN `bankId` TEXT DEFAULT 'builtin'");
            // word_reviews 表新增 bankId 列
            database.execSQL("ALTER TABLE `word_reviews` ADD COLUMN `bankId` TEXT DEFAULT 'builtin'");
            // 插入默认内置词库
            database.execSQL("INSERT OR IGNORE INTO `word_banks` (`id`, `name`, `sourceType`, `gradeLabel`, `active`) " +
                    "VALUES ('builtin', '内置词库（小学）', 'builtin', 'primary', 1)");
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
                    .allowMainThreadQueries().fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}