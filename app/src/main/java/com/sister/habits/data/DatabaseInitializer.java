package com.sister.habits.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sister.habits.data.dao.EconomyConfigDao;
import com.sister.habits.data.dao.VocabularyDao;
import com.sister.habits.data.models.EconomyConfig;
import com.sister.habits.data.models.Vocabulary;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseInitializer {
    private static final String TAG = "DatabaseInit";
    private static boolean initialized = false;

    public static synchronized void init(Context context) {
        if (initialized) return;
        AppDatabase db = AppDatabase.getInstance(context);

        EconomyConfigDao configDao = db.economyConfigDao();
        if (configDao.getConfig() == null) {
            configDao.setConfig(new EconomyConfig());
        }

        VocabularyDao vocabDao = db.vocabularyDao();
        int existingCount = vocabDao.getActiveCount();
        if (existingCount == 0) {
            List<Vocabulary> words = null;
            // 优先从 JSON 加载
            try {
                AssetManager am = context.getAssets();
                InputStream is = am.open("wordbank.json");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String json = new String(buffer, "UTF-8");
                Type type = new TypeToken<List<JsonWord>>(){}.getType();
                List<JsonWord> jsonWords = new Gson().fromJson(json, type);
                if (jsonWords != null && !jsonWords.isEmpty()) {
                    words = new ArrayList<>();
                    for (JsonWord jw : jsonWords) {
                        Vocabulary v = new Vocabulary();
                        v.id = UUID.randomUUID().toString();
                        v.word = jw.w;
                        v.meaning = jw.m;
                        v.phonetic = jw.p;
                        v.category = jw.c;
                        v.gradeLevel = jw.g;
                        v.level = jw.l;
                        v.mastered = false;
                        v.active = true;
                        words.add(v);
                    }
                }
                Log.d(TAG, "从JSON加载词库成功: " + (words != null ? words.size() : 0) + " 词");
            } catch (Exception e) {
                Log.w(TAG, "JSON词库加载失败，使用硬编码兜底", e);
            }
            if (words == null || words.isEmpty()) {
                words = WordBankLoader.getAllWords();
                Log.d(TAG, "使用硬编码词库: " + words.size() + " 词");
            }
            vocabDao.insertAll(words);
        }
        initialized = true;
    }

    private static class JsonWord {
        String g; String c; String w; String m; String p; int l;
    }
}
