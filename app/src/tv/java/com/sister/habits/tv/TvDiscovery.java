package com.sister.habits.tv;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 局域网自动发现 Hub。
 *
 * 思路（零额外依赖、Hub 侧无需改动）：
 *  1. 取本机所在网段（site-local IPv4 的 /24 前缀，例如 192.168.1.）；
 *  2. 并发 TCP 探测 254 个地址的 23458 端口（只挑端口开着的，通常 1~2 个）；
 *  3. 对端口开着的主机发一次 GET /habit/cuiyi/tv/status（带 token），
 *     返回体里含 "ok"+true 的才认定为 Hub；
 *  4. 局域网找不到时，退回公网隧道地址（在外面也能用）。
 *
 * 注意：本方法为阻塞调用，必须在后台线程执行。
 */
public final class TvDiscovery {

    /** 公网兜底地址（局域网找不到时使用，速度较慢） */
    public static final String TUNNEL_HUB = "https://sync.cuiyisrama.top/habit/cuiyi/tv";

    public interface Callback {
        /** 找到可用服务器（参数形如 http://192.168.1.17:23458/habit/cuiyi/tv） */
        void onFound(String hubBase, boolean viaTunnel);

        /** 没找到 */
        void onNotFound();
    }

    private static final int PORT = 23458;
    private static final String TENANT_PATH = "/habit/cuiyi/tv";
    private static final int TCP_TIMEOUT_MS = 350;
    private static final int SCAN_THREADS = 48;
    private static final long SCAN_TOTAL_WAIT_MS = 9000L;

    private TvDiscovery() {
    }

    /** 阻塞式发现（后台线程调用）。token 由 TvPrefs 提供。 */
    public static void discover(String token, Callback cb) {
        for (String prefix : localPrefixes()) {
            String hit = scanPrefix(prefix, token);
            if (hit != null) {
                cb.onFound(hit, false);
                return;
            }
        }
        if (verify(TUNNEL_HUB, token)) {
            cb.onFound(TUNNEL_HUB, true);
            return;
        }
        cb.onNotFound();
    }

    /** 本机所有 site-local IPv4 所属的 /24 前缀，如 "192.168.1." */
    private static List<String> localPrefixes() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        String ip = a.getHostAddress();
                        int dot = ip.lastIndexOf('.');
                        if (dot > 0) {
                            String prefix = ip.substring(0, dot + 1);
                            if (!out.contains(prefix)) {
                                out.add(prefix);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 拿不到网卡信息时退回最常见网段
        }
        if (out.isEmpty()) {
            out.add("192.168.1.");
        }
        return out;
    }

    /** 扫描一个 /24，返回第一个可用 Hub 的 base url；找不到返回 null */
    private static String scanPrefix(String prefix, String token) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> found = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(SCAN_THREADS);
        try {
            for (int i = 1; i <= 254; i++) {
                final String ip = prefix + i;
                pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (latch.getCount() == 0) {
                            return;
                        }
                        if (!tcpOpen(ip, PORT, TCP_TIMEOUT_MS)) {
                            return;
                        }
                        String base = "http://" + ip + ":" + PORT + TENANT_PATH;
                        if (verify(base, token) && latch.getCount() > 0) {
                            found.set(base);
                            latch.countDown();
                        }
                    }
                });
            }
            latch.await(SCAN_TOTAL_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return found.get();
    }

    private static boolean tcpOpen(String ip, int port, int timeoutMs) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    /** 用 /status 验证该地址确实是本家庭的 Hub */
    private static boolean verify(String base, String token) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + "/status").openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("X-Hub-Token", token);
            conn.setRequestProperty("Accept", "application/json");
            InputStream in = conn.getInputStream();
            byte[] buf = new byte[512];
            int n = in.read(buf);
            in.close();
            if (n <= 0) {
                return false;
            }
            String body = new String(buf, 0, n, StandardCharsets.UTF_8);
            return body.contains("\"ok\"") && body.contains("true");
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
