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
import com.sister.habits.utils.SoundHelper;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child);

                db = AppDatabase.getInstance(this);
        syncManager = SyncManager.getInstance(this);
        soundHelper = new SoundHelper(this);

        tvCoinBalance = findViewById(R.id.tv_coin_balance);
        Button btnCheckIn = findViewById(R.id.btn_check_in);
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        // 显示每日一句
        TextView tvQuote = findViewById(R.id.tv_daily_quote);
        tvQuote.setText("💬 " + DailyQuote.getTodayQuote());

        // 儿童设置齿轮⚙️（不显眼，在右上角）
        TextView btnSettings = findViewById(R.id.btn_child_settings);
        btnSettings.setOnClickListener(v -> showChildSettings());

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
    }

    public void refreshCoinBalance() {
        Integer balance = db.coinTransactionDao().getBalance("sister");
        tvCoinBalance.setText("🪙 " + (balance != null ? balance : 0));
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
                        "🔐 进入家长管理"
                }, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            int totalCheckIns = db.checkInDao().getTotalCheckIns("sister");
                            int maxStreak = db.checkInDao().getMaxStreak("sister");
                            Integer balance = db.coinTransactionDao().getBalance("sister");
                            int wordMastered = db.vocabularyDao().getMasteredCount();
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
                            int dueCount = db.wordReviewDao().getDueCount(System.currentTimeMillis());
                            int totalLearning = db.wordReviewDao().getTotalLearningCount();
                            int mastered = db.wordReviewDao().getMasteredCount(WordReview.MAX_STAGE, Long.MAX_VALUE);
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
}