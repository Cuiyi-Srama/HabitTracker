package com.sister.habits.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** TV 看片：服务器地址 + 家庭 Token（可在电视上用遥控器修改） */
public final class TvPrefs {

    private static final String FILE = "tv_prefs";
    public static final String DEFAULT_HUB = "http://192.168.1.17:23458/habit/cuiyi/tv";
    public static final String DEFAULT_TOKEN = "kDSBygdyGS0gWt0EZbyGei0qRbyAW94O";

    private final SharedPreferences sp;

    public TvPrefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String hub() {
        return sp.getString("hub", DEFAULT_HUB);
    }

    public String token() {
        return sp.getString("token", DEFAULT_TOKEN);
    }

    public void setHub(String value) {
        sp.edit().putString("hub", value).apply();
    }

    public void setToken(String value) {
        sp.edit().putString("token", value).apply();
    }

    /** 配对成功后一次性写入地址与 Token */
    public void save(String hubValue, String tokenValue) {
        SharedPreferences.Editor e = sp.edit();
        if (hubValue != null && !hubValue.trim().isEmpty()) {
            e.putString("hub", hubValue.trim());
        }
        if (tokenValue != null && !tokenValue.trim().isEmpty()) {
            e.putString("token", tokenValue.trim());
        }
        e.apply();
    }

    public TvHubApi api() {
        return new TvHubApi(hub(), token());
    }
}