package com.sister.habits.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 用户个人信息管理器 — 支持昵称/头像自定义
 * 所有硬编码的"妹妹"改为通过此类获取
 */
public class ProfileManager {
    private static final String PREFS = "user_profile";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_AVATAR_PATH = "avatar_path";
    private static final String KEY_APP_TITLE = "app_title";
    private static final String KEY_BIRTHDAY = "birthday";  // yyyy-MM-dd

    private static ProfileManager instance;
    private final SharedPreferences prefs;
    private final String defaultNickname;
    private final String defaultAppTitle;

    private ProfileManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defaultNickname = "妹妹";
        defaultAppTitle = "好习惯养成";
    }

    public static synchronized ProfileManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProfileManager(context.getApplicationContext());
        }
        return instance;
    }

    /** 获取昵称 */
    public String getNickname() {
        return prefs.getString(KEY_NICKNAME, defaultNickname);
    }

    /** 设置昵称 */
    public void setNickname(String nickname) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply();
    }

    /** 获取App标题（顶部显示的标题） */
    public String getAppTitle() {
        return prefs.getString(KEY_APP_TITLE, defaultAppTitle);
    }

    /** 设置App标题 */
    public void setAppTitle(String title) {
        prefs.edit().putString(KEY_APP_TITLE, title).apply();
    }

    /** 获取头像路径（空字符串=未设置） */
    public String getAvatarPath() {
        return prefs.getString(KEY_AVATAR_PATH, "");
    }

    /** 设置头像路径 */
    public void setAvatarPath(String path) {
        prefs.edit().putString(KEY_AVATAR_PATH, path).apply();
    }
    /** 获取生日（yyyy-MM-dd，空字符串=未设置） */
    public String getBirthday() {
        return prefs.getString(KEY_BIRTHDAY, "");
    }
    /** 设置生日 */
    public void setBirthday(String birthday) {
        prefs.edit().putString(KEY_BIRTHDAY, birthday).apply();
    }
}