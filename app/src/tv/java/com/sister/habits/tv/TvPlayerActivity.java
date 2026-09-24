package com.sister.habits.tv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.ui.PlayerView;

import com.sister.habits.R;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** TV 看片：播放页（ExoPlayer + 真实时间倒计时 + 心跳 + 到点退出） */
public class TvPlayerActivity extends Activity {

    private ExoPlayer player;
    private TextView tvCount;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TvPrefs prefs;
    private String sessionId;
    private long startedAt;
    private long lastHeartbeatAt;
    private int plannedSeconds;
    private boolean finished;
    private Runnable ticker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_player);
        prefs = new TvPrefs(this);
        sessionId = getIntent().getStringExtra("sessionId");
        String mediaUrl = getIntent().getStringExtra("mediaUrl");
        String title = getIntent().getStringExtra("title");
        plannedSeconds = getIntent().getIntExtra("plannedSeconds", 1800);
        if (plannedSeconds <= 0) {
            plannedSeconds = 1800;
        }

        tvCount = findViewById(R.id.tv_count);
        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(title == null ? "" : title);

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

        startedAt = System.currentTimeMillis();
        lastHeartbeatAt = startedAt;
        ticker = new Runnable() {
            @Override
            public void run() {
                if (finished) {
                    return;
                }
                long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
                int remain = (int) (plannedSeconds - elapsed);
                tvCount.setText("剩余 " + fmt(remain));
                if (remain <= 0) {
                    finishSession(elapsed);
                    return;
                }
                if (System.currentTimeMillis() - lastHeartbeatAt >= 10000) {
                    lastHeartbeatAt = System.currentTimeMillis();
                    sendHeartbeat(elapsed);
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(ticker, 1000);
    }

    private static String fmt(int sec) {
        int s = Math.max(0, sec);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private void sendHeartbeat(final long elapsed) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sessionId", sessionId);
                body.put("playedSeconds", (int) elapsed);
                prefs.api().post("/heartbeat", body);
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void finishSession(final long elapsed) {
        if (finished) {
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
        tvCount.setText("时间到，今天先到这里啦");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sessionId", sessionId);
                body.put("playedSeconds", (int) elapsed);
                prefs.api().post("/finish", body);
            } catch (Exception ignored) {
            }
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    finish();
                }
            });
        }).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishSession((System.currentTimeMillis() - startedAt) / 1000);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (!finished) {
            finished = true;
            final long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("sessionId", sessionId);
                    body.put("playedSeconds", (int) elapsed);
                    prefs.api().post("/finish", body);
                } catch (Exception ignored) {
                }
            }).start();
        }
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }
}