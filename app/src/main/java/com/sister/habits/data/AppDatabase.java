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
        WordReview.class
    },
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}