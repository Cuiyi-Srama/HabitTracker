package com.sister.habits.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** TV 看片：服务器地址 + 家庭 Token（可在电视上用遥控器修改） */
public final class TvPrefs {

    private static final String FILE = "tv_prefs";
    public static final String DEFAULT_HUB = "http://192.168.1.16:23458/habit/cuiyi/tv";
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

    public TvHubApi api() {
        return new TvHubApi(hub(), token());
    }
}