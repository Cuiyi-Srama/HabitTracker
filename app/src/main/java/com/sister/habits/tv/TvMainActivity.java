package com.sister.habits.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.sister.habits.R;
import com.sister.habits.utils.PinHelper;

/**
 * TV 模式主页（骨架版，步骤①）。
 *
 * 后续步骤将在此基础上接入：
 *   ② 遥控器焦点链（所有控件显式 nextFocus*）
 *   ③ PIN 门禁（进入敏感区前校验）
 *   ④ 扫码配对 + 局域网自动发现 Hub
 */
public class TvMainActivity extends Activity {

    private TextView tvTitle;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        tvTitle = findViewById(R.id.tv_title);
        tvStatus = findViewById(R.id.tv_status);

        Button btnMode = findViewById(R.id.btn_tv_mode_switch);
        Button btnClose = findViewById(R.id.btn_tv_exit);

        refreshStatus();

        btnMode.setOnClickListener(v -> {
            boolean now = PinHelper.isTvMode(this);
            PinHelper.setForceTvMode(this, !now);
            refreshStatus();
        });

        btnClose.setOnClickListener(v -> finish());
    }

    private void refreshStatus() {
        boolean tv = PinHelper.isTvMode(this);
        boolean pinReady = PinHelper.isAppPinEnabled(this) && PinHelper.isPinSet(this);
        tvStatus.setText("TV 模式：" + (tv ? "已开启" : "未开启")
                + "\n应用PIN：" + (pinReady ? "已设置" : "未设置（TV 模式下必须设置）"));
        if (tvTitle != null) {
            tvTitle.setText("\ud83d\udcfa HabitTracker TV");
        }
    }
}
