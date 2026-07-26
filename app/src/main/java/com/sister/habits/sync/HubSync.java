package com.sister.habits.sync;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.google.gson.Gson;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.*;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 家庭中枢（Hub）同步模块
 *
 * 将一台旧设备变为 24/7 家庭数据服务器：
 * - 运行 NanoHTTPD 在端口 18081
 * - 任意设备可通过 POST /hub/sync 上报数据
 * - 任意设备可通过 GET /hub/pull 拉取最新数据
 * - 数据通过 DataMerger 自动合并冲突
 * - 支持跨网络访问（配合 Tailscale 等组网工具）
 *
 * 与 LanSync 的关系：
 * - LanSync 是 P2P 对等同步，适合临时发现
 * - HubSync 是 中心化存储，适合 24/7 持续同步
 * - 两者可以共存：有Hub时走Hub，没Hub时走P2P
 */
public class HubSync {
    private static final String TAG = "HubSync";
    private static final int HUB_PORT = 18081;

    private final Context context;
    private final AppDatabase db;
    private final DataMerger merger;
    private final Gson gson;

    private NanoHTTPD server;
    private boolean running = false;
    private boolean hubModeEnabled = false;

    // 缓存的Hub IP地址
    private String cachedHubIp = null;
    private long lastHubDiscovery = 0;

