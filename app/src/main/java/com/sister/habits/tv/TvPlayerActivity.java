package com.sister.habits.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.sister.habits.R;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * TV 播放页（2026-09-25 v4.0 重做）。
 *
 * 核心语义变更（对应「天级时长池」需求）：
 *   旧：倒计时 = 本次进入播放页以来经过的秒数；退出即 /finish 结束会话。
 *   新：倒计时 = 服务端下发的「本会话配额」- 「本会话已播」；
 *       误触返回【不】结束会话，重进自动续看。
 *
 * 三条结束路径（只有这三条才发 /finish）：
 *   ① 倒计时归零；
 *   ② 用户在浮层点「返回列表」；
 *   ③ 用户按返回键【且】明确选择结束。
 *
 * 时间到不是退出，而是【暂停】并展示浮层，提供「再兑 N 分钟继续看」。
 */
public class TvPlayerActivity extends Activity {

    /** 剩余多少秒进入「醒目态」 */
    private static final int WARN_SECONDS = 5 * 60;

    private ExoPlayer player;
    private TextView tvCount;
    private TextView tvTimeupSub;
    private View overlayTimeup;
    private Button btnExtend;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TvPrefs prefs;
    private TvHubApi api;
    private String sessionId;
    private String videoId;
    private long startedAt;
    private long lastHeartbeatAt;
    /** 本会话配额（服务端 /start 下发，单位秒） */
    private int plannedSeconds;
    /** 会话进入时服务端已记录的「本会话已播秒数」，用于重进续算 */
    private int baselinePlayed;
    /** 是否已进入「时间到」状态 */
    private boolean timeUp;
    /** 是否已发 /finish（幂等保护） */
    private boolean finished;
    /** 是否已开始过会话（防止未有 sessionId 就上报） */
    private boolean started;
    private Runnable ticker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_player);
        prefs = new TvPrefs(this);
        api = prefs.api();

        sessionId = getIntent().getStringExtra("sessionId");
        videoId = getIntent().getStringExtra("videoId");
        String mediaUrl = getIntent().getStringExtra("mediaUrl");
        String title = getIntent().getStringExtra("title");
        plannedSeconds = getIntent().getIntExtra("plannedSeconds", 0);
        baselinePlayed = getIntent().getIntExtra("elapsedSeconds", 0);

        tvCount = findViewById(R.id.tv_count);
        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(title == null ? "" : title);

        overlayTimeup = findViewById(R.id.overlay_timeup);
        tvTimeupSub = findViewById(R.id.tv_timeup_sub);
        btnExtend = findViewById(R.id.btn_extend);
        findViewById(R.id.btn_timeup_back).setOnClickListener(v -> finishByUser());
        btnExtend.setOnClickListener(v -> showExtendDialog());

        // 兜底：极端情况下没有配额信息，给一个保守值避免除零/负数
        if (plannedSeconds <= 0) {
            plannedSeconds = 30 * 60;
        }

        initPlayer(mediaUrl);

        startedAt = System.currentTimeMillis();
        lastHeartbeatAt = startedAt;
        started = true;

        ticker = new Runnable() {
            @Override
            public void run() {
                if (finished || timeUp) {
                    return;
                }
                int remain = remaining();
                renderCountdown(remain);
                if (remain <= 0) {
                    onTimeUp();
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastHeartbeatAt >= 10000) {
                    lastHeartbeatAt = now;
                    sendHeartbeat();
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(ticker, 1000);
    }

    private void initPlayer(String mediaUrl) {
        try {
            PlayerView playerView = findViewById(R.id.player_view);
            Map<String, String> hdrs = new HashMap<>();
            hdrs.put("X-Hub-Token", prefs.token());
            DefaultHttpDataSource.Factory df = new DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(hdrs)
                    .setConnectTimeoutMs(8000)
                    .setReadTimeoutMs(20000);
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(df))
                    .build();
            playerView.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(mediaUrl == null ? "" : mediaUrl));
            player.setPlayWhenReady(true);
            player.prepare();
        } catch (Exception e) {
            Toast.makeText(this, "播放器初始化失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 本会话剩余秒数：配额 - 本会话已播（含重进前的累计） */
    private int remaining() {
        long localElapsed = (System.currentTimeMillis() - startedAt) / 1000;
        long used = (long) baselinePlayed + localElapsed;
        return (int) Math.max(0, plannedSeconds - used);
    }

    private static String fmt(int sec) {
        int s = Math.max(0, sec);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    /**
     * 渲染倒计时。
     *
     * 设计（2026-09-25 v4.0）：
     *   默认弱化 —— 18sp、alpha 0.75，不抢视线；
     *   剩余 <= 5 分钟才切醒目态 —— 24sp、alpha 1.0、暖色，提醒孩子快结束了。
     */
    private void renderCountdown(int remain) {
        tvCount.setText(fmt(remain));
        boolean warn = remain <= WARN_SECONDS;
        if (warn) {
            tvCount.setTextSize(24f);
            tvCount.setAlpha(1.0f);
            tvCount.setTextColor(0xFFFFC46B);  // 暖橙
        } else {
            tvCount.setTextSize(18f);
            tvCount.setAlpha(0.75f);
            tvCount.setTextColor(0xFFCFE8DD);
        }
    }

    private void sendHeartbeat() {
        if (!started || sessionId == null || sessionId.isEmpty()) {
            return;
        }
        final int played = playedTotal();
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("sessionId", sessionId);
                b.put("playedSeconds", played);
                api.post("/heartbeat", b);
            } catch (Exception ignored) {
            }
        }).start();
    }

    /** 本会话累计已播秒数（跨重进累加） */
    private int playedTotal() {
        long localElapsed = (System.currentTimeMillis() - startedAt) / 1000;
        long used = (long) baselinePlayed + localElapsed;
        return (int) Math.max(0, Math.min(plannedSeconds, used));
    }

    /**
     * 时间到：暂停播放 + 展示浮层（不退出）。
     *
     * 注意：此时【不发】/finish —— 会话仍由服务端持有，
     *   家长点「继续看」可原地续兑；点「返回列表」才结算。
     */
    private void onTimeUp() {
        if (timeUp) return;
        timeUp = true;
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.pause();
            } catch (Exception ignored) {
            }
        }
        sendHeartbeat();   // 把最后的进度报上去，便于结算准确
        tvCount.setText("00:00");
        tvTimeupSub.setText("本次配额 " + (plannedSeconds / 60) + " 分钟已用完");
        overlayTimeup.setVisibility(View.VISIBLE);
        overlayTimeup.requestFocus();
        btnExtend.requestFocus();
    }

    /** 「再兑 N 分钟继续看」：先 /exchange 入银行，再 /start 开新会话 */
    private void showExtendDialog() {
        final int[] opts = {30, 60, 90};
        String[] labels = new String[opts.length];
        for (int i = 0; i < opts.length; i++) {
            int cost = (int) Math.ceil(opts[i] / 30.0) * 20;
            labels[i] = opts[i] + " 分钟（" + cost + " 积分）";
        }
        new AlertDialog.Builder(this)
                .setTitle("兑换时长继续看")
                .setItems(labels, (d, which) -> exchangeThenRestart(opts[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private void exchangeThenRestart(final int minutes) {
        btnExtend.setEnabled(false);
        tvTimeupSub.setText("正在兑换…");
        new Thread(() -> {
            String err = null;
            try {
                // 1) 兑换：积分 -> 时间银行
                JSONObject body = new JSONObject();
                body.put("minutes", minutes);
                JSONObject r = api.post("/exchange", body);
                if (!r.optBoolean("ok", false)) {
                    err = reasonText(r.optString("reason", ""), r);
                }
            } catch (Exception e) {
                err = "兑换失败：" + e.getMessage();
            }
            final String e2 = err;
            runOnUiThread(() -> {
                btnExtend.setEnabled(true);
                if (e2 != null) {
                    tvTimeupSub.setText(e2);
                    return;
                }
                // 2) 兑换成功：结束当前会话（结算并退回未用完），再开新会话
                finishSession(() -> {
                    Toast.makeText(this, "已兑换 " + minutes + " 分钟，正在继续…",
                            Toast.LENGTH_SHORT).show();
                    finish();   // 由 TvVideoActivity 重新选片/自动续播
                });
            });
        }).start();
    }

    private static String reasonText(String reason, JSONObject r) {
        switch (reason) {
            case "insufficient_coins":
                return "积分不足（当前 " + r.optInt("balance", 0) + "）";
            case "invalid_minutes":
                return "时长档位无效";
            case "picker_unavailable":
                return "选片服务没启动，请检查电脑端";
            default:
                return "无法兑换（" + reason + "）";
        }
    }

    /** 用户主动结束（按返回键 / 点「返回列表」） */
    private void finishByUser() {
        confirmQuitThen(this::finish);
    }

    /**
     * 结算并结束会话（单一出口）。
     *
     * @param after 结算完成后的收尾动作
     */
    private void finishSession(final Runnable after) {
        if (finished) {
            if (after != null) after.run();
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
        }
        final int played = playedTotal();
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("sessionId", sessionId);
                b.put("playedSeconds", played);
                api.post("/finish", b);
            } catch (Exception ignored) {
            }
            runOnUiThread(() -> {
                if (after != null) after.run();
            });
        }).start();
    }

    /**
     * 结束前确认（异步）。
     *
     * ⚠️ 实现说明：AlertDialog 是异步弹出的，不能用「函数返回值」表示用户是否确认
     *   —— 那样必然返回 false，导致「结束」永远不生效。
     *   故改为回调：用户点「结束」后才执行 proceed。
     */
    private void confirmQuitThen(final Runnable proceed) {
        new AlertDialog.Builder(this)
                .setTitle("结束观看？")
                .setMessage("结束会按实际观看时间结算，未用完的时长退回时间银行。")
                .setPositiveButton("结束", (d, w) -> finishSession(proceed))
                .setNegativeButton("继续看", null)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishByUser();   // 不论什么状态，返回都先询问确认
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * ⚠️ v4.0 关键：onDestroy 不再无条件调 /finish。
     *
     * 旧实现的问题：误触返回 / 切后台 / 进程被杀都会结束会话，
     *   导致「重进时间归零」且服务端要等 3 小时超时才释放。
     * 现在：只有 finishSession() 会结算；其他情况交给服务端 90 秒心跳超时兜底。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }
}
