package com.sister.habits.parent;

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
import com.sister.habits.sync.SyncManager;
import com.sister.habits.utils.SoundHelper;
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
                    // 复制到App内部存储
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    String fileName = "shop_" + System.currentTimeMillis() + ".jpg";
                    java.io.File outFile = new java.io.File(getFilesDir(), "shop_images/" + fileName);
                    outFile.getParentFile().mkdirs();
                    java.io.OutputStream os = new java.io.FileOutputStream(outFile);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        os.write(buffer, 0, len);
                    }
                    os.close();
                    is.close();
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

    private static class JsonImportWord {
        String g; String c; String w; String m; String p; int l;
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
        progress.setMessage("正在下载: " + source.name);
        progress.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(source.url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                java.io.InputStream is = conn.getInputStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
                is.close();
                String json = baos.toString("UTF-8");

                // 自动解析
                String grade = source.gradeLabel != null ? source.gradeLabel : "external";
                java.util.List<com.sister.habits.data.models.Vocabulary> words = com.sister.habits.utils.WordBankParser.parse(json, grade);

                runOnUiThread(() -> {
                    progress.dismiss();
                    if (words.isEmpty()) {
                        Toast.makeText(this, "❌ 词库为空或格式不兼容", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // 构建预览消息
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
                                    "格式: " + source.format.toUpperCase() + "（自动兼容）\n\n" +
                                    "📝 示例:\n" + samples.toString() + "\n" +
                                    "💡 下载后学习进度独立保存，不影响现有词库进度。")
                            .setPositiveButton("✅ 确认使用", (d, w) -> applyExternalWordbank(words, source))
                            .setNegativeButton("取消", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(this, "❌ 下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        int maxStreak = db.checkInDao().getMaxStreak("sister");
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        int pendingTaskCount = db.taskDao().getByStatus("pending").size();
        Integer balance = db.coinTransactionDao().getBalance("sister");

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        boolean checkedInToday = db.checkInDao().getByDate("sister", today) != null;

        String nickname = profile.getNickname();
        tvStats.setText(
                "📊 " + nickname + "的习惯数据\n" +
                "━━━━━━━━━━━━━━━\n" +
                "今日打卡: " + (checkedInToday ? "✅ 已打卡" : "⭕ 未打卡") + "\n" +
                "总打卡: " + totalCheckIns + " 天\n" +
                "最长连续: " + maxStreak + " 天 🏆\n" +
                "金币余额: 🪙 " + (balance != null ? balance : 0) + "\n" +
                "待审批兑换: " + pendingCount + " 项\n" +
                "待确认任务: " + pendingTaskCount + " 项"
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
            tilDeadline.setVisibility(checkedId == R.id.rb_type_timed ? View.VISIBLE : View.GONE);
        });

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

    private void showSettingsDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }

        // 初始化默认模式选择
        SharedPreferences prefs = getSharedPreferences("parent_prefs", MODE_PRIVATE);
        String currentMode = prefs.getString("default_mode", "child");
        RadioGroup rgMode = view.findViewById(R.id.rg_default_mode);
        if ("child".equals(currentMode)) rgMode.check(R.id.rb_mode_child);
        else if ("parent".equals(currentMode)) rgMode.check(R.id.rb_mode_parent);
        else rgMode.check(R.id.rb_mode_ask);

        // 初始化Hub模式开关状态
        androidx.appcompat.widget.SwitchCompat switchHub = view.findViewById(R.id.switch_hub_mode);
        switchHub.setChecked(syncManager.isHubModeEnabled());
        switchHub.setOnCheckedChangeListener((buttonView, isChecked) -> {
            syncManager.setHubModeEnabled(isChecked);
            Toast.makeText(this, isChecked ? "🏠 家庭中枢模式已开启" : "🏠 家庭中枢模式已关闭",
                    Toast.LENGTH_SHORT).show();
        });

        EconomyConfig finalConfig = config;

        // 初始化单词参数输入框
        android.widget.EditText etWordReward = view.findViewById(R.id.et_word_reward);
        android.widget.EditText etMaxWords = view.findViewById(R.id.et_max_words);
        android.widget.EditText etMaxReview = view.findViewById(R.id.et_max_review);
        etWordReward.setText(String.valueOf(finalConfig.wordLearnReward));
        etMaxWords.setText(String.valueOf(finalConfig.maxDailyWords));
        etMaxReview.setText(String.valueOf(finalConfig.maxDailyReview));

        // 词库管理按钮
        Button btnWordbank = view.findViewById(R.id.btn_wordbank_mgr);
        btnWordbank.setOnClickListener(v -> showWordbankDialog());

        // 商城管理按钮
        Button btnManageShop = view.findViewById(R.id.btn_manage_shop);
        btnManageShop.setOnClickListener(v -> showManageShopDialog());

        // ===== 个人信息字段 =====
        android.widget.EditText etNickname = view.findViewById(R.id.et_nickname);
        android.widget.EditText etAppTitle = view.findViewById(R.id.et_app_title);
        Button btnPickAvatar = view.findViewById(R.id.btn_pick_avatar);

        etNickname.setText(profile.getNickname());
        etAppTitle.setText(profile.getAppTitle());

        btnPickAvatar.setOnClickListener(v -> {
            soundHelper.playClickSound();
            pickShopImageLauncher.launch("image/*");  // 复用商店图片选择器
        });

        new AlertDialog.Builder(this)
                .setTitle("⚙️ 设置")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        // 保存个人信息
                        String newNickname = etNickname.getText().toString().trim();
                        if (!newNickname.isEmpty()) {
                            profile.setNickname(newNickname);
                        }
                        String newTitle = etAppTitle.getText().toString().trim();
                        if (!newTitle.isEmpty()) {
                            profile.setAppTitle(newTitle);
                        }
                        // 保存默认模式
                        int checkedId = rgMode.getCheckedRadioButtonId();
                        String mode = "child";
                        if (checkedId == R.id.rb_mode_child) mode = "child";
                        else if (checkedId == R.id.rb_mode_parent) mode = "parent";
                        else if (checkedId == R.id.rb_mode_ask) mode = "ask";
                        prefs.edit().putString("default_mode", mode).apply();

                        // 保存经济参数
                        android.widget.EditText etBaseReward = view.findViewById(R.id.et_base_reward);
                        android.widget.EditText etStreak7 = view.findViewById(R.id.et_streak7);
                        android.widget.EditText etMaxDaily = view.findViewById(R.id.et_max_daily);
                        db.economyConfigDao().updateAll(
                                parseInt(etBaseReward, 10),
                                finalConfig.streak3Bonus,
                                parseInt(etStreak7, 50),
                                finalConfig.streak14Bonus,
                                finalConfig.streak30Bonus,
                                parseInt(etWordReward, 2),
                                finalConfig.wordBatchBonus10,
                                finalConfig.wordBatchBonus20,
                                finalConfig.taskDailyMin,
                                finalConfig.taskDailyMax,
                                finalConfig.taskChallengeMin,
                                finalConfig.taskChallengeMax,
                                parseInt(etMaxDaily, 500),
                                parseInt(etMaxWords, 10),
                                parseInt(etMaxReview, 30)
                        );
                        Toast.makeText(this, "参数已更新 ✅", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "参数格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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

        // 恢复年级选择状态
        ((CheckBox) view.findViewById(R.id.cb_grade1)).setChecked(prefs.getBoolean("grade1", true));
        ((CheckBox) view.findViewById(R.id.cb_grade2)).setChecked(prefs.getBoolean("grade2", true));
        ((CheckBox) view.findViewById(R.id.cb_grade3)).setChecked(prefs.getBoolean("grade3", true));
        ((CheckBox) view.findViewById(R.id.cb_grade4)).setChecked(prefs.getBoolean("grade4", false));
        ((CheckBox) view.findViewById(R.id.cb_grade5)).setChecked(prefs.getBoolean("grade5", false));
        ((CheckBox) view.findViewById(R.id.cb_grade_junior)).setChecked(prefs.getBoolean("grade_junior", false));

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
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            String json = new String(buf, "UTF-8");
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
                tvTag.setTextColor(0xFF6B35);
                tvTag.setBackgroundResource(R.drawable.card_background);
                tvTag.setPadding(6, 2, 6, 2);

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

                // 下载按钮
                Button btnDownload = new Button(this);
                btnDownload.setText("📥 下载并预览");
                btnDownload.setTextSize(13);
                btnDownload.setAllCaps(false);
                btnDownload.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        36));
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
                    // 保存年级选择
                    boolean g1 = ((CheckBox) view.findViewById(R.id.cb_grade1)).isChecked();
                    boolean g2 = ((CheckBox) view.findViewById(R.id.cb_grade2)).isChecked();
                    boolean g3 = ((CheckBox) view.findViewById(R.id.cb_grade3)).isChecked();
                    boolean g4 = ((CheckBox) view.findViewById(R.id.cb_grade4)).isChecked();
                    boolean g5 = ((CheckBox) view.findViewById(R.id.cb_grade5)).isChecked();
                    boolean gj = ((CheckBox) view.findViewById(R.id.cb_grade_junior)).isChecked();
                    prefs.edit()
                            .putBoolean("grade1", g1)
                            .putBoolean("grade2", g2)
                            .putBoolean("grade3", g3)
                            .putBoolean("grade4", g4)
                            .putBoolean("grade5", g5)
                            .putBoolean("grade_junior", gj)
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
                .setNegativeButton("取消", null)
                .show();
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
}