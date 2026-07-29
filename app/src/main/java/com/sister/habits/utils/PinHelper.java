package com.sister.habits.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;

/**
 * 家长安全防护工具 — 支持三种验证方式
 * - PIN码（SHA-256哈希存储）
 * - 指纹（BiometricPrompt）
 * - 设备锁（系统锁屏密码/图案）
 */
public class PinHelper {
    private static final String PREFS = "pin_security";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_AUTH_ENABLED = "auth_enabled";
    private static final String KEY_AUTH_MODE = "auth_mode";

    public static final String MODE_PIN = "pin";
    public static final String MODE_FINGERPRINT = "fingerprint";
    public static final String MODE_DEVICE_LOCK = "device_lock";

    /** 是否已启用安全防护 */
    public static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTH_ENABLED, false);
    }

    /** 获取当前验证模式 */
    public static String getAuthMode(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_AUTH_MODE, MODE_PIN);
    }

    /** 设置验证模式 */
    public static void setAuthMode(Context ctx, String mode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_AUTH_MODE, mode)
                .putBoolean(KEY_AUTH_ENABLED, true)
                .apply();
    }

    /** 彻底关闭安全防护 */
    public static void disableAll(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_PIN_HASH)
                .putBoolean(KEY_AUTH_ENABLED, false)
                .putString(KEY_AUTH_MODE, MODE_PIN)
                .apply();
    }

    // ===== PIN码 =====

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
                    .putBoolean(KEY_AUTH_ENABLED, true)
                    .apply();
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

    /** 修改PIN前需先验证旧PIN */
    public static boolean changePin(Context ctx, String oldPin, String newPin) {
        if (!verifyPin(ctx, oldPin)) return false;
        return setPin(ctx, newPin);
    }

    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
