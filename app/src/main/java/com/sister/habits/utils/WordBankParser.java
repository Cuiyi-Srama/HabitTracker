package com.sister.habits.utils;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sister.habits.data.models.Vocabulary;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 词库多格式自动解析器
 * <p>
 * 支持格式：
 * 1. 自有格式：[{"w":"cat","m":"猫","g":"grade1","c":"animal","p":"/kæt/","l":1}]
 * 2. KyleBing格式：[{"word":"ability","translations":[{"translation":"能力","type":"n"}],"phrases":[...]}]
 * 3. 通用格式：[{"word":"cat","meaning":"猫","phonetic":"/kæt/"}]
 * 4. 纯词表格式(endict_wordlist)：["word1","word2","phrase with space",...]
 * <p>
 * 自动检测：根据第一个词条包含哪些 key 来判断
 */
public class WordBankParser {

    private static final String TAG = "WordBankParser";

    /**
     * 从 JSON 字符串解析词库，自动检测格式
     */
    public static List<Vocabulary> parse(String json, String sourceGradeLevel) {
        return parse(json, sourceGradeLevel, "external");
    }
    public static List<Vocabulary> parse(String json, String sourceGradeLevel, String bankId) {
        // ★ Fix 3: 去除UTF-8 BOM头（\uFEFF）
        if (json != null && json.length() > 0 && json.charAt(0) == '\uFEFF') {
            json = json.substring(1);
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            return parseJsonArray(trimmed, sourceGradeLevel, bankId);
        }
        Log.w(TAG, "不支持的JSON格式，不以[开头，实际前50字符: " + trimmed.substring(0, Math.min(50, trimmed.length())));
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Vocabulary> parseJsonArray(String json, String gradeLevel, String bankId) {
        try {
            // 先尝试解析成通用 List<Map>
            Type mapType = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> rawList = new Gson().fromJson(json, mapType);
            if (rawList != null && !rawList.isEmpty()) {
                Map<String, Object> first = rawList.get(0);

                // === 格式检测 ===
                if (first.containsKey("word") && (first.containsKey("translations") || first.containsKey("translation"))) {
                    // KyleBing 格式
                    return parseKyleBing(rawList, gradeLevel, bankId);
                } else if (first.containsKey("w") && first.containsKey("m")) {
                    // 自有格式
                    return parseNative(rawList, gradeLevel, bankId);
                } else if (first.containsKey("word") && first.containsKey("meaning")) {
                    // 通用格式
                    return parseGeneric(rawList, gradeLevel, bankId);
                } else {
                    Log.w(TAG, "未知格式，尝试作为自有格式解析。keys: " + first.keySet());
                    return parseNative(rawList, gradeLevel, bankId);
                }
            }
        } catch (Exception ignored) {
            // 解析为Map失败，尝试纯词表格式
        }

        // 尝试解析为纯词表格式 [string, string, ...]
        try {
            Type stringType = new TypeToken<List<String>>() {}.getType();
            List<String> wordList = new Gson().fromJson(json, stringType);
            if (wordList != null && !wordList.isEmpty()) {
                return parseWordlist(wordList, gradeLevel, bankId);
            }
        } catch (Exception e) {
            Log.e(TAG, "纯词表格式解析也失败", e);
        }

        return new ArrayList<>();
    }

    /** 纯词表格式解析 (endict_wordlist)：["word1", "word2", ...] */
    private static List<Vocabulary> parseWordlist(List<String> wordList, String gradeLevel, String bankId) {
        List<Vocabulary> result = new ArrayList<>();
        for (String word : wordList) {
            try {
                Vocabulary v = new Vocabulary();
                v.id = UUID.randomUUID().toString();
                v.word = word;
                v.meaning = word;  // 无翻译时退化为显示单词本身
                v.phonetic = "";
                v.category = word.contains(" ") ? "phrase" : "word";
                v.gradeLevel = gradeLevel;
                v.level = word.contains(" ") ? 2 : 1;
                v.mastered = false;
                v.active = true;
                v.bankId = bankId;
                result.add(v);
            } catch (Exception e) {
                Log.w(TAG, "跳过异常词条", e);
            }
        }
        return result;
    }

    /** KyleBing 格式解析（含短语） */
    private static List<Vocabulary> parseKyleBing(List<Map<String, Object>> rawList, String gradeLevel, String bankId) {
        List<Vocabulary> result = new ArrayList<>();
        for (Map<String, Object> item : rawList) {
            try {
                Vocabulary v = new Vocabulary();
                v.id = UUID.randomUUID().toString();
                v.word = (String) item.get("word");
                v.meaning = extractTranslation(item);
                v.phonetic = "";
                v.category = extractCategory(item);
                v.gradeLevel = gradeLevel;
                v.level = 1;
                v.mastered = false;
                v.active = true;
                v.bankId = bankId;
                result.add(v);
            } catch (Exception e) {
                Log.w(TAG, "跳过异常词条", e);
            }
        }
        return result;
    }

    /** 从 KyleBing 的 translations 数组中提取中文翻译 */
    private static String extractTranslation(Map<String, Object> item) {
        try {
            Object transObj = item.get("translations");
            if (transObj instanceof List) {
                List<Map<String, Object>> transList = (List<Map<String, Object>>) transObj;
                if (!transList.isEmpty()) {
                    Map<String, Object> first = transList.get(0);
                    String t = (String) first.get("translation");
                    String type = (String) first.get("type");
                    return (type != null ? "[" + type + "] " : "") + (t != null ? t : "");
                }
            }
        } catch (Exception ignored) {}
        Object m = item.get("translation");
        return m != null ? m.toString() : "";
    }

    /** 从 KyleBing 格式中提取分类（优先使用短语中的名词分类） */
    private static String extractCategory(Map<String, Object> item) {
        try {
            Object transObj = item.get("translations");
            if (transObj instanceof List) {
                List<Map<String, Object>> transList = (List<Map<String, Object>>) transObj;
                if (!transList.isEmpty()) {
                    String type = (String) transList.get(0).get("type");
                    if (type != null) return type;
                }
            }
        } catch (Exception ignored) {}
        return "general";
    }

    /** 自有格式解析 */
    private static List<Vocabulary> parseNative(List<Map<String, Object>> rawList, String gradeLevel, String bankId) {
        List<Vocabulary> result = new ArrayList<>();
        for (Map<String, Object> item : rawList) {
            try {
                Vocabulary v = new Vocabulary();
                v.id = UUID.randomUUID().toString();
                v.word = (String) item.get("w");
                v.meaning = (String) item.get("m");
                v.phonetic = (String) item.getOrDefault("p", "");
                v.category = (String) item.getOrDefault("c", "general");
                v.gradeLevel = (String) item.getOrDefault("g", gradeLevel);
                Object level = item.get("l");
                v.level = level instanceof Number ? ((Number) level).intValue() : 1;
                v.mastered = false;
                v.active = true;
                v.bankId = bankId;
                result.add(v);
            } catch (Exception e) {
                Log.w(TAG, "跳过异常词条", e);
            }
        }
        return result;
    }

    /** 通用格式解析 */
    private static List<Vocabulary> parseGeneric(List<Map<String, Object>> rawList, String gradeLevel, String bankId) {
        List<Vocabulary> result = new ArrayList<>();
        for (Map<String, Object> item : rawList) {
            try {
                Vocabulary v = new Vocabulary();
                v.id = UUID.randomUUID().toString();
                v.word = (String) item.get("word");
                v.meaning = (String) item.getOrDefault("meaning", "");
                v.phonetic = (String) item.getOrDefault("phonetic", "");
                v.category = (String) item.getOrDefault("category", "general");
                v.gradeLevel = gradeLevel;
                v.level = 1;
                v.mastered = false;
                v.active = true;
                v.bankId = bankId;
                result.add(v);
            } catch (Exception e) {
                Log.w(TAG, "跳过异常词条", e);
            }
        }
        return result;
    }
}