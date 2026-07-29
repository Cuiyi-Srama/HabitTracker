package com.sister.habits.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;

/**
 * PIN码安全防护工具
 * - SHA-256 哈希存储，不存明文
 * - 4~6位数字PIN
 */
public class PinHelper {
    private static final String PREFS = "pin_security";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_ENABLED = "pin_enabled";

    public static boolean isPinSet(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PIN_ENABLED, false)
                && prefs.getString(KEY_PIN_HASH, null) != null;
    }

    public static boolean setPin(Context ctx, String pin) {
        if (pin == null || pin.length() < 4 || pin.length() > 6) return false;
        if (!pin.matches("\\d+")) return false;
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_PIN_HASH, sha256(pin))
                    .putBoolean(KEY_PIN_ENABLED, true)
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

    public static void disablePin(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_PIN_HASH)
                .putBoolean(KEY_PIN_ENABLED, false)
                .apply();
    }

    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
