package com.sister.habits.data;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DatabaseInitializer {
    private static final String TAG = "DatabaseInit";
    private static final String PREFS_NAME = "wordbank_prefs";
    private static final String KEY_GRADE_VERSION = "grade_version";
    private static final String KEY_LAST_GRADE_VERSION = "last_grade_version";
    private static boolean initialized = false;

    public static synchronized void init(Context context) {
        if (initialized) return;
        AppDatabase db = AppDatabase.getInstance(context);

        EconomyConfigDao configDao = db.economyConfigDao();
        if (configDao.getConfig() == null) {
            configDao.setConfig(new EconomyConfig());
        }

        VocabularyDao vocabDao = db.vocabularyDao();

        // 检查年级配置是否变化
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentVersion = prefs.getInt(KEY_GRADE_VERSION, 0);
        int lastVersion = prefs.getInt(KEY_LAST_GRADE_VERSION, 0);

        boolean needsReload = false;
        if (currentVersion != lastVersion && vocabDao.getActiveCount("builtin") > 0) {
            // 年级配置变了 → 只清空内置词库重新加载（绝不删除外部词库词汇）
            vocabDao.deleteByBankId("builtin");
            prefs.edit().putInt(KEY_LAST_GRADE_VERSION, currentVersion).apply();
            needsReload = true;
            Log.d(TAG, "年级配置变化，重新加载词库 (v" + lastVersion + "→ v" + currentVersion + ")");
        }

        int existingCount = vocabDao.getActiveCount("builtin");
        if (existingCount == 0) {
            List<Vocabulary> allWords = null;
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
                    allWords = new ArrayList<>();
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
                        v.bankId = "builtin";
                        allWords.add(v);
                    }
                }
                Log.d(TAG, "从JSON加载词库成功: " + (allWords != null ? allWords.size() : 0) + " 词");
            } catch (Exception e) {
                Log.w(TAG, "JSON词库加载失败，使用硬编码兜底", e);
            }
            if (allWords == null || allWords.isEmpty()) {
                allWords = WordBankLoader.getAllWords();
                Log.d(TAG, "使用硬编码词库: " + allWords.size() + " 词");
            }

            // 按年级过滤
            List<Vocabulary> filtered = filterByGrade(allWords, prefs);
            Log.d(TAG, "年级过滤后: " + filtered.size() + "/" + allWords.size() + " 词");

            vocabDao.insertAll(filtered);

            // 记录本次加载的版本号
            if (!needsReload) {
                prefs.edit().putInt(KEY_LAST_GRADE_VERSION, currentVersion).apply();
            }
        }
        initialized = true;
    }

    private static List<Vocabulary> filterByGrade(List<Vocabulary> words, SharedPreferences prefs) {
        // 收集选中的学段
        boolean selectPrimary = prefs.getBoolean("grade_primary", true);
        boolean selectJunior = prefs.getBoolean("grade_junior", true);
        boolean selectSenior = prefs.getBoolean("grade_senior", false);

        // 如果全没选，返回全部
        if (!selectPrimary && !selectJunior && !selectSenior) return words;

        // 构建选中年级的集合（兼容新旧gradeLevel值）
        Set<String> selectedGrades = new HashSet<>();
        if (selectPrimary) {
            selectedGrades.addAll(Arrays.asList("grade1", "grade2", "grade3", "grade4", "grade5", "primary"));
        }
        if (selectJunior) {
            selectedGrades.add("junior");
        }
        if (selectSenior) {
            selectedGrades.add("senior");
        }

        List<Vocabulary> result = new ArrayList<>();
        for (Vocabulary v : words) {
            if (v.gradeLevel != null && selectedGrades.contains(v.gradeLevel)) {
                result.add(v);
            }
        }
        return result;
    }

    private static class JsonWord {
        String g; String c; String w; String m; String p; int l;
    }
}
