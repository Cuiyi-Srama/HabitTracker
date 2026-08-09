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
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;

/**
 * 局域网同步模块
 * 基于 NanoHTTPD 搭建轻量 HTTP Server
 * 同一WiFi下设备通过端口扫描发现 + 数据交换
 */
public class LanSync {
    private static final String TAG = "LanSync";
    private static final int PORT = 18080;

    private final Context context;
    private final AppDatabase db;
    private final DataMerger merger;
    private final Gson gson;
    private NanoHTTPD server;
    private boolean running = false;

    public LanSync(Context context, AppDatabase db, DataMerger merger, Gson gson) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.merger = merger;
        this.gson = gson;
    }

    public void start() {
        if (running) return;
        try {
            server = new NanoHTTPD(PORT) {
                @Override
                public Response serve(IHTTPSession session) {
                    String uri = session.getUri();
                    switch (uri) {
                        case "/peek":
                            return servePeek();
                        case "/sync":
                            return serveSync(session);
                        case "/shop_image":
                            return serveShopImage(session);
                        default:
                            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
                    }
                }
            };
            server.start();
            running = true;
            Log.d(TAG, "局域网同步服务已启动，端口: " + PORT);
        } catch (IOException e) {
            Log.e(TAG, "启动局域网服务失败", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
            running = false;
        }
    }

    public void syncAll() {
        syncAll(null);
    }

    /** 扫描并同步所有可达设备；扫描结束后回调 onDone（无论是否找到设备） */
    public void syncAll(final Runnable onDone) {
        if (!running) start();
        new Thread(() -> {
            try {
                // Android 10+ 必须用 ConnectivityManager 获取 IP（WifiInfo.getIpAddress 返回0）
                String myIp = SyncManager.getInstance(context).getLocalIpv4();
                if (myIp == null) {
                    Log.e(TAG, "无法获取本机IPv4，跳过局域网扫描");
                    return;
                }

                String subnet = myIp.substring(0, myIp.lastIndexOf('.') + 1);
                for (int i = 1; i < 255; i++) {
                    String targetIp = subnet + i;
                    if (targetIp.equals(myIp)) continue;
                    try {
                        Socket socket = new Socket();
                        socket.connect(new InetSocketAddress(targetIp, PORT), 800);
                        socket.close();
                        syncWithDevice(targetIp);
                    } catch (Exception e) {
                        Log.d(TAG, "设备 " + targetIp + " 不可达");
                    }
            }
        } catch (Exception e) {
            Log.e(TAG, "局域网同步扫描失败", e);
        } finally {
            if (onDone != null) {
                try { onDone.run(); } catch (Exception ignored) {}
            }
        }
        }).start();
    }

    /** 扫码直连同步：QR码携带对方IP，直接连接交换数据（不依赖扫描发现） */
    public void syncToDevice(final String targetIp) {
        syncToDevice(targetIp, null);
    }

    /** 扫码直连同步（带进度回调：连接状态/收发条数/结果） */
    public void syncToDevice(final String targetIp, final SyncCallback callback) {
        if (targetIp == null || targetIp.isEmpty()) return;
        if (!running) start();
        new Thread(() -> {
            try {
                Log.d(TAG, "📡 扫码直连: " + targetIp);
                syncWithDevice(targetIp, callback);
            } catch (Exception e) {
                Log.e(TAG, "扫码直连失败: " + targetIp + " - " + e.getMessage());
                if (callback != null) callback.onSyncComplete(false, "❌ 连接异常: " + e.getMessage());
            }
        }).start();
    }

    private void syncWithDevice(String targetIp) {
        syncWithDevice(targetIp, null);
    }

    private void syncWithDevice(String targetIp, SyncCallback callback) {
        try {
            if (callback != null) callback.onStatusUpdate("📡 正在连接 " + targetIp + "...");
            URL syncUrl = new URL("http://" + targetIp + ":" + PORT + "/sync");
            HttpURLConnection syncConn = (HttpURLConnection) syncUrl.openConnection();
            syncConn.setRequestMethod("POST");
            syncConn.setDoOutput(true);
            syncConn.setConnectTimeout(10000);
            syncConn.setReadTimeout(10000);

            String localData = buildSyncPayload();
            // 必须显式设置 Content-Type + Content-Length：
            // ① NanoHTTPD 对 x-www-form-urlencoded 会把 JSON 吞进 parms（postData 永远 null）
            // ② 不设 Content-Length 时 HttpURLConnection 用 chunked，NanoHTTPD 2.3.1 不支持
            byte[] postBytes = localData.getBytes("UTF-8");
            syncConn.setRequestProperty("Content-Type", "application/json");
            syncConn.setFixedLengthStreamingMode(postBytes.length);
            java.io.OutputStream os = syncConn.getOutputStream();
            os.write(postBytes);
            os.flush();
            os.close();

            // 发送统计
            try {
                SyncPayload sent = gson.fromJson(localData, SyncPayload.class);
                if (callback != null && sent != null)
                    callback.onStatusUpdate("📤 发送: " + payloadSummary(sent));
            } catch (Exception ignored) {}

            BufferedReader reader = new BufferedReader(new InputStreamReader(syncConn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            // 接收统计
            try {
                SyncPayload received = gson.fromJson(response.toString(), SyncPayload.class);
                if (callback != null && received != null)
                    callback.onStatusUpdate("📥 收到: " + payloadSummary(received));
            } catch (Exception ignored) {}

            mergeRemoteData(response.toString(), targetIp);
            syncConn.disconnect();
            if (callback != null) callback.onSyncComplete(true, "✅ 同步完成");
        } catch (Exception e) {
            Log.d(TAG, "同步失败: " + targetIp + " - " + e.getMessage());
            if (callback != null) callback.onSyncComplete(false, "❌ 连接失败: " + e.getMessage());
        }
    }

    /** 汇总载荷中各表条数（用于进度显示） */
    private String payloadSummary(SyncPayload p) {
        StringBuilder sb = new StringBuilder();
        if (p.checkIns != null && !p.checkIns.isEmpty()) sb.append("打卡").append(p.checkIns.size()).append("条 ");
        if (p.coins != null && !p.coins.isEmpty()) sb.append("流水").append(p.coins.size()).append("条 ");
        if (p.tasks != null && !p.tasks.isEmpty()) sb.append("任务").append(p.tasks.size()).append("条 ");
        if (p.redemptions != null && !p.redemptions.isEmpty()) sb.append("兑换").append(p.redemptions.size()).append("条 ");
        if (p.vocabularies != null && !p.vocabularies.isEmpty()) sb.append("单词").append(p.vocabularies.size()).append("个 ");
        if (p.shopItems != null && !p.shopItems.isEmpty()) sb.append("商品").append(p.shopItems.size()).append("个 ");
        if (p.wishlistItems != null && !p.wishlistItems.isEmpty()) sb.append("心愿单").append(p.wishlistItems.size()).append("条 ");
        if (p.wordBanks != null && !p.wordBanks.isEmpty()) sb.append("词库").append(p.wordBanks.size()).append("个 ");
        if (p.dailyGates != null && !p.dailyGates.isEmpty()) sb.append("作业记录").append(p.dailyGates.size()).append("条 ");
        return sb.length() == 0 ? "（无数据）" : sb.toString().trim();
    }

    /** 收集商品图标路径（值留空=接收端按需流式拉取，不再塞 base64，避免 OOM） */
    private java.util.Map<String, String> collectShopImages() {
        java.util.Map<String, String> images = new java.util.HashMap<>();
        try {
            for (com.sister.habits.data.models.ShopItem s : db.shopItemDao().getAll()) {
                if (s.iconUrl == null || s.iconUrl.isEmpty()) continue;
                java.io.File f = new java.io.File(s.iconUrl);
                if (!f.exists() || f.length() <= 0) continue;
                images.put(s.iconUrl, "");
            }
        } catch (Exception e) {
            Log.w(TAG, "收集商品图片失败: " + e.getMessage());
        }
        return images;
    }

    private String buildSyncPayload() {
        SyncPayload payload = new SyncPayload();
        payload.checkIns = db.checkInDao().getUnsynced();
        payload.coins = db.coinTransactionDao().getUnsynced();
        payload.tasks = db.taskDao().getUnsynced();
        payload.redemptions = db.redemptionDao().getUnsynced();
        payload.vocabularies = db.vocabularyDao().getAll();
        payload.wordReviews = db.wordReviewDao().getAll();
        payload.shopItems = db.shopItemDao().getAll();
        payload.wishlistItems = db.wishlistDao().getAll();
        payload.wordBanks = db.wordBankDao().getAll();
        payload.economyConfig = db.economyConfigDao().getConfig();
        payload.gateConfig = db.gateConfigDao().getConfig();
        payload.dailyGates = db.dailyGateDao().getUnsynced();
        payload.shopImages = collectShopImages();
        payload.deviceId = SyncManager.getInstance(context).getDeviceId();
        return gson.toJson(payload);
    }

    /** 全量载荷：新设备首次加入时拉取全部历史数据（DataMerger幂等去重，安全） */
    private String buildFullPayload() {
        SyncPayload payload = new SyncPayload();
        payload.checkIns = db.checkInDao().getByUser("sister");
        payload.coins = db.coinTransactionDao().getByUser("sister");
        payload.tasks = db.taskDao().getAll();
        payload.redemptions = db.redemptionDao().getAll();
        payload.vocabularies = db.vocabularyDao().getAll();
        payload.wordReviews = db.wordReviewDao().getAll();
        payload.shopItems = db.shopItemDao().getAll();
        payload.wishlistItems = db.wishlistDao().getAll();
        payload.wordBanks = db.wordBankDao().getAll();
        payload.economyConfig = db.economyConfigDao().getConfig();
        payload.gateConfig = db.gateConfigDao().getConfig();
        payload.dailyGates = db.dailyGateDao().getUnsynced();
        payload.shopImages = collectShopImages();
        payload.deviceId = SyncManager.getInstance(context).getDeviceId();
        return gson.toJson(payload);
    }

    /** 判断对方是否新设备（增量字段全空 → 需要全量引导） */
    private boolean isEmptyDevice(String json) {
        try {
            SyncPayload p = gson.fromJson(json, SyncPayload.class);
            if (p == null) return true;
            boolean hasIncremental =
                    (p.checkIns != null && !p.checkIns.isEmpty()) ||
                    (p.coins != null && !p.coins.isEmpty()) ||
                    (p.tasks != null && !p.tasks.isEmpty()) ||
                    (p.redemptions != null && !p.redemptions.isEmpty()) ||
                    (p.dailyGates != null && !p.dailyGates.isEmpty());
            return !hasIncremental;
        } catch (Exception e) {
            return true; // 解析失败视为空设备，全量引导更安全
        }
    }

    private void mergeRemoteData(String json, String sourceIp) {
        SyncPayload payload = gson.fromJson(json, SyncPayload.class);
        if (payload == null) return;
        if (payload.shopImages != null && !payload.shopImages.isEmpty())
            handleShopImages(payload.shopImages, sourceIp);
        if (payload.wordReviews != null) merger.mergeWordReviews(payload.wordReviews);
        if (payload.checkIns != null) merger.mergeCheckIns(payload.checkIns);
        if (payload.coins != null) merger.mergeCoinTransactions(payload.coins);
        if (payload.tasks != null) merger.mergeTasks(payload.tasks);
        if (payload.redemptions != null) merger.mergeRedemptions(payload.redemptions);
        if (payload.vocabularies != null) merger.mergeVocabularies(payload.vocabularies);
        // WordReview merge skipped - WordReviewDao has no getAll
        if (payload.shopItems != null) merger.mergeShopItems(payload.shopItems);
        if (payload.wishlistItems != null) merger.mergeWishlistItems(payload.wishlistItems);
        if (payload.wordBanks != null) merger.mergeWordBanks(payload.wordBanks);
        if (payload.economyConfig != null) merger.mergeEconomyConfig(payload.economyConfig);
        if (payload.gateConfig != null) merger.mergeGateConfig(payload.gateConfig);
        if (payload.dailyGates != null) merger.mergeDailyGates(payload.dailyGates);
        // mark daily gates synced
        if (payload.dailyGates != null)
            for (DailyGate g : payload.dailyGates) db.dailyGateDao().markSynced(g.date);
        if (payload.checkIns != null)
            for (CheckIn c : payload.checkIns) db.checkInDao().markSynced(c.id);
        if (payload.coins != null)
            for (CoinTransaction t : payload.coins) db.coinTransactionDao().markSynced(t.id);
    }

    /** 商品图标处理：值非空=旧版 base64（兼容）；值空=从来源设备流式按需拉取 */
    private void handleShopImages(java.util.Map<String, String> images, String sourceIp) {
        if (images == null || images.isEmpty() || sourceIp == null) return;
        java.io.File dir = new java.io.File(context.getFilesDir(), "shop_images");
        if (!dir.exists()) dir.mkdirs();
        int got = 0;
        for (java.util.Map.Entry<String, String> e : images.entrySet()) {
            try {
                String remotePath = e.getKey();
                String fileName = remotePath.substring(remotePath.lastIndexOf('/') + 1);
                if (fileName.isEmpty()) continue;
                java.io.File out = new java.io.File(dir, fileName);
                if (out.exists()) continue; // 已有则跳过
                String b64 = e.getValue();
                if (b64 != null && !b64.isEmpty()) {
                    // 旧版对端：base64 直解
                    byte[] data = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                    fos.write(data);
                    fos.close();
                } else {
                    // 新版：流式按需下载（不占内存）
                    downloadShopImage(sourceIp, fileName, out);
                }
                got++;
            } catch (Exception ex) {
                Log.w(TAG, "图片获取失败: " + ex.getMessage());
            }
        }
        if (got > 0) Log.d(TAG, "商品图片同步完成: 新增 " + got + "/" + images.size());
    }

    /** 从对端流式下载图片（固定缓冲，内存安全） */
    private void downloadShopImage(String sourceIp, String fileName, java.io.File out) {
        try {
            URL url = new URL("http://" + sourceIp + ":" + PORT + "/shop_image?f=" + fileName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                is.close();
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "图片下载失败: " + fileName + " - " + e.getMessage());
        }
    }

    /** GET /shop_image?f=文件名 — 流式返回商品图片（供对端按需拉取） */
    private Response serveShopImage(IHTTPSession session) {
        try {
            String f = session.getParms().get("f");
            if (f == null || f.isEmpty()) return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "missing f");
            String fileName = f.substring(f.lastIndexOf('/') + 1); // 防路径穿越
            java.io.File img = new java.io.File(context.getFilesDir(), "shop_images/" + fileName);
            if (!img.exists()) return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found");
            return NanoHTTPD.newChunkedResponse(Response.Status.OK, "image/jpeg", new java.io.FileInputStream(img));
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    private Response servePeek() {
        String json = "{\"status\":\"online\",\"device\":\"" +
                SyncManager.getInstance(context).getDeviceId() + "\"}";
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response serveSync(IHTTPSession session) {
        try {
            String payload = readBody(session);
            mergeRemoteData(payload, session.getRemoteIpAddress());
            // 新设备（增量数据为空）→ 返回全量历史数据引导；否则增量交换
            String responsePayload = isEmptyDevice(payload)
                    ? buildFullPayload() : buildSyncPayload();
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", responsePayload);
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    /**
     * 读取请求体：优先按 Content-Length 从 InputStream 读（可靠）
     * fallback parseBody（兼容无 Content-Length 的旧客户端）
     * ⚠️ 不能直接依赖 parseBody 的 postData：Content-Type 为
     * x-www-form-urlencoded 时 JSON 会被 decodeParms 吞进 parms
     */
    private String readBody(IHTTPSession session) throws IOException {
        String cl = session.getHeaders().get("content-length");
        if (cl != null) {
            int contentLength;
            try {
                contentLength = Integer.parseInt(cl.trim());
            } catch (Exception e) {
                contentLength = 0;
            }
            if (contentLength > 0) {
                byte[] buf = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = session.getInputStream().read(buf, read, contentLength - read);
                    if (n < 0) break;
                    read += n;
                }
                return new String(buf, 0, read, "UTF-8");
            }
        }
        Map<String, String> bodyMap = new HashMap<>();
        try {
            session.parseBody(bodyMap);
        } catch (Exception ignored) {}
        String p = bodyMap.get("postData");
        return p != null ? p : "";
    }

    private static class SyncPayload {
        String deviceId;
        List<CheckIn> checkIns;
        List<CoinTransaction> coins;
        List<Task> tasks;
        List<Redemption> redemptions;
        List<Vocabulary> vocabularies;
        // List<WordReview> wordReviews; // skipped - no getAll()
        List<ShopItem> shopItems;
        List<WishlistItem> wishlistItems;
        List<WordBank> wordBanks;
        com.sister.habits.data.models.EconomyConfig economyConfig;
        com.sister.habits.data.models.GateConfig gateConfig;
        List<DailyGate> dailyGates;
        java.util.Map<String, String> shopImages; // 商品图标 base64（跨设备图片同步）
        List<WordReview> wordReviews; // 复习进度（艾宾浩斯）
    }
}