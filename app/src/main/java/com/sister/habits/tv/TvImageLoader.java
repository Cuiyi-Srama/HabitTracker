package com.sister.habits.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TV 封面图加载器（2026-09-25 v4.0 新增）。
 *
 * 为什么需要它：
 *   TV 端路由白名单只放行 Hub，无法直连 i2.hdslb.com（B 站 CDN 有防盗链）。
 *   故 Hub 提供代理端点 /tv/cover/{videoId}，本类只管从 Hub 拉字节并解码。
 *
 * 设计要点：
 *   1. 内存 LRU 缓存（默认 48 张）—— 电视滚动列表时不重复请求；
 *   2. 单独线程池（2 线程）—— 不阻塞列表渲染；
 *   3. 弱引用式回填 —— view 被复用时通过 tag 校验，避免错位；
 *   4. 失败静默 —— 封面缺失不影响功能，只留空位。
 */
public final class TvImageLoader {

    private static final int MAX_MEM_ENTRIES = 48;
    private static final long MAX_MEM_BYTES = 24L * 1024 * 1024;  // 24MB 上限

    /** 内存缓存：LinkedHashMap 按访问顺序，超限即淘汰最旧 */
    private static final Map<String, Bitmap> MEM =
            new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    if (size() > MAX_MEM_ENTRIES) return true;
                    long total = 0;
                    for (Bitmap b : values()) {
                        if (b != null) total += b.getByteCount();
                    }
                    return total > MAX_MEM_BYTES;
                }
            };

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.ExecutorService POOL =
            java.util.concurrent.Executors.newFixedThreadPool(2);

    private TvImageLoader() {}

    /**
     * 加载封面到 ImageView。
     *
     * @param iv      目标控件
     * @param api     Hub 通信层（用它拼 base + token）
     * @param videoId B 站视频号（BV 号）
     */
    public static void load(final ImageView iv, final TvHubApi api, final String videoId) {
        if (iv == null || videoId == null || videoId.isEmpty()) {
            return;
        }
        // 标记当前请求归属，防止列表复用导致的错位
        iv.setTag(com.sister.habits.R.id.tv_cover_tag, videoId);
        iv.setImageDrawable(null);

        final Bitmap cached = MEM.get(videoId);
        if (cached != null && !cached.isRecycled()) {
            iv.setImageDrawable(new BitmapDrawable(iv.getResources(), cached));
            return;
        }

        POOL.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = fetch(api, videoId);
                if (bmp == null) {
                    return;
                }
                synchronized (MEM) {
                    MEM.put(videoId, bmp);
                }
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        Object tag = iv.getTag(com.sister.habits.R.id.tv_cover_tag);
                        // 只有仍是同一个 videoId 才回填，避免复用错位
                        if (videoId.equals(tag)) {
                            iv.setImageDrawable(new BitmapDrawable(iv.getResources(), bmp));
                        }
                    }
                });
            }
        });
    }

    /** 从 Hub 拉取封面字节并解码；任何失败都返回 null（静默） */
    private static Bitmap fetch(TvHubApi api, String videoId) {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            String url = api.coverUrl(videoId);
            if (url == null || url.isEmpty()) {
                return null;
            }
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("X-Hub-Token", api.token());
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            is = conn.getInputStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            // 电视内存有限，封面统一降采样到不超过 640px 宽
            if (bmp == null) {
                return null;
            }
            if (bmp.getWidth() > 640) {
                int h = (int) (bmp.getHeight() * (640f / bmp.getWidth()));
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 640, h, true);
                if (scaled != bmp) {
                    bmp.recycle();
                }
                bmp = scaled;
            }
            return bmp;
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
