package com.sister.habits.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * \u8bbe\u5907\u552f\u4e00\u8eab\u4efd\u6807\u8bc6
 * \u9996\u6b21\u542f\u52a8\u65f6\u751f\u6210\uff0c\u6c38\u4e45\u4fdd\u5b58\u3002
 * \u91c7\u7528 SHA-256 + \u591a\u91cd\u71b5\u6e90 \u786e\u4fdd\u5168\u5c40\u552f\u4e00\u6027\u3002
 * \u8f93\u51fa\u683c\u5f0f\uff1a XXXX-XXXX-XXXX-XXXX \uff08Base32 \u7f16\u7801\uff0c\u53ef\u8bfb\u53ef\u5199\uff09
 */
public class DeviceIdentity {
    private static final String PREFS_NAME = "device_identity";
    private static final String KEY_DEVICE_KEY = "device_key";
    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static String cachedKey = null;

    /** \u83b7\u53d6\u8bbe\u5907\u552f\u4e00\u8eab\u4efd\u6807\u8bc6\uff0c\u4e0d\u5b58\u5728\u65f6\u81ea\u52a8\u751f\u6210 */
    public static synchronized String getDeviceKey(Context context) {
        if (cachedKey != null) return cachedKey;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cachedKey = prefs.getString(KEY_DEVICE_KEY, null);

        if (cachedKey == null) {
            cachedKey = generateKey(context);
            prefs.edit().putString(KEY_DEVICE_KEY, cachedKey).apply();
        }
        return cachedKey;
    }

    /** \u751f\u6210 16 \u4f4d\u53ef\u8bfb Key \uff08SHA-256 + Base32\uff09 */
    private static String generateKey(Context context) {
        try {
            long timestamp = System.currentTimeMillis();
            String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String fingerprint = android.os.Build.FINGERPRINT;
            String serial = android.os.Build.SERIAL;
            String salt = UUID.randomUUID().toString();

            // \u591a\u91cd\u71b5\u6e90\u7ec4\u5408
            String seed = timestamp + "|" + (androidId != null ? androidId : "")
                + "|" + (fingerprint != null ? fingerprint : "")
                + "|" + (serial != null ? serial : "") + "|" + salt;

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seed.getBytes("UTF-8"));

            // \u53d6\u524d 16 \u5b57\u8282\uff0c\u6620\u5c04\u5230 Base32 \u5b57\u6bcd\u8868
            StringBuilder key = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                key.append(CHARSET.charAt(hash[i] & 0x1F));
                if ((i + 1) % 4 == 0 && i < 15) key.append('-');
            }
            return key.toString();
        } catch (Exception e) {
            // \u5907\u7528\uff1aUUID \u4e0a\u5bf9\u5199
            return UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}
