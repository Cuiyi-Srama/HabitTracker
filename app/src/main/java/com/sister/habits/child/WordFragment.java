package com.sister.habits.child;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.CoinTransaction;
import com.sister.habits.data.models.Vocabulary;
import com.sister.habits.sync.SyncManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单词学习Fragment——多邻国风格选择题
 * 上方显示单词，下方4个选项选中文释义
 * 连续答对获得奖励金币
 */
public class WordFragment extends Fragment {

    private AppDatabase db;
    private SyncManager syncManager;

    private TextView tvStats, tvWord, tvPhonetic, tvPrompt, tvStreak;
    private Button btnOption1, btnOption2, btnOption3, btnOption4;
    private Button btnNewWords, btnReview;

    private final List<Button> optionButtons = new ArrayList<>();

    private Vocabulary currentWord;
    private List<Vocabulary> quizQueue = new ArrayList<>();
    private int streakCount = 0;
    private boolean isAnswering = false;
    private boolean isReviewMode = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word, container, false);
        db = AppDatabase.getInstance(requireContext());
        syncManager = SyncManager.getInstance(requireContext());

        tvStats = view.findViewById(R.id.tv_word_stats);
        tvWord = view.findViewById(R.id.tv_word_display);
        tvPhonetic = view.findViewById(R.id.tv_phonetic);
        tvPrompt = view.findViewById(R.id.tv_prompt);
        tvStreak = view.findViewById(R.id.tv_streak);

        btnOption1 = view.findViewById(R.id.btn_option_1);
        btnOption2 = view.findViewById(R.id.btn_option_2);
        btnOption3 = view.findViewById(R.id.btn_option_3);
        btnOption4 = view.findViewById(R.id.btn_option_4);
        optionButtons.add(btnOption1);
        optionButtons.add(btnOption2);
        optionButtons.add(btnOption3);
        optionButtons.add(btnOption4);

        for (Button btn : optionButtons) {
            btn.setOnClickListener(this::onOptionClicked);
        }

        btnNewWords = view.findViewById(R.id.btn_new_words);
        btnReview = view.findViewById(R.id.btn_review);
        btnNewWords.setOnClickListener(v -> startQuiz(false));
        btnReview.setOnClickListener(v -> startQuiz(true));

        startQuiz(false);
        return view;
    }

    private void startQuiz(boolean review) {
        isReviewMode = review;
        streakCount = 0;
        updateStreakDisplay();

        List<Vocabulary> words;
        if (review) {
            words = db.vocabularyDao().getRandom(12);
            if (words.size() < 5) {
                List<Vocabulary> mastered = db.vocabularyDao().getMastered();
                if (mastered.size() > 3) {
                    Collections.shuffle(mastered);
                    words.addAll(mastered.subList(0, 3));
                } else {
                    words.addAll(mastered);
                }
            }
            Collections.shuffle(words);
            tvPrompt.setText("🔄 复习模式");
        } else {
            words = db.vocabularyDao().getUnmastered();
            Collections.shuffle(words);
            if (words.size() > 20) words = words.subList(0, 20);
            tvPrompt.setText("📚 学习新词");
        }

        if (words.isEmpty()) {
            tvWord.setText("🎉 全部掌握！");
            tvPhonetic.setText("太棒啦 🎉");
            tvPrompt.setText("所有单词都学会了！去逛逛商城吧 🏪");
            for (Button btn : optionButtons) btn.setVisibility(View.GONE);
            updateStats();
            return;
        }

        quizQueue = new ArrayList<>(words);
        updateStats();
        showNextWord();
    }

    private void showNextWord() {
        if (quizQueue.isEmpty()) {
            tvWord.setText("🎉 本轮完成！");
            tvPhonetic.setText("");
            tvPrompt.setText("点击「📚新词」或「🔄复习」继续");
            for (Button btn : optionButtons) btn.setVisibility(View.GONE);
            updateStats();
            return;
        }

        currentWord = quizQueue.remove(0);
        tvWord.setText(currentWord.word);
        tvPhonetic.setText(currentWord.phonetic != null ? currentWord.phonetic : "");
        tvPrompt.setText("选出正确的中文意思 👇");

        // 生成选项：1正确 + 3干扰
        List<String> options = new ArrayList<>();
        options.add(currentWord.meaning);

        List<Vocabulary> distractors = db.vocabularyDao().getRandom(8);
        Collections.shuffle(distractors);
        for (Vocabulary v : distractors) {
            if (!v.meaning.equals(currentWord.meaning) && !options.contains(v.meaning)) {
                options.add(v.meaning);
                if (options.size() >= 4) break;
            }
        }
        while (options.size() < 4) {
            String[] fb = {"你好", "谢谢", "再见", "好的", "早上好", "晚上好", "爸爸", "妈妈"};
            for (String s : fb) {
                if (!options.contains(s)) {
                    options.add(s);
                    if (options.size() >= 4) break;
                }
            }
        }

        Collections.shuffle(options);

        isAnswering = true;
        for (int i = 0; i < optionButtons.size(); i++) {
            Button btn = optionButtons.get(i);
            btn.setVisibility(View.VISIBLE);
            btn.setEnabled(true);
            btn.setText(options.get(i));
            btn.setBackgroundColor(0xFFF5F5F5);
            btn.setTextColor(0xFF333333);
        }
    }

    private void onOptionClicked(View v) {
        if (!isAnswering || currentWord == null) return;
        isAnswering = false;

        Button clicked = (Button) v;
        String selected = clicked.getText().toString();
        boolean correct = selected.equals(currentWord.meaning);

        // 锁定并高亮
        for (Button btn : optionButtons) {
            btn.setEnabled(false);
            if (btn.getText().toString().equals(currentWord.meaning)) {
                btn.setBackgroundColor(0xFF4CAF50);
                btn.setTextColor(0xFFFFFFFF);
            }
        }

        if (correct) {
            clicked.setBackgroundColor(0xFF4CAF50);
            clicked.setTextColor(0xFFFFFFFF);
            streakCount++;
            // 新词模式：答对即奖励，连续有额外奖励
            int wordBonus = 2;
            if (streakCount == 5) {
                wordBonus = 12;
                tvPrompt.setText("🌟 连续答对5题！+12金币！");
            } else if (streakCount == 10) {
                wordBonus = 35;
                tvPrompt.setText("🏆 连续答对10题！+35金币！");
            } else if (streakCount >= 3) {
                wordBonus = 4;
                tvPrompt.setText("✅ 正确！连续" + streakCount + "题啦 💪");
            } else {
                tvPrompt.setText("✅ 正确！+2金币");
            }

            // 发放金币
            Integer balance = db.coinTransactionDao().getBalance("sister");
            int newBalance = (balance != null ? balance : 0) + wordBonus;
            CoinTransaction ct = new CoinTransaction(
                    "sister", wordBonus, newBalance,
                    "word_learn", "单词: " + currentWord.word,
                    syncManager.getDeviceId());
            db.coinTransactionDao().insert(ct);

            // 新词模式才标记为已掌握（复习模式不重复标记）
            if (!isReviewMode && !currentWord.mastered) {
                db.vocabularyDao().markMastered(currentWord.id, System.currentTimeMillis());
            }

            syncManager.onDataChanged();

        } else {
            clicked.setBackgroundColor(0xFFE53935);
            clicked.setTextColor(0xFFFFFFFF);
            streakCount = 0;
            tvPrompt.setText("❌ " + currentWord.meaning + " 才是对的哦");
        }

        updateStats();
        updateStreakDisplay();

        new Handler(Looper.getMainLooper()).postDelayed(this::showNextWord, 1200);
    }

    private void updateStats() {
        int mastered = db.vocabularyDao().getMasteredCount();
        int total = db.vocabularyDao().getUnmasteredCount() + mastered;
        tvStats.setText("📖 已掌握 " + mastered + "/" + total + " 个单词");
    }

    private void updateStreakDisplay() {
        StringBuilder sb = new StringBuilder("🔥 ");
        for (int i = 0; i < Math.min(streakCount, 10); i++) sb.append("⭐");
        if (streakCount > 10) sb.append("+" + (streakCount - 10));
        tvStreak.setText(sb.toString());
    }
}