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

        // 初始化数据库（填充词库种子 + 经济参数）
        DatabaseInitializer.init(this);

        // 初始化同步管理器
        SyncManager syncManager = SyncManager.getInstance(this);
        syncManager.triggerLanSync();
    }
}