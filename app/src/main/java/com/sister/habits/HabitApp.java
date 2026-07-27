package com.sister.habits;

import android.app.Application;
import com.sister.habits.data.DatabaseInitializer;
import com.sister.habits.sync.SyncManager;

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
    }
}