package com.sister.habits.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;

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
 *
 * 2026-10-03 v4.1.0 家庭组升级：
 *   1) 新增「PIN 快照」（pin_snapshot_v3，JSON）作为唯一权威格式：
 *      {"kdf","iterations","salt","hash"}（salt/hash 为 Base64），
 *      KDF 升级为 PBKDF2-HMAC-SHA256 × 200000 轮，抗 GPU 爆破。
 *   2) 快照可导出/导入，由 FamilySecuritySync 与家庭 Hub 同步：
 *      全家共用一个 PIN —— 任意设备改一次，其余设备自动生效。
 *   3) 旧 v2/v1 数据仍可校验；校验通过后自动升级为快照并标记待同步。
 *
 * 快照语义：hubVersion = 本机快照对应的 Hub 版本号（未同步为 -1）；
 *           pendingPush = 本地有未推送的改动（离线修改 / 首次升级）。
 */
public class PinHelper {
    private static final String PREFS = "pin_security";

    // 旧键（v1 无盐 SHA-256）
    private static final String KEY_PIN_HASH = "pin_hash";
    // 新键（v2 HMAC + salt + 拉伸）
    private static final String KEY_PIN_HASH_V2 = "pin_hash_v2";
    private static final String KEY_PIN_SALT_V2 = "pin_salt_v2";

    // ==================== v4.1.0 家庭同步快照 ====================
    private static final String KEY_SNAPSHOT = "pin_snapshot_v3";
    private static final String KEY_HUB_VERSION = "pin_hub_version";
    private static final String KEY_PENDING_PUSH = "pin_pending_push";

    /** 新快照 KDF：PBKDF2-HMAC-SHA256 */
    public static final String KDF_PBKDF2 = "pbkdf2-sha256";
    /** 兼容 KDF：HMAC-SHA256 链式拉伸（历史格式 / 导入用） */
    public static final String KDF_HMAC = "hmac-sha256";
    /** 新快照 PBKDF2 轮数 */
    public static final int PBKDF2_ITERATIONS = 200000;

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

