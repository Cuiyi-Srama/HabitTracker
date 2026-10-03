package com.sister.habits.tv;

import android.content.Context;
import android.os.Build;

import com.sister.habits.utils.PinHelper;

import org.json.JSONObject;

/**
 * 家庭组安全同步：把「家庭统一 PIN」快照与 Hub 同步。
 *
 * 职责：
 *   1) syncDown(ctx)     拉取远端快照并按版本合并（后台线程调用；静默失败）
 *   2) pushNow(ctx)      把本机快照推送至 Hub（带 version 乐观锁；返回明确结果）
 *   3) pushForce(ctx,v)  冲突后「以本机为准」重推
 *   4) adoptRemote(ctx)  冲突后「用最新」：拉取远端并覆盖本机
 *   5) verifyWithFallback(ctx, pin)  先本机校验；失败再试最近缓存的远端副本
 *      （覆盖「手机刚改完 PIN / 本机快照落后」场景）
 *
 * 说明：本类不弹 UI、不弹 Toast；线程模型为「调用方负责丢到后台线程」。
 */
public final class FamilySecuritySync {

    private FamilySecuritySync() {}

    public static final int OK = 0;
    public static final int CONFLICT = 1;
    public static final int OFFLINE = 2;
    public static final int NOPIN = 3;
    public static final int DISABLED = 4;

    public static final class Outcome {
        public final int status;
        public final int currentVersion;
        public final String message;

        public Outcome(int status, int currentVersion, String message) {
            this.status = status;
            this.currentVersion = currentVersion;
            this.message = message;
        }

        public boolean ok() {
            return status == OK;
        }
    }

    /** 最近一次从 Hub 拉到的快照（进程内缓存，用于校验回退） */
    private static volatile PinHelper.Snap lastRemote = null;
    private static volatile int lastRemoteVersion = -1;

