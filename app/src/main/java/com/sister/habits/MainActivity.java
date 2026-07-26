package com.sister.habits;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sister.habits.child.ChildActivity;
import com.sister.habits.parent.ParentActivity;

/**
 * 主入口——双模式选择
 * 孩子模式：一键进入（大按钮，卡通界面）
 * 家长模式：需要 PIN 验证
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "parent_prefs";
    private static final String KEY_PIN = "parent_pin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnChild = findViewById(R.id.btn_child_mode);
        Button btnParent = findViewById(R.id.btn_parent_mode);
        TextView tvSubtitle = findViewById(R.id.tv_subtitle);

        // 孩子模式—一键进入
        btnChild.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChildActivity.class);
            startActivity(intent);
        });

        // 家长模式—需要PIN
        btnParent.setOnClickListener(v -> {
            String savedPin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_PIN, null);
            if (savedPin == null) {
                // 首次使用：设置PIN
                showSetPinDialog();
            } else {
                // 验证PIN
                showVerifyPinDialog();
            }
        });
    }

    private void showSetPinDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("设置家长密码");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("请输入4位数字密码");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String pin = input.getText().toString().trim();
            if (pin.length() == 4) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_PIN, pin)
                        .apply();
                enterParentMode();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showVerifyPinDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("输入家长密码");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("请输入4位数字密码");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String pin = input.getText().toString().trim();
            String savedPin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_PIN, "");
            if (pin.equals(savedPin)) {
                enterParentMode();
            } else {
                android.widget.Toast.makeText(this, "密码错误", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void enterParentMode() {
        Intent intent = new Intent(MainActivity.this, ParentActivity.class);
        startActivity(intent);
    }
}