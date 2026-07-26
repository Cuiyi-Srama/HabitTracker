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
                public NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session) {
                    String uri = session.getUri();
                    switch (uri) {
                        case "/peek":
                            return servePeek();
                        case "/sync":
                            return serveSync(session);
                        default:
                            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not Found");
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
                    } catch (Exception ignored) {}
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
        payload.deviceId = SyncManager.getInstance(context).getDeviceId();
        return gson.toJson(payload);
    }

    private void mergeRemoteData(String json) {
        SyncPayload payload = gson.fromJson(json, SyncPayload.class);
        if (payload == null) return;
        if (payload.checkIns != null) merger.mergeCheckIns(payload.checkIns);
        if (payload.coins != null) merger.mergeCoinTransactions(payload.coins);
        if (payload.tasks != null) merger.mergeTasks(payload.tasks);
        if (payload.redemptions != null) merger.mergeRedemptions(payload.redemptions);
        if (payload.checkIns != null)
            for (CheckIn c : payload.checkIns) db.checkInDao().markSynced(c.id);
        if (payload.coins != null)
            for (CoinTransaction t : payload.coins) db.coinTransactionDao().markSynced(t.id);
    }

    private NanoHTTPD.Response servePeek() {
        String json = "{\"status\":\"online\",\"device\":\"" +
                SyncManager.getInstance(context).getDeviceId() + "\"}";
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json);
    }

    private NanoHTTPD.Response serveSync(NanoHTTPD.IHTTPSession session) {
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
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", buildSyncPayload());
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
    }

    private static class SyncPayload {
        String deviceId;
        List<CheckIn> checkIns;
        List<CoinTransaction> coins;
        List<Task> tasks;
        List<Redemption> redemptions;
    }
}