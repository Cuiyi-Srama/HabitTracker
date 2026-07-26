package com.sister.habits.child;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sister.habits.R;

/**
 * 新手引导页 — 首次安装时展示
 * 引导妹妹了解App的主要功能
 */
public class WelcomeActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "onboarding";
    private static final String KEY_DONE = "onboarding_done";

    private final int[][] pages = {
        {R.string.welcome_title_1, R.string.welcome_desc_1},
        {R.string.welcome_title_2, R.string.welcome_desc_2},
        {R.string.welcome_title_3, R.string.welcome_desc_3},
    };
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 如果已经完成引导，直接跳过
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false)) {
            startActivity(new Intent(this, ChildActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);
        showPage(0);

        Button btnNext = findViewById(R.id.btn_welcome_next);
        Button btnSkip = findViewById(R.id.btn_welcome_skip);

        btnNext.setOnClickListener(v -> {
            currentPage++;
            if (currentPage >= pages.length) {
                finishOnboarding();
            } else {
                showPage(currentPage);
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void showPage(int page) {
        TextView tvTitle = findViewById(R.id.tv_welcome_title);
        TextView tvDesc = findViewById(R.id.tv_welcome_desc);
        tvTitle.setText(pages[page][0]);
        tvDesc.setText(pages[page][1]);
    }

    private void finishOnboarding() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_DONE, true).apply();
        startActivity(new Intent(this, ChildActivity.class));
        finish();
    }
}