    public HubSync(Context context, AppDatabase db, DataMerger merger, Gson gson) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.merger = merger;
        this.gson = gson;
    }

    // ==================== Hub模式开关 ====================

    /** 检查本设备是否启用了Hub模式 */
    public boolean isHubModeEnabled() {
        return hubModeEnabled;
    }

    /** 启用/禁用Hub模式 */
    public synchronized void setHubModeEnabled(boolean enabled) {
        if (enabled == hubModeEnabled) return;

        if (enabled) {
            startHubServer();
        } else {
            stopHubServer();
        }
        hubModeEnabled = enabled;
    }

    // ==================== Hub端：启动HTTP Server ====================

    private void startHubServer() {
        if (server != null && running) return;

        try {
            server = new NanoHTTPD(HUB_PORT) {
                @Override
                public Response serve(IHTTPSession session) {
                    String uri = session.getUri();
                    String method = session.getMethod().name();

                    switch (uri) {
                        case "/hub/sync":
                            if ("POST".equals(method)) return handleSync(session);
                            break;
                        case "/hub/pull":
                            if ("GET".equals(method)) return handlePull(session);
                            break;
                        case "/hub/peek":
                            return handlePeek();
                        case "/hub/discover":
                            return handleDiscover();
                    }
                    return newFixedLengthResponse(
                            Response.Status.NOT_FOUND, "text/plain", "Not Found");
                }
            };
            server.start();
            running = true;
            Log.i(TAG, "🏠 Hub模式已启动，端口: " + HUB_PORT);
        } catch (IOException e) {
            Log.e(TAG, "Hub服务启动失败", e);
        }
    }

    private void stopHubServer() {
        if (server != null) {
            server.stop();
            running = false;
            Log.i(TAG, "🏠 Hub模式已停止");
        }
    }

    // ==================== Hub端：HTTP处理 ====================

    /** POST /hub/sync — 接收设备上报的数据 */
    private Response handleSync(IHTTPSession session) {
        try {
            String body = readBody(session);
            SyncPayload payload = gson.fromJson(body, SyncPayload.class);

            if (payload != null) {
                // 合并数据到Hub本地数据库
                if (payload.checkIns != null) merger.mergeCheckIns(payload.checkIns);
                if (payload.coins != null) merger.mergeCoinTransactions(payload.coins);
                if (payload.tasks != null) merger.mergeTasks(payload.tasks);
                if (payload.redemptions != null) merger.mergeRedemptions(payload.redemptions);

                // 标记已同步
                if (payload.checkIns != null)
                    for (CheckIn c : payload.checkIns) db.checkInDao().markSynced(c.id);
                if (payload.coins != null)
                    for (CoinTransaction t : payload.coins) db.coinTransactionDao().markSynced(t.id);
            }

            // 返回Hub上积累的所有未同步数据给请求方
            String responseData = buildAccumulatedPayload();
            return newFixedLengthResponse(
                    Response.Status.OK, "application/json", responseData);

        } catch (Exception e) {
            Log.e(TAG, "处理同步请求失败", e);
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    /** GET /hub/pull?since=timestamp — 拉取自某个时间点后的增量数据 */
    private Response handlePull(IHTTPSession session) {
        try {
            long since = 0;
            String sinceParam = session.getParms().get("since");
            if (sinceParam != null) {
                try { since = Long.parseLong(sinceParam); } catch (Exception e) {
                    Log.d(TAG, "解析since参数失败: " + sinceParam);
                }
            }

            // 构建拉取响应
            PullResponse response = new PullResponse();
            response.serverTime = System.currentTimeMillis();
            response.hubDeviceId = SyncManager.getInstance(context).getDeviceId();

            String result = gson.toJson(response);
            return newFixedLengthResponse(Response.Status.OK, "application/json", result);

        } catch (Exception e) {
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    /** GET /hub/peek — 健康检查 */
    private Response handlePeek() {
        String json = "{\"status\":\"hub_online\",\"device\":\""
                + SyncManager.getInstance(context).getDeviceId()
                + "\",\"uptime\":\"active\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    /** GET /hub/discover — Hub发现接口（用于设备扫描） */
    private Response handleDiscover() {
        String json = "{\"type\":\"habit_hub\",\"version\":1,\"device\":\""
                + SyncManager.getInstance(context).getDeviceId() + "\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    // ==================== 客户端：设备端调用 ====================

    /**
     * 向Hub同步数据
     * 1. 发现Hub（扫描局域网或使用缓存）
     * 2. 上传本地未同步数据
     * 3. 下载Hub上的最新数据
     * @return true 同步成功
     */
    public boolean syncToHub() {
        String hubIp = discoverHub();
        if (hubIp == null) return false;

        try {
            // 上传本地未同步数据
            URL syncUrl = new URL("http://" + hubIp + ":" + HUB_PORT + "/hub/sync");
            HttpURLConnection conn = (HttpURLConnection) syncUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            String localData = buildSyncPayload();
            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write(localData);
            writer.flush();
            writer.close();

            // 读取Hub返回的数据并合并
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();
            conn.disconnect();

            // 解析并合并Hub的数据
            SyncPayload hubData = gson.fromJson(response.toString(), SyncPayload.class);
            if (hubData != null) {
                if (hubData.checkIns != null) merger.mergeCheckIns(hubData.checkIns);
                if (hubData.coins != null) merger.mergeCoinTransactions(hubData.coins);
                if (hubData.tasks != null) merger.mergeTasks(hubData.tasks);
                if (hubData.redemptions != null) merger.mergeRedemptions(hubData.redemptions);

                // 标记这些数据为已同步
                if (hubData.checkIns != null)
                    for (CheckIn c : hubData.checkIns) db.checkInDao().markSynced(c.id);
                if (hubData.coins != null)
                    for (CoinTransaction t : hubData.coins) db.coinTransactionDao().markSynced(t.id);
            }

            Log.d(TAG, "✅ Hub同步成功: " + hubIp);
            return true;

        } catch (Exception e) {
            Log.d(TAG, "Hub同步失败: " + e.getMessage());
            cachedHubIp = null; // 清除缓存，下次重新发现
            return false;
        }
    }

    /**
     * 发现局域网内的Hub设备
     * 策略：先尝试缓存 → 再扫描端口 18081
     */
    private String discoverHub() {
        // 缓存有效期内使用缓存
        if (cachedHubIp != null && (System.currentTimeMillis() - lastHubDiscovery) < 300000) {
            return cachedHubIp;
        }

        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return null;
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) return null;

            int ipInt = wifiInfo.getIpAddress();
            String myIp = String.format("%d.%d.%d.%d",
                    (ipInt & 0xff), (ipInt >> 8 & 0xff),
                    (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
            String subnet = myIp.substring(0, myIp.lastIndexOf('.') + 1);

            // 扫描子网，查找端口18081
            for (int i = 1; i < 255; i++) {
                String targetIp = subnet + i;
                if (targetIp.equals(myIp)) continue;

                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(targetIp, HUB_PORT), 150);
                    socket.close();

                    // 发现Hub！验证一下
                    URL peekUrl = new URL("http://" + targetIp + ":" + HUB_PORT + "/hub/discover");
                    HttpURLConnection conn = (HttpURLConnection) peekUrl.openConnection();
                    conn.setConnectTimeout(300);
                    if (conn.getResponseCode() == 200) {
                        cachedHubIp = targetIp;
                        lastHubDiscovery = System.currentTimeMillis();
                        Log.d(TAG, "🔍 发现Hub: " + targetIp);
                        return targetIp;
                    }
                } catch (Exception e) {
                        Log.d(TAG, "Hub扫描跳过: " + targetIp);
                    }
            }
        } catch (Exception e) {
            Log.e(TAG, "Hub发现失败", e);
        }
        return null;
    }

    /** 清除Hub缓存（强制重新发现） */
    public void clearHubCache() {
        cachedHubIp = null;
        lastHubDiscovery = 0;
    }

    // ==================== 数据负载构建 ====================

    private String buildSyncPayload() {
        SyncPayload payload = new SyncPayload();
        payload.checkIns = db.checkInDao().getUnsynced();
        payload.coins = db.coinTransactionDao().getUnsynced();
        payload.tasks = db.taskDao().getUnsynced();
        payload.redemptions = db.redemptionDao().getUnsynced();
        payload.deviceId = SyncManager.getInstance(context).getDeviceId();
        return gson.toJson(payload);
    }

    /** 构建Hub上所有待同步数据（给请求方返回） */
    private String buildAccumulatedPayload() {
        SyncPayload payload = new SyncPayload();
        payload.checkIns = db.checkInDao().getUnsynced();
        payload.coins = db.coinTransactionDao().getUnsynced();
        payload.tasks = db.taskDao().getUnsynced();
        payload.redemptions = db.redemptionDao().getUnsynced();
        payload.deviceId = SyncManager.getInstance(context).getDeviceId();
        return gson.toJson(payload);
    }

    // ==================== 工具方法 ====================

    private String readBody(IHTTPSession session) throws IOException {
        int contentLength = Integer.parseInt(
                session.getHeaders().getOrDefault("content-length", "0"));
        if (contentLength > 0) {
            byte[] buf = new byte[contentLength];
            session.getInputStream().read(buf, 0, contentLength);
            return new String(buf, "UTF-8");
        }
        // 尝试从body map读取
        Map<String, String> bodyMap = new HashMap<>();
        try { session.parseBody(bodyMap); } catch (Exception e) {
            Log.d(TAG, "解析请求体失败", e);
        }
        return bodyMap.get("postData");
    }

    // ===================== 内部数据类 =====================

    private static class SyncPayload {
        String deviceId;
        List<CheckIn> checkIns;
        List<CoinTransaction> coins;
        List<Task> tasks;
        List<Redemption> redemptions;
    }

    private static class PullResponse {
        long serverTime;
        String hubDeviceId;
    }
}