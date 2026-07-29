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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * 家庭中枢（Hub）同步模块
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
    private String cachedHubIp = null;
    private long lastHubDiscovery = 0;

    // ==================== 状态追踪 ====================
    private long lastSyncTime = 0;
    private boolean lastSyncSuccess = false;
    private String lastSyncMessage = "";
    private final java.util.List<String> discoveredHubs = new java.util.ArrayList<>();
    private volatile boolean scanning = false;

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
                    return NanoHTTPD.newFixedLengthResponse(
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
                if (payload.vocabularies != null) merger.mergeVocabularies(payload.vocabularies);
                if (payload.shopItems != null) merger.mergeShopItems(payload.shopItems);
                if (payload.wishlistItems != null) merger.mergeWishlistItems(payload.wishlistItems);
                if (payload.wordBanks != null) merger.mergeWordBanks(payload.wordBanks);
                if (payload.coinEarnings != null) merger.mergeCoinEarnings(payload.coinEarnings);
                if (payload.economyConfig != null) merger.mergeEconomyConfig(payload.economyConfig);

                // 标记已同步
                if (payload.checkIns != null)
                    for (CheckIn c : payload.checkIns) db.checkInDao().markSynced(c.id);
                if (payload.coins != null)
                    for (CoinTransaction t : payload.coins) db.coinTransactionDao().markSynced(t.id);
            }

            // 返回Hub上积累的所有未同步数据给请求方
            String responseData = buildAccumulatedPayload();
            return NanoHTTPD.newFixedLengthResponse(
                    Response.Status.OK, "application/json", responseData);

        } catch (Exception e) {
            Log.e(TAG, "处理同步请求失败", e);
            return NanoHTTPD.newFixedLengthResponse(
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

            // 返回Hub上所有数据（增量+全量）
            String result = buildAccumulatedPayload();
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", result);

        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    /** GET /hub/peek — 健康检查 */
    private Response handlePeek() {
        String json = "{\"status\":\"hub_online\",\"device\":\""
                + SyncManager.getInstance(context).getDeviceId()
                + "\",\"uptime\":\"active\"}";
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    /** GET /hub/discover — Hub发现接口（用于设备扫描） */
    private Response handleDiscover() {
        String json = "{\"type\":\"habit_hub\",\"version\":1,\"device\":\""
                + SyncManager.getInstance(context).getDeviceId() + "\"}";
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", json);
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
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

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
                if (hubData.vocabularies != null) merger.mergeVocabularies(hubData.vocabularies);
                if (hubData.shopItems != null) merger.mergeShopItems(hubData.shopItems);
                if (hubData.wishlistItems != null) merger.mergeWishlistItems(hubData.wishlistItems);
                if (hubData.wordBanks != null) merger.mergeWordBanks(hubData.wordBanks);
                if (hubData.coinEarnings != null) merger.mergeCoinEarnings(hubData.coinEarnings);
                if (hubData.economyConfig != null) merger.mergeEconomyConfig(hubData.economyConfig);

                // 标记这些数据为已同步
                if (hubData.checkIns != null)
                    for (CheckIn c : hubData.checkIns) db.checkInDao().markSynced(c.id);
                if (hubData.coins != null)
                    for (CoinTransaction t : hubData.coins) db.coinTransactionDao().markSynced(t.id);
            }

            Log.d(TAG, "✅ Hub同步成功: " + hubIp);
            lastSyncTime = System.currentTimeMillis();
            lastSyncSuccess = true;
            lastSyncMessage = "来自 " + hubIp;
            return true;

        } catch (Exception e) {
            Log.d(TAG, "Hub同步失败: " + e.getMessage());
            cachedHubIp = null;
            lastSyncTime = System.currentTimeMillis();
            lastSyncSuccess = false;
            lastSyncMessage = e.getMessage();
            return false;
        }
    }

    /** 手动设置Hub IP（跳过扫描，用户直接输入） */
    public void setManualHubIp(String ip) {
        if (ip != null && !ip.isEmpty()) {
            this.cachedHubIp = ip;
            this.lastHubDiscovery = System.currentTimeMillis();
            Log.d(TAG, "📌 手动设置Hub IP: " + ip);
        }
    }

    /**
     * 发现局域网内的Hub设备
     * 策略：先尝试缓存 → 并行扫描端口 18081（32线程，8秒内完成）
     */
    private String discoverHub() {
        // 缓存有效期内使用缓存（5分钟）
        if (cachedHubIp != null && (System.currentTimeMillis() - lastHubDiscovery) < 300000) {
            return cachedHubIp;
        }

        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return null;
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) return null;

            int ipInt = wifiInfo.getIpAddress();
            if (ipInt == 0) return null;
            String myIp = String.format("%d.%d.%d.%d",
                    (ipInt & 0xff), (ipInt >> 8 & 0xff),
                    (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
            String subnet = myIp.substring(0, myIp.lastIndexOf('.') + 1);

            // 并行扫描子网（32线程大幅加速）
            ExecutorService executor = Executors.newFixedThreadPool(32);
            AtomicReference<String> foundHub = new AtomicReference<>(null);

            for (int i = 1; i < 255; i++) {
                final String targetIp = subnet + i;
                if (targetIp.equals(myIp)) continue;
                executor.submit(() -> {
                    if (foundHub.get() != null) return;
                    try {
                        Socket socket = new Socket();
                        socket.connect(new InetSocketAddress(targetIp, HUB_PORT), 250);
                        socket.close();

                        URL peekUrl = new URL("http://" + targetIp + ":" + HUB_PORT + "/hub/discover");
                        HttpURLConnection conn = (HttpURLConnection) peekUrl.openConnection();
                        conn.setConnectTimeout(800);
                        conn.setReadTimeout(800);
                        if (conn.getResponseCode() == 200) {
                            foundHub.set(targetIp);
                            Log.d(TAG, "🔍 发现Hub: " + targetIp);
                        }
                        conn.disconnect();
                    } catch (Exception ignored) {}
                });
            }

            executor.shutdown();
            try { executor.awaitTermination(8, TimeUnit.SECONDS); } catch (InterruptedException e) { executor.shutdownNow(); }

            String result = foundHub.get();
            if (result != null) {
                cachedHubIp = result;
                lastHubDiscovery = System.currentTimeMillis();
                return result;
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
        payload.vocabularies = db.vocabularyDao().getAll();
        payload.shopItems = db.shopItemDao().getAll();
        payload.wishlistItems = db.wishlistDao().getAll();
        payload.wordBanks = db.wordBankDao().getAll();
        payload.coinEarnings = db.coinEarningDao().getUnsynced();
        payload.economyConfig = db.economyConfigDao().getConfig();
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
        payload.vocabularies = db.vocabularyDao().getAll();
        payload.shopItems = db.shopItemDao().getAll();
        payload.wishlistItems = db.wishlistDao().getAll();
        payload.wordBanks = db.wordBankDao().getAll();
        payload.coinEarnings = db.coinEarningDao().getUnsynced();
        payload.economyConfig = db.economyConfigDao().getConfig();
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

    
    // ==================== 状态查询 ====================
    /** 获取Hub服务器运行状态 */
    public String getServerStatus() {
        if (server != null && running) return "🟢 运行中 (端口 " + HUB_PORT + ")";
        return "🔴 已停止";
    }
    /** 获取上次同步时间 */
    public String getLastSyncInfo() {
        if (lastSyncTime == 0) return "⏳ 尚未同步";
        String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(lastSyncTime));
        return (lastSyncSuccess ? "✅ " : "❌ ") + time + " - " + lastSyncMessage;
    }
    /** 获取已发现的Hub设备列表 */
    public java.util.List<String> getDiscoveredHubs() {
        return new java.util.ArrayList<>(discoveredHubs);
    }
    /** 清除发现的Hub缓存 */
    public void clearDiscoveredHubs() {
        discoveredHubs.clear();
    }

    // ==================== 并行网络扫描 ====================
    /**
     * 快速扫描局域网内的Hub设备（并行批量扫描）
     * 将254个IP分为多批并行连接，大幅缩短扫描时间
     */
    public void scanNetwork(final int timeoutMsPerHost, final SyncCallback callback) {
        if (scanning) {
            if (callback != null) callback.onStatusUpdate("⏳ 正在扫描中，请稍候...");
            return;
        }
        scanning = true;
        discoveredHubs.clear();
        final int[] scannedCount = {0};
        final int totalHosts = 254;
        final java.util.List<String> foundHubs = new java.util.ArrayList<>();
        
        if (callback != null) callback.onStatusUpdate("🔍 正在扫描局域网设备...");
        
        new Thread(() -> {
            try {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifiManager == null) { scanning = false; return; }
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo == null) { scanning = false; return; }
                
                int ipInt = wifiInfo.getIpAddress();
                String myIp = String.format("%d.%d.%d.%d",
                        (ipInt & 0xff), (ipInt >> 8 & 0xff),
                        (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                String subnet = myIp.substring(0, myIp.lastIndexOf('.') + 1);
                
                // 分批并行扫描（每批10个）
                final int BATCH_SIZE = 10;
                for (int batchStart = 1; batchStart <= totalHosts; batchStart += BATCH_SIZE) {
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(
                            Math.min(BATCH_SIZE, totalHosts - batchStart + 1));
                    
                    for (int offset = 0; offset < BATCH_SIZE && batchStart + offset <= totalHosts; offset++) {
                        final int i = batchStart + offset;
                        final String targetIp = subnet + i;
                        
                        if (targetIp.equals(myIp)) {
                            latch.countDown();
                            synchronized (scannedCount) { scannedCount[0]++; }
                            continue;
                        }
                        
                        final int finalI = i;
                        new Thread(() -> {
                            try {
                                Socket socket = new Socket();
                                socket.connect(new InetSocketAddress(targetIp, HUB_PORT), 
                                        Math.min(timeoutMsPerHost, 200));
                                socket.close();
                                
                                // 验证是否是Hub
                                URL peekUrl = new URL("http://" + targetIp + ":" + HUB_PORT + "/hub/discover");
                                HttpURLConnection conn = (HttpURLConnection) peekUrl.openConnection();
                                conn.setConnectTimeout(200);
                                conn.setReadTimeout(200);
                                if (conn.getResponseCode() == 200) {
                                    String respBody;
                                    try (java.util.Scanner s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A")) {
                                        respBody = s.hasNext() ? s.next() : "";
                                    }
                                    String devId = "";
                                    if (respBody.contains("device")) {
                                        try {
                                            java.util.Map<String, Object> map = new com.google.gson.GsonBuilder().create()
                                                    .fromJson(respBody, java.util.Map.class);
                                            if (map.get("device") != null) devId = (String) map.get("device");
                                        } catch (Exception e) { }
                                    }
                                    synchronized (foundHubs) {
                                        foundHubs.add(targetIp);
                                        discoveredHubs.add(targetIp);
                                    }
                                    if (callback != null) callback.onHubFound(targetIp, devId);
                                }
                                conn.disconnect();
                            } catch (Exception e) {
                                // 不可达，忽略
                            } finally {
                                latch.countDown();
                                synchronized (scannedCount) {
                                    scannedCount[0]++;
                                    if (callback != null && scannedCount[0] % 10 == 0)
                                        callback.onScanProgress(scannedCount[0], totalHosts);
                                }
                            }
                        }).start();
                    }
                    
                    try { latch.await(3, java.util.concurrent.TimeUnit.SECONDS); } 
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                
                // 扫描完成
                scanning = false;
                final int found = foundHubs.size();
                if (callback != null) {
                    if (found > 0) {
                        callback.onStatusUpdate("✅ 扫描完成，发现 " + found + " 台Hub设备");
                    } else {
                        callback.onStatusUpdate("❌ 未发现其他Hub设备\n请确保另一台设备已开启「Hub中枢」并连接同一WiFi");
                    }
                    callback.onScanProgress(totalHosts, totalHosts);
                }
            } catch (Exception e) {
                scanning = false;
                if (callback != null)
                    callback.onStatusUpdate("❌ 扫描失败: " + e.getMessage());
            }
        }).start();
    }

    /** 同步到Hub（带回调版本） */
    public void syncToHub(final SyncCallback callback) {
        if (callback != null) callback.onStatusUpdate("🔍 正在发现Hub...");
        new Thread(() -> {
            boolean result = syncToHub();
            if (callback != null) {
                if (result) {
                    callback.onStatusUpdate("✅ 同步成功");
                    callback.onSyncComplete(true, "数据已同步到中枢");
                } else {
                    String msg = discoveredHubs.isEmpty() 
                            ? "未发现任何Hub设备\n请先扫描网络或确认其他设备已开启Hub中枢"
                            : "连接Hub失败，请重试";
                    callback.onStatusUpdate("❌ " + msg);
                    callback.onSyncComplete(false, msg);
                }
            }
        }).start();
    }

    // 在 syncToHub() 成功后记录状态
    // 重写原有syncToHub方法以记录状态 - 但保持签名兼容
// ===================== 内部数据类 =====================

    private static class SyncPayload {
        String deviceId;
        List<CheckIn> checkIns;
        List<CoinTransaction> coins;
        List<Task> tasks;
        List<Redemption> redemptions;
        List<Vocabulary> vocabularies;
        List<ShopItem> shopItems;
        List<WishlistItem> wishlistItems;
        List<WordBank> wordBanks;
        List<CoinEarning> coinEarnings;
        com.sister.habits.data.models.EconomyConfig economyConfig;
    }

    private static class PullResponse {
        long serverTime;
        String hubDeviceId;
    }
}