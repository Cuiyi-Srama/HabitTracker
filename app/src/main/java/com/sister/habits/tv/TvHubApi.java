package com.sister.habits.tv;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** TV 看片：Hub 通信层（仅用内置 org.json + HttpURLConnection） */
public final class TvHubApi {

    private final String base;
    private final String token;

    public TvHubApi(String base, String token) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        this.base = b;
        this.token = token == null ? "" : token.trim();
    }

    /** 暴露 token，供封面图加载器等直接建连的场景使用 */
    public String token() {
        return token;
    }

    /** 暴露 base，供直接建连的场景使用 */
    public String base() {
        return base;
    }

    /**
     * 封面图代理地址（2026-09-25 v4.0）。
     * TV 路由白名单只放行 Hub，无法直连 B 站 CDN，故走 Hub 代理端点。
     */
    public String coverUrl(String videoId) {
        if (base.isEmpty() || videoId == null || videoId.isEmpty()) {
            return null;
        }
        try {
            return base + "/cover/" + java.net.URLEncoder.encode(videoId, "UTF-8");
        } catch (Exception e) {
            return null;
        }
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