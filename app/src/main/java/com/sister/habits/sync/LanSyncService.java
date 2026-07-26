package com.sister.habits.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * 局域网同步后台服务
 * 在后台保持 NanoHTTPD 运行，监听局域网同步请求
 */
public class LanSyncService extends Service {
    private static final String TAG = "LanSyncService";

    @Override
    public void onCreate() {
        super.onCreate();
        SyncManager syncManager = SyncManager.getInstance(this);
        Log.d(TAG, "局域网同步服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 服务已在后台运行，持续监听同步请求
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "局域网同步服务已停止");
    }
}