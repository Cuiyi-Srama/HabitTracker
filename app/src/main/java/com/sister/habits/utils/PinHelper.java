package com.sister.habits.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 家长验证 — 双独立开关: 系统锁屏 | 应用PIN码
 *
 * 2026-09-25 安全升级（TV 模式前置）：
 *   PIN 存储由「无盐 SHA-256」升级为「HMAC-SHA256 + 随机盐 + 10000 轮拉伸」。
 *   6 位数字 PIN 仅 10^6 空间，无盐 SHA-256 可被 GPU 秒破；
 *   因 TV 端无指纹/无系统锁屏，PIN 是唯一防线，故必须加固。
 *
 *   兼容：读取到旧格式（64 位十六进制，无 salt）时仍可校验通过，
 *         并在下一次 setPin() 时自动升级为新格式。
 */
public class PinHelper {
    private static final String PREFS = "pin_security";

    // 旧键（v1 无盐 SHA-256）
    private static final String KEY_PIN_HASH = "pin_hash";
    // 新键（v2 HMAC + salt + 拉伸）
    private static final String KEY_PIN_HASH_V2 = "pin_hash_v2";
    private static final String KEY_PIN_SALT_V2 = "pin_salt_v2";

    private static final String KEY_USE_SYSTEM_LOCK = "use_system_lock";
    private static final String KEY_USE_APP_PIN = "use_app_pin";
    // ==================== 设备模式（三态，2026-09-25） ====================
    // 设计原则：「自动判定」只能解决「设备是什么」，解决不了「家长想让它当什么」。
    //   典型失效场景：手机接大屏当电视用、智慧屏被识别成手机。
    //   故手动选择优先级最高，自动判定仅作为默认值。
    /** 自动：按设备硬件判定（默认） */
    public static final int DEVICE_AUTO = 0;
    /** 强制 TV：即使是手机也按电视行为（不用系统锁屏、字号放大、遥控器焦点） */
    public static final int DEVICE_TV = 1;
    /** 强制手机：即使在电视上也按手机行为（调试场景） */
    public static final int DEVICE_PHONE = 2;

    private static final String KEY_DEVICE_MODE = "device_mode";
    /** 旧键（仅读不再写，仅用于向上兼容旧数据） */
    private static final String KEY_FORCE_TV_MODE_LEGACY = "force_tv_mode";

    /** PIN 拉伸轮数：10000 轮 HMAC，手机上约 20~60ms，不影响体感 */
    private static final int ITERATIONS = 10000;
    /** 盐长度（字节） */
    private static final int SALT_BYTES = 16;

