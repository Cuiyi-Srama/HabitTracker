package com.sister.habits;

import android.app.Application;
import android.util.Log;

import com.sister.habits.data.DatabaseInitializer;
import com.sister.habits.sync.SyncManager;
import com.sister.habits.utils.SnapshotServer;

import java.io.IOException;

/**
 * 应用 Application 类
 * 启动时初始化数据库和同步服务
 */
public class HabitApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // \u521d\u59cb\u5316\u8bbe\u5907\u552f\u4e00\u8eab\u4efd\uff08\u9996\u6b21\u542f\u52a8\u65f6\u751f\u6210\u5e76\u6c38\u4e45\u4fdd\u5b58\uff09
        com.sister.habits.utils.DeviceIdentity.getDeviceKey(this);

        // \u521d\u59cb\u5316\u6570\u636e\u5e93\u548c\u540c\u6b65\u670d\u52a1
        new Thread(() -> {
            DatabaseInitializer.init(this);
            SyncManager syncManager = SyncManager.getInstance(this);
            syncManager.triggerLanSync();
        }).start();

        // \u542f\u52a8\u5feb\u7167\u4e0a\u62a5\u670d\u52a1\uff08\u865a\u62df\u5c4f\u81ea\u52a8\u5316\u7528\uff0c\u7aef\u53e318082\uff09
        new Thread(() -> {
            try {
                new SnapshotServer(this).start();
            } catch (IOException e) {
                Log.w("HabitApp", "SnapshotServer start failed", e);
            }
        }).start();
    }
}