package com.sister.habits.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
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
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}