    /** Hub 是否已配置（有地址即可尝试；请求失败一律静默） */
    public static boolean isEnabled(Context c) {
        try {
            TvPrefs p = new TvPrefs(c);
            String h = p.hub();
            return h != null && !h.trim().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public static PinHelper.Snap remoteSnapshot() {
        return lastRemote;
    }

    /** 后台线程调用：拉取远端 → 按版本合并 → 必要时回推本地。任何异常静默。 */
    public static void syncDown(Context c) {
        if (!isEnabled(c)) return;
        try {
            TvHubApi api = new TvPrefs(c).api();
            // 1) 本地有未推送改动 → 先尝试推送
            if (PinHelper.isPendingPush(c) && PinHelper.readSnapshot(c) != null) {
                Outcome o = pushLocal(c, api);
                if (o.status == OFFLINE) return;   // 离线：保留本地，不做覆盖
                if (o.status == CONFLICT) {
                    JSONObject r2 = fetch(api);
                    if (r2 != null) {
                        cacheRemote(r2);
                        PinHelper.Snap loc = PinHelper.readSnapshot(c);
                        PinHelper.Snap rem = lastRemote;
                        if (loc != null && rem != null
                                && loc.kdf.equals(rem.kdf)
                                && loc.saltB64.equals(rem.saltB64)
                                && loc.hashB64.equals(rem.hashB64)) {
                            // 内容相同：视为已同步，仅对齐版本号
                            PinHelper.setHubVersion(c, r2.optInt("version", 0));
                            PinHelper.setPendingPush(c, false);
                        }
                    }
                    return;
                }
            }
            // 2) 拉取远端
            JSONObject r = fetch(api);
            if (r == null) return;
            cacheRemote(r);
            if (!r.optBoolean("configured")) {
                // 远端未设置：本机有快照且无版本 → 建立初始版本
                if (PinHelper.readSnapshot(c) != null && PinHelper.getHubVersion(c) <= 0) {
                    pushLocal(c, api);
                }
                return;
            }
            long rv = r.optLong("version", 0);
            long lv = PinHelper.getHubVersion(c);
            if (rv > lv || !PinHelper.hasSnapshot(c)) {
                boolean imported = PinHelper.importSnapshot(c,
                        r.optString("kdf", ""), r.optInt("iterations", 0),
                        r.optString("salt", ""), r.optString("hash", ""));
                if (imported) {
                    PinHelper.setHubVersion(c, rv);
                    PinHelper.setPendingPush(c, false);
                }
            }
        } catch (Throwable t) {
            // 静默：主流程不因同步失败而中断
        }
    }

    /** 显式推送（家长改 PIN 后的界面流程；请在后台线程调用） */
    public static Outcome pushNow(Context c) {
        if (!isEnabled(c)) return new Outcome(DISABLED, 0, "未配置家庭服务器");
        try {
            return pushLocal(c, new TvPrefs(c).api());
        } catch (Throwable t) {
            return new Outcome(OFFLINE, 0, "同步失败");
        }
    }

    /** 冲突后「以本机为准」：用指定版本号强制重推 */
    public static Outcome pushForce(Context c, int expectedVersion) {
        if (!isEnabled(c)) return new Outcome(DISABLED, 0, "未配置家庭服务器");
        try {
            return pushLocal(c, new TvPrefs(c).api(), expectedVersion);
        } catch (Throwable t) {
            return new Outcome(OFFLINE, 0, "同步失败");
        }
    }

    /** 冲突后「用最新」：拉取远端覆盖本机 */
    public static Outcome adoptRemote(Context c) {
        if (!isEnabled(c)) return new Outcome(DISABLED, 0, "未配置家庭服务器");
        try {
            TvHubApi api = new TvPrefs(c).api();
            JSONObject r = fetch(api);
            if (r == null) return new Outcome(OFFLINE, 0, "拉取失败");
            cacheRemote(r);
            if (!r.optBoolean("configured")) return new Outcome(NOPIN, 0, "远端未设置 PIN");
            boolean imported = PinHelper.importSnapshot(c,
                    r.optString("kdf", ""), r.optInt("iterations", 0),
                    r.optString("salt", ""), r.optString("hash", ""));
            if (!imported) return new Outcome(OFFLINE, 0, "写入失败");
            int rv = r.optInt("version", 0);
            PinHelper.setHubVersion(c, rv);
            PinHelper.setPendingPush(c, false);
            return new Outcome(OK, rv, "已采用最新版本");
        } catch (Throwable t) {
            return new Outcome(OFFLINE, 0, "同步失败");
        }
    }

    /**
     * 校验 PIN：先本机；失败则尝试最近缓存的远端副本（命中则覆盖本机并放行）。
     * 用于 TV 端「手机刚改完 PIN」与「本机快照落后」场景。
     */
    public static boolean verifyWithFallback(Context c, String pin) {
        if (PinHelper.verifyPin(c, pin)) return true;
        PinHelper.Snap r = lastRemote;
        if (r != null && PinHelper.verifyFields(r.kdf, r.iterations, r.saltB64, r.hashB64, pin)) {
            PinHelper.importSnapshot(c, r.kdf, r.iterations, r.saltB64, r.hashB64);
            PinHelper.setHubVersion(c, lastRemoteVersion);
            PinHelper.setPendingPush(c, false);
            return true;
        }
        return false;
    }

    // ==================== 内部实现 ====================

    private static Outcome pushLocal(Context c, TvHubApi api) {
        PinHelper.Snap s = PinHelper.readSnapshot(c);
        if (s == null) return new Outcome(NOPIN, 0, "本机未设置 PIN");
        int expected = (int) Math.max(0, PinHelper.getHubVersion(c));
        return pushLocal(c, api, expected);
    }

    private static Outcome pushLocal(Context c, TvHubApi api, int expectedVersion) {
        PinHelper.Snap s = PinHelper.readSnapshot(c);
        if (s == null) return new Outcome(NOPIN, 0, "本机未设置 PIN");
        try {
            JSONObject r = api.pushFamilySecurity(expectedVersion, s.kdf, s.iterations,
                    s.saltB64, s.hashB64, clientTag());
            int v = r.optInt("version", expectedVersion + 1);
            PinHelper.setHubVersion(c, v);
            PinHelper.setPendingPush(c, false);
            return new Outcome(OK, v, "已同步");
        } catch (TvHubApi.HubException he) {
            if (he.code == 409) {
                return new Outcome(CONFLICT, parseCurrentVersion(he.body), "PIN 已在另一台设备上修改");
            }
            return new Outcome(OFFLINE, 0, "同步失败（HTTP " + he.code + "）");
        } catch (Throwable t) {
            return new Outcome(OFFLINE, 0, "同步失败，请检查网络");
        }
    }

    private static JSONObject fetch(TvHubApi api) {
        try {
            return api.fetchFamilySecurity();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void cacheRemote(JSONObject r) {
        try {
            if (r == null || !r.optBoolean("configured")) return;
            String kdf = r.optString("kdf", "");
            int it = r.optInt("iterations", 0);
            String salt = r.optString("salt", "");
            String hash = r.optString("hash", "");
            if (kdf.isEmpty() || it <= 0 || salt.isEmpty() || hash.isEmpty()) return;
            lastRemote = new PinHelper.Snap(kdf, it, salt, hash);
            lastRemoteVersion = r.optInt("version", -1);
        } catch (Throwable ignored) {
        }
    }

    private static int parseCurrentVersion(String body) {
        try {
            return new JSONObject(body).optInt("currentVersion", 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String clientTag() {
        try {
            String m = Build.MODEL == null ? "dev" : Build.MODEL;
            if (m.length() > 32) m = m.substring(0, 32);
            return "pin-" + m;
        } catch (Throwable t) {
            return "pin-dev";
        }
    }
}
