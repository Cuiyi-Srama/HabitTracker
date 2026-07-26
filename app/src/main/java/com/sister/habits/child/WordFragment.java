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
import com.sister.habits.data.models.EconomyConfig;
import com.sister.habits.data.models.Vocabulary;
import com.sister.habits.data.models.WordReview;
import com.sister.habits.sync.SyncManager;
import com.sister.habits.utils.SoundHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单词学习Fragment——多邻国风格选择题 + 艾宾浩斯遗忘曲线复习
 * 学习模式：出未学过的新词，答对自动进入艾宾浩斯复习周期
 * 复习模式：出到期的待复习单词，答对推进阶段，答错第二天再试
 * TTS朗读 + 音效 + 震动反馈
 */
public class WordFragment extends Fragment {

    private AppDatabase db;
    private SyncManager syncManager;
    private SoundHelper soundHelper;

    private TextView tvStats, tvWord, tvPhonetic, tvPrompt, tvStreak;
    private Button btnOption1, btnOption2, btnOption3, btnOption4;
    private Button btnNewWords, btnReview;

    private final List<Button> optionButtons = new ArrayList<>();

    private Vocabulary currentWord;
    private List<Vocabulary> quizQueue = new ArrayList<>();
    private int streakCount = 0;
    private int todayLearnedCount = 0;
    private int dailyWordLimit = 10;
    private boolean isAnswering = false;
    private boolean isReviewMode = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word, container, false);
        db = AppDatabase.getInstance(requireContext());
        syncManager = SyncManager.getInstance(requireContext());
        soundHelper = new SoundHelper(requireContext());

        // 读取每日单词上限
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config != null) dailyWordLimit = config.maxDailyWords;
        if (dailyWordLimit <= 0) dailyWordLimit = 10;

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
        btnNewWords.setOnClickListener(v -> {
            soundHelper.playClickSound();
            startQuiz(false);
        });
        btnReview.setOnClickListener(v -> {
            soundHelper.playClickSound();
            startQuiz(true);
        });

        startQuiz(false);
        return view;
    }

    private void startQuiz(boolean review) {
        isReviewMode = review;
        streakCount = 0;
        todayLearnedCount = 0;
        updateStreakDisplay();

        List<Vocabulary> words;
        if (review) {
            // ===== 复习模式：获取到期的待复习单词 =====
            List<WordReview> dueReviews = db.wordReviewDao().getDueReviews(System.currentTimeMillis());
            words = new ArrayList<>();
            for (WordReview wr : dueReviews) {
                Vocabulary v = db.vocabularyDao().getById(wr.wordId);
                if (v != null && v.active && !v.mastered) {
                    words.add(v);
                }
            }
            Collections.shuffle(words);
            if (words.size() > 20) words = words.subList(0, 20);
            tvPrompt.setText("🔄 今日复习 (" + words.size() + " 个待复习)");
        } else {
            // ===== 学习模式：获取未学过的新词 =====
            List<Vocabulary> allActive = db.vocabularyDao().getActiveUnmastered();
            words = new ArrayList<>();
            Set<String> existingReviews = new HashSet<>();
            // 查询已有 WordReview 的单词（说明已进入学习周期）
            for (Vocabulary v : allActive) {
                WordReview wr = db.wordReviewDao().getByWordId(v.id);
                if (wr == null) {
                    // 完全没有复习记录 → 新词
                    words.add(v);
                    if (words.size() >= dailyWordLimit) break;
                }
            }
            // 如果新词不够，补充已学但需要重试的（stage=0 且到期的）
            if (words.size() < 5) {
                for (Vocabulary v : allActive) {
                    if (words.size() >= dailyWordLimit) break;
                    WordReview wr = db.wordReviewDao().getByWordId(v.id);
                    if (wr != null && wr.stage == 0 && wr.isDue() && !words.contains(v)) {
                        words.add(v);
                    }
                }
            }
            Collections.shuffle(words);
            tvPrompt.setText("📚 今日新词 (" + words.size() + " 个)");
        }

        if (words.isEmpty()) {
            tvWord.setText("🎉 全部完成！");
            tvPhonetic.setText(isReviewMode ? "复习完了，真棒 🎉" : "所有新词都学完啦 🎉");
            tvPrompt.setText(isReviewMode
                    ? "所有单词都复习好了！去逛逛商城吧 🏪"
                    : "暂时没有新单词了，切换到复习看看吧 🔄");
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

        // 锁定并高亮正确答案
        for (Button btn : optionButtons) {
            btn.setEnabled(false);
            if (btn.getText().toString().equals(currentWord.meaning)) {
                btn.setBackgroundColor(0xFF4CAF50);
                btn.setTextColor(0xFFFFFFFF);
            }
        }

        if (correct) {
            // ✅ 答对
            clicked.setBackgroundColor(0xFF4CAF50);
            clicked.setTextColor(0xFFFFFFFF);
            streakCount++;
            todayLearnedCount++;

            // 计算金币奖励
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

            // 艾宾浩斯复习：推进 WordReview 阶段
            updateWordReview(currentWord.id, true);

            // TTS朗读单词
            soundHelper.speakWord(currentWord.word);

            // 音效 + 震动
            if (streakCount >= 5) {
                soundHelper.playStreakSound(streakCount);
            } else {
                soundHelper.playCorrectSound();
            }

            syncManager.onDataChanged();

        } else {
            // ❌ 答错（不惩罚🌸）
            clicked.setBackgroundColor(0xFFE53935);
            clicked.setTextColor(0xFFFFFFFF);
            streakCount = 0;
            tvPrompt.setText("🌸 " + currentWord.meaning + " 才是对的哦，下次加油～");

            // 艾宾浩斯：答错不惩罚，推到下次再试
            updateWordReview(currentWord.id, false);
        }

        updateStats();
        updateStreakDisplay();

        new Handler(Looper.getMainLooper()).postDelayed(this::showNextWord, 1200);
    }

    /**
     * 更新艾宾浩斯复习状态
     */
    private void updateWordReview(String wordId, boolean correct) {
        WordReview wr = db.wordReviewDao().getByWordId(wordId);
        if (wr == null && correct) {
            // 第一次答对 → 创建复习记录，进入阶段0
            wr = WordReview.createNew(wordId);
            db.wordReviewDao().insert(wr);
        } else if (wr != null) {
            if (correct) {
                // 答对 → 推进到下一阶段
                wr.advanceStage();
                db.wordReviewDao().update(wr);
                // 如果已到最终阶段，标记单词为掌握
                if (wr.isMastered()) {
                    db.vocabularyDao().markMastered(wordId, System.currentTimeMillis());
                }
            } else {
                // 答错 → 不降级，推到明天再试（鼓励模式🌸）
                wr.failStage();
                db.wordReviewDao().update(wr);
            }
        }
        // 如果 wr==null && !correct（第一次就答错），不做任何事，下次继续出现
    }

    private void updateStats() {
        int mastered = db.vocabularyDao().getMasteredCount();
        int total = db.vocabularyDao().getActiveCount();
        int dueCount = db.wordReviewDao().getDueCount(System.currentTimeMillis());
        String mode = isReviewMode ? "🔄 复习" : "📚 学习";
        tvStats.setText(mode + "  |  ✅ " + mastered + "/" + total + "  |  ⏰ " + dueCount + " 待复习");
    }

    private void updateStreakDisplay() {
        StringBuilder sb = new StringBuilder("🔥 ");
        for (int i = 0; i < Math.min(streakCount, 10); i++) sb.append("⭐");
        if (streakCount > 10) sb.append("+" + (streakCount - 10));
        tvStreak.setText(sb.toString());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (soundHelper != null) {
            soundHelper.shutdown();
        }
    }
}