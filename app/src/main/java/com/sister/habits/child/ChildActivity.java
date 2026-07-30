package com.sister.habits.child;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.DailyQuote;
import com.sister.habits.data.models.CheckIn;
import com.sister.habits.data.models.CoinTransaction;
import com.sister.habits.data.models.EconomyConfig;
import com.sister.habits.data.models.WordReview;
import com.sister.habits.sync.SyncManager;
import com.sister.habits.sync.EarningService;
import com.sister.habits.sync.AcceleratorService;
import com.sister.habits.utils.SoundHelper;
import com.sister.habits.utils.BindKeyManager;
import com.sister.habits.data.models.DailyGate;
import com.sister.habits.data.models.LaundryTask;
import com.sister.habits.data.models.LotteryPrize;
import com.sister.habits.data.models.LotteryRecord;
import com.sister.habits.data.models.SchoolReward;
import com.sister.habits.utils.GateHelper;
import com.sister.habits.utils.NotificationHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 孩子模式主界面
 * 使用 ViewPager2 + TabLayout 做底部导航
 * 包含：打卡 | 金币 | 商城 | 任务 | 单词
 */
public class ChildActivity extends AppCompatActivity {

        private AppDatabase db;
    private SyncManager syncManager;
    private SoundHelper soundHelper;
    private TextView tvCoinBalance;
    private TextView tvEarningEstimate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child);

                db = AppDatabase.getInstance(this);
        syncManager = SyncManager.getInstance(this);
        soundHelper = SoundHelper.getInstance(this);

        tvCoinBalance = findViewById(R.id.tv_coin_balance);
        tvEarningEstimate = findViewById(R.id.tv_earning_estimate);
        Button btnCheckIn = findViewById(R.id.btn_check_in);
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        // 显示每日一句
        TextView tvQuote = findViewById(R.id.tv_daily_quote);
        tvQuote.setText("💬 " + DailyQuote.getTodayQuote());

        // 儿童设置齿轮⚙️（不显眼，在右上角）
        TextView btnSettings = findViewById(R.id.btn_child_settings);
        btnSettings.setOnClickListener(v -> showChildSettings());

        // 📝 作业提交按钮
        TextView btnHomework = findViewById(R.id.btn_submit_homework);
        btnHomework.setOnClickListener(v -> submitTodayHomework());

        // 🧺 洗衣任务按钮
        TextView btnLaundry = findViewById(R.id.btn_laundry);
        btnLaundry.setOnClickListener(v -> showLaundryDialog());

        // 🎰 抽奖按钮
        TextView btnLottery = findViewById(R.id.btn_lottery);
        btnLottery.setOnClickListener(v -> showLotteryDialog());

        // 刷新金币余额
        refreshCoinBalance();

        // 打卡按钮
        btnCheckIn.setOnClickListener(v -> performCheckIn());

        // ViewPager + TabLayout 设置
        ChildPagerAdapter adapter = new ChildPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("🏪 商城"); break;
                case 1: tab.setText("📋 任务"); break;
                case 2: tab.setText("📖 单词"); break;
            }
        }).attach();

        // Tab 点击震动反馈
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            private boolean firstSelect = true;
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (firstSelect) { firstSelect = false; return; } // 跳过 attach 触发的那次
                soundHelper.playTabClickSound();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                soundHelper.playTabClickSound();
            }
        });
    }

        public void refreshCoinBalance() {
        Integer balance = db.coinTransactionDao().getBalance("sister");
        tvCoinBalance.setText("🪙 " + (balance != null ? balance : 0));
        refreshEarningEstimate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshEarningEstimate();
    }

    public void refreshEarningEstimate() {
        int confirmed = com.sister.habits.sync.EarningService.calculateTodayConfirmed(this);
        int pending = com.sister.habits.sync.EarningService.calculateTodayPending(this);
        int totalExpected = confirmed + pending;
        int limit = com.sister.habits.sync.EarningService.getDailySoftLimit(this);
        String boostSummary = com.sister.habits.sync.AcceleratorService.getTodayBoostSummary(this);
        String base = "📈 预计今日: " + totalExpected + "分 (已确认:" + confirmed + " + 待审:" + pending + ") | 上限:" + limit;
        if (!boostSummary.isEmpty()) {
            tvEarningEstimate.setText(base + "\n" + boostSummary);
        } else {
            tvEarningEstimate.setText(base);
        }
    }

    private void performCheckIn() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        String deviceId = syncManager.getDeviceId();

        // 检查今天是否已经打卡
        CheckIn existing = db.checkInDao().getByDate("sister", today);
        if (existing != null) {
            Toast.makeText(this, "今天已经打卡啦！连续 " + existing.streakDay + " 天 👏", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取配置
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config == null) {
            config = new EconomyConfig();
            db.economyConfigDao().setConfig(config);
        }

        // 计算连续天数
        CheckIn latest = db.checkInDao().getLatest("sister");
        int streakDay = 1;
        if (latest != null) {
            // 检查是否是昨天
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                Date latestDate = sdf.parse(latest.date);
                Date yesterday = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
                if (sdf.format(latestDate).equals(sdf.format(yesterday))) {
                    streakDay = latest.streakDay + 1;
                }
            } catch (Exception e) {
                Log.e("ChildActivity", "解析日期失败", e);
            }
        }

        // 计算金币奖励
        int coinsEarned = config.checkInBaseReward;
        if (streakDay >= 30) coinsEarned += config.streak30Bonus;
        else if (streakDay >= 14) coinsEarned += config.streak14Bonus;
        else if (streakDay >= 7) coinsEarned += config.streak7Bonus;
        else if (streakDay >= 3) coinsEarned += config.streak3Bonus;

        // 创建打卡记录
        CheckIn checkIn = new CheckIn("sister", today, streakDay, coinsEarned, deviceId);
        db.checkInDao().insert(checkIn);

        // 记录金币流水
        Integer currentBalance = db.coinTransactionDao().getBalance("sister");
        int newBalance = (currentBalance != null ? currentBalance : 0) + coinsEarned;
        CoinTransaction transaction = new CoinTransaction("sister", coinsEarned,
                newBalance, "check_in",
                "打卡签到 连续" + streakDay + "天", deviceId);
        db.coinTransactionDao().insert(transaction);

        // 刷新余额
        refreshCoinBalance();

        // 触发同步
        syncManager.onDataChanged();

        // 打卡成功音效 + 庆祝震动
        soundHelper.playCheckInSound();

        // 显示动画效果
        String msg = "🎉 打卡成功！+ " + coinsEarned + " 金币\n连续打卡 " + streakDay + " 天";
        if (streakDay == 7) msg += "\n🌟 恭喜获得连续7天奖励！";
        else if (streakDay == 30) msg += "\n🏆 太棒了！连续一个月！";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void showChildSettings() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("⚙️ 设置")
                .setItems(new String[]{
                        "👤 查看我的统计",
                        "🔊 朗读速度(正常/慢速)",
                        "📖 今日单词进度",
                        "🔑 我的Key",
                        "🔐 进入家长管理"
                }, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            int totalCheckIns = db.checkInDao().getTotalCheckIns("sister");
                            int maxStreak = db.checkInDao().getMaxStreak("sister");
                            Integer balance = db.coinTransactionDao().getBalance("sister");
                            String bankId = getSharedPreferences("wordbank_prefs", MODE_PRIVATE).getString("active_bank_id", "builtin");
                            int wordMastered = db.vocabularyDao().getMasteredCount(bankId);
                            new android.app.AlertDialog.Builder(this)
                                    .setTitle("👤 我的统计")
                                    .setMessage(
                                            "📅 总打卡: " + totalCheckIns + " 天\n" +
                                            "🏆 最长连续: " + maxStreak + " 天\n" +
                                            "🪙 金币: " + (balance != null ? balance : 0) + "\n" +
                                            "📖 学会单词: " + wordMastered + " 个")
                                    .setPositiveButton("好的", null)
                                    .show();
                            break;
                        case 1:
                            // 切换TTS速度
                            SharedPreferences prefs = getSharedPreferences("child_prefs", MODE_PRIVATE);
                            boolean isSlow = prefs.getBoolean("tts_slow", false);
                            float newSpeed = isSlow ? 1.0f : 0.5f;
                            prefs.edit().putBoolean("tts_slow", !isSlow).apply();
                            soundHelper.setTtsSpeed(newSpeed);
                            String speedText = isSlow ? "🔊 已切换为正常速度" : "🐢 已切换为慢速(0.5x)";
                            soundHelper.speakWord("Hello", newSpeed);
                            Toast.makeText(this, speedText, Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            String bankId2 = getSharedPreferences("wordbank_prefs", MODE_PRIVATE).getString("active_bank_id", "builtin");
                            int dueCount = db.wordReviewDao().getDueCount(System.currentTimeMillis(), bankId2);
                            int totalLearning = db.wordReviewDao().getTotalLearningCount(bankId2);
                            int mastered = db.wordReviewDao().getMasteredCount(WordReview.MAX_STAGE, Long.MAX_VALUE, bankId2);
                            new android.app.AlertDialog.Builder(this)
                                    .setTitle("📖 单词进度")
                                    .setMessage(
                                            "📚 待复习: " + dueCount + " 个\n" +
                                            "🔄 学习中: " + totalLearning + " 个\n" +
                                            "✅ 已掌握: " + mastered + " 个")
                                    .setPositiveButton("继续加油 💪", null)
                                    .show();
                            break;
                        case 3:
                            // 显示我的Child Key
                            String childKey = BindKeyManager.generateChildKey(this);
                            java.util.Set<String> parents = BindKeyManager.getBoundParents(this);
                            StringBuilder sb = new StringBuilder();
                            sb.append("🔑 你的Key: ").append(childKey).append("\n\n");
                            sb.append("👨‍👩‍👧 已绑定的家长:\n");
                            if (parents.isEmpty()) {
                                sb.append("（暂无）\n\n请把Key告诉家长进行绑定");
                            } else {
                                int idx = 1;
                                for (String pk : parents) {
                                    sb.append(idx).append(". ").append(pk).append("\n");
                                    idx++;
                                }
                            }
                            new android.app.AlertDialog.Builder(this)
                                .setTitle("🔑 我的Key")
                                .setMessage(sb.toString())
                                .setPositiveButton("📋 复制Key", (d2, w2) -> {
                                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    android.content.ClipData clip = android.content.ClipData.newPlainText("ChildKey", childKey);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(this, "✅ Key已复制到剪贴板", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("关闭", null)
                                .show();
                            break;
                        case 4:
                            // 进入家长管理（需要验证）
                            Intent intent = new Intent(this, com.sister.habits.MainActivity.class);
                            intent.putExtra("force_parent_mode", true);
                            startActivity(intent);
                            finish();
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }



    /** 🎰 抽奖 */
    private void showLotteryDialog() {
        List<LotteryPrize> prizes = db.lotteryDao().getEnabledPrizes();
        if (prizes.isEmpty()) {
            Toast.makeText(this, "🎰 暂无可用奖品，请联系家长添加", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 获取抽奖消耗（取第一个奖品的cost作为统一价格）
        int costPerDraw = prizes.get(0).cost;
        Integer balance = db.coinTransactionDao().getBalance("sister");
        int currentBalance = balance != null ? balance : 0;
        
        String[] items = new String[prizes.size() + 2];
        for (int i = 0; i < prizes.size(); i++) {
            LotteryPrize p = prizes.get(i);
            items[i] = p.icon + " " + p.name + " (概率" + p.probability + "%)";
        }
        items[prizes.size()] = "📋 查看抽奖记录";
        items[prizes.size() + 1] = "🏆 荣誉墙（学校奖励）";
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("🎰 积分抽奖 (余额: " + currentBalance + "分, " + costPerDraw + "分/次)")
            .setItems(items, (dialog, which) -> {
                if (which < prizes.size()) {
                    // 抽奖
                    if (currentBalance < costPerDraw) {
                        Toast.makeText(this, "积分不足！需要 " + costPerDraw + " 分", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    performLottery(prizes, costPerDraw);
                } else if (which == prizes.size()) {
                    showLotteryRecords();
                } else {
                    showHonorWall();
                }
            })
            .setNegativeButton("关闭", null)
            .show();
    }
    
    /** 执行抽奖 */
    private void performLottery(List<LotteryPrize> prizes, int cost) {
        // 加权随机：按probability权重抽取
        int totalWeight = 0;
        for (LotteryPrize p : prizes) totalWeight += p.probability;
        int rand = new java.util.Random().nextInt(totalWeight);
        int cumulative = 0;
        LotteryPrize won = prizes.get(0);
        for (LotteryPrize p : prizes) {
            cumulative += p.probability;
            if (rand < cumulative) { won = p; break; }
        }
        
        // 扣积分
        Integer balance = db.coinTransactionDao().getBalance("sister");
        int newBalance = (balance != null ? balance : 0) - cost;
        com.sister.habits.data.models.CoinTransaction tx = new com.sister.habits.data.models.CoinTransaction(
            "sister", -cost, newBalance, "lottery", "🎰抽奖 → " + won.name, syncManager.getDeviceId());
        db.coinTransactionDao().insert(tx);
        
        // 记录
        LotteryRecord record = new LotteryRecord();
        record.prizeName = won.name;
        record.prizeIcon = won.icon;
        record.cost = cost;
        record.deviceId = syncManager.getDeviceId();
        db.lotteryDao().insertRecord(record);
        
        // 库存减1
        if (won.stock > 0) {
            won.stock--;
            if (won.stock == 0) won.enabled = false;
            db.lotteryDao().updatePrize(won);
        }
        
        refreshCoinBalance();
        soundHelper.playCheckInSound();
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("🎉 恭喜中奖！")
            .setMessage(won.icon + " " + won.name + "\n\n消耗: " + cost + "分\n余额: " + newBalance + "分")
            .setPositiveButton("🎉 太棒了", null)
            .show();
    }
    
    /** 抽奖记录 */
    private void showLotteryRecords() {
        List<LotteryRecord> records = db.lotteryDao().getRecentRecords();
        if (records.isEmpty()) {
            Toast.makeText(this, "还没有抽奖记录哦~", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        for (LotteryRecord r : records) {
            sb.append(r.prizeIcon).append(" ").append(r.prizeName)
              .append(" (").append(sdf.format(new Date(r.wonAt))).append(")\n");
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("📋 抽奖记录")
            .setMessage(sb.toString())
            .setPositiveButton("好的", null)
            .show();
    }
    
    /** 🏆 荣誉墙 */
    private void showHonorWall() {
        List<SchoolReward> rewards = db.schoolRewardDao().getAll();
        int totalPoints = db.schoolRewardDao().getTotalPoints();
        if (rewards.isEmpty()) {
            Toast.makeText(this, "🏆 还没有学校奖励记录哦~", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("累计学校奖励: ").append(totalPoints).append("分\n\n");
        for (SchoolReward r : rewards) {
            sb.append(r.badge).append(" ").append(r.name)
              .append(" +" + r.points + "分")
              .append(" (").append(r.date).append(")\n");
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("🏆 荣誉墙")
            .setMessage(sb.toString())
            .setPositiveButton("继续努力 💪", null)
            .show();
    }
    /** 🧺 洗衣任务 - 选择衣物类型和件数提交 */
    private void showLaundryDialog() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        
        // 先检查是否已开启
        SharedPreferences prefs = getSharedPreferences("laundry_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("laundry_enabled", true)) {
            Toast.makeText(this, "🚫 洗衣任务暂未开启，请联系家长", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示衣物类型选择
        String[][] types = LaundryTask.CLOTHING_TYPES;
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            // 检查今天该类型是否已提交
            LaundryTask existing = db.laundryDao().getByDateAndType(today, types[i][0]);
            String mark = existing != null ? " (今日已提交)" : "";
            labels[i] = types[i][0] + " — " + types[i][1] + mark;
        }
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("🧺 洗衣任务")
                .setMessage("选择你今天洗的衣物类型：")
                .setItems(labels, (dialog, which) -> {
                    LaundryTask existing = db.laundryDao().getByDateAndType(today, types[which][0]);
                    if (existing != null) {
                        Toast.makeText(this, "今天已提交过「" + types[which][0] + "」啦！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showLaundryQuantityDialog(types[which][0], LaundryTask.getPointsForType(types[which][0]));
                })
                .setNegativeButton("关闭", null)
                .show();
    }
    
    /** 选择件数 */
    private void showLaundryQuantityDialog(String clothingType, int pointsPerItem) {
        String[] quantities = {"1件", "2件", "3件", "4件", "5件"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("🧺 " + clothingType + " (" + pointsPerItem + "分/件)")
                .setItems(quantities, (dialog, which) -> {
                    int qty = which + 1;
                    int total = pointsPerItem * qty;
                    submitLaundryTask(clothingType, qty, pointsPerItem, total);
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    /** 提交洗衣任务 */
    private void submitLaundryTask(String clothingType, int quantity, int pointsPerItem, int totalPoints) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        
        // 再次检查是否已提交
        LaundryTask existing = db.laundryDao().getByDateAndType(today, clothingType);
        if (existing != null) {
            Toast.makeText(this, "今天已提交过啦！", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 打折系统影响
        double multiplier = com.sister.habits.utils.GateHelper.getTodayMultiplier(this);
        int finalPoints = (int) Math.round(totalPoints * multiplier);
        
        LaundryTask task = new LaundryTask();
        task.date = today;
        task.clothingType = clothingType;
        task.quantity = quantity;
        task.points = pointsPerItem;
        task.totalPoints = finalPoints;
        task.deviceId = syncManager.getDeviceId();
        db.laundryDao().insert(task);
        
        String info = multiplier < 1.0 ? " (打折后: " + finalPoints + "分)" : "";
        Toast.makeText(this, "✅ 已提交！" + clothingType + "×" + quantity + " = " + finalPoints + "分" + info, Toast.LENGTH_SHORT).show();
        
        refreshEarningEstimate();
    }
    /** 📝 提交今日作业 */
    private void submitTodayHomework() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        
        // 检查是否今天已提交
        DailyGate existing = db.dailyGateDao().getByDate(today);
        if (existing != null) {
            String statusText;
            switch (existing.status) {
                case DailyGate.STATUS_PENDING: statusText = "⏳ 待审核"; break;
                case DailyGate.STATUS_COMPLETED: statusText = "✅ 已完成"; break;
                case DailyGate.STATUS_INCOMPLETE: statusText = "❌ 未完成"; break;
                case DailyGate.STATUS_AI_DETECTED: statusText = "🤖 AI作弊"; break;
                case DailyGate.STATUS_SKIPPED: statusText = "⏭️ 免检"; break;
                default: statusText = existing.status;
            }
            new android.app.AlertDialog.Builder(this)
                    .setTitle("📝 今日作业")
                    .setMessage("今日已提交作业\n状态：" + statusText + "\n\n如需重新提交请联系家长")
                    .setPositiveButton("好的", null)
                    .show();
            return;
        }
        
        // 创建新提交
        DailyGate gate = new DailyGate();
        gate.date = today;
        gate.status = DailyGate.STATUS_PENDING;
        gate.submittedAt = System.currentTimeMillis();
        gate.deviceId = syncManager.getDeviceId();
        db.dailyGateDao().insert(gate);
        
        // 通知家长
        NotificationHelper.notifyGateSubmission(this, today);
        
        // 检查是否假期模式（提示孩子）
        com.sister.habits.data.models.GateConfig gConfig = db.gateConfigDao().getConfig();
        boolean isHoliday = gConfig != null && GateHelper.isDiscountMode(gConfig);
        String tip = isHoliday ? "\n\n⚠️ 当前为假期打折模式，完成任务积分可能打折哦" : "";
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("📝 作业已提交")
                .setMessage("今日作业已提交，等待家长审核！" + tip)
                .setPositiveButton("知道了", null)
                .show();
    }
}