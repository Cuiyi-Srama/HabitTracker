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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sister.habits.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * TV 看片：首页（v4.0 重做）。
 *
 * 本版变更（2026-09-25）：
 *   1. 顶部状态栏改为「时间银行余额」语义 —— 旧版显示「今日已看 6/4」是废弃的条数模型；
 *   2. 选片流程加入【兑换】步骤：先 /exchange 把积分换成时长入银行，再 /start 开播；
 *   3. 进入时先查 /status，若服务端仍有活跃会话，直接跳回播放页续看（不再干瞪眼）；
 *   4. 详情卡片加入封面图（走 Hub 代理 /tv/cover/{id}，电视无法直连 B 站 CDN）。
 */
public class TvVideoActivity extends Activity {

    /** 兑换档位（分钟）。与 costOf() 的计价规则对应：每 30 分钟 20 积分 */
    private static final int[] MINUTES = {30, 60, 90};

    private TvPrefs prefs;
    private TvHubApi api;
    private TextView tvStatus;
    private TextView tvSub;
    private LinearLayout list;
    private boolean loading;
    /** 连接失败后只自动查找一次，避免失败重试死循环 */
    private boolean autoTried;
    /** 进入时是否已尝试过「恢复活跃会话」检查 */
    private boolean restoreChecked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_video);
        prefs = new TvPrefs(this);
        api = prefs.api();
        tvStatus = findViewById(R.id.tv_status);
        tvSub = findViewById(R.id.tv_sub);
        list = findViewById(R.id.list);
        findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            autoTried = false;
            load();
        });
        findViewById(R.id.btn_auto).setOnClickListener(v -> autoFind(false));
        findViewById(R.id.btn_setup).setOnClickListener(v -> showSetup());
        tvSub.setText("服务器：" + prefs.hub());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从播放页返回时刷新余额（可能刚结算，退回了未用完时长）
        restoreChecked = false;
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
                final JSONObject status = api.get("/status");
                final JSONObject cands = api.get("/candidates");
                runOnUiThread(() -> {
                    loading = false;
                    // ★ 恢复活跃会话优先于渲染列表
                    if (!restoreChecked && tryRestoreActiveSession(status)) {
                        return;
                    }
                    render(status, cands);
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    loading = false;
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

    /**
     * 若服务端仍有活跃会话，直接跳回播放页续看。
     *
     * ★ v4.0 新增：解决「误触返回后卡住、必须等超时才放行」的问题。
     *   服务端 /status 的 activeSession 字段含 sessionId/videoId/plannedSeconds 等。
     *
     * @return true 表示已跳转（本次不再渲染列表）
     */
    private boolean tryRestoreActiveSession(JSONObject status) {
        restoreChecked = true;
        JSONObject active = status.optJSONObject("activeSession");
        if (active == null) {
            return false;
        }
        String sid = active.optString("sessionId", "");
        if (sid.isEmpty()) {
            return false;
        }
        // 恢复时按「已过秒数」折算 baseline，保证倒计时接着走而不是从头
        int planned = active.optInt("plannedSeconds", 0);
        int elapsed = active.optInt("elapsedSeconds", 0);
        Intent i = new Intent(this, TvPlayerActivity.class);
        i.putExtra("sessionId", sid);
        i.putExtra("videoId", active.optString("videoId", ""));
        i.putExtra("title", active.optString("title", ""));
        i.putExtra("mediaUrl", active.optString("mediaUrl", ""));
        i.putExtra("plannedSeconds", planned);
        i.putExtra("elapsedSeconds", elapsed);
        startActivity(i);
        return true;
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
                api = prefs.api();
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

    /**
     * 渲染首页。
     *
     * ★ v4.0：状态栏改用「时间银行」语义。
     *   服务端 /status 返回的关键字段：
     *     timeBankMinutes —— 兑换累积的可看总时长（这是真正能看的量）
     *     playedSeconds   —— 今日已播（统计用）
     *     budgetSeconds   —— 今日基础额度（家长 add_minutes 会加）
     */
    private void render(JSONObject status, JSONObject cands) {
        int balance = status.optInt("balance", 0);
        int bankMin = status.optInt("timeBankMinutes", 0);
        int played = status.optInt("playedSeconds", 0);
        boolean pickerOk = status.optBoolean("pickerOk", false);

        if (bankMin <= 0) {
            tvStatus.setText("可用 0 分钟  ·  积分 " + balance);
            tvSub.setText("时间不够啦，先去攒积分再兑换吧");
            list.removeAllViews();
            addHint("积分：" + balance + "　·　每 30 分钟需要 " + status.optInt("costPer30Min", 20) + " 积分");
            addHint("今日已看 " + fmtClock(played));
            return;
        }

        tvStatus.setText("可用 " + bankMin + " 分钟  ·  积分 " + balance);
        tvSub.setText("服务器：" + prefs.hub()
                + "　·　今日已看 " + fmtClock(played)
                + (pickerOk ? "" : "（选片服务未就绪）"));

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

    private static String fmtClock(int sec) {
        int s = Math.max(0, sec);
        return String.format(Locale.US, "%d 分 %02d 秒", s / 60, s % 60);
    }

    /**
     * 构建单个推荐卡片：封面 + 标题 + 元信息。
     *
     * ★ v4.0 新增封面。封面通过 TvImageLoader 异步从 Hub 代理拉取；
     *   拉取失败静默留空，不影响功能。
     */
    private View buildCard(final JSONObject it) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackgroundResource(R.drawable.card_bg);
        box.setPadding(20, 18, 24, 18);
        box.setFocusable(true);
        box.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        box.setLayoutParams(lp);

        // ---- 封面（左侧，16:9 小图）----
        ImageView cover = new ImageView(this);
        int w = dp(190);
        int h = dp(107);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(w, h);
        clp.rightMargin = dp(20);
        cover.setLayoutParams(clp);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(0xFF232A33);
        box.addView(cover);

        final String vid = it.optString("videoId", "");
        TvImageLoader.load(cover, api, vid);

        // ---- 右侧文字区 ----
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(texts);

        TextView title = new TextView(this);
        title.setText(it.optString("title", "未命名"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setMaxLines(2);
        texts.addView(title);

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
        android.widget.LinearLayout.LayoutParams mlp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        meta.setLayoutParams(mlp);
        texts.addView(meta);

        final String name = it.optString("title", "");
        box.setOnClickListener(v -> showExchangeDialog(vid, name));
        return box;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void addHint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9AA7B4"));
        tv.setTextSize(18);
        tv.setPadding(dp(8), dp(20), dp(8), dp(20));
        list.addView(tv);
    }

    /**
     * 选片 → 先兑换时长，再开始播放。
     *
     * ★ v4.0 关键流程修正：
     *   旧版直接调 /start，而 /start 是【从时间银行扣除】——
     *   但 App 从未调用过 /exchange 给银行充值，导致银行只减不增。
     *   现在补上兑换步骤：/exchange（积分→银行）→ /start（银行→本次会话）。
     */
    private void showExchangeDialog(final String videoId, final String title) {
        String[] labels = new String[MINUTES.length];
        for (int i = 0; i < MINUTES.length; i++) {
            labels[i] = "兑换 " + MINUTES[i] + " 分钟（" + costOf(MINUTES[i]) + " 积分）";
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("选择要兑换的时长，兑换后立即开始播放。\n未看完的时长会退回，可下次接着用。")
                .setItems(labels, (d, which) -> exchangeThenPlay(videoId, MINUTES[which]))
                .setNegativeButton("取消", null)
                .show();
    }

    private static int costOf(int minutes) {
        return (int) Math.ceil(minutes / 30.0) * 20;
    }

    /** 第一步：兑换（积分 → 时间银行） */
    private void exchangeThenPlay(final String videoId, final int minutes) {
        tvStatus.setText("正在兑换…");
        new Thread(() -> {
            String err = null;
            try {
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
                if (e2 != null) {
                    Toast.makeText(this, e2, Toast.LENGTH_LONG).show();
                    load();
                    return;
                }
                Toast.makeText(this, "已兑换 " + minutes + " 分钟", Toast.LENGTH_SHORT).show();
                startSession(videoId, minutes);
            });
        }).start();
    }

    /** 第二步：开始播放（银行 → 本次会话） */
    private void startSession(final String videoId, final int minutes) {
        tvStatus.setText("正在开始…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("videoId", videoId);
                body.put("minutes", minutes);
                final JSONObject r = api.post("/start", body);

                // ★ 已有活跃会话：不报错，直接恢复播放
                if ("session_active".equals(r.optString("reason", ""))) {
                    final JSONObject ra = r;
                    runOnUiThread(() -> {
                        Intent i = new Intent(TvVideoActivity.this, TvPlayerActivity.class);
                        i.putExtra("sessionId", ra.optString("sessionId"));
                        i.putExtra("videoId", ra.optString("videoId", videoId));
                        i.putExtra("title", ra.optString("title", ""));
                        i.putExtra("mediaUrl", ra.optString("mediaUrl", ""));
                        i.putExtra("plannedSeconds", ra.optInt("plannedSeconds", minutes * 60));
                        i.putExtra("elapsedSeconds", ra.optInt("elapsedSeconds", 0));
                        startActivity(i);
                    });
                    return;
                }
                if (!r.optBoolean("ok", false)) {
                    final String msg = reasonText(r.optString("reason", ""), r);
                    runOnUiThread(() -> {
                        Toast.makeText(TvVideoActivity.this, msg, Toast.LENGTH_LONG).show();
                        tvStatus.setText(msg);
                        load();
                    });
                    return;
                }
                runOnUiThread(() -> {
                    Intent i = new Intent(TvVideoActivity.this, TvPlayerActivity.class);
                    i.putExtra("sessionId", r.optString("sessionId"));
                    i.putExtra("videoId", videoId);
                    i.putExtra("mediaUrl", r.optString("mediaUrl"));
                    i.putExtra("title", r.optString("title", videoId));
                    i.putExtra("plannedSeconds", r.optInt("plannedSeconds", minutes * 60));
                    i.putExtra("elapsedSeconds", 0);
                    startActivity(i);
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("开始失败：" + e.getMessage());
                    load();
                });
            }
        }).start();
    }

    private String reasonText(String reason, JSONObject r) {
        switch (reason) {
            case "insufficient_coins":
                return "积分不足（当前 " + r.optInt("balance", 0) + "）";
            case "no_time_left":
                return "时间银行余额不足，请先兑换时长";
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
                        api = prefs.api();
                        tvSub.setText("服务器：" + v);
                        autoTried = false;
                        load();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
