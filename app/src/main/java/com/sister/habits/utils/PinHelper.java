package com.sister.habits.utils;
import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;

/** 家长验证 — 双独立开关: 系统锁屏 | 应用PIN码 */
public class PinHelper {
    private static final String PREFS = "pin_security";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_USE_SYSTEM_LOCK = "use_system_lock";
    private static final String KEY_USE_APP_PIN = "use_app_pin";

    // 系统锁屏开关
    public static boolean isSystemLockEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_USE_SYSTEM_LOCK, true);
    }
    public static void setSystemLockEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_USE_SYSTEM_LOCK, enabled).apply();
    }

    // 应用PIN码开关
    public static boolean isAppPinEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_USE_APP_PIN, false);
    }
    public static void setAppPinEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_USE_APP_PIN, enabled).apply();
    }

    // 是否有任何验证开启
    public static boolean isAnyEnabled(Context ctx) {
        return isSystemLockEnabled(ctx) || isAppPinEnabled(ctx);
    }

    // 兼容旧版(已废弃)
    @Deprecated
    public static boolean isEnabled(Context ctx) { return isAnyEnabled(ctx); }
    @Deprecated
    public static String getAuthMode(Context ctx) { return ""; }
    @Deprecated
    public static void setAuthMode(Context ctx, String mode) {}

    // PIN码操作
    public static boolean isPinSet(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PIN_HASH, null) != null;
    }
    public static boolean setPin(Context ctx, String pin) {
        if (pin == null || pin.length() < 4 || pin.length() > 6) return false;
        if (!pin.matches("\\d+")) return false;
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_PIN_HASH, sha256(pin))
                    .putBoolean(KEY_USE_APP_PIN, true).apply();
            return true;
        } catch (Exception e) { return false; }
    }
    public static boolean verifyPin(Context ctx, String pin) {
        if (pin == null) return false;
        String hash = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PIN_HASH, null);
        if (hash == null) return false;
        try { return sha256(pin).equals(hash); }
        catch (Exception e) { return false; }
    }

    // 关闭所有验证(至少保留一个)
    public static void disableAll(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_PIN_HASH)
                .putBoolean(KEY_USE_SYSTEM_LOCK, true)
                .putBoolean(KEY_USE_APP_PIN, false).apply();
    }

    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
