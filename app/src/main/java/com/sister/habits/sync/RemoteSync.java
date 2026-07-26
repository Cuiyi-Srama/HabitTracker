package com.sister.habits.sync;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.sister.habits.data.AppDatabase;

/**
 * 远程云端同步模块（占位）
 * 当前版本暂未实现远程云同步
 * 后续可通过 Firebase / 自建服务器实现
 */
public class RemoteSync {
    private static final String TAG = "RemoteSync";
    private final Context context;
    private final AppDatabase db;
    private final DataMerger merger;
    private final Gson gson;

    public RemoteSync(Context context, AppDatabase db, DataMerger merger, Gson gson) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.merger = merger;
        this.gson = gson;
    }

    /**
     * 执行远程同步（当前为空实现）
     */
    public void syncAll() {
        Log.d(TAG, "☁️ 远程同步：暂未实现，未来可通过Firebase/自建服务器接入");
    }
}