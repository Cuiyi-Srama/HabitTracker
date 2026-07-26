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

        // 初始化数据库和同步服务（放在子线程，Room禁止主线程操作）
        new Thread(() -> {
            DatabaseInitializer.init(this);
            SyncManager syncManager = SyncManager.getInstance(this);
            syncManager.triggerLanSync();
        }).start();
    }
}