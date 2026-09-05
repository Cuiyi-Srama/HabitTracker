package com.sister.habits.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.utils.BackupExportHelper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.util.Base64;

/**
 * 远程云端同步模块（WebDAV 实现）
 * 通过 WebDAV 服务器（坚果云/Nextcloud等）加密同步全量数据
 * 载荷格式：AES加密的备份ZIP（复用 BackupExportHelper），含数据库+Preferences（含Key绑定）
 * 文件：{webdav_url}/habit_sync.bin
 */
public class RemoteSync {
    private static final String TAG = "RemoteSync";
    private static final String PREFS = "remote_sync_prefs";
    private static final String KEY_URL = "webdav_url";
    private static final String KEY_USER = "webdav_user";
    private static final String KEY_PASS = "webdav_pass";
    private static final String KEY_SYNC_PASS = "sync_password";
    private static final String REMOTE_FILE = "habit_sync.bin";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 20000;

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

    // ==================== 配置管理 ====================
    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setConfig(String url, String user, String pass, String syncPass) {
        prefs().edit()
                .putString(KEY_URL, url.trim())
                .putString(KEY_USER, user.trim())
                .putString(KEY_PASS, pass)
                .putString(KEY_SYNC_PASS, syncPass)
                .apply();
        Log.i(TAG, "☁️ WebDAV配置已保存: " + url.trim());
    }

    public void clearConfig() {
        prefs().edit().clear().apply();
        Log.i(TAG, "☁️ WebDAV配置已清除");
    }

    public boolean isConfigured() {
        String url = prefs().getString(KEY_URL, null);
        return url != null && !url.isEmpty();
    }

    public String getStatusText() {
        String url = prefs().getString(KEY_URL, null);
        if (url == null || url.isEmpty()) return "未配置";
        String user = prefs().getString(KEY_USER, "");
        return "已配置: " + user + " @ " + url + "（文件: " + REMOTE_FILE + "）";
    }

    private String getSyncPassword() {
        String p = prefs().getString(KEY_SYNC_PASS, null);
        return (p == null || p.isEmpty()) ? "0903" : p;  // 默认与备份密码一致
    }

    // ==================== 同步核心 ====================
    /**
     * 执行远程同步（后台线程调用）：
     * 1. 下载云端加密快照 → 恢复合并到本地（含Key绑定等Preferences）
     * 2. 上传本地最新加密快照到云端
     */
    public void syncAll() {
        syncAll(null);
    }

    public void syncAll(final SyncCallback callback) {
        if (!isConfigured()) {
            Log.d(TAG, "☁️ 远程同步：未配置WebDAV，跳过");
            if (callback != null) callback.onStatusUpdate("☁️ 未配置WebDAV");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                if (callback != null) callback.onStatusUpdate("☁️ 开始远程同步...");
                String baseUrl = prefs().getString(KEY_URL, "");
                if (!baseUrl.endsWith("/")) baseUrl = baseUrl + "/";
                String user = prefs().getString(KEY_USER, "");
                String pass = prefs().getString(KEY_PASS, "");
                String syncPass = getSyncPassword();
                String remoteFile = baseUrl + REMOTE_FILE;

                // ① 下载远端快照（v3.0.76：合并式，只增不删，绝不覆盖本地数据）
                byte[] remote = webdavGet(remoteFile, user, pass);
                if (remote != null) {
                    BackupExportHelper helper = new BackupExportHelper(context);
                    boolean ok = helper.mergeBackupBytes(remote, syncPass);
                    Log.i(TAG, "☁️ 远端快照下载并" + (ok ? "合并成功(只增不删)" : "合并失败(密码错误?)"));
                    if (callback != null) callback.onStatusUpdate(ok ? "☁️ 已合并云端数据（只增不删）" : "☁️ 云端数据解密失败");
                } else {
                    Log.d(TAG, "☁️ 云端无快照，仅上传");
                }

                // ② 上传本地最新快照（覆盖云端）
                BackupExportHelper helper = new BackupExportHelper(context);
                byte[] local = helper.createEncryptedBackup(syncPass);
                boolean up = webdavPut(remoteFile, user, pass, local);
                Log.i(TAG, "☁️ 本地快照上传" + (up ? "成功" : "失败"));
                if (callback != null) callback.onStatusUpdate(up ? "✅ 远程同步完成" : "❌ 上传失败");
            } catch (Exception e) {
                Log.e(TAG, "☁️ 远程同步失败", e);
                if (callback != null) callback.onStatusUpdate("❌ 同步失败: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ==================== WebDAV HTTP ====================
    private HttpURLConnection openConn(String urlStr, String user, String pass, String method) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        String auth = user + ":" + pass;
        String encoded = Base64.encodeToString(auth.getBytes("UTF-8"), Base64.NO_WRAP);
        conn.setRequestProperty("Authorization", "Basic " + encoded);
        conn.setRequestProperty("User-Agent", "HabitTracker/2.1 WebDAV");
        return conn;
    }

    /** GET：返回字节流；404返回null；其他异常抛出 */
    private byte[] webdavGet(String urlStr, String user, String pass) throws Exception {
        HttpURLConnection conn = openConn(urlStr, user, pass, "GET");
        try {
            int code = conn.getResponseCode();
            if (code == 404) return null;
            if (code != 200) {
                Log.w(TAG, "☁️ GET " + code);
                return null;
            }
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            is.close();
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    /** PUT：上传字节流 */
    private boolean webdavPut(String urlStr, String user, String pass, byte[] data) throws Exception {
        HttpURLConnection conn = openConn(urlStr, user, pass, "PUT");
        try {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            OutputStream os = conn.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            Log.d(TAG, "☁️ PUT " + code);
            return code == 200 || code == 201 || code == 204;
        } finally {
            conn.disconnect();
        }
    }
}
