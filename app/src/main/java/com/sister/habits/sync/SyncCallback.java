package com.sister.habits.sync;

/** 同步操作回调接口 — 用于实时反馈同步状态 */
public interface SyncCallback {
    void onStatusUpdate(String status);
    void onHubFound(String ip, String deviceId);
    void onSyncComplete(boolean success, String message);
    void onScanProgress(int scanned, int total);
}