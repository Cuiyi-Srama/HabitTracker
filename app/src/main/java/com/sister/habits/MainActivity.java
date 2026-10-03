package com.sister.habits;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sister.habits.child.ChildActivity;
import com.sister.habits.parent.ParentActivity;
import com.sister.habits.utils.ProfileManager;


/**
 * 主入口——双模式选择
 * 说明：进入家长界面的身份校验统一由 ParentActivity + TvPinGuard（PinHelper）负责
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "parent_prefs";
    private static final String KEY_DEFAULT_MODE = "default_mode";

    private static final String ONBOARDING_PREFS = "onboarding";
    private static final String ONBOARDING_DONE = "onboarding_done";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // v3.0.61：一次性数据迁移 —— 旧版（≤v3.0.60）兑换申请提交时已立即扣款，
        // 本次改为审批通过时才扣款，故对存量 pending 申请执行退款，保证新旧语义一致
        // （副作用：顺带修复旧版多设备双花导致的负余额）
        if (!prefs.getBoolean("v3061_migration_done", false)) {
            try {
                com.sister.habits.data.AppDatabase db = com.sister.habits.data.AppDatabase.getInstance(this);
                String deviceId = com.sister.habits.sync.SyncManager.getInstance(this).getDeviceId();
                int refunded = com.sister.habits.sync.RedemptionApprovalService.migratePendingRefunds(
                        db.coinTransactionDao(), db.redemptionDao(), deviceId);
                if (refunded > 0) {
                    android.util.Log.i("MainActivity", "v3.0.61迁移: 已退回 " + refunded + " 笔旧兑换扣款");
                }
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "v3.0.61迁移失败", e);
            }
            prefs.edit().putBoolean("v3061_migration_done", true).apply();
        }

        // 检查是否从儿童模式强制跳转到家长模式
        boolean forceParent = getIntent().getBooleanExtra("force_parent_mode", false);
        if (forceParent) {
            enterParentMode();
            return;
        }

        String defaultMode = prefs.getString(KEY_DEFAULT_MODE, "child");

        if ("child".equals(defaultMode)) {
            launchChildMode();
            return;
        } else if ("parent".equals(defaultMode)) {
            enterParentMode();
            return;
        } else if ("tv".equals(defaultMode)) {
            launchTvMode();
            return;
        }

        showModeSelection();
    }

    private void launchChildMode() {
        SharedPreferences onboardingPrefs = getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE);
        if (onboardingPrefs.getBoolean(ONBOARDING_DONE, false)) {
            startActivity(new Intent(this, ChildActivity.class));
        } else {
            startActivity(new Intent(this, com.sister.habits.child.WelcomeActivity.class));
        }
        finish();
    }

    private void showModeSelection() {
        setContentView(R.layout.activity_main);

        ProfileManager profile = ProfileManager.getInstance(this);
        android.widget.TextView tvTitle = findViewById(R.id.tv_app_title);
        tvTitle.setText("🌟 " + profile.getAppTitle());

        Button btnChild = findViewById(R.id.btn_child_mode);
        btnChild.setText("🎀 " + profile.getNickname() + "的乐园");
        Button btnParent = findViewById(R.id.btn_parent_mode);

        btnChild.setOnClickListener(v -> {
            SharedPreferences onboardingPrefs = getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE);
            if (onboardingPrefs.getBoolean(ONBOARDING_DONE, false)) {
                startActivity(new Intent(MainActivity.this, ChildActivity.class));
            } else {
                startActivity(new Intent(MainActivity.this, com.sister.habits.child.WelcomeActivity.class));
            }
        });

        btnParent.setOnClickListener(v -> {
            enterParentMode();
        });






    }

    /**
     * 默认启动模式 = TV 看片。
     *
     * ★ 反射调用：TV 渠道的类位于 src/tv，phone flavor 编译时不可见，
     *   直接 import 会导致 phone 构建失败。故按类名加载。
     */
    private void launchTvMode() {
        try {
            Class<?> cls = Class.forName("com.sister.habits.tv.TvVideoActivity");
            startActivity(new Intent(this, cls));
            finish();
        } catch (Throwable e) {
            Toast.makeText(this, "当前包未包含 TV 渠道，已回退模式选择", Toast.LENGTH_LONG).show();
            showModeSelection();
        }
    }

    private void enterParentMode() {
        Intent intent = new Intent(MainActivity.this, ParentActivity.class);
        startActivity(intent);
        finish();
    }
}