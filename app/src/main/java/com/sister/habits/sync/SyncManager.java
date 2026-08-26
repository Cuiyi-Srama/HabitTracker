package com.sister.habits.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.dao.*;
import com.sister.habits.data.models.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

/**
 * 统一同步管理器 —— 四层同步调度
 * 优先级：Hub > 局域网P2P > 远程云端 > QR码（兜底）
 * 每次数据变动后自动触发
 */
public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final String PREFS_NAME = "sync_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_HUB_MODE = "hub_mode_enabled";
    // ==================== 同步模式（v3.0.64） ====================
    private static final String KEY_SYNC_MODE = "sync_mode";
    /** 同步模式：0=仅局域网P2P（默认，兼容旧行为） 1=仅中心服务器 2=自动（服务器→Hub→P2P→WebDAV） */
    public static final int MODE_P2P_ONLY = 0;
    public static final int MODE_SERVER_ONLY = 1;
    public static final int MODE_AUTO = 2;

    private static SyncManager instance;

    private final Context context;
    private final AppDatabase db;
    private final DataMerger merger;
    private final LanSync lanSync;
    private final HubSync hubSync;
    private final RemoteSync remoteSync;
    private final Gson gson;

    private String deviceId;

    private SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(context);
        this.gson = new GsonBuilder().create();

        this.merger = new DataMerger(db,
                db.checkInDao(), db.coinTransactionDao(),
                db.taskDao(), db.redemptionDao()
        );
        this.lanSync = new LanSync(context, db, merger, gson);
        this.hubSync = new HubSync(context, db, merger, gson);
        this.remoteSync = new RemoteSync(context, db, merger, gson);

        this.deviceId = getDeviceId();

        // 如果之前启用了Hub模式，自动重启
        if (getHubModePref()) {
            hubSync.setHubModeEnabled(true);
        }
    }

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context);
        }
        return instance;
    }

    public String getDeviceId() {
        if (deviceId == null) {
            deviceId = com.sister.habits.utils.DeviceIdentity.getDeviceKey(context);
        }
        return deviceId;
    }

    // ==================== Hub模式管理 ====================

    public boolean isHubModeEnabled() {
        return hubSync.isHubModeEnabled();
    }
    public void setHubModeEnabled(boolean enabled) {
        hubSync.setHubModeEnabled(enabled);
    }
    // ==================== 同步模式（v3.0.64） ====================
    public int getSyncMode() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SYNC_MODE, MODE_P2P_ONLY);
    }
    public void setSyncMode(int mode) {
        if (mode < MODE_P2P_ONLY || mode > MODE_AUTO) mode = MODE_P2P_ONLY;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_SYNC_MODE, mode).apply();
    }
    public String getSyncModeText() {
        switch (getSyncMode()) {
            case MODE_SERVER_ONLY: return "仅中心服务器";
            case MODE_AUTO: return "自动（服务器→局域网→WebDAV）";
            default: return "仅局域网P2P";
        }
    }

    public HubSync getHubSync() {
        return hubSync;
    }
    public LanSync getLanSync() {
        return lanSync;
    }
    public RemoteSync getRemoteSync() {
        return remoteSync;
    }

    private boolean getHubModePref() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_HUB_MODE, false);
    }

    // ==================== 同步调度 ====================

    /**
     * 数据变动后调用——自动选择最优同步方式
     * 优先级：Hub > 局域网P2P > 远程云端 > QR码兜底
     */
    private volatile long lastAutoSyncAt = 0; // 自动同步节流（3秒）

    public void onDataChanged() {
        // ⚠️ 可能被主线程调用（答题/审批等点击回调），同步含网络IO，必须异步执行，否则 ANR 卡死
        final long now = System.currentTimeMillis();
        if (now - lastAutoSyncAt < 3000) return; // 3秒节流：答题连点时避免并发同步
        lastAutoSyncAt = now;
        new Thread(() -> {
            try {
                doAutoSync();
            } catch (Exception e) {
                Log.w(TAG, "自动同步异常: " + e.getMessage());
            }
        }, "auto-sync").start();
    }

    private void doAutoSync() {
        int mode = getSyncMode();
        if (mode == MODE_SERVER_ONLY) {
            // ① 仅中心服务器模式
            if (isOnline()) {
                if (hubSync.isServerConfigured() && hubSync.syncToServer()) {
                    Log.d(TAG, "☁️ 中心服务器同步成功");
                    return;
                }
                Log.d(TAG, "☁️ 中心服务器不可达，等待下次");
            } else {
                Log.d(TAG, "📴 离线状态，数据已缓存");
            }
            return;
        }
        if (mode == MODE_AUTO) {
            // ① 自动模式：服务器优先（配置了服务器才尝试）
            if (hubSync.isServerConfigured() && isOnline() && hubSync.syncToServer()) {
                Log.d(TAG, "☁️ 自动模式: 中心服务器同步成功");
                return;
            }
            // ② 同WiFi → Hub
            if (isSameWifi() && hubSync.syncToHub()) {
                Log.d(TAG, "🏠 自动模式: Hub同步成功");
                return;
            }
            // ③ 同WiFi → 局域网P2P
            if (isSameWifi()) {
                Log.d(TAG, "📡 局域网P2P同步");
                lanSync.syncAll();
                return;
            }
            // ④ 有互联网 → WebDAV
            if (isOnline()) {
                Log.d(TAG, "☁️ WebDAV同步");
                remoteSync.syncAll();
                return;
            }
            Log.d(TAG, "📴 离线状态，数据已缓存");
            return;
        }
        // 默认：仅局域网P2P（兼容旧行为）
        // ① 优先尝试 Hub 同步（如果有Hub设备在线）
        if (isSameWifi() && hubSync.syncToHub()) {
            Log.d(TAG, "🏠 Hub同步成功");
            return;
        }
        // ② Hub不可用 → 尝试局域网P2P同步
        if (isSameWifi()) {
            Log.d(TAG, "📡 局域网P2P同步");
            lanSync.syncAll();
            return;
        }
        // ③ 不同网络但有互联网 → 远程云端同步
        if (isOnline()) {
            Log.d(TAG, "☁️ 远程云端同步");
            remoteSync.syncAll();
            return;
        }
        // ④ 完全离线 → 保存到本地，等待下次同步时机或QR码导出兜底
        Log.d(TAG, "📴 离线状态，数据已缓存");
    }

    /**
     * 手动触发全同步（所有方式）
     */
    public void triggerFullSync() {
        int mode = getSyncMode();
        if (mode == MODE_SERVER_ONLY) {
            // 仅中心服务器
            hubSync.syncToServer();
            Log.d(TAG, "🔄 全同步完成（服务器模式）");
            return;
        }
        if (mode == MODE_AUTO) {
            // 自动：服务器 → Hub → P2P → WebDAV 全走一遍（互相补充）
            if (hubSync.isServerConfigured()) hubSync.syncToServer();
            if (isSameWifi()) {
                hubSync.syncToHub();
                lanSync.syncAll();
            }
            remoteSync.syncAll();
            Log.d(TAG, "🔄 全同步完成（自动模式）");
            return;
        }
        // 默认 P2P only（兼容旧行为）
        // 先尝试Hub
        if (isSameWifi()) {
            if (hubSync.syncToHub()) {
                Log.d(TAG, "🏠 手动Hub同步成功");
            }
            lanSync.syncAll();
        }
        // 远程同步
        remoteSync.syncAll();
        Log.d(TAG, "🔄 全同步完成");
    }

    /** 异步版全同步：Hub发现是阻塞调用（最多8秒），必须在子线程执行，避免卡UI */
    public void triggerFullSyncAsync() {
        triggerFullSyncAsync(null);
    }

    /** 异步版全同步 + 完成回调：onComplete 在局域网扫描结束后触发（P2P完成即回调） */
    public void triggerFullSyncAsync(final Runnable onComplete) {
        new Thread(() -> {
            try {
                boolean lanDone = false;
                if (isSameWifi()) {
                    if (hubSync.syncToHub()) {
                        Log.d(TAG, "🏠 手动Hub同步成功");
                    }
                    // onComplete 绑定到 P2P 扫描结束（Hub 成功也继续 P2P 交换，保持原行为）
                    lanSync.syncAll(onComplete);
                    lanDone = true;
                }
                // 远程同步
                remoteSync.syncAll();
                if (!lanDone && onComplete != null) {
                    try { onComplete.run(); } catch (Exception ignored) {}
                }
                Log.d(TAG, "🔄 全同步完成");
            } catch (Exception e) {
                Log.e(TAG, "全同步异常", e);
                if (onComplete != null) {
                    try { onComplete.run(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    public void triggerLanSync() {
        if (isSameWifi()) {
            if (!hubSync.syncToHub()) {
                lanSync.syncAll();
            }
        }
    }

    public void triggerRemoteSync() {
        remoteSync.syncAll();
    }

    /** 手动设置Hub IP（跳过自动发现） */
    public void setManualHubIp(String ip) {
        hubSync.setManualHubIp(ip);
    }

    /**
     * 生成QR码数据（增量同步兜底）
     */
    public String generateQrData() {
        StringBuilder sb = new StringBuilder();
        sb.append("HSYNCv1:");  // 协议头

        // 未同步的打卡
        List<CheckIn> checkIns = db.checkInDao().getUnsynced();
        for (CheckIn c : checkIns) {
            sb.append("C:").append(gson.toJson(c)).append("|");
        }

        // 未同步的金币流水
        List<CoinTransaction> coins = db.coinTransactionDao().getUnsynced();
        for (CoinTransaction c : coins) {
            sb.append("T:").append(gson.toJson(c)).append("|");
        }

        return sb.toString();
    }

    /**
     * 从QR码导入数据
     */
    public void importFromQr(String qrData) {
        if (!qrData.startsWith("HSYNCv1:")) return;

        String payload = qrData.substring(8);
        String[] entries = payload.split("\\|");

        for (String entry : entries) {
            if (entry.isEmpty()) continue;

            try {
                char type = entry.charAt(0);
                String json = entry.substring(2);

                switch (type) {
                    case 'C':
                        CheckIn checkIn = gson.fromJson(json, CheckIn.class);
                        db.checkInDao().insert(checkIn);
                        break;
                    case 'T':
                        CoinTransaction coin = gson.fromJson(json, CoinTransaction.class);
                        db.coinTransactionDao().insert(coin);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "QR导入解析失败: " + entry, e);
            }
        }
    }

    // ==================== 网络状态检测 ====================

    /**
     * 获取本机局域网 IPv4（Android 10+ 兼容）
     * ⚠️ WifiInfo.getIpAddress() 在 Android 10+ 返回 0（隐私限制），不可用
     * 优先 ConnectivityManager.getLinkProperties（API23+，无需位置权限），fallback NetworkInterface
     */
    public String getLocalIpv4() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network active = cm.getActiveNetwork();
                if (active != null) {
                    LinkProperties lp = cm.getLinkProperties(active);
                    if (lp != null) {
                        for (LinkAddress la : lp.getLinkAddresses()) {
                            InetAddress addr = la.getAddress();
                            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                                return addr.getHostAddress();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "获取IPv4失败(ConnectivityManager)", e);
        }
        // fallback：遍历网络接口
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                if (ni.getName() != null && ni.getName().startsWith("wlan")) {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                            return a.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "获取IPv4失败(NetworkInterface)", e);
        }
        return null;
    }

    /** 判断是否连接了 WiFi（Android 10+ 兼容：不用 getNetworkId） */
    public boolean isSameWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network active = cm.getActiveNetwork();
                if (active != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                    if (caps != null) {
                        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Wifi状态检测失败", e);
        }
        return false;
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo active = cm.getActiveNetworkInfo();
                return active != null && active.isConnected();
            }
        } catch (Exception e) {
            Log.d(TAG, "网络状态检测失败", e);
        }
        return false;
    }
}