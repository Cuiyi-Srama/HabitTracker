package com.sister.habits.data;

import android.content.Context;
import com.sister.habits.data.dao.EconomyConfigDao;
import com.sister.habits.data.dao.VocabularyDao;
import com.sister.habits.data.models.EconomyConfig;

/**
 * 数据库初始化——首次启动时填充种子数据
 */
public class DatabaseInitializer {

    private static boolean initialized = false;

    public static synchronized void init(Context context) {
        if (initialized) return;

        AppDatabase db = AppDatabase.getInstance(context);

        // 初始化经济参数
        EconomyConfigDao configDao = db.economyConfigDao();
        if (configDao.getConfig() == null) {
            configDao.setConfig(new EconomyConfig());
        }

        // 初始化三年级词库
        VocabularyDao vocabDao = db.vocabularyDao();
        if (vocabDao.getUnmasteredCount() + vocabDao.getMasteredCount() == 0) {
            vocabDao.insertAll(WordSeed.getWords());
        }

        initialized = true;
    }
}