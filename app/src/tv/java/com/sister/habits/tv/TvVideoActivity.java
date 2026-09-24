package com.sister.habits.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sister.habits.R;

import org.json.JSONArray;
import org.json.JSONObject;

/** TV 看片：首页（积分 / 今日次数 / 今日推荐 3 条），遥控器 OK 键选片；支持局域网自动发现服务器 */
public class TvVideoActivity extends Activity {

    private static final int[] MINUTES = {30, 60, 90};

    private TvPrefs prefs;
    private TextView tvStatus;
    private TextView tvSub;
    private LinearLayout list;
    private boolean loading;
    /** 连接失败后只自动查找一次，避免失败重试死循环 */
    private boolean autoTried;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_video);
        prefs = new TvPrefs(this);
        tvStatus = findViewById(R.id.tv_status);
        tvSub = findViewById(R.id.tv_sub);
        list = findViewById(R.id.list);
        findViewById(R.id.btn_refresh).setOnClickListener(v -> load());
        findViewById(R.id.btn_auto).setOnClickListener(v -> autoFind(false));
        findViewById(R.id.btn_setup).setOnClickListener(v -> showSetup());
        tvSub.setText("服务器：" + prefs.hub());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!loading) {
            load();
        }
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        tvStatus.setText("正在读取…");
        list.removeAllViews();
        new Thread(() -> {
            try {
                TvHubApi api = prefs.api();
                final JSONObject status = api.get("/status");
                final JSONObject cands = api.get("/candidates");
                runOnUiThread(() -> {
                    loading = false;
                    render(status, cands);
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    // 地址失效（如电脑 IP 变了）→ 自动查找一次
                    if (!autoTried) {
                        autoTried = true;
                        autoFind(true);
                        return;
                    }
                    tvStatus.setText("连接失败");
                    tvSub.setText(String.valueOf(e.getMessage()));
                    list.removeAllViews();
                    addHint("请确认电视连着家里 WiFi，且电脑端服务已开启");
                });
            }
        }).start();
    }

    /** 自动查找服务器：先扫局域网，再退公网隧道；找到即保存并重新加载 */
    private void autoFind(final boolean fromFailure) {
        loading = true;
        tvStatus.setText("正在查找服务器…");
        list.removeAllViews();
        tvSub.setText(fromFailure ? "原地址连不上，正在自动查找…" : "正在扫描局域网…");
        new Thread(() -> TvDiscovery.discover(prefs.token(), new TvDiscovery.Callback() {
            @Override
            public void onFound(final String hubBase, final boolean viaTunnel) {
                prefs.setHub(hubBase);
                runOnUiThread(() -> {
                    loading = false;
                    tvSub.setText("服务器：" + hubBase + (viaTunnel ? "（公网）" : "（自动发现）"));
                    Toast.makeText(TvVideoActivity.this, "已找到服务器", Toast.LENGTH_SHORT).show();
                    load();
                });
            }

            @Override
            public void onNotFound() {
                runOnUiThread(() -> {
                    loading = false;
                    tvStatus.setText("没有找到服务器");
                    tvSub.setText("当前地址：" + prefs.hub());
                    list.removeAllViews();
                    addHint("检查：① 电脑已开机  ② 电视与电脑连同一个 WiFi  ③ 或用「设置服务器地址」手动填写");
                });
            }
        })).start();
    }

    private void render(JSONObject status, JSONObject cands) {
        int balance = status.optInt("balance", 0);
        int today = status.optInt("todayPlayed", 0);
        int limit = status.optInt("dailyLimit", 2);
        boolean pickerOk = status.optBoolean("pickerOk", false);
        tvStatus.setText("积分 " + balance + "  ·  今日已看 " + today + "/" + limit);
        tvSub.setText("服务器：" + prefs.hub() + (pickerOk ? "" : "（选片服务未就绪）"));
        JSONArray items = cands.optJSONArray("items");
        if (items == null || items.length() == 0) {
            addHint("今天没有可用推荐，按「刷新」再试一次");
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it != null) {
                list.addView(buildCard(it));
            }
        }
        if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        }
    }

    private View buildCard(final JSONObject it) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.card_bg);
        box.setPadding(28, 22, 28, 22);
        box.setFocusable(true);
        box.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        box.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(it.optString("title", "未命名"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        box.addView(title);

        TextView meta = new TextView(this);
        int sec = it.optInt("durationSec", 0);
        String author = it.optString("author", "");
        StringBuilder sb = new StringBuilder();
        sb.append(sec > 0 ? (sec / 60 + " 分钟") : "时长未知");
        if (!author.isEmpty()) {
            sb.append("  ·  ").append(author);
        }
        sb.append(it.optBoolean("ready", false) ? "  ·  已就绪" : "  ·  准备中");
        meta.setText(sb.toString());
        meta.setTextColor(Color.parseColor("#9AA7B4"));
        meta.setTextSize(15);
        box.addView(meta);

        final String vid = it.optString("videoId", "");
        final String name = it.optString("title", "");
        box.setOnClickListener(v -> showDurationDialog(vid, name));
        return box;
    }

    private void addHint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9AA7B4"));
        tv.setTextSize(18);
        tv.setPadding(8, 20, 8, 20);
        list.addView(tv);
    }

    private void showDurationDialog(final String videoId, final String title) {
        String[] labels = new String[MINUTES.length];
        for (int i = 0; i < MINUTES.length; i++) {
            labels[i] = MINUTES[i] + " 分钟（" + costOf(MINUTES[i]) + " 积分）";
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, (d, which) -> startSession(videoId, MINUTES[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private static int costOf(int minutes) {
        return (int) Math.ceil(minutes / 30.0) * 20;
    }

    private void startSession(final String videoId, final int minutes) {
        tvStatus.setText("正在开始…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("videoId", videoId);
                body.put("minutes", minutes);
                final JSONObject r = prefs.api().post("/start", body);
                if (!r.optBoolean("ok", false)) {
                    final String msg = reasonText(r.optString("reason", ""), r);
                    runOnUiThread(() -> {
                        Toast.makeText(TvVideoActivity.this, msg, Toast.LENGTH_LONG).show();
                        tvStatus.setText(msg);
                        loading = false;
                        load();
                    });
                    return;
                }
                runOnUiThread(() -> {
                    Intent i = new Intent(TvVideoActivity.this, TvPlayerActivity.class);
                    i.putExtra("sessionId", r.optString("sessionId"));
                    i.putExtra("mediaUrl", r.optString("mediaUrl"));
                    i.putExtra("title", r.optString("title", videoId));
                    i.putExtra("plannedSeconds", r.optInt("plannedSeconds", minutes * 60));
                    startActivity(i);
                });
            } catch (final Exception e) {
                runOnUiThread(() -> tvStatus.setText("开始失败：" + e.getMessage()));
            }
        }).start();
    }

    private String reasonText(String reason, JSONObject r) {
        switch (reason) {
            case "insufficient_coins":
                return "积分不足（当前 " + r.optInt("balance", 0) + "）";
            case "daily_limit_reached":
                return "今天已经看过 " + r.optInt("todayPlayed", 0) + " 次啦，明天再来";
            case "session_active":
                return "电视上还有一次观看没结束";
            case "invalid_video":
                return "这集不在今日推荐里，刷新后再选";
            case "video_not_ready":
                return "这一集还在准备中，稍后再试";
            case "picker_unavailable":
                return "选片服务没启动，请检查电脑端";
            default:
                return "无法开始（" + reason + "）";
        }
    }

    private void showSetup() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        et.setText(prefs.hub());
        new AlertDialog.Builder(this)
                .setTitle("服务器地址")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String v = et.getText().toString().trim();
                    if (!v.isEmpty()) {
                        prefs.setHub(v);
                        load();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}