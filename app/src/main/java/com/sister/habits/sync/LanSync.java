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
        if (!running) start();
        new Thread(() -> {
            try {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifiManager == null) return;
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo == null) return;

                int ipInt = wifiInfo.getIpAddress();
                String myIp = String.format("%d.%d.%d.%d",
                        (ipInt & 0xff), (ipInt >> 8 & 0xff),
                        (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));

                String subnet = myIp.substring(0, myIp.lastIndexOf('.') + 1);
                for (int i = 1; i < 255; i++) {
                    String targetIp = subnet + i;
                    if (targetIp.equals(myIp)) continue;
                    try {
                        Socket socket = new Socket();
                        socket.connect(new InetSocketAddress(targetIp, PORT), 200);
                        socket.close();
                        syncWithDevice(targetIp);
                    } catch (Exception e) {
                        Log.d(TAG, "设备 " + targetIp + " 不可达");
                    }
            }
        } catch (Exception e) {
            Log.e(TAG, "局域网同步扫描失败", e);
        }
        }).start();
    }

    private void syncWithDevice(String targetIp) {
        try {
            URL syncUrl = new URL("http://" + targetIp + ":" + PORT + "/sync");
            HttpURLConnection syncConn = (HttpURLConnection) syncUrl.openConnection();
            syncConn.setRequestMethod("POST");
            syncConn.setDoOutput(true);
            syncConn.setConnectTimeout(2000);
            syncConn.setReadTimeout(2000);

            String localData = buildSyncPayload();
            OutputStreamWriter writer = new OutputStreamWriter(syncConn.getOutputStream());
            writer.write(localData);
            writer.flush();
            writer.close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(syncConn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            mergeRemoteData(response.toString());
            syncConn.disconnect();
        } catch (Exception e) {
            Log.d(TAG, "同步失败: " + targetIp + " - " + e.getMessage());
        }
    }

    private String buildSyncPayload() {
        SyncPayload payload = new SyncPayload();
        payload.checkIns = db.checkInDao().getUnsynced();
        payload.coins = db.coinTransactionDao().getUnsynced();
        payload.tasks = db.taskDao().getUnsynced();
        payload.redemptions = db.redemptionDao().getUnsynced();
        payload.vocabularies = db.vocabularyDao().getAll();
        // payload.wordReviews = db.wordReviewDao().getAll(); // WordReviewDao has no getAll
        payload.shopItems = db.shopItemDao().getAll();
        payload.wishlistItems = db.wishlistDao().getAll();
        payload.wordBanks = db.wordBankDao().getAll();
        payload.economyConfig = db.economyConfigDao().getConfig();
        payload.gateConfig = db.gateConfigDao().getConfig();
        payload.dailyGates = db.dailyGateDao().getUnsynced();
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
        payload.shopItems = db.shopItemDao().getAll();
        payload.wishlistItems = db.wishlistDao().getAll();
        payload.wordBanks = db.wordBankDao().getAll();
        payload.economyConfig = db.economyConfigDao().getConfig();
        payload.gateConfig = db.gateConfigDao().getConfig();
        payload.dailyGates = db.dailyGateDao().getUnsynced();
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

    private void mergeRemoteData(String json) {
        SyncPayload payload = gson.fromJson(json, SyncPayload.class);
        if (payload == null) return;
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

    private Response servePeek() {
        String json = "{\"status\":\"online\",\"device\":\"" +
                SyncManager.getInstance(context).getDeviceId() + "\"}";
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response serveSync(IHTTPSession session) {
        try {
            Map<String, String> bodyMap = new HashMap<>();
            session.parseBody(bodyMap);
            String payload = bodyMap.get("postData");
            if (payload == null) {
                // 尝试从body读取
                StringBuilder sb = new StringBuilder();
                try (InputStream in = session.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) sb.append(new String(buf, 0, n));
                }
                payload = sb.toString();
            }
            mergeRemoteData(payload != null ? payload : "");
            // 新设备（增量数据为空）→ 返回全量历史数据引导；否则增量交换
            String responsePayload = isEmptyDevice(payload != null ? payload : "")
                    ? buildFullPayload() : buildSyncPayload();
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", responsePayload);
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
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
    }
}