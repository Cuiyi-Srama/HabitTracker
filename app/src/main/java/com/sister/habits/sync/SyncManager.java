package com.sister.habits.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.dao.*;
import com.sister.habits.data.models.*;

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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_HUB_MODE, enabled)
                .apply();
        Log.i(TAG, "🏠 Hub模式: " + (enabled ? "开启" : "关闭"));
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
    public void onDataChanged() {
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

    private boolean isSameWifi() {
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                return wifiInfo != null && wifiInfo.getNetworkId() != -1;
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