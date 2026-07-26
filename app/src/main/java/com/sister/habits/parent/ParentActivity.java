package com.sister.habits.parent;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.EconomyConfig;
import com.sister.habits.data.models.Redemption;
import com.sister.habits.data.models.ShopItem;
import com.sister.habits.data.models.Task;
import com.sister.habits.sync.SyncManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 家长模式——统一管理界面
 * 看板/审批兑换/发布任务/管理商城/调整参数
 */
public class ParentActivity extends AppCompatActivity {

    private AppDatabase db;
    private SyncManager syncManager;

    private TextView tvStats;
    private RecyclerView rvPendingApprovals;
    private Button btnAddTask, btnAddShopItem, btnSettings, btnSync, btnRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        db = AppDatabase.getInstance(this);
        syncManager = SyncManager.getInstance(this);

        tvStats = findViewById(R.id.tv_parent_stats);
        rvPendingApprovals = findViewById(R.id.rv_pending_approvals);
        btnAddTask = findViewById(R.id.btn_add_task);
        btnAddShopItem = findViewById(R.id.btn_add_shop_item);
        btnSettings = findViewById(R.id.btn_settings);
        btnSync = findViewById(R.id.btn_sync);
        btnRefresh = findViewById(R.id.btn_refresh);

        rvPendingApprovals.setLayoutManager(new LinearLayoutManager(this));

        btnAddTask.setOnClickListener(v -> showAddTaskDialog());
        btnAddShopItem.setOnClickListener(v -> showAddShopItemDialog());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        btnSync.setOnClickListener(v -> { syncManager.triggerRemoteSync(); syncManager.triggerLanSync(); Toast.makeText(this, "同步已触发", Toast.LENGTH_SHORT).show(); });
        btnRefresh.setOnClickListener(v -> refreshAll());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void refreshAll() {
        refreshStats();
        loadPendingApprovals();
    }

    private void refreshStats() {
        int totalCheckIns = db.checkInDao().getTotalCheckIns("sister");
        int maxStreak = db.checkInDao().getMaxStreak("sister");
        int pendingCount = db.redemptionDao().getByStatus("pending").size();
        Integer balance = db.coinTransactionDao().getBalance("sister");

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        boolean checkedInToday = db.checkInDao().getByDate("sister", today) != null;

        tvStats.setText(
                "📊 妹妹的习惯数据\n" +
                "━━━━━━━━━━━━━━━\n" +
                "今日打卡: " + (checkedInToday ? "✅ 已打卡" : "⭕ 未打卡") + "\n" +
                "总打卡: " + totalCheckIns + " 天\n" +
                "最长连续: " + maxStreak + " 天 🏆\n" +
                "金币余额: 🪙 " + (balance != null ? balance : 0) + "\n" +
                "待审批兑换: " + pendingCount + " 项"
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

    private void showAddTaskDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        android.widget.EditText etTitle = view.findViewById(R.id.et_task_title);
        android.widget.EditText etDesc = view.findViewById(R.id.et_task_desc);
        android.widget.EditText etReward = view.findViewById(R.id.et_task_reward);

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
                    task.deviceId = syncManager.getDeviceId();
                    db.taskDao().insert(task);
                    syncManager.onDataChanged();
                    Toast.makeText(this, "任务已发布 🎯", Toast.LENGTH_SHORT).show();
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
                    db.shopItemDao().insert(item);
                    Toast.makeText(this, "商品已上架 🏪", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSettingsDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) { config = new EconomyConfig(); db.economyConfigDao().setConfig(config); }

        // 初始化Hub模式开关状态
        androidx.appcompat.widget.SwitchCompat switchHub = view.findViewById(R.id.switch_hub_mode);
        switchHub.setChecked(syncManager.isHubModeEnabled());
        switchHub.setOnCheckedChangeListener((buttonView, isChecked) -> {
            syncManager.setHubModeEnabled(isChecked);
            Toast.makeText(this, isChecked ? "🏠 家庭中枢模式已开启" : "🏠 家庭中枢模式已关闭",
                    Toast.LENGTH_SHORT).show();
        });

        EconomyConfig finalConfig = config;
        new AlertDialog.Builder(this)
                .setTitle("⚙️ 设置")
                .setView(view)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        android.widget.EditText etBaseReward = view.findViewById(R.id.et_base_reward);
                        android.widget.EditText etStreak7 = view.findViewById(R.id.et_streak7);
                        android.widget.EditText etMaxDaily = view.findViewById(R.id.et_max_daily);
                        db.economyConfigDao().updateAll(
                                parseInt(etBaseReward, 10),
                                finalConfig.streak3Bonus,
                                parseInt(etStreak7, 50),
                                finalConfig.streak14Bonus,
                                finalConfig.streak30Bonus,
                                finalConfig.wordLearnReward,
                                finalConfig.wordBatchBonus10,
                                finalConfig.wordBatchBonus20,
                                finalConfig.taskDailyMin,
                                finalConfig.taskDailyMax,
                                finalConfig.taskChallengeMin,
                                finalConfig.taskChallengeMax,
                                parseInt(etMaxDaily, 500),
                                finalConfig.maxDailyWords
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