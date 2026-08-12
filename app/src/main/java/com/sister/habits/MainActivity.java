package com.sister.habits;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.sister.habits.child.ChildActivity;
import com.sister.habits.parent.ParentActivity;
import com.sister.habits.utils.ProfileManager;

import java.util.concurrent.Executor;

/**
 * 主入口——双模式选择
 * 支持：默认模式自动进入、指纹/设备解锁、自定义密码
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "parent_prefs";
    private static final String KEY_PIN = "parent_pin";
    private static final String KEY_DEFAULT_MODE = "default_mode";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";

    private static final String ONBOARDING_PREFS = "onboarding";
    private static final String ONBOARDING_DONE = "onboarding_done";

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean pendingBiometricAuth = false;

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

    /**
     * 指纹/设备解锁认证（优先）+ PIN码回退
     */
    private void authenticateWithBiometricOrPin(boolean autoEnter) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true);

        if (biometricEnabled && isBiometricAvailable()) {
            // 尝试指纹/设备解锁
            tryBiometricAuth(autoEnter);
        } else {
            // 回退到PIN验证
            showVerifyPinDialog(autoEnter);
        }
    }

    private boolean isBiometricAvailable() {
        BiometricManager manager = BiometricManager.from(this);
        int result = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void tryBiometricAuth(boolean autoEnter) {
        executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // 用户取消或出错 → 回退到PIN
                        showVerifyPinDialog(autoEnter);
                    }

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Toast.makeText(MainActivity.this, "🔓 指纹验证通过", Toast.LENGTH_SHORT).show();
                        enterParentMode();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // 指纹不匹配，不处理，让用户继续试
                    }
                });

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("验证家长身份")
                .setSubtitle("使用指纹或设备密码解锁")
                .setDescription("只有家长才能进入管理界面哦")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        // 注意：当允许 DEVICE_CREDENTIAL 时，不能设置 setNegativeButtonText
        // 系统会自动提供取消按钮
        promptInfo = builder.build();

        biometricPrompt.authenticate(promptInfo);
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

    private void showSetPinDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("设置家长密码");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("请输入密码（数字/字母/符号均可）");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String pin = input.getText().toString().trim();
            if (pin.length() >= 4 && pin.length() <= 64) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_PIN, pin)
                        .apply();
                // 询问是否启用指纹
                if (isBiometricAvailable()) {
                    askEnableBiometric();
                } else {
                    enterParentMode();
                }
            } else {
                Toast.makeText(this, "密码长度4-64位", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void askEnableBiometric() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("启用指纹解锁？")
                .setMessage("下次进入家长管理时，可以用指纹或设备解锁密码快速验证。\n\n（可在设置中随时关闭）")
                .setPositiveButton("启用 ✅", (d, w) -> {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
                            .apply();
                    enterParentMode();
                })
                .setNegativeButton("跳过", (d, w) -> {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
                            .apply();
                    enterParentMode();
                })
                .show();
    }

    private void showVerifyPinDialog(boolean autoEnter) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("输入家长密码");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("请输入密码");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String pin = input.getText().toString().trim();
            String savedPin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_PIN, "");
            if (pin.equals(savedPin)) {
                enterParentMode();
            } else {
                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                if (!autoEnter) showModeSelection();
                else finish();
            }
        });
        builder.setNegativeButton("取消", (d, w) -> {
            if (!autoEnter) showModeSelection();
            else finish();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void enterParentMode() {
        Intent intent = new Intent(MainActivity.this, ParentActivity.class);
        startActivity(intent);
        finish();
    }
}