    /** 旧 v2 拉伸轮数：仅用于兼容校验历史数据 */
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
        return sp.getString(KEY_SNAPSHOT, null) != null
                || sp.getString(KEY_PIN_HASH_V2, null) != null
                || sp.getString(KEY_PIN_HASH, null) != null;
    }

    /** 设置/修改 PIN（4~6 位纯数字），写入 v2 格式 */
    public static boolean setPin(Context ctx, String pin) {
        if (pin == null) return false;
        String p = pin.trim();
        if (p.length() < 4 || p.length() > 6) return false;
        if (!p.matches("\\d+")) return false;
        try {
            String snap = buildSnapshotJson(p);
            if (snap == null) return false;
            prefs(ctx).edit()
                    .putString(KEY_SNAPSHOT, snap)
                    .remove(KEY_PIN_HASH)
                    .remove(KEY_PIN_HASH_V2)
                    .remove(KEY_PIN_SALT_V2)
                    .putBoolean(KEY_USE_APP_PIN, true)
                    .putBoolean(KEY_PENDING_PUSH, true)
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
            Snap snap = readSnapshot(ctx);
            if (snap != null) {
                return verifyFields(snap.kdf, snap.iterations, snap.saltB64, snap.hashB64, p);
            }
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
        if (!hasSnapshot(ctx)) {
            // 旧格式校验通过 → 用同一 PIN 生成新快照（新盐），并标记待同步
            try {
                String snap = buildSnapshotJson(pin.trim());
                if (snap != null) {
                    prefs(ctx).edit()
                            .putString(KEY_SNAPSHOT, snap)
                            .remove(KEY_PIN_HASH)
                            .remove(KEY_PIN_HASH_V2)
                            .remove(KEY_PIN_SALT_V2)
                            .putBoolean(KEY_PENDING_PUSH, true)
                            .apply();
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    /** 关闭所有验证（重置为默认：仅系统锁屏） */
    public static void disableAll(Context ctx) {
        prefs(ctx).edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_HASH_V2)
                .remove(KEY_PIN_SALT_V2)
                .remove(KEY_SNAPSHOT)
                .remove(KEY_PENDING_PUSH)
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
                .remove(KEY_SNAPSHOT)
                .remove(KEY_PENDING_PUSH)
                .putBoolean(KEY_USE_APP_PIN, false)
                .apply();
    }

    // ==================== 家庭同步快照（v4.1.0） ====================

    /** PIN 快照（kdf / iterations / saltB64 / hashB64） */
    public static final class Snap {
        public final String kdf;
        public final int iterations;
        public final String saltB64;
        public final String hashB64;

        public Snap(String kdf, int iterations, String saltB64, String hashB64) {
            this.kdf = kdf;
            this.iterations = iterations;
            this.saltB64 = saltB64;
            this.hashB64 = hashB64;
        }
    }

    /** 是否存在快照（v4.1.0 格式） */
    public static boolean hasSnapshot(Context ctx) {
        return prefs(ctx).getString(KEY_SNAPSHOT, null) != null;
    }

    /** 读取本机快照；无或损坏返回 null */
    public static Snap readSnapshot(Context ctx) {
        try {
            String s = prefs(ctx).getString(KEY_SNAPSHOT, null);
            if (s == null || s.trim().isEmpty()) return null;
            JSONObject o = new JSONObject(s);
            String kdf = o.optString("kdf", "");
            int it = o.optInt("iterations", 0);
            String salt = o.optString("salt", "");
            String hash = o.optString("hash", "");
            if (kdf.isEmpty() || it <= 0 || salt.isEmpty() || hash.isEmpty()) return null;
            return new Snap(kdf, it, salt, hash);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 写入/覆盖本机快照（导入用；不改变开关状态） */
    public static boolean importSnapshot(Context ctx, String kdf, int iterations, String saltB64, String hashB64) {
        try {
            if (kdf == null || kdf.isEmpty() || iterations <= 0) return false;
            if (saltB64 == null || saltB64.isEmpty() || hashB64 == null || hashB64.isEmpty()) return false;
            JSONObject o = new JSONObject();
            o.put("kdf", kdf);
            o.put("iterations", iterations);
            o.put("salt", saltB64);
            o.put("hash", hashB64);
            prefs(ctx).edit()
                    .putString(KEY_SNAPSHOT, o.toString())
                    .remove(KEY_PIN_HASH)
                    .remove(KEY_PIN_HASH_V2)
                    .remove(KEY_PIN_SALT_V2)
                    .apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 用 PIN 生成新快照 JSON（PBKDF2 × 200000），供 setPin / 升级使用 */
    public static String buildSnapshotJson(String pin) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] dk = pbkdf2(pin, salt, PBKDF2_ITERATIONS);
        JSONObject o = new JSONObject();
        o.put("kdf", KDF_PBKDF2);
        o.put("iterations", PBKDF2_ITERATIONS);
        o.put("salt", b64e(salt));
        o.put("hash", b64e(dk));
        return o.toString();
    }

    /** 校验「给定字段」是否匹配 PIN（供同步层验证远端快照） */
    public static boolean verifyFields(String kdf, int iterations, String saltB64, String hashB64, String pin) {
        if (pin == null || kdf == null || saltB64 == null || hashB64 == null) return false;
        try {
            byte[] salt = b64d(saltB64);
            if (KDF_PBKDF2.equals(kdf)) {
                return constantTimeEquals(hashB64, b64e(pbkdf2(pin, salt, iterations)));
            }
            if (KDF_HMAC.equals(kdf)) {
                return constantTimeEquals(hashB64, b64e(hmacChain(pin, salt, iterations)));
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== Hub 版本簿记（家庭同步用） ====================

    /** 本机快照对应的 Hub 版本号；未同步返回 -1 */
    public static long getHubVersion(Context ctx) {
        return prefs(ctx).getLong(KEY_HUB_VERSION, -1L);
    }

    public static void setHubVersion(Context ctx, long version) {
        prefs(ctx).edit().putLong(KEY_HUB_VERSION, version).apply();
    }

    /** 本地是否有未推送的改动 */
    public static boolean isPendingPush(Context ctx) {
        return prefs(ctx).getBoolean(KEY_PENDING_PUSH, false);
    }

    public static void setPendingPush(Context ctx, boolean pending) {
        prefs(ctx).edit().putBoolean(KEY_PENDING_PUSH, pending).apply();
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

    /** PBKDF2-HMAC-SHA256 → 32 字节 */
    private static byte[] pbkdf2(String pin, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, 256);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /** HMAC-SHA256 链式拉伸（字节版，兼容导入的历史格式） */
    private static byte[] hmacChain(String pin, byte[] salt, int iterations) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] key = pin.getBytes(StandardCharsets.UTF_8);
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] out = mac.doFinal(salt);
        for (int i = 1; i < iterations; i++) {
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            out = mac.doFinal(out);
        }
        return out;
    }

    private static String b64e(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] b64d(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
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
