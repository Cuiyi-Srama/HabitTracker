package com.sister.habits.parent;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.EconomyConfig;
import com.sister.habits.data.models.Redemption;
import com.sister.habits.data.models.ShopItem;
import com.sister.habits.data.models.Task;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.sister.habits.sync.SyncManager;
import com.sister.habits.sync.EarningService;
import com.sister.habits.data.models.CoinEarning;
import com.sister.habits.utils.SoundHelper;
import com.sister.habits.utils.NotificationHelper;
import com.sister.habits.utils.ProfileManager;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * 家长模式——统一管理界面
 * 看板/审批兑换/发布任务/管理商城/调整参数
 */
public class ParentActivity extends AppCompatActivity {

    private AppDatabase db;
    private SyncManager syncManager;
    private SoundHelper soundHelper;
    private ProfileManager profile;

    private TextView tvStats;
    private RecyclerView rvPendingApprovals, rvPendingTasks;
    private View btnAddTask, btnAddShopItem, btnSettings, btnSync, btnRefresh;

    // 相册选图 — 当前选中的商品图片路径
    private String selectedShopImagePath;
    // 当前打开的商品对话框View（用于图片预览更新）
    private View currentShopDialogView;

    // 相册选图启动器
    private final ActivityResultLauncher<String> pickShopImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                try {
                    // 压缩并保存到App内部存储（最大1280px，JPEG质量75%）
                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opts);
                    // 计算采样率
                    int maxDim = 1280;
                    int scale = 1;
                    while (opts.outWidth / scale > maxDim || opts.outHeight / scale > maxDim) {
                        scale *= 2;
                    }
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = scale;
                    java.io.InputStream is2 = getContentResolver().openInputStream(uri);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is2, null, opts);
                    is2.close();
                    if (bmp == null) throw new Exception("无法读取图片");
                    String fileName = "shop_" + System.currentTimeMillis() + ".jpg";
                    java.io.File outFile = new java.io.File(getFilesDir(), "shop_images/" + fileName);
                    outFile.getParentFile().mkdirs();
                    java.io.OutputStream os = new java.io.FileOutputStream(outFile);
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, os);
                    os.close();
                    bmp.recycle();
                    selectedShopImagePath = outFile.getAbsolutePath();

                    // 通知对话框中的预览控件更新（如果有打开的对话框）
                    if (currentShopDialogView != null) {
                        ImageView preview = currentShopDialogView.findViewById(R.id.iv_image_preview);
                        TextView tvName = currentShopDialogView.findViewById(R.id.tv_image_name);
                        if (preview != null && tvName != null) {
                            preview.setVisibility(View.VISIBLE);
                            tvName.setVisibility(View.VISIBLE);
                            tvName.setText("已选择: " + fileName);
                            Glide.with(this).load(outFile).into(preview);
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("ParentActivity", "图片选择失败", e);
                    Toast.makeText(this, "❌ 图片加载失败", Toast.LENGTH_SHORT).show();
                }
            });

    // 词库JSON导入 — 使用 bankId 隔离，不丢失学习进度
    private final ActivityResultLauncher<String[]> wordbankImportLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                try {
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    byte[] buffer = new byte[is.available()];
                    is.read(buffer);
                    is.close();
                    String json = new String(buffer, "UTF-8");

                    java.lang.reflect.Type type = new TypeToken<List<JsonImportWord>>(){}.getType();
                    List<JsonImportWord> importWords = new Gson().fromJson(json, type);

                    if (importWords == null || importWords.isEmpty()) {
                        Toast.makeText(this, "词库文件格式有误，未找到有效单词", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // 创建新词库记录
                    String bankId = "import_" + System.currentTimeMillis();
                    com.sister.habits.data.models.WordBank bank = com.sister.habits.data.models.WordBank.fromImport("自定义词库", importWords.size());
                    bank.id = bankId;
                    db.wordBankDao().insert(bank);

                    // 批量入库（带 bankId）
                    List<com.sister.habits.data.models.Vocabulary> words = new java.util.ArrayList<>();
                    for (JsonImportWord jw : importWords) {
                        com.sister.habits.data.models.Vocabulary v = new com.sister.habits.data.models.Vocabulary();
                        v.id = UUID.randomUUID().toString();
                        v.word = jw.w;
                        v.meaning = jw.m;
                        v.phonetic = jw.p;
                        v.category = jw.c;
                        v.gradeLevel = jw.g;
                        v.level = jw.l;
                        v.mastered = false;
                        v.active = true;
                        v.bankId = bankId;
                        words.add(v);
                    }

                    int batchSize = 50;
                    for (int i = 0; i < words.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, words.size());
                        db.vocabularyDao().insertAll(words.subList(i, end));
                    }

                    // 切换到新词库
                    SharedPreferences prefs = getSharedPreferences("wordbank_prefs", MODE_PRIVATE);
                    prefs.edit().putString("active_bank_id", bankId).apply();
                    db.wordBankDao().deactivateAll();
                    db.wordBankDao().setActive(bankId);

                    Toast.makeText(this, "✅ 导入成功！共 " + words.size() + " 个单词，已切换到新词库", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    android.util.Log.e("ParentActivity", "词库导入失败", e);
                    Toast.makeText(this, "❌ 导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

    /** QR扫码配对启动器 */
    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result != null && result.getContents() != null) {
                    String qrContent = result.getContents();
                    String deviceKey = com.sister.habits.utils.QRCodeHelper.parseDeviceKey(qrContent);
                    String deviceName = com.sister.habits.utils.QRCodeHelper.parseDeviceName(qrContent);
                    if (deviceKey != null) {
                        Toast.makeText(this, "📡 已配对: " + deviceName + "\n正在同步数据...", Toast.LENGTH_LONG).show();
                        // 把配对设备信息存到SharedPreferences
                        getSharedPreferences("paired_devices", MODE_PRIVATE)
                                .edit()
                                .putString("paired_" + deviceKey, deviceName)
                                .apply();
                        // 配对后主动触发局域网P2P同步，把本机数据推过去
                        syncManager.triggerLanSync();
                    } else {
                        Toast.makeText(this, "❌ 无效的配对码: " + qrContent.substring(0, Math.min(30, qrContent.length())), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "❌ 扫码取消或失败", Toast.LENGTH_SHORT).show();
                }
            });

    private static class JsonImportWord {
        String g; String c; String w; String m; String p; int l;
    }

    /** 从静态源下载词库 */
    private void downloadFromStaticSource(String id, String name, String url, String format, String gradeLabel) {
        ExternalSource src = new ExternalSource();
        src.id = id; src.name = name; src.url = url; src.format = format; src.gradeLabel = gradeLabel;
        downloadAndPreview(src);
    }

    /** 外部词库源 */
    private static class ExternalSource {
        String id;
        String name;
        String description;
        String url;
        String format;
        String gradeLabel;
    }

    /** 下载外部词库 → 预览 → 确认后应用 */
    private void downloadAndPreview(ExternalSource source) {
        soundHelper.playClickSound();
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setTitle("📥 下载词库中...");
        progress.setMessage("正在连接: " + source.name);
        progress.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setCancelable(false);
        progress.show();

        final int MAX_RETRIES = 2;
        new Thread(() -> {
            byte[] rawData = null;
            String errorMsg = null;

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    final int currentAttempt = attempt;
                    runOnUiThread(() -> progress.setMessage("正在下载: " + source.name + " (第" + currentAttempt + "次)"));

                    java.net.URI uri = new java.net.URI(source.url);
                    java.net.URL url = uri.toURL();
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(60000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) HabitTracker/1.5.0");
                    conn.setInstanceFollowRedirects(true);

                    int responseCode = conn.getResponseCode();
                    if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                        throw new java.io.IOException("HTTP " + responseCode);
                    }

                    int contentLength = conn.getContentLength();
                    java.io.InputStream is = conn.getInputStream();
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(contentLength > 0 ? contentLength : 65536);
                    byte[] buf = new byte[8192];
                    int len;
                    long totalRead = 0;
                    long lastUpdate = 0;

                    while ((len = is.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                        totalRead += len;
                        // 每200ms更新一次进度
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 200) {
                            lastUpdate = now;
                            final long read = totalRead;
                            final int pct = contentLength > 0 ? (int) (read * 100 / contentLength) : -1;
                            runOnUiThread(() -> {
                                if (pct >= 0) {
                                    progress.setProgress(Math.min(pct, 99));
                                    progress.setMessage("下载中: " + (read / 1024) + "KB / " + (contentLength / 1024) + "KB");
                                } else {
                                    progress.setMessage("下载中: " + (read / 1024) + "KB...");
                                }
                            });
                        }
                    }
                    is.close();
                    rawData = baos.toByteArray();
                    break; // 成功，跳出重试循环

                } catch (java.net.SocketException e) {
                    errorMsg = "连接中断: " + e.getMessage() + " (尝试 " + attempt + "/" + MAX_RETRIES + ")";
                    android.util.Log.e("Download", errorMsg);
                    if (attempt < MAX_RETRIES) {
                        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    }
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                    break; // 非Socket异常，不重试
                }
            }

            if (rawData == null) {
                final String msg = errorMsg;
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(this, "❌ 下载失败: " + msg, Toast.LENGTH_LONG).show();
                });
                return;
            }

            // 解析JSON
            try {
                String json = new String(rawData, "UTF-8");
                String grade = source.gradeLabel != null ? source.gradeLabel : "external";
                java.util.List<com.sister.habits.data.models.Vocabulary> words = com.sister.habits.utils.WordBankParser.parse(json, grade);
                for (com.sister.habits.data.models.Vocabulary w : words) { w.bankId = source.id; w.active = true; }
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (words.isEmpty()) {
                        Toast.makeText(this, "❌ 词库解析失败：格式不兼容或为空", Toast.LENGTH_LONG).show();
                        return;
                    }

                    StringBuilder samples = new StringBuilder();
                    int sampleCount = Math.min(5, words.size());
                    for (int i = 0; i < sampleCount; i++) {
                        com.sister.habits.data.models.Vocabulary v = words.get(i);
                        samples.append("• ").append(v.word).append(" — ").append(v.meaning).append("\n");
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("📖 预览词库")
                            .setMessage("来源: " + source.name + "\n" +
                                    "共 " + words.size() + " 个单词\n" +
                                    "格式: " + source.format + "\n\n" +
                                    "📝 示例:\n" + samples.toString())
                            .setPositiveButton("✅ 确认使用", (d, w) -> applyExternalWordbank(words, source))
                            .setNegativeButton("取消", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(this, "❌ 解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** 应用外部词库到数据库 — 使用 bankId 隔离，学习进度独立保存 */
    private void applyExternalWordbank(java.util.List<com.sister.habits.data.models.Vocabulary> words, ExternalSource source) {
        try {
            String bankId = "ext_" + source.id;
            for (com.sister.habits.data.models.Vocabulary v : words) {
                v.bankId = bankId;
            }

            // 创建或更新词库记录
            com.sister.habits.data.models.WordBank bank = com.sister.habits.data.models.WordBank.fromExternal(
                    source.id, source.name, source.url, source.gradeLabel, words.size());
            bank.id = bankId;
            db.wordBankDao().insert(bank);

            // 分批插入（不清除旧词库）
            int batchSize = 100;
            for (int i = 0; i < words.size(); i += batchSize) {
                int end = Math.min(i + batchSize, words.size());
                db.vocabularyDao().insertAll(words.subList(i, end));
            }

            // 切换到新词库
            SharedPreferences prefs = getSharedPreferences("wordbank_prefs", MODE_PRIVATE);
            prefs.edit().putString("active_bank_id", bankId).apply();
            db.wordBankDao().deactivateAll();
            db.wordBankDao().setActive(bankId);

            Toast.makeText(this, "✅ 已启用: " + source.name + "（" + words.size() + "词）\n学习进度独立保存，切回旧词库不丢失", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            android.util.Log.e("ParentActivity", "应用外部词库失败", e);
            Toast.makeText(this, "❌ 应用失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        db = AppDatabase.getInstance(this);
        syncManager = SyncManager.getInstance(this);
        soundHelper = SoundHelper.getInstance(this);
        profile = ProfileManager.getInstance(this);

        tvStats = findViewById(R.id.tv_parent_stats);
        rvPendingApprovals = findViewById(R.id.rv_pending_approvals);
        rvPendingTasks = findViewById(R.id.rv_pending_tasks);
        btnAddTask = findViewById(R.id.btn_add_task);
        btnAddShopItem = findViewById(R.id.btn_add_shop_item);
        btnSettings = findViewById(R.id.btn_settings);
        btnSync = findViewById(R.id.btn_sync);
        btnRefresh = findViewById(R.id.btn_refresh);

        rvPendingApprovals.setLayoutManager(new LinearLayoutManager(this));
        rvPendingTasks.setLayoutManager(new LinearLayoutManager(this));

        btnAddTask.setOnClickListener(v -> { soundHelper.playClickSound(); showAddTaskDialog(); });
        btnAddShopItem.setOnClickListener(v -> { soundHelper.playClickSound(); showAddShopItemDialog(); });
        btnSettings.setOnClickListener(v -> { soundHelper.playClickSound(); showSettingsDialog(); });
        btnSync.setOnClickListener(v -> { soundHelper.playClickSound(); syncManager.triggerRemoteSync(); syncManager.triggerLanSync(); Toast.makeText(this, "同步已触发", Toast.LENGTH_SHORT).show(); });
        btnRefresh.setOnClickListener(v -> { soundHelper.playClickSound(); refreshAll(); });

        // 创建通知渠道
        NotificationHelper.createChannel(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SoundHelper.releaseInstance();
    }

    private void refreshAll() {
        refreshStats();
        loadPendingApprovals();
        loadPendingTasks();
    }

        private void refreshStats() {
        int totalCheckIns = db.checkInDao().getTotalCheckIns("sister");
        int pendingEarningCount = db.coinEarningDao().getPendingCount();
        int maxStreak = db.checkInDao().getMaxStreak("sister");
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        int pendingTaskCount = db.taskDao().getByStatus("pending").size();
        int earningPendingCount = db.coinEarningDao().getPendingCount();
        Integer balance = db.coinTransactionDao().getBalance("sister");

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        boolean checkedInToday = db.checkInDao().getByDate("sister", today) != null;

        long todayStart = 0;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            todayStart = sdf.parse(today).getTime();
        } catch (Exception e) { }
        int todayEarned = db.coinTransactionDao().getTotalEarnedSince("sister", todayStart);
        int todaySpent = db.coinTransactionDao().getTotalSpentSince("sister", todayStart);

        String nickname = profile.getNickname();
        tvStats.setText(
                "\uD83D\uDCCA " + nickname + "\u7684\u4E60\u60EF\u6570\u636E\n" +
                "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                "\u4ECA\u65E5\u6253\u5361: " + (checkedInToday ? "\u2705 \u5DF2\u6253\u5361" : "\u2B55 \u672A\u6253\u5361") + "\n" +
                "\u603B\u6253\u5361: " + totalCheckIns + " \u5929  |  \u6700\u957F\u8FDE\u7EED: " + maxStreak + " \u5929 \uD83C\uDFC6\n" +
                "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                "\uD83D\uDCB0 \u91D1\u5E01\u4F59\u989D: " + (balance != null ? balance : 0) + "\n" +
                "\uD83D\uDDE5 \u4ECA\u65E5\u6536\u5165: +" + todayEarned + "  |  \u4ECA\u65E5\u6D88\u8D39: -" + todaySpent + "\n" +
                "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                "\u5F85\u5BA1\u6279\u5151\u6362: " + pendingCount + " \u9879  |  \u5F85\u786E\u8BA4\u4EFB\u52A1: " + pendingTaskCount + " \u9879\n" +
                "\uD83D\uDCB0 \u5F85\u5BA1\u79EF\u5206: " + pendingEarningCount + " \u9879"
        );
    }

    private void loadPendingApprovals() {
        List<Redemption> pending = db.redemptionDao().getByStatus("pending");
        rvPendingApprovals.setAdapter(new ApprovalAdapter(pending, this::processApproval));
    }

    private void processApproval(Redemption redemption, boolean approved) {
        String status = approved ? "confirmed" : "rejected";
        db.redemptionDao().process(redemption.id, status, System.currentTimeMillis(),
                approved ? "已确认 ✅" : "已拒绝 ❌");

        if (!approved) {
            // 拒绝时退还金币
            Integer balance = db.coinTransactionDao().getBalance("sister");
            int newBalance = (balance != null ? balance : 0) + redemption.coinsCost;
            com.sister.habits.data.models.CoinTransaction ct =
                    new com.sister.habits.data.models.CoinTransaction(
                            "sister", redemption.coinsCost, newBalance,
                            "parent_adjust", "兑换退回: " + redemption.itemName,
                            syncManager.getDeviceId());
            db.coinTransactionDao().insert(ct);
        }

        syncManager.onDataChanged();
        refreshAll();
        Toast.makeText(this, (approved ? "✅ 已确认" : "❌ 已拒绝") + " " + redemption.itemName,
                Toast.LENGTH_SHORT).show();
    }

    // ===== 任务审批 =====
    private void loadPendingTasks() {
        List<Task> pending = db.taskDao().getPending();
        if (pending.isEmpty()) {
            rvPendingTasks.setAdapter(null);
            return;
        }
        rvPendingTasks.setAdapter(new TaskApprovalAdapter(pending, this::processTaskApproval));
    }

    private void processTaskApproval(Task task, boolean approved) {
        if (approved) {
            // 确认 → 发金币
            db.taskDao().confirmTask(task.id, System.currentTimeMillis());
            // 同步更新对应的CoinEarning状态
            java.util.List<com.sister.habits.data.models.CoinEarning> earnings = db.coinEarningDao().getBySource("task", task.id);
            for (com.sister.habits.data.models.CoinEarning e : earnings) {
                if ("pending".equals(e.status)) {
                    e.status = "confirmed";
                    e.confirmedAt = System.currentTimeMillis();
                    db.coinEarningDao().update(e);
                }
            }
            Integer balance = db.coinTransactionDao().getBalance("sister");
            int newBalance = (balance != null ? balance : 0) + task.rewardCoins;
            com.sister.habits.data.models.CoinTransaction ct =
                    new com.sister.habits.data.models.CoinTransaction(
                            "sister", task.rewardCoins, newBalance,
                            "task_reward", "任务奖励: " + task.title,
                            syncManager.getDeviceId());
            db.coinTransactionDao().insert(ct);
            String nickname = profile.getNickname();
            Toast.makeText(this, "✅ 已确认 " + task.title + "，" + nickname + "获得 🪙+" + task.rewardCoins, Toast.LENGTH_SHORT).show();
        } else {
            // 拒绝 → 退回待完成状态
            db.taskDao().reactivate(task.id);
            Toast.makeText(this, "❌ 已拒绝 " + task.title + "，任务退回", Toast.LENGTH_SHORT).show();
        }
        syncManager.onDataChanged();
        refreshAll();
    }

    private static class TaskApprovalAdapter extends RecyclerView.Adapter<TaskApprovalAdapter.ViewHolder> {
        private final List<Task> tasks;
        private final OnTaskApprovalListener listener;

        interface OnTaskApprovalListener { void onApprove(Task task, boolean approved); }

        TaskApprovalAdapter(List<Task> tasks, OnTaskApprovalListener listener) {
            this.tasks = tasks;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Task task = tasks.get(position);
            holder.textView.setText("📋 " + task.title + "  🪙+" + task.rewardCoins);
            holder.itemView.setOnClickListener(v -> {
                String nickname = ProfileManager.getInstance(v.getContext()).getNickname();
                new AlertDialog.Builder(v.getContext())
                        .setTitle("确认任务完成")
                        .setMessage("任务: " + task.title + "\n描述: " + task.description + "\n奖励: 🪙" + task.rewardCoins + "\n\n确认" + nickname + "已完成此任务吗？")
                        .setPositiveButton("✅ 确认发金币", (d, w) -> listener.onApprove(task, true))
                        .setNegativeButton("❌ 未完成", (d, w) -> listener.onApprove(task, false))
                        .setNeutralButton("稍后", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() { return tasks.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView textView;
            ViewHolder(View v) { super(v); textView = v.findViewById(android.R.id.text1); }
        }
    }

    

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("open_approval")) {
            String type = intent.getStringExtra("open_approval");
            int itemId = intent.getIntExtra("item_id", -1);
            showApprovalCenterDialog(type, itemId);
        }
    }

    /** 从通知直达审批中心 */
    private void showApprovalCenterDialog(String focusType, int focusId) {
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        int pendingTaskCount = db.taskDao().getByStatus("pending").size();
        int earningPendingCount = db.coinEarningDao().getPendingCount();
        String[] items = {
            "💳 兑换审批（" + pendingCount + "项待处理）",
            "📋 任务确认（" + pendingTaskCount + "项待确认）",
            "💰 积分审批（" + earningPendingCount + "项待审）",
            "📜 历史记录"
        };
        new AlertDialog.Builder(this)
                .setTitle("✅ 审批中心")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: loadPendingApprovals(); Toast.makeText(this, "已刷新审批列表", Toast.LENGTH_SHORT).show(); break;
                        case 1: loadPendingTasks(); Toast.makeText(this, "已刷新任务列表", Toast.LENGTH_SHORT).show(); break;
                        case 2: showEarningApprovals(); break;
                        case 3: Toast.makeText(this, "历史记录（待实现）", Toast.LENGTH_SHORT).show(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSettingsDialog())
                .show();
    }

    /** 显示日期+时间选择器，更新按钮文字 */
    private void showDateTimePicker(android.widget.EditText etDate) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        new android.app.DatePickerDialog(this, (view, year, month, day) -> {
            java.util.Calendar dateCal = java.util.Calendar.getInstance();
            dateCal.set(year, month, day);
            // 选择时间
            new android.app.TimePickerDialog(this, (view2, hour, minute) -> {
                String dateStr = year + "-" + (month+1) + "-" + day + " " + hour + ":" + (minute < 10 ? "0"+minute : minute);
                etDate.setText("📅 " + dateStr);
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private void showAddTaskDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        android.widget.EditText etTitle = view.findViewById(R.id.et_task_title);
        android.widget.EditText etDesc = view.findViewById(R.id.et_task_desc);
        android.widget.EditText etReward = view.findViewById(R.id.et_task_reward);
        RadioGroup rgType = view.findViewById(R.id.rg_task_type);
        com.google.android.material.textfield.TextInputLayout tilDeadline = view.findViewById(R.id.til_deadline);
        android.widget.EditText etDeadline = view.findViewById(R.id.et_task_deadline);

        // 选择"限时"时显示截止时间输入
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            view.findViewById(R.id.til_deadline).setVisibility(checkedId == R.id.rb_type_timed ? View.VISIBLE : View.GONE);
        });
        // 日期时间选择器（使用til_deadline的EditText点击触发）
        android.widget.EditText etDate = view.findViewById(R.id.et_task_deadline);
        etDate.setOnClickListener(v -> showDateTimePicker(etDate));
        etDate.setFocusable(false);

        new AlertDialog.Builder(this)
                .setTitle("发布新任务")
                .setView(view)
                .setPositiveButton("发布", (d, w) -> {
                    Task task = new Task();
                    task.title = etTitle.getText().toString();
                    task.description = etDesc.getText().toString();
                    try { task.rewardCoins = Integer.parseInt(etReward.getText().toString()); }
                    catch (Exception e) { task.rewardCoins = 10; }
                    task.type = "custom";
                    // 读取任务类型
                    int typeId = rgType.getCheckedRadioButtonId();
                    if (typeId == R.id.rb_type_weekly) task.recurrenceType = "weekly";
                    else if (typeId == R.id.rb_type_monthly) task.recurrenceType = "monthly";
                    else if (typeId == R.id.rb_type_permanent) task.recurrenceType = "permanent";
                    else if (typeId == R.id.rb_type_timed) {
                        task.recurrenceType = "timed";
                        // 解析截止时间
                        String deadlineStr = etDeadline.getText().toString().trim();
                        if (!deadlineStr.isEmpty()) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA);
                                java.util.Date deadlineDate = sdf.parse(deadlineStr);
                                task.deadline = deadlineDate.getTime();
                            } catch (Exception ex) {
                                Toast.makeText(this, "⚠️ 日期格式错误，将使用默认7天后", Toast.LENGTH_SHORT).show();
                                task.deadline = System.currentTimeMillis() + 7L * 24 * 3600 * 1000;
                            }
                        } else {
                            task.deadline = System.currentTimeMillis() + 7L * 24 * 3600 * 1000; // 默认7天
                        }
                    } else {
                        task.recurrenceType = "once";
                    }
                    task.deviceId = syncManager.getDeviceId();
                    db.taskDao().insert(task);
                    syncManager.onDataChanged();
                    Toast.makeText(this, "任务已发布 🎯 (" + task.recurrenceType + ")", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddShopItemDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_shop_item, null);
        android.widget.EditText etName = view.findViewById(R.id.et_item_name);
        android.widget.EditText etDesc = view.findViewById(R.id.et_item_desc);
        android.widget.EditText etPrice = view.findViewById(R.id.et_item_price);
        android.widget.EditText etCategory = view.findViewById(R.id.et_item_category);
        Button btnPickImage = view.findViewById(R.id.btn_pick_image);
        ImageView ivPreview = view.findViewById(R.id.iv_image_preview);
        TextView tvImageName = view.findViewById(R.id.tv_image_name);

        // 重置之前选中的图片
        selectedShopImagePath = null;

        // 从相册选图按钮
        btnPickImage.setOnClickListener(v -> {
            soundHelper.playClickSound();
            currentShopDialogView = view;  // 保存引用，图片选择后更新预览
            pickShopImageLauncher.launch("image/*");
        });

        new AlertDialog.Builder(this)
                .setTitle("添加上架商品")
                .setView(view)
                .setPositiveButton("上架", (d, w) -> {
                    ShopItem item = new ShopItem();
                    item.name = etName.getText().toString();
                    item.description = etDesc.getText().toString();
                    try { item.priceCoins = Integer.parseInt(etPrice.getText().toString()); }
                    catch (Exception e) { item.priceCoins = 50; }
                    item.category = etCategory.getText().toString();
                    item.iconUrl = selectedShopImagePath != null ? selectedShopImagePath : "";
                    db.shopItemDao().insert(item);
                    selectedShopImagePath = null;
                    currentShopDialogView = null;
                    Toast.makeText(this, "商品已上架 🏪", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 家长管理主菜单 — 按层级重新组织
     * 一级：总览 | 学习 | 商城 | 任务 | 系统
     */
    private void showSettingsDialog() {
        soundHelper.playClickSound();
        int pendingTotal = db.redemptionDao().getByStatus("pending").size()
            + db.taskDao().getByStatus("pending").size()
            + db.coinEarningDao().getPendingCount();
        String[] mainLabels = {
                "📊 数据总览",
                "✅ 审批中心" + (pendingTotal > 0 ? "（" + pendingTotal + "项待处理）" : ""),
                "📚 学习管理",
                "🏪 商城管理",
                "📋 任务管理",
                "⚙️ 系统设置"
        };
        com.sister.habits.utils.MenuHelper.show(this, "📱 家长管理中心", mainLabels,
                this::showDashboardMenu,
                () -> showApprovalCenterDialog(null, -1),
                this::showLearningMenu,
                this::showShopMenu,
                this::showTaskMenu,
                this::showSystemMenu
        );
    }

    /** 二级菜单：📊 总览与审批 */
    private void showDashboardMenu() {
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        int pendingTaskCount = db.taskDao().getByStatus("pending").size();
        int earningPendingCount = db.coinEarningDao().getPendingCount();
        String[] items = {
                "📊 查看统计数据",
                "✅ 兑换审批（" + pendingCount + "项待处理）",
                "📋 任务审批（" + pendingTaskCount + "项待确认）",
                "🔄 手动同步"
        };
        new AlertDialog.Builder(this)
                .setTitle("📊 总览与审批")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: refreshStats(); Toast.makeText(this, tvStats.getText(), Toast.LENGTH_LONG).show(); break;
                        case 1: loadPendingApprovals(); Toast.makeText(this, "已刷新审批列表", Toast.LENGTH_SHORT).show(); break;
                        case 2: loadPendingTasks(); Toast.makeText(this, "已刷新任务列表", Toast.LENGTH_SHORT).show(); break;
                        case 3: syncManager.triggerRemoteSync(); syncManager.triggerLanSync(); Toast.makeText(this, "同步已触发", Toast.LENGTH_SHORT).show(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSettingsDialog())
                .show();
    }

    /** 二级菜单：📚 学习管理 */
    private void showLearningMenu() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        int dailyWords = config != null ? config.maxDailyWords : 10;
        int dailyReview = config != null ? config.maxDailyReview : 20;
        String[] learnLabels = {
                "📖 词库管理（年级/下载/切换）",
                "📝 每日学习限额（新词:" + dailyWords + " 复习:" + dailyReview + "）",
                "💰 学习奖励参数"
        };
        com.sister.habits.utils.MenuHelper.showWithBack(this, "📚 学习管理", learnLabels,
                this::showSettingsDialog,
                this::showWordbankDialog,
                this::showLearningLimitDialog,
                this::showLearningRewardDialog
        );
    }

    /** 📝 每日学习限额 */
    private void showLearningLimitDialog() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }
        EconomyConfig fc = config;
        View v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        android.widget.EditText etWords = new android.widget.EditText(this);
        etWords.setHint("每日新词上限");
        etWords.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etWords.setText(String.valueOf(fc.maxDailyWords));
        android.widget.EditText etReview = new android.widget.EditText(this);
        etReview.setHint("每日复习上限");
        etReview.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etReview.setText(String.valueOf(fc.maxDailyReview));
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(32,16,32,16);
        ll.addView(etWords);
        ll.addView(etReview);
        new AlertDialog.Builder(this)
                .setTitle("📝 每日学习限额")
                .setView(ll)
                .setPositiveButton("保存", (d,w2)->{
                    fc.maxDailyWords = parseInt(etWords,10);
                    fc.maxDailyReview = parseInt(etReview,20);
                    db.economyConfigDao().setConfig(fc);
                    Toast.makeText(this,"学习限额已更新",Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("← 返回上级",(d,w2)->showLearningMenu()).show();
    }

    /** 💰 学习奖励参数 */
    private void showLearningRewardDialog() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }
        EconomyConfig fc = config;
        android.widget.EditText etLearn = new android.widget.EditText(this);
        etLearn.setHint("新词答对奖励");
        etLearn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etLearn.setText(String.valueOf(fc.wordLearnReward));
        android.widget.EditText etPass = new android.widget.EditText(this);
        etPass.setHint("复习通关奖励");
        etPass.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPass.setText(String.valueOf(fc.reviewPassReward));
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(32,16,32,16);
        ll.addView(etLearn);
        ll.addView(etPass);
        new AlertDialog.Builder(this)
                .setTitle("💰 学习奖励参数")
                .setView(ll)
                .setPositiveButton("保存", (d,w2)->{
                    fc.wordLearnReward = parseInt(etLearn,2);
                    fc.reviewPassReward = parseInt(etPass,2);
                    db.economyConfigDao().setConfig(fc);
                    Toast.makeText(this,"学习奖励已更新",Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("← 返回上级",(d,w2)->showLearningMenu()).show();
    }

    /** 二级菜单：🏪 商城管理 */
    private void showShopMenu() {
        int shopCount = db.shopItemDao().getAll().size();
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        String[] items = {
                "➕ 上架新商品",
                "✏️ 管理已有商品（" + shopCount + "件）",
                "✅ 兑换审批（" + pendingCount + "项待处理）"
        };
        new AlertDialog.Builder(this)
                .setTitle("🏪 商城管理")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showAddShopItemDialog(); break;
                        case 1: showManageShopDialog(); break;
                        case 2: loadPendingApprovals(); Toast.makeText(this, "已刷新审批列表", Toast.LENGTH_SHORT).show(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSettingsDialog())
                .show();
    }

    /** 二级菜单：📋 任务管理 */
    private void showTaskMenu() {
        int pendingCount = db.taskDao().getByStatus("pending").size();
        String[] taskLabels = {
                "➕ 添加新任务",
                "⏳ 待确认任务（" + pendingCount + "项）",
                "📋 任务模板库",
                "📝 管理已发布任务"
        };
        com.sister.habits.utils.MenuHelper.showWithBack(this, "📋 任务管理", taskLabels,
                this::showSettingsDialog,
                this::showAddTaskDialog,
                () -> { loadPendingTasks(); Toast.makeText(this, "已刷新任务列表", Toast.LENGTH_SHORT).show(); },
                this::showTaskTemplates,
                this::showManageTasksDialog
        );
    }

    /** 二级菜单：⚙️ 系统设置 */
    private void showSystemMenu() {
        String[] items = {
                "👤 个人信息（昵称/头像/标题）",
                "🏠 启动模式 & Hub中枢",
                "💰 完整经济参数",
                "🚀 加速器管理（双倍积分日/打卡勋章/周月奖励）",
                "📋 任务模板库（20+预设任务）",
                "💰 积分审批（待审积分确认）",
                "🔐 数据导出备份",
                "📡 设备同步 & QR配对",
                "🔄 检查更新"
        };
        new AlertDialog.Builder(this)
                .setTitle("⚙️ 系统设置")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showProfileSettings(); break;
                        case 1: showHubSettings(); break;
                        case 2: showEconomySettings(); break;
                        case 3: showAcceleratorSettings(); break;
                        case 4: showTaskTemplates(); break;
                        case 5: showEarningApprovals(); break;
                        case 6: showBackupRestoreDialog(); break;
                        case 7: showSyncDashboardDialog(); break;
                        case 8: checkForUpdate(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSettingsDialog())
                .show();
    }

    /** ⚙️ 检查更新 */
    private void checkForUpdate() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://api.github.com/repos/Cuiyi-Srama/HabitTracker/releases/latest");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "HabitTracker/1.5.0");
                conn.setInstanceFollowRedirects(true);
                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                String json = s.hasNext() ? s.next() : "";
                is.close();
                com.google.gson.Gson gson = new com.google.gson.Gson();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> release = (java.util.Map<String, Object>) gson.fromJson(json, java.util.Map.class);
                String latestTag = (String) release.get("tag_name");
                String currentVer = "v1.5.0";
                java.util.List<Object> assets = (java.util.List<Object>) release.get("assets");
                String apkUrl = null;
                if (assets != null) {
                    for (Object a : assets) {
                        java.util.Map<String, Object> asset = (java.util.Map<String, Object>) a;
                        if (((String)asset.get("name")).endsWith(".apk")) {
                            apkUrl = (String) asset.get("browser_download_url");
                            break;
                        }
                    }
                }
                final String fTag = latestTag;
                final String fApkUrl = apkUrl;
                if (latestTag == null) {
                    runOnUiThread(() -> Toast.makeText(this, "❌ 检查失败", Toast.LENGTH_SHORT).show());
                    return;
                }
                boolean isNewer = !latestTag.equals(currentVer);
                runOnUiThread(() -> {
                    if (isNewer && fApkUrl != null) {
                        new AlertDialog.Builder(this)
                                .setTitle("🔄 发现新版本")
                                .setMessage("当前: " + currentVer + "\n最新: " + fTag + "\n\n是否下载更新？")
                                .setPositiveButton("📥 下载更新", (d, w) -> downloadUpdate(fApkUrl))
                                .setNegativeButton("☮ 稍后", null)
                                .show();
                    } else {
                        Toast.makeText(this, "✅ 已是最新版本 (" + currentVer + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "❌ 检查失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void downloadUpdate(String apkUrl) {
        Toast.makeText(this, "📥 下载中...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(apkUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                int totalSize = conn.getContentLength();
                java.io.InputStream is = conn.getInputStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream("/storage/emulated/0/Download/HabitTracker_update.apk");
                byte[] buf = new byte[8192];
                int len;
                long downloaded = 0;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                    downloaded += len;
                    final long d = downloaded;
                    final int pct = totalSize > 0 ? (int)(downloaded*100/totalSize) : -1;
                    runOnUiThread(() -> {
                        if (pct >= 0)
                            Toast.makeText(this, "📥 "+pct+"% ("+(d/1024)+"KB)", Toast.LENGTH_SHORT).show();
                    });
                }
                fos.close(); is.close();
                runOnUiThread(() -> {
                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    i.setDataAndType(android.net.Uri.fromFile(new java.io.File("/storage/emulated/0/Download/HabitTracker_update.apk")),
                            "application/vnd.android.package-archive");
                    i.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "❌ 下载失败: "+e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
private void showProfileSettings() {
        View view = getLayoutInflater().inflate(R.layout.dialog_profile_settings, null);
        android.widget.EditText etNickname = view.findViewById(R.id.et_nickname);
        android.widget.EditText etAppTitle = view.findViewById(R.id.et_app_title);
        Button btnPickAvatar = view.findViewById(R.id.btn_pick_avatar);
        android.widget.TextView tvBirthday = view.findViewById(R.id.tv_birthday);
        etNickname.setText(profile.getNickname());
        etAppTitle.setText(profile.getAppTitle());
        // 生日显示
        String bday = profile.getBirthday();
        tvBirthday.setText(bday.isEmpty() ? "🎂 点击设置孩子生日" : "🎂 生日: " + bday);
        tvBirthday.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            // 如果已有生日，用已有的日期
            if (!bday.isEmpty()) {
                try {
                    String[] parts = bday.split("-");
                    cal.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])-1, Integer.parseInt(parts[2]));
                } catch (Exception e) {}
            }
            new android.app.DatePickerDialog(this, (datePicker, year, month, day) -> {
                String picked = year + "-" + (month+1) + "-" + day;
                tvBirthday.setText("🎂 生日: " + picked);
                profile.setBirthday(picked);
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        btnPickAvatar.setOnClickListener(v -> pickShopImageLauncher.launch("image/*"));
        new AlertDialog.Builder(this)
                .setTitle("👤 个人信息")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    String nn = etNickname.getText().toString().trim();
                    if (!nn.isEmpty()) profile.setNickname(nn);
                    String at = etAppTitle.getText().toString().trim();
                    if (!at.isEmpty()) profile.setAppTitle(at);
                    Toast.makeText(this, "个人信息已更新 ✅", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

                        /** 🚀 加速器管理：双倍积分日、加速器开关 */

    /** 管理所有已发布的任务（编辑/删除） */
    private void showManageTasksDialog() {
        java.util.List<com.sister.habits.data.models.Task> all = db.taskDao().getAll();
        if (all == null || all.isEmpty()) {
            Toast.makeText(this, "暂无已发布任务", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            com.sister.habits.data.models.Task t = all.get(i);
            names[i] = ("active".equals(t.status) ? "🟢" : "confirmed".equals(t.status) ? "✅" : "⏳") + " " + t.title + " (" + t.rewardCoins + "分)";
        }
        new AlertDialog.Builder(this)
                .setTitle("📝 管理已发布任务 (" + all.size() + "个)")
                .setItems(names, (d, which) -> {
                    com.sister.habits.data.models.Task task = all.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle(task.title)
                            .setMessage("状态: " + task.status + " | 积分: " + task.rewardCoins + " | 类型: " + task.recurrenceType)
                            .setPositiveButton("✏️ 编辑", (d2, w2) -> showEditTaskDialog(task))
                            .setNeutralButton("🗑️ 删除", (d2, w2) -> {
                                new AlertDialog.Builder(this)
                                        .setTitle("确认删除")
                                        .setMessage("确定删除任务「" + task.title + "」吗？此操作不可恢复。")
                                        .setPositiveButton("确认删除", (d3, w3) -> {
                                            db.taskDao().delete(task);
                                            Toast.makeText(this, "已删除: " + task.title, Toast.LENGTH_SHORT).show();
                                            refreshAll();
                                        })
                                        .setNegativeButton("取消", null).show();
                            })
                            .setNegativeButton("← 返回", null).show();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showTaskMenu()).show();
    }
    /** 编辑已有任务 */
    private void showEditTaskDialog(com.sister.habits.data.models.Task task) {
        android.widget.EditText etTitle = new android.widget.EditText(this);
        etTitle.setText(task.title);
        etTitle.setHint("任务名称");
        android.widget.EditText etReward = new android.widget.EditText(this);
        etReward.setText(String.valueOf(task.rewardCoins));
        etReward.setHint("奖励积分");
        etReward.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        String[] types = {"一次性", "每日", "每周", "每月", "常驻", "限时"};
        String[] typeCodes = {"once", "daily", "weekly", "monthly", "permanent", "timed"};
        final int[] sel = {java.util.Arrays.asList(typeCodes).indexOf(task.recurrenceType)};
        if (sel[0] < 0) sel[0] = 0;
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(32,16,32,16);
        ll.addView(etTitle);
        ll.addView(etReward);
        new AlertDialog.Builder(this)
                .setTitle("编辑任务")
                .setView(ll)
                .setSingleChoiceItems(types, sel[0], (d2, w2) -> sel[0] = w2)
                .setPositiveButton("保存", (d2, w2) -> {
                    String title = etTitle.getText().toString().trim();
                    String rewardStr = etReward.getText().toString().trim();
                    if (title.isEmpty() || rewardStr.isEmpty()) {
                        Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    task.title = title;
                    task.rewardCoins = Integer.parseInt(rewardStr);
                    task.recurrenceType = typeCodes[sel[0]];
                    db.taskDao().update(task);
                    Toast.makeText(this, "✅ 已更新: " + title, Toast.LENGTH_SHORT).show();
                    refreshAll();
                })
                .setNegativeButton("取消", null).show();
    }
    private void showTaskTemplates() {
        String[] templates = {
            "H01-整理床铺 (3分)", "H02-玩具归位 (3分)", "H03-擦拭餐桌 (5分)",
            "H04-倒垃圾 (5分)", "H05-扫地一间 (10分)", "H06-拖地一间 (15分)",
            "H07-洗碗一餐 (10分)", "H08-晾收衣服 (8分)", "H09-叠衣服 (8分)",
            "H15-大扫除30min (30分)", "S01-背单词10个 (8分)", "S02-课外阅读20min (5分)",
            "S03-练琴练字20min (8分)", "S04-额外练习一页 (5分)", "S05-背古诗古文 (5分)",
            "S06-写日记小作文 (10分)", "S09-错题5道整理 (5分)", "C01-主动帮小事 (5分)",
            "C03-连续三天不挑食 (5分)", "C06-教妹妹学习15min (8分)"
        };
        final boolean[] checked = new boolean[templates.length];
        new AlertDialog.Builder(this)
                .setTitle("任务模板库(20个)-多选后一键添加")
                .setMultiChoiceItems(templates, checked, (d, which, isChecked) -> {})
                .setPositiveButton("✅ 批量添加选中项", (d, which) -> {
                    int added = 0;
                    for (int i = 0; i < templates.length; i++) {
                        if (!checked[i]) continue;
                        String t = templates[i];
                        String[] p = t.split("-", 2);
                        String code = p[0];
                        String desc = p.length > 1 ? p[1] : "";
                        int reward = 5;
                        if (desc.contains("(") && desc.contains("分")) {
                            try {
                                reward = Integer.parseInt(desc.substring(desc.indexOf("(")+1, desc.indexOf("分")));
                            } catch (Exception e) {}
                        }
                        String title = desc.contains("(") ? desc.substring(0, desc.indexOf("(")).trim() : desc;
                        com.sister.habits.data.models.Task task = new com.sister.habits.data.models.Task();
                        task.id = code + "_" + System.currentTimeMillis() + "_" + i;
                        task.title = title;
                        task.description = code + " " + title;
                        task.rewardCoins = reward;
                        task.recurrenceType = "daily";
                        task.status = "active";
                        task.createdAt = System.currentTimeMillis();
                        db.taskDao().insert(task);
                        added++;
                    }
                    Toast.makeText(this, "✅ 已添加 " + added + " 个任务", Toast.LENGTH_SHORT).show();
                    refreshAll();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu()).show();
    }


    private void showEarningApprovals() {
        java.util.List<com.sister.habits.data.models.CoinEarning> pendings = db.coinEarningDao().getPending();
        if (pendings == null || pendings.isEmpty()) {
            Toast.makeText(this, "无待审积分", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[pendings.size()];
        for (int i = 0; i < pendings.size(); i++) {
            com.sister.habits.data.models.CoinEarning e = pendings.get(i);
            String desc = e.description != null ? e.description : "未知";
            String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date(e.requestedAt));
            items[i] = "+" + e.amount + "分 " + desc + "  (" + time + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle("" + pendings.size() + "项待审积分")
                .setItems(items, (d, which) -> {
                    com.sister.habits.data.models.CoinEarning e = pendings.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle("审批: " + e.description)
                            .setMessage("金额: +" + e.amount + "分\n来源: " + e.sourceType)
                            .setPositiveButton("确认", (d2, w2) -> {
                                e.status = "confirmed";
                                e.confirmedAt = System.currentTimeMillis();
                                db.coinEarningDao().update(e);
                                Integer balance = db.coinTransactionDao().getBalance("sister");
                                int newBal = (balance != null ? balance : 0) + e.amount;
                                com.sister.habits.data.models.CoinTransaction ct = new com.sister.habits.data.models.CoinTransaction(
                                        "sister", e.amount, newBal,
                                        e.sourceType, e.description,
                                        com.sister.habits.sync.SyncManager.getInstance(ParentActivity.this).getDeviceId());
                                db.coinTransactionDao().insert(ct);
                                Toast.makeText(this, "已确认 +" + e.amount + "分!", Toast.LENGTH_SHORT).show();
                                refreshStats();
                            })
                            .setNegativeButton("拒绝", (d2, w2) -> {
                                e.status = "rejected";
                                e.rejectedAt = System.currentTimeMillis();
                                db.coinEarningDao().update(e);
                                Toast.makeText(this, "已拒绝该积分申请", Toast.LENGTH_SHORT).show();
                            })
                            .setNeutralButton("返回", null)
                            .show();
                })
                .setNegativeButton("返回上级", (d, w) -> showSystemMenu()).show();
    }

    private void showAcceleratorSettings() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }
        EconomyConfig finalConfig = config;
        View view = getLayoutInflater().inflate(R.layout.dialog_accelerator_settings, null);

        android.widget.Switch swDoublePoints = view.findViewById(R.id.sw_double_points);
        android.widget.TextView tvDoubleDate = view.findViewById(R.id.tv_double_date);
        android.widget.EditText etStreak7 = view.findViewById(R.id.et_accel_streak7);
        android.widget.EditText etWeek = view.findViewById(R.id.et_accel_week);
        android.widget.EditText etMonth = view.findViewById(R.id.et_accel_month);
        android.widget.EditText etBirthday = view.findViewById(R.id.et_accel_birthday);
        android.widget.EditText etHoliday = view.findViewById(R.id.et_accel_holiday);
        android.widget.EditText etLimitWeekday = view.findViewById(R.id.et_limit_weekday);
        android.widget.EditText etLimitWeekend = view.findViewById(R.id.et_limit_weekend);
        android.widget.TextView tvSummary = view.findViewById(R.id.tv_accel_summary);

        swDoublePoints.setChecked(finalConfig.doublePointsEnabled);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date());
        tvDoubleDate.setText("📅 启用日期: " + (finalConfig.doublePointDate != null ? finalConfig.doublePointDate : today));
        etStreak7.setText(String.valueOf(finalConfig.boostStreak7));
        etWeek.setText(String.valueOf(finalConfig.boostWeek));
        etMonth.setText(String.valueOf(finalConfig.boostMonth));
        etBirthday.setText(String.valueOf(finalConfig.boostBirthday));
        etHoliday.setText(String.valueOf(finalConfig.boostHoliday));
        etLimitWeekday.setText(String.valueOf(finalConfig.softLimitWeekday));
        etLimitWeekend.setText(String.valueOf(finalConfig.softLimitWeekend));

        // 双倍积分日开关：点日期可修改
        tvDoubleDate.setOnClickListener(v -> {
            String dateStr = tvDoubleDate.getText().toString().replace("📅 启用日期: ", "").trim();
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA);
                java.util.Date d = sdf.parse(dateStr);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(d);
                new android.app.DatePickerDialog(this, (view2, year, month, day) -> {
                    String picked = year + "-" + (month+1) + "-" + day;
                    tvDoubleDate.setText("📅 启用日期: " + picked);
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
            } catch (Exception e) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                new android.app.DatePickerDialog(this, (view2, year, month, day) -> {
                    String picked = year + "-" + (month+1) + "-" + day;
                    tvDoubleDate.setText("📅 启用日期: " + picked);
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
            }
        });

        // 显示当前加速器摘要
        String boostSummary = com.sister.habits.sync.AcceleratorService.getTodayBoostSummary(this);
        tvSummary.setText(!boostSummary.isEmpty() ? boostSummary : "今日暂无加速器触发");

        new AlertDialog.Builder(this)
                .setTitle("🚀 加速器管理")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    boolean doubleOn = swDoublePoints.isChecked();
                    String doubleDate = tvDoubleDate.getText().toString().replace("📅 启用日期: ", "").trim();
                    finalConfig.doublePointsEnabled = doubleOn;
                    finalConfig.doublePointDate = doubleOn ? doubleDate : null;
                    finalConfig.boostStreak7 = parseInt(etStreak7, 15);
                    finalConfig.boostWeek = parseInt(etWeek, 30);
                    finalConfig.boostMonth = parseInt(etMonth, 80);
                    finalConfig.boostBirthday = parseInt(etBirthday, 100);
                    finalConfig.boostHoliday = parseInt(etHoliday, 50);
                    finalConfig.softLimitWeekday = parseInt(etLimitWeekday, 60);
                    finalConfig.softLimitWeekend = parseInt(etLimitWeekend, 100);
                    db.economyConfigDao().setConfig(finalConfig);
                    Toast.makeText(this, "加速器设置已保存 ✅", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("🚀 立即检查加速器", (d, w) -> {
                    com.sister.habits.sync.AcceleratorService.checkAndApply(this);
                    Toast.makeText(this, "加速器检查完成，查看积分审批确认结果", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu()).show();
    }

    private void showEconomySettings() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }
        View view = getLayoutInflater().inflate(R.layout.dialog_economy_settings, null);
        EconomyConfig finalConfig = config;
        android.widget.EditText etCheckinBase = view.findViewById(R.id.et_checkin_base);
        android.widget.EditText etStreak3 = view.findViewById(R.id.et_streak3);
        android.widget.EditText etStreak7 = view.findViewById(R.id.et_streak7);
        android.widget.EditText etStreak14 = view.findViewById(R.id.et_streak14);
        android.widget.EditText etStreak30 = view.findViewById(R.id.et_streak30);
        android.widget.EditText etWordLearn = view.findViewById(R.id.et_word_learn);
        android.widget.EditText etReviewPass = view.findViewById(R.id.et_review_pass);
        android.widget.EditText etTaskDailyMin = view.findViewById(R.id.et_task_daily_min);
        android.widget.EditText etTaskDailyMax = view.findViewById(R.id.et_task_daily_max);
        android.widget.EditText etTaskChallengeMin = view.findViewById(R.id.et_task_challenge_min);
        android.widget.EditText etTaskChallengeMax = view.findViewById(R.id.et_task_challenge_max);
        android.widget.EditText etScreen15 = view.findViewById(R.id.et_screen_15);
        android.widget.EditText etScreen30 = view.findViewById(R.id.et_screen_30);
        android.widget.EditText etScreen60 = view.findViewById(R.id.et_screen_60);
        android.widget.EditText etMaxDailyCoins = view.findViewById(R.id.et_max_daily_coins);
        android.widget.EditText etMaxWords = view.findViewById(R.id.et_max_words);
        android.widget.EditText etMaxReview = view.findViewById(R.id.et_max_review);
        etCheckinBase.setText(String.valueOf(finalConfig.checkInBaseReward));
        etStreak3.setText(String.valueOf(finalConfig.streak3Bonus));
        etStreak7.setText(String.valueOf(finalConfig.streak7Bonus));
        etStreak14.setText(String.valueOf(finalConfig.streak14Bonus));
        etStreak30.setText(String.valueOf(finalConfig.streak30Bonus));
        etWordLearn.setText(String.valueOf(finalConfig.wordLearnReward));
        etReviewPass.setText(String.valueOf(finalConfig.reviewPassReward));
        etTaskDailyMin.setText(String.valueOf(finalConfig.taskDailyMin));
        etTaskDailyMax.setText(String.valueOf(finalConfig.taskDailyMax));
        etTaskChallengeMin.setText(String.valueOf(finalConfig.taskChallengeMin));
        etTaskChallengeMax.setText(String.valueOf(finalConfig.taskChallengeMax));
        etScreen15.setText(String.valueOf(finalConfig.screenTime15min));
        etScreen30.setText(String.valueOf(finalConfig.screenTime30min));
        etScreen60.setText(String.valueOf(finalConfig.screenTime60min));
        etMaxDailyCoins.setText(String.valueOf(finalConfig.maxDailyCoins));
        etMaxWords.setText(String.valueOf(finalConfig.maxDailyWords));
        etMaxReview.setText(String.valueOf(finalConfig.maxDailyReview));
        new AlertDialog.Builder(this)
                .setTitle("💰 经济参数")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    finalConfig.checkInBaseReward = parseInt(etCheckinBase, 10);
                    finalConfig.streak3Bonus = parseInt(etStreak3, 5);
                    finalConfig.streak7Bonus = parseInt(etStreak7, 15);
                    finalConfig.streak14Bonus = parseInt(etStreak14, 30);
                    finalConfig.streak30Bonus = parseInt(etStreak30, 100);
                    finalConfig.wordLearnReward = parseInt(etWordLearn, 2);
                    finalConfig.reviewPassReward = parseInt(etReviewPass, 2);
                    finalConfig.taskDailyMin = parseInt(etTaskDailyMin, 5);
                    finalConfig.taskDailyMax = parseInt(etTaskDailyMax, 15);
                    finalConfig.taskChallengeMin = parseInt(etTaskChallengeMin, 20);
                    finalConfig.taskChallengeMax = parseInt(etTaskChallengeMax, 50);
                    finalConfig.screenTime15min = parseInt(etScreen15, 10);
                    finalConfig.screenTime30min = parseInt(etScreen30, 18);
                    finalConfig.screenTime60min = parseInt(etScreen60, 30);
                    finalConfig.maxDailyCoins = parseInt(etMaxDailyCoins, 500);
                    finalConfig.maxDailyWords = parseInt(etMaxWords, 10);
                    finalConfig.maxDailyReview = parseInt(etMaxReview, 30);
                    db.economyConfigDao().setConfig(finalConfig);
                    Toast.makeText(this, "参数已更新 ✅", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu())
                .show();
    }

    private void showHubSettings() {
        SharedPreferences prefs = getSharedPreferences("parent_prefs", MODE_PRIVATE);
        String currentMode = prefs.getString("default_mode", "child");
        String[] modes = {"👧 默认进入孩子模式", "👨 默认进入家长模式", "❓ 每次询问"};
        int checked = "child".equals(currentMode) ? 0 : "parent".equals(currentMode) ? 1 : 2;
        new AlertDialog.Builder(this)
                .setTitle("🏠 默认启动模式 + Hub中枢")
                .setSingleChoiceItems(modes, checked, (d, w) -> {
                    String mode = w == 0 ? "child" : w == 1 ? "parent" : "ask";
                    prefs.edit().putString("default_mode", mode).apply();
                })
                .setNeutralButton("Hub模式开关: " + (syncManager.isHubModeEnabled() ? "🟢开启" : "🔴关闭"), (d, w) -> {
                    syncManager.setHubModeEnabled(!syncManager.isHubModeEnabled());
                    Toast.makeText(this, syncManager.isHubModeEnabled() ? "🏠 中枢已开启" : "🏠 中枢已关闭", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu()).show();
    }

    private int parseInt(android.widget.EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString()); } catch (Exception e) { return def; }
    }

    /**
     * 词库管理对话框：年级选择 + 每日单词量 + JSON导入
     */
    private void showWordbankDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_wordbank, null);
        SharedPreferences prefs = getSharedPreferences("wordbank_prefs", MODE_PRIVATE);

        // 恢复年级选择状态（小学/初中/高中）
        ((CheckBox) view.findViewById(R.id.cb_grade_primary)).setChecked(prefs.getBoolean("grade_primary", true));
        ((CheckBox) view.findViewById(R.id.cb_grade_junior)).setChecked(prefs.getBoolean("grade_junior", true));
        ((CheckBox) view.findViewById(R.id.cb_grade_senior)).setChecked(prefs.getBoolean("grade_senior", false));

        // 恢复每日单词量
        EconomyConfig config = db.economyConfigDao().getConfig();
        android.widget.EditText etDailyWords = view.findViewById(R.id.et_daily_words);
        etDailyWords.setText(String.valueOf(config != null ? config.maxDailyWords : 10));

        // JSON导入按钮——启动文件选择器
        Button btnImport = view.findViewById(R.id.btn_import_wordbank);
        btnImport.setOnClickListener(v -> {
            soundHelper.playClickSound();
            wordbankImportLauncher.launch(new String[]{"application/json", "*/*"});
        });

        // ===== 外部词库源加载 =====
        LinearLayout externalLayout = view.findViewById(R.id.layout_external_sources);
        try {
            android.content.res.AssetManager am = getAssets();
            java.io.InputStream is = am.open("wordbank_sources.json");
            // ★ Fix 2: 使用ByteArrayOutputStream确保读取完整文件
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = is.read(tmp)) != -1) {
                baos.write(tmp, 0, n);
            }
            is.close();
            String json = baos.toString("UTF-8");
            Type sourceType = new TypeToken<List<ExternalSource>>(){}.getType();
            List<ExternalSource> sources = new Gson().fromJson(json, sourceType);

            for (ExternalSource source : sources) {
                // 卡片式布局
                android.widget.LinearLayout card = new android.widget.LinearLayout(this);
                card.setOrientation(android.widget.LinearLayout.VERTICAL);
                card.setPadding(12, 12, 12, 12);
                android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, 8);
                card.setLayoutParams(cardParams);
                card.setBackgroundResource(R.drawable.card_background);

                // 名称+标签
                android.widget.LinearLayout headerRow = new android.widget.LinearLayout(this);
                headerRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                headerRow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

                TextView tvName = new TextView(this);
                tvName.setText(source.name);
                tvName.setTextSize(14);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                tvName.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                TextView tvTag = new TextView(this);
                tvTag.setText(source.gradeLabel);
                tvTag.setTextSize(12);
                tvTag.setTextColor(0xFFFFFFFF);
                tvTag.setPadding(8, 3, 8, 3);
                tvTag.setBackgroundColor(0xFF6B35);
                android.widget.LinearLayout.LayoutParams tagLp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                tagLp.setMargins(0, 0, 4, 0);
                tvTag.setLayoutParams(tagLp);
                android.graphics.drawable.GradientDrawable tagBg = new android.graphics.drawable.GradientDrawable();
                tagBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                tagBg.setCornerRadius(20);
                tagBg.setColor(0xFF6B35);
                tvTag.setBackground(tagBg);

                headerRow.addView(tvName);
                headerRow.addView(tvTag);
                card.addView(headerRow);

                // 描述
                TextView tvDesc = new TextView(this);
                tvDesc.setText(source.description);
                tvDesc.setTextSize(12);
                tvDesc.setTextColor(0xFF666666);
                tvDesc.setPadding(0, 4, 0, 8);
                card.addView(tvDesc);

                // 下载按钮 — MATCH_PARENT全宽，自适应高度
                Button btnDownload = new Button(this);
                btnDownload.setText("📥 下载并预览");
                btnDownload.setTextSize(14);
                btnDownload.setAllCaps(false);
                btnDownload.setTextColor(0xFFFFFFFF);
                btnDownload.setBackgroundColor(0xFF1976D2);
                android.graphics.drawable.GradientDrawable btnDlBg = new android.graphics.drawable.GradientDrawable();
                btnDlBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                btnDlBg.setCornerRadius(8);
                btnDlBg.setColor(0xFF1976D2);
                btnDownload.setBackground(btnDlBg);
                int dp12dl = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
                int dp48dl = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
                btnDownload.setPadding(dp12dl, 0, dp12dl, 0);
                btnDownload.setMinHeight(dp48dl);
                btnDownload.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                btnDownload.setOnClickListener(v -> downloadAndPreview(source));
                card.addView(btnDownload);

                externalLayout.addView(card);
            }
        } catch (Exception e) {
            android.util.Log.e("ParentActivity", "加载外部词库源失败", e);
            TextView tvError = new TextView(this);
            tvError.setText("外部词库加载失败: " + e.getMessage());
            tvError.setTextSize(12);
            tvError.setTextColor(0xFFFF4444);
            tvError.setPadding(0, 8, 0, 0);
            externalLayout.addView(tvError);
        }

        // ===== 已下载词库列表（切换功能） =====
        LinearLayout installedLayout = view.findViewById(R.id.layout_installed_banks);
        String activeBankId = getSharedPreferences("wordbank_prefs", MODE_PRIVATE).getString("active_bank_id", "builtin");
        java.util.List<com.sister.habits.data.models.WordBank> installedBanks = db.wordBankDao().getAll();
        if (installedBanks.isEmpty()) {
            TextView tvNoBank = new TextView(this);
            tvNoBank.setText("暂无已下载词库");
            tvNoBank.setTextSize(12);
            tvNoBank.setTextColor(0xFF888888);
            tvNoBank.setPadding(8, 8, 8, 8);
            installedLayout.addView(tvNoBank);
        } else {
            for (com.sister.habits.data.models.WordBank bank : installedBanks) {
                boolean isActive = bank.id.equals(activeBankId);
                Button btnBank = new Button(this);
                String prefix = isActive ? "✅ " : "  ";
                btnBank.setText(prefix + bank.name + " (" + bank.wordCount + "词)");
                btnBank.setTextSize(13);
                btnBank.setAllCaps(false);
                btnBank.setBackgroundColor(isActive ? 0xFFE8F5E9 : 0xFFF5F5F5);
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 3, 0, 3);
                btnBank.setLayoutParams(lp);
                final String bankId = bank.id;
                final String bankName = bank.name;
                btnBank.setOnClickListener(v -> {
                    if (bankId.equals(activeBankId)) {
                        Toast.makeText(this, "当前已在使用此词库", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    getSharedPreferences("wordbank_prefs", MODE_PRIVATE)
                            .edit().putString("active_bank_id", bankId).apply();
                    db.wordBankDao().deactivateAll();
                    db.wordBankDao().setActive(bankId);
                    Toast.makeText(this, "✅ 已切换到: " + bankName, Toast.LENGTH_LONG).show();
                });
                installedLayout.addView(btnBank);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("📚 词库管理")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    // 保存学段选择（小学/初中/高中）
                    boolean gPrimary = ((CheckBox) view.findViewById(R.id.cb_grade_primary)).isChecked();
                    boolean gJunior = ((CheckBox) view.findViewById(R.id.cb_grade_junior)).isChecked();
                    boolean gSenior = ((CheckBox) view.findViewById(R.id.cb_grade_senior)).isChecked();
                    prefs.edit()
                            .putBoolean("grade_primary", gPrimary)
                            .putBoolean("grade_junior", gJunior)
                            .putBoolean("grade_senior", gSenior)
                            // 自增版本号，触发DatabaseInitializer重新加载
                            .putInt("grade_version", prefs.getInt("grade_version", 0) + 1)
                            .apply();

                    // 保存每日单词量
                    try {
                        int dailyWords = Integer.parseInt(etDailyWords.getText().toString());
                        if (dailyWords > 0) {
                            db.economyConfigDao().updateMaxDailyWords(dailyWords);
                        }
                    } catch (Exception ignored) {}

                    Toast.makeText(this, "词库配置已保存 ✅\n重启App后生效", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("\u2190 返回上级", (d, w) -> showLearningMenu()).show();
    }

    

    /** 商城管理对话框 — 编辑/下架商品 */
    private void showManageShopDialog() {
        soundHelper.playClickSound();
        java.util.List<ShopItem> allItems = db.shopItemDao().getAll();
        if (allItems.isEmpty()) {
            Toast.makeText(this, "暂无商品，请先上架", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建可滚动列表
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 16, 16, 16);
        scrollView.addView(container);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🏪 管理已有商品（点击编辑）");
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 12);
        container.addView(tvTitle);

        for (ShopItem item : allItems) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(8, 10, 8, 10);
            android.widget.LinearLayout.LayoutParams rowParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 2, 0, 2);
            row.setLayoutParams(rowParams);
            row.setBackgroundColor(item.active ? 0xFFF5F5F5 : 0xFFFFF0F0);

            TextView tvItem = new TextView(this);
            String status = item.active ? "" : " [已下架]";
            tvItem.setText((item.active ? "🟢 " : "🔴 ") + item.name + status + "  🪙" + item.priceCoins);
            tvItem.setTextSize(13);
            android.widget.LinearLayout.LayoutParams tvParams = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvItem.setLayoutParams(tvParams);
            row.addView(tvItem);

            // 编辑按钮
            Button btnEdit = new Button(this);
            btnEdit.setText("✏️");
            btnEdit.setTextSize(11);
            btnEdit.setOnClickListener(v -> showEditShopItemDialog(item));
            row.addView(btnEdit);

            // 下架/上架按钮
            Button btnToggle = new Button(this);
            btnToggle.setText(item.active ? "⬇" : "⬆");
            btnToggle.setTextSize(11);
            btnToggle.setOnClickListener(v -> {
                item.active = !item.active;
                db.shopItemDao().update(item);
                Toast.makeText(this, (item.active ? "✅ 已上架: " : "⬇ 已下架: ") + item.name, Toast.LENGTH_SHORT).show();
                // 关闭当前对话框，重新打开
                if (v.getRootView() != null) {
                    ((ViewGroup) v.getRootView()).removeAllViews();
                }
                showManageShopDialog();
            });
            row.addView(btnToggle);

            container.addView(row);
        }

        new AlertDialog.Builder(this)
                .setTitle("🏪 商城管理")
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .show();
    }

    /** 编辑商品 */
    private void showEditShopItemDialog(ShopItem item) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_shop_item, null);
        android.widget.EditText etName = view.findViewById(R.id.et_item_name);
        android.widget.EditText etDesc = view.findViewById(R.id.et_item_desc);
        android.widget.EditText etPrice = view.findViewById(R.id.et_item_price);
        android.widget.EditText etCategory = view.findViewById(R.id.et_item_category);
        Button btnPickImage = view.findViewById(R.id.btn_pick_image);
        ImageView ivPreview = view.findViewById(R.id.iv_image_preview);
        TextView tvImageName = view.findViewById(R.id.tv_image_name);

        etName.setText(item.name);
        etDesc.setText(item.description);
        etPrice.setText(String.valueOf(item.priceCoins));
        etCategory.setText(item.category);
        selectedShopImagePath = item.iconUrl;

        if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
            ivPreview.setVisibility(View.VISIBLE);
            tvImageName.setVisibility(View.VISIBLE);
            tvImageName.setText("当前图片: " + item.iconUrl.substring(Math.max(0, item.iconUrl.length() - 30)));
            Glide.with(this).load(new java.io.File(item.iconUrl)).into(ivPreview);
        }

        btnPickImage.setOnClickListener(v -> {
            currentShopDialogView = view;
            pickShopImageLauncher.launch("image/*");
        });

        new AlertDialog.Builder(this)
                .setTitle("✏️ 编辑商品")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    item.name = etName.getText().toString();
                    item.description = etDesc.getText().toString();
                    try { item.priceCoins = Integer.parseInt(etPrice.getText().toString()); }
                    catch (Exception e) { item.priceCoins = 50; }
                    item.category = etCategory.getText().toString();
                    if (selectedShopImagePath != null) item.iconUrl = selectedShopImagePath;
                    db.shopItemDao().update(item);
                    currentShopDialogView = null;
                    selectedShopImagePath = null;
                    Toast.makeText(this, "✅ 商品已更新", Toast.LENGTH_SHORT).show();
                    showManageShopDialog();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 审批适配器
    private static class ApprovalAdapter extends RecyclerView.Adapter<ApprovalAdapter.ViewHolder> {
        private final List<Redemption> items;
        private final OnApprovalListener listener;

        interface OnApprovalListener { void onApprove(Redemption item, boolean approved); }

        ApprovalAdapter(List<Redemption> items, OnApprovalListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Redemption item = items.get(position);
            holder.textView.setText("🪙 " + item.itemName + "  (" + item.coinsCost + "金币)");
            holder.itemView.setOnClickListener(v -> {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("审批兑换申请")
                        .setMessage("兑换: " + item.itemName + "\n消耗: " + item.coinsCost + " 金币\n申请时间: " +
                                new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(item.requestedAt)))
                        .setPositiveButton("✅ 确认", (d, w) -> listener.onApprove(item, true))
                        .setNegativeButton("❌ 拒绝", (d, w) -> listener.onApprove(item, false))
                        .setNeutralButton("稍后", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView textView;
            ViewHolder(View v) { super(v); textView = v.findViewById(android.R.id.text1); }
        }
    }

    // ==================== 数据导出备份 ====================
    private void showBackupRestoreDialog() {
        String[] items = {
            "📤 导出加密备份（AES-256-GCM）",
            "📥 从备份文件恢复",
            "📂 查看已有备份文件"
        };
        new AlertDialog.Builder(this)
                .setTitle("🔐 数据备份与恢复")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: doExportBackup(); break;
                        case 1: doImportBackup(); break;
                        case 2: listBackupFiles(); break;
                    }
                })
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu())
                .show();
    }

    private void doExportBackup() {
        android.widget.EditText etPwd = new android.widget.EditText(this);
        etPwd.setHint("设置备份密码（用于加密保护）");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("📤 导出加密备份")
                .setMessage("将导出所有数据为 .habitbak 加密备份文件\n保存到 /Download 目录")
                .setView(etPwd)
                .setPositiveButton("导出", (d, w) -> {
                    String pwd = etPwd.getText().toString();
                    if (pwd.length() < 4) { Toast.makeText(this, "密码至少4位", Toast.LENGTH_SHORT).show(); return; }
                    new Thread(() -> {
                        try {
                            com.sister.habits.utils.BackupExportHelper helper = new com.sister.habits.utils.BackupExportHelper(this);
                            java.io.File f = helper.exportBackup(pwd);
                            runOnUiThread(() -> Toast.makeText(this, "✅ 备份成功: " + f.getName(), Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "❌ 备份失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doImportBackup() {
        java.io.File[] backups = com.sister.habits.utils.BackupExportHelper.findBackupFiles();
        if (backups == null || backups.length == 0) {
            Toast.makeText(this, "❌ 未在 Download 目录找到 .habitbak 备份文件", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[backups.length];
        for (int i = 0; i < backups.length; i++) {
            names[i] = backups[i].getName();
        }
        final java.io.File[] finalBackups = backups;
        new AlertDialog.Builder(this)
                .setTitle("📥 选择备份文件恢复")
                .setItems(names, (d, which) -> {
                    java.io.File selected = finalBackups[which];
                    android.widget.EditText etPwd = new android.widget.EditText(this);
                    etPwd.setHint("输入备份密码");
                    etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    new AlertDialog.Builder(this)
                            .setTitle("恢复: " + selected.getName())
                            .setMessage("⚠️ 恢复将覆盖当前所有数据！确定继续？")
                            .setView(etPwd)
                            .setPositiveButton("恢复", (d2, w2) -> {
                                String pwd = etPwd.getText().toString();
                                new Thread(() -> {
                                    try {
                                        com.sister.habits.utils.BackupExportHelper helper = new com.sister.habits.utils.BackupExportHelper(this);
                                        helper.importBackup(selected, pwd);
                                        runOnUiThread(() -> {
                                            Toast.makeText(this, "✅ 恢复成功！请重启App", Toast.LENGTH_LONG).show();
                                            refreshAll();
                                        });
                                    } catch (Exception e) {
                                        runOnUiThread(() -> Toast.makeText(this, "❌ 恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                    }
                                }).start();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void listBackupFiles() {
        // Android 10+ 需要运行时权限读公共目录
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 200);
                Toast.makeText(this, "请先授予存储读取权限再试", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        java.io.File[] backups = com.sister.habits.utils.BackupExportHelper.findBackupFiles();
        if (backups == null || backups.length == 0) {
            Toast.makeText(this, "📂 Download 目录下暂无 .habitbak 备份文件", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder("📂 找到 " + backups.length + " 个备份文件:\n\n");
        for (java.io.File f : backups) {
            sb.append("• ").append(f.getName()).append("\n  (").append(f.length() / 1024).append(" KB)\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("📂 已有备份")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    // ==================== 设备同步 & QR配对 ====================
    private void showSyncDashboardDialog() {
        String deviceKey = com.sister.habits.utils.DeviceIdentity.getDeviceKey(this);
        String shortKey = deviceKey != null && deviceKey.length() >= 19 ? deviceKey.substring(0, 19) : deviceKey;
        String nickname = profile.getNickname();
        String model = android.os.Build.MODEL;

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.setFillViewport(false);

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);

        // ========== 设备信息 ==========
        TextView tvDeviceInfo = new TextView(this);
        tvDeviceInfo.setText("👤 " + nickname + " (" + model + ")\n🆔 " + (shortKey != null ? shortKey : "未知"));
        tvDeviceInfo.setTextSize(14);
        tvDeviceInfo.setTextColor(0xFF666666);
        tvDeviceInfo.setPadding(0, 0, 0, 8);
        container.addView(tvDeviceInfo);

        // ========== 实时状态显示 ==========
        TextView tvStatus = new TextView(this);
        tvStatus.setId(android.view.View.generateViewId());
        tvStatus.setText("⏳ 准备就绪");
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(0xFF1976D2);
        tvStatus.setPadding(8, 8, 8, 8);
        android.graphics.drawable.GradientDrawable statusBg = new android.graphics.drawable.GradientDrawable();
        statusBg.setCornerRadius(8);
        statusBg.setColor(0xFFE3F2FD);
        tvStatus.setBackground(statusBg);
        android.widget.LinearLayout.LayoutParams statusLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.setMargins(0, 0, 0, 12);
        tvStatus.setLayoutParams(statusLp);
        container.addView(tvStatus);

        // ========== 设备列表区 ==========
        TextView tvSectionLabel = new TextView(this);
        tvSectionLabel.setId(android.view.View.generateViewId());
        tvSectionLabel.setText("📱 发现的设备");
        tvSectionLabel.setTextSize(13);
        tvSectionLabel.setTextColor(0xFF999999);
        tvSectionLabel.setPadding(0, 0, 0, 4);
        container.addView(tvSectionLabel);

        android.widget.LinearLayout deviceListContainer = new android.widget.LinearLayout(this);
        deviceListContainer.setId(android.view.View.generateViewId());
        deviceListContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        deviceListContainer.setPadding(0, 0, 0, 12);
        container.addView(deviceListContainer);

        // 初始提示
        TextView tvEmpty = new TextView(this);
        tvEmpty.setId(android.view.View.generateViewId());
        tvEmpty.setText("尚未扫描网络，点击下方「扫描网络」按钮");
        tvEmpty.setTextSize(13);
        tvEmpty.setTextColor(0xFFBBBBBB);
        tvEmpty.setPadding(8, 4, 8, 4);
        deviceListContainer.addView(tvEmpty);

        // 分隔线
        View divider = new View(this);
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0E0E0);
        container.addView(divider);

        // ========== 操作按钮（6个） ==========
        String[] labels = {
            "📷 扫描设备QR码配对",
            "📱 展示本机QR码",
            "🔍 扫描网络设备",
            "🏠 切换Hub中枢",
            "🔄 开始同步",
            "🧹 清除发现缓存"
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button btn = new Button(this);
            // 标签动态更新（Hub状态）
            String btnText = labels[i];
            if (i == 3) {
                boolean hubOn = syncManager.isHubModeEnabled();
                btnText = "🏠 " + (hubOn ? "关闭" : "开启") + " Hub中枢 (" + syncManager.getHubSync().getServerStatus() + ")";
            }
            btn.setText(btnText);
            btn.setTextSize(14);
            btn.setAllCaps(false);
            btn.setTextColor(0xFFFFFFFF);
            android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
            btnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            btnBg.setCornerRadius(8);
            btnBg.setColor(i == 4 ? 0xFF43A047 : 0xFF1976D2);
            btn.setBackground(btnBg);
            int dp24 = (int) (24 * getResources().getDisplayMetrics().density + 0.5f);
            int dp48 = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
            btn.setPadding(dp24, 0, dp24, 0);
            btn.setMinHeight(dp48);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = 8;
            btn.setLayoutParams(lp);

            final Button finalBtn = btn;
            btn.setOnClickListener(v -> {
                switch (idx) {
                    case 0: { // QR扫描配对
                        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
                        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
                        options.setPrompt("扫描对方设备的配对码");
                        options.setBeepEnabled(true);
                        options.setOrientationLocked(true);
                        qrScanLauncher.launch(options);
                        break;
                    }
                    case 1: { // 展示本机QR码
                        String qrContent = com.sister.habits.utils.QRCodeHelper.buildDeviceQrContent(ParentActivity.this);
                        android.graphics.Bitmap qrBitmap = com.sister.habits.utils.QRCodeHelper.generateQrBitmap(qrContent);
                        if (qrBitmap != null) {
                            android.widget.ImageView iv = new android.widget.ImageView(ParentActivity.this);
                            iv.setImageBitmap(qrBitmap);
                            iv.setPadding(32, 32, 32, 32);
                            new AlertDialog.Builder(ParentActivity.this)
                                    .setTitle("📱 本机配对码")
                                    .setMessage("让对方扫描此码进行配对\n昵称: " + nickname + " | 设备: " + model)
                                    .setView(iv)
                                    .setPositiveButton("关闭", null)
                                    .show();
                        } else {
                            Toast.makeText(ParentActivity.this, "❌ QR码生成失败", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    }
                    case 2: { // 扫描网络
                        tvStatus.setText("🔍 正在扫描局域网...");
                        tvStatus.setTextColor(0xFF1976D2);
                        deviceListContainer.removeAllViews();
                        syncManager.getHubSync().clearDiscoveredHubs();
                        syncManager.getHubSync().scanNetwork(150, new com.sister.habits.sync.SyncCallback() {
                            @Override
                            public void onStatusUpdate(String status) {
                                runOnUiThread(() -> tvStatus.setText(status));
                            }
                            @Override
                            public void onHubFound(String ip, String deviceId) {
                                runOnUiThread(() -> {
                                    // 移除空提示
                                    if (tvEmpty.getParent() != null) deviceListContainer.removeView(tvEmpty);
                                    String showId = deviceId != null && deviceId.length() > 8 ? deviceId.substring(0, 8) + "..." : "未知";
                                    TextView tvHub = new TextView(ParentActivity.this);
                                    tvHub.setText("🟢 " + ip + " (设备: " + showId + ")");
                                    tvHub.setTextSize(13);
                                    tvHub.setTextColor(0xFF333333);
                                    tvHub.setPadding(8, 4, 8, 4);
                                    deviceListContainer.addView(tvHub);
                                });
                            }
                            @Override
                            public void onSyncComplete(boolean success, String message) {
                                runOnUiThread(() -> {
                                    tvStatus.setText((success ? "✅ " : "❌ ") + message);
                                    tvStatus.setTextColor(success ? 0xFF43A047 : 0xFFE53935);
                                });
                            }
                            @Override
                            public void onScanProgress(int scanned, int total) {
                                runOnUiThread(() -> tvStatus.setText("🔍 扫描中... " + scanned + "/" + total));
                            }
                        });
                        break;
                    }
                    case 3: { // 切换Hub中枢
                        boolean newState = !syncManager.isHubModeEnabled();
                        syncManager.setHubModeEnabled(newState);
                        // 更新按钮文字
                        String serverStatus = syncManager.getHubSync().getServerStatus();
                        finalBtn.setText("🏠 " + (newState ? "关闭" : "开启") + " Hub中枢 (" + serverStatus + ")");
                        tvStatus.setText((newState ? "🟢 " : "🔴 ") + "Hub服务器: " + serverStatus);
                        tvStatus.setTextColor(newState ? 0xFF43A047 : 0xFF666666);
                        break;
                    }
                    case 4: { // 开始同步
                        tvStatus.setText("🔄 正在同步...");
                        tvStatus.setTextColor(0xFF1976D2);
                        syncManager.getHubSync().syncToHub(new com.sister.habits.sync.SyncCallback() {
                            @Override
                            public void onStatusUpdate(String status) {
                                runOnUiThread(() -> tvStatus.setText(status));
                            }
                            @Override
                            public void onHubFound(String ip, String deviceId) {}
                            @Override
                            public void onSyncComplete(boolean success, String message) {
                                runOnUiThread(() -> {
                                    if (success) {
                                        tvStatus.setText("✅ " + message);
                                        tvStatus.setTextColor(0xFF43A047);
                                        // 同时尝试局域网P2P同步
                                        syncManager.triggerLanSync();
                                    } else {
                                        tvStatus.setText("❌ " + message);
                                        tvStatus.setTextColor(0xFFE53935);
                                        // 降级到局域网P2P
                                        tvStatus.setText(tvStatus.getText() + "\n📡 尝试局域网P2P同步...");
                                        syncManager.triggerLanSync();
                                    }
                                });
                            }
                            @Override
                            public void onScanProgress(int scanned, int total) {}
                        });
                        break;
                    }
                    case 5: { // 清除发现缓存
                        syncManager.getHubSync().clearDiscoveredHubs();
                        deviceListContainer.removeAllViews();
                        deviceListContainer.addView(tvEmpty);
                        tvStatus.setText("🧹 已清除缓存，下次将重新扫描");
                        tvStatus.setTextColor(0xFF666666);
                        break;
                    }
                }
            });
            container.addView(btn);
        }

        // ========== 上次同步信息 ==========
        TextView tvLastSync = new TextView(this);
        tvLastSync.setId(android.view.View.generateViewId());
        tvLastSync.setText("📋 " + syncManager.getHubSync().getLastSyncInfo());
        tvLastSync.setTextSize(12);
        tvLastSync.setTextColor(0xFF999999);
        tvLastSync.setPadding(0, 12, 0, 0);
        container.addView(tvLastSync);

        scrollView.addView(container);

        new AlertDialog.Builder(this)
                .setTitle("📡 设备同步中心")
                .setView(scrollView)
                .setNegativeButton("← 返回上级", (d, w) -> showSystemMenu())
                .show();
    }
}
