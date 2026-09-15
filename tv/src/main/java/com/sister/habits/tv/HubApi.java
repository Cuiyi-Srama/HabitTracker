package com.sister.habits.tv;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Hub 通信层：仅依赖 Android 内置 org.json，无第三方 HTTP 库 */
public final class HubApi {

    private final String base;
    private final String token;

    public HubApi(String base, String token) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        this.base = b;
        this.token = token == null ? "" : token.trim();
    }

    public JSONObject get(String path) throws Exception {
        return call("GET", path, null);
    }

    public JSONObject post(String path, JSONObject body) throws Exception {
        return call("POST", path, body);
    }

    private JSONObject call(String method, String path, JSONObject body) throws Exception {
        if (base.isEmpty()) {
            throw new Exception("服务器地址未设置");
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("X-Hub-Token", token);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] raw = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setFixedLengthStreamingMode(raw.length);
                OutputStream os = null;
                try {
                    os = conn.getOutputStream();
                    os.write(raw);
                    os.flush();
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = is == null ? "" : readAll(is);
            if (code >= 400) {
                throw new Exception("HTTP " + code + " " + text);
            }
            return new JSONObject(text.isEmpty() ? "{}" : text);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
        } finally {
            try {
                is.close();
            } catch (Exception ignored) {
            }
        }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }
}