    // ==================== 系统锁屏开关 ====================
    public static boolean isSystemLockEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_USE_SYSTEM_LOCK, true);
    }

    public static void setSystemLockEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_USE_SYSTEM_LOCK, enabled).apply();
    }

    // ==================== 应用PIN码开关 ====================
    public static boolean isAppPinEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_USE_APP_PIN, false);
    }

    public static void setAppPinEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_USE_APP_PIN, enabled).apply();
    }

    // ==================== 设备模式（手动优先） ====================

    /**
     * 读取家长设置的设备模式。
     * 向上兼容：若读到旧的 force_tv_mode=true，视为 DEVICE_TV。
     */
    public static int getDeviceMode(Context ctx) {
        int m = prefs(ctx).getInt(KEY_DEVICE_MODE, -1);
        if (m == DEVICE_AUTO || m == DEVICE_TV || m == DEVICE_PHONE) {
            return m;
        }
        // 旧数据迁移：旧版只有 force_tv_mode 布尔值
        if (prefs(ctx).getBoolean(KEY_FORCE_TV_MODE_LEGACY, false)) {
            return DEVICE_TV;
        }
        return DEVICE_AUTO;
    }

    /**
     * 设置设备模式。DEVICE_AUTO / DEVICE_TV / DEVICE_PHONE。
     *
     * ★ 设计说明：设备模式决定 TV 行为（焦点适配、字号放大、看片入口）
     *   与 PIN 策略（TV 下不用系统锁屏，只认应用 PIN）。
     */
    public static void setDeviceMode(Context ctx, int mode) {
        android.content.SharedPreferences.Editor e = prefs(ctx).edit();
        e.putInt(KEY_DEVICE_MODE, mode);
        // 同步旧键，避免降级回旧版后设置丢失
        e.putBoolean(KEY_FORCE_TV_MODE_LEGACY, mode == DEVICE_TV);
        e.apply();
    }

    /** 当前设备是否应按 TV 行为运行（手动设置优先） */
    public static boolean isTvMode(Context ctx) {
        int mode = getDeviceMode(ctx);
        if (mode == DEVICE_TV) return true;
        if (mode == DEVICE_PHONE) return false;
        return isRealTv(ctx);
    }

    /**
     * 纯硬件判定：设备本身是否为电视。
     * 用于：（1）DEVICE_AUTO 下的默认值；（２）设置界面向家长展示「自动判定结果」。
     * 不受 SharedPreferences 影响，不会被手动设置污染。
     */
    public static boolean isRealTv(Context ctx) {
        // 第一道：UiModeManager
        boolean tv;
        try {
            android.app.UiModeManager um =
                    (android.app.UiModeManager) ctx.getSystemService(Context.UI_MODE_SERVICE);
            tv = um != null
                    && um.getCurrentModeType()
                       == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        } catch (Throwable e) {
            return false;
        }
        if (!tv) return false;
        // 第二道：有触摸屏且无 leanback 特征 → 定义为手机（防智慧屏误报）
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            boolean hasTouch = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN);
            boolean hasLeanback = pm.hasSystemFeature("android.software.leanback");
            if (hasTouch && !hasLeanback) return false;
        } catch (Throwable ignored) {
        }
        return true;
    }

    /** 设备模式的中文名称（供 UI 展示） */
    public static String deviceModeName(Context ctx) {
        switch (getDeviceMode(ctx)) {
            case DEVICE_TV:    return "强制 TV 模式";
            case DEVICE_PHONE: return "强制手机模式";
            default:           return "自动判定";
        }
    }

    /** 当前实际生效行为的中文描述（供 UI 展示） */
    public static String deviceModeEffective(Context ctx) {
        boolean tv = isTvMode(ctx);
        int mode = getDeviceMode(ctx);
        if (mode == DEVICE_AUTO) {
            return tv ? "自动判定 → 电视" : "自动判定 → 手机";
        }
        return tv ? "强制 TV 模式" : "强制手机模式";
    }

    /**
     * @deprecated 使用 {@link #setDeviceMode(Context, int)}。
     *   保留以免旧调用点编译失败。
     */
    @Deprecated
    public static void setForceTvMode(Context ctx, boolean enabled) {
        setDeviceMode(ctx, enabled ? DEVICE_TV : DEVICE_PHONE);
    }
    public static boolean isAnyEnabled(Context ctx) {
        if (isTvMode(ctx)) {
            return isAppPinEnabled(ctx) && isPinSet(ctx);
        }
        return isSystemLockEnabled(ctx) || isAppPinEnabled(ctx);
    }

    /** 兼容旧版（已废弃） */
    @Deprecated
    public static boolean isEnabled(Context ctx) { return isAnyEnabled(ctx); }

    @Deprecated
    public static String getAuthMode(Context ctx) { return ""; }

    @Deprecated
    public static void setAuthMode(Context ctx, String mode) {}

    // ==================== PIN 码操作 ====================
    public static boolean isPinSet(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        return sp.getString(KEY_PIN_HASH_V2, null) != null
                || sp.getString(KEY_PIN_HASH, null) != null;
    }

    /** 设置/修改 PIN（4~6 位纯数字），写入 v2 格式 */
    public static boolean setPin(Context ctx, String pin) {
        if (pin == null) return false;
        String p = pin.trim();
        if (p.length() < 4 || p.length() > 6) return false;
        if (!p.matches("\\d+")) return false;
        try {
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            String hash = stretch(p, salt);
            prefs(ctx).edit()
                    .putString(KEY_PIN_SALT_V2, toHex(salt))
                    .putString(KEY_PIN_HASH_V2, hash)
                    .remove(KEY_PIN_HASH)          // 清除旧格式，完成升级
                    .putBoolean(KEY_USE_APP_PIN, true)
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验 PIN。
     * 优先按 v2（HMAC+盐）校验；若只有 v1 旧值（无盐 SHA-256），
     * 则按旧算法比对以保持兼容。
     */
    public static boolean verifyPin(Context ctx, String pin) {
        if (pin == null) return false;
        String p = pin.trim();
        if (p.isEmpty()) return false;
        SharedPreferences sp = prefs(ctx);
        try {
            String saltHex = sp.getString(KEY_PIN_SALT_V2, null);
            String hashV2 = sp.getString(KEY_PIN_HASH_V2, null);
            if (saltHex != null && hashV2 != null) {
                return constantTimeEquals(hashV2, stretch(p, fromHex(saltHex)));
            }
            String hashV1 = sp.getString(KEY_PIN_HASH, null);
            if (hashV1 != null) {
                return constantTimeEquals(hashV1, sha256Legacy(p));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验成功且当前仍是旧格式时，顺手升级为新格式（无感迁移，不需要用户重设）。
     * @return 校验是否通过
     */
    public static boolean verifyAndUpgrade(Context ctx, String pin) {
        boolean ok = verifyPin(ctx, pin);
        if (!ok) return false;
        SharedPreferences sp = prefs(ctx);
        boolean isLegacy = sp.getString(KEY_PIN_HASH_V2, null) == null
                && sp.getString(KEY_PIN_HASH, null) != null;
        if (isLegacy) {
            setPin(ctx, pin);
        }
        return true;
    }

    /** 关闭所有验证（重置为默认：仅系统锁屏） */
    public static void disableAll(Context ctx) {
        prefs(ctx).edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_HASH_V2)
                .remove(KEY_PIN_SALT_V2)
                .putBoolean(KEY_USE_SYSTEM_LOCK, true)
                .putBoolean(KEY_USE_APP_PIN, false)
                .apply();
    }

    /** TV 模式：重置 PIN（保留 force_tv_mode 标记） */
    public static void clearPinOnly(Context ctx) {
        prefs(ctx).edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_HASH_V2)
                .remove(KEY_PIN_SALT_V2)
                .putBoolean(KEY_USE_APP_PIN, false)
                .apply();
    }

    // ==================== 内部实现 ====================
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** HMAC-SHA256 链式拉伸：h = HMAC(pin, salt + i)，迭代 ITERATIONS 轮 */
    private static String stretch(String pin, byte[] salt) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] key = pin.getBytes(StandardCharsets.UTF_8);
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] out = mac.doFinal(salt);
        for (int i = 1; i < ITERATIONS; i++) {
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            out = mac.doFinal(out);
        }
        return toHex(out);
    }

    /** 旧版算法，仅用于兼容历史数据 */
    private static String sha256Legacy(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return toHex(hash);
    }

    /** 定长时间比较，防时序侧信道 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= (x[i] ^ y[i]);
        }
        return diff == 0;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
