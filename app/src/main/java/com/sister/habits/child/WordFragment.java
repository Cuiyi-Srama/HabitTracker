package com.sister.habits.child;

import android.content.SharedPreferences;
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
import java.util.List;

/**
 * 单词学习Fragment——多邻国风格选择题 + 艾宾浩斯遗忘曲线复习
 * 学习模式：出未学过的新词，答对自动进入艾宾浩斯复习周期
 * 复习模式：出到期的待复习单词，答对推进阶段，答错第二天再试
 * TTS朗读 + 音效 + 震动反馈
 * 词库隔离：通过 bankId 区分不同词库的学习进度
 */
public class WordFragment extends Fragment {

    private AppDatabase db;
    private SyncManager syncManager;
    private SoundHelper soundHelper;

    private TextView tvStats, tvWord, tvPhonetic, tvPrompt, tvStreak, btnSpeak;
    private Button btnOption1, btnOption2, btnOption3, btnOption4;
    private Button btnNewWords, btnReview;

    private final List<Button> optionButtons = new ArrayList<>();

    private Vocabulary currentWord;
    private List<Vocabulary> quizQueue = new ArrayList<>();
    private int streakCount = 0;
    private int dailyWordLimit = 10;
    private int dailyReviewLimit = 20;
    private boolean isAnswering = false;
    private boolean isReviewMode = false;
    private String currentBankId = "builtin";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word, container, false);
        db = AppDatabase.getInstance(requireContext());
        syncManager = SyncManager.getInstance(requireContext());
        soundHelper = SoundHelper.getInstance(requireContext());

        // 读取当前词库ID
        SharedPreferences prefs = requireContext().getSharedPreferences("wordbank_prefs", 0);
        currentBankId = prefs.getString("active_bank_id", "builtin");

        // 读取每日单词上限（每次创建时重新读取，确保家长设置生效）
        loadConfig();

        tvStats = view.findViewById(R.id.tv_word_stats);
        tvWord = view.findViewById(R.id.tv_word_display);
        tvPhonetic = view.findViewById(R.id.tv_phonetic);
        tvPrompt = view.findViewById(R.id.tv_prompt);
        tvStreak = view.findViewById(R.id.tv_streak);
        btnSpeak = view.findViewById(R.id.btn_speak);
        btnSpeak.setOnClickListener(v -> {
            if (currentWord != null) {
                soundHelper.speakWord(currentWord.word);
            }
        });

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

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到此页面时重新加载配置（家长修改限额后立即生效）
        loadConfig();
        // 重新读取活跃词库ID（家长切换词库后立即生效）
        SharedPreferences prefs = requireContext().getSharedPreferences("wordbank_prefs", 0);
        String newBankId = prefs.getString("active_bank_id", "builtin");
        if (!newBankId.equals(currentBankId)) {
            currentBankId = newBankId;
            startQuiz(isReviewMode);
        } else {
            updateStats();
        }
    }

    private void loadConfig() {
        EconomyConfig config = db.economyConfigDao().getConfig();
        if (config != null) {
            dailyWordLimit = config.maxDailyWords;
            dailyReviewLimit = config.maxDailyReview;
        }
        if (dailyWordLimit <= 0) dailyWordLimit = 10;
        if (dailyReviewLimit <= 0) dailyReviewLimit = 20;
    }

    private String getCurrentBankId() {
        return currentBankId;
    }

    private void startQuiz(boolean review) {
        isReviewMode = review;
        streakCount = 0;
        updateStreakDisplay();

        List<Vocabulary> words;
        String bankId = getCurrentBankId();
        if (review) {
            // ===== 复习检测模式：获取到期的待复习单词 =====
            List<WordReview> dueReviews = db.wordReviewDao().getDueReviews(System.currentTimeMillis(), bankId);
            words = new ArrayList<>();
            for (WordReview wr : dueReviews) {
                if (words.size() >= dailyReviewLimit) break;
                Vocabulary v = db.vocabularyDao().getById(wr.wordId);
                if (v != null && v.active && !v.mastered && bankId.equals(v.bankId)) {
                    words.add(v);
                }
            }
            Collections.shuffle(words);
            tvPrompt.setText("🔄 复习检测 (" + words.size() + "个待复习)  答对得金币 🪙");
        } else {
            // ===== 学习模式：只出未学过的新词，今日限额 =====
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            int learnedToday = db.wordReviewDao().getTodayCount(cal.getTimeInMillis(), bankId);
            // Limit review count too in learning mode
            int reviewedToday = db.wordReviewDao().getTodayCount(cal.getTimeInMillis(), bankId) - learnedToday;
            int remainingNew = Math.max(0, dailyWordLimit - learnedToday);

            List<Vocabulary> allActive = db.vocabularyDao().getActiveUnmastered(bankId);
            words = new ArrayList<>();
            for (Vocabulary v : allActive) {
                WordReview wr = db.wordReviewDao().getByWordId(v.id, bankId);
                if (wr == null) {
                    words.add(v);
                    if (words.size() >= remainingNew) break;
                }
            }
            tvPrompt.setText("📚 今日新词 (" + words.size() + "/" + dailyWordLimit + " 可学)  学习不给金币哦");
        }

        if (words.isEmpty()) {
            tvWord.setText("🎉 全部完成！");
            tvPhonetic.setText(isReviewMode ? "复习完了，真棒 🎉" : "今日新词已学完 🎉");
            tvPrompt.setText(isReviewMode
                    ? "所有单词都检测通过了！去逛逛商城吧 🏪"
                    : "今天的新词学完了，切换到「复习检测」赚金币吧 🪙");
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
        // 自动朗读单词
        soundHelper.speakWord(currentWord.word);

        // 生成选项：1正确 + 3干扰
        List<String> options = new ArrayList<>();
        options.add(currentWord.meaning);

        List<Vocabulary> distractors = db.vocabularyDao().getRandom(8, getCurrentBankId());
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

            // ===== 复习检测模式才给金币，学习模式不给 =====
            if (isReviewMode) {
                // 计算金币奖励（复习检测）
                int wordBonus = 2;
                if (streakCount == 5) {
                    wordBonus = 12;
                    tvPrompt.setText("🌟 连续答对5题！+12金币！");
                } else if (streakCount == 10) {
                    wordBonus = 35;
                    tvPrompt.setText("🏆 连续答对10题！+35金币！");
                } else if (streakCount >= 3) {
                    wordBonus = 4;
                    tvPrompt.setText("✅ 检测通过！连续" + streakCount + "题啦 💪 +4金币");
                } else {
                    tvPrompt.setText("✅ 检测通过！+2金币 🪙");
                }

                // 发放金币
                Integer balance = db.coinTransactionDao().getBalance("sister");
                int newBalance = (balance != null ? balance : 0) + wordBonus;
                CoinTransaction ct = new CoinTransaction(
                        "sister", wordBonus, newBalance,
                        "word_review_pass", "复习检测: " + currentWord.word,
                        syncManager.getDeviceId());
                db.coinTransactionDao().insert(ct);

                // 音效 + 震动
                if (streakCount >= 5) {
                    soundHelper.playStreakSound(streakCount);
                } else {
                    soundHelper.playCorrectSound();
                }
            } else {
                tvPrompt.setText("📖 已记住: " + currentWord.word + "  切换到复习赚金币吧 🪙");
                // 学习模式不给金币，轻柔反馈即可
                soundHelper.playClickSound();
            }

            syncManager.onDataChanged();

            // 实时刷新金币余额显示
            if (getActivity() instanceof ChildActivity) {
                ((ChildActivity) getActivity()).refreshCoinBalance();
            }

        } else {
            // ❌ 答错（不惩罚🌸）
            clicked.setBackgroundColor(0xFFE53935);
            clicked.setTextColor(0xFFFFFFFF);
            streakCount = 0;
            tvPrompt.setText("🌸 " + currentWord.meaning + " 才是对的哦，下次加油～");

            // 艾宾浩斯：答错不惩罚，推到下次再试
            updateWordReview(currentWord.id, false);

            // 轻柔震动反馈，提醒但不焦虑
            soundHelper.playErrorVibration();
        }

        updateStats();
        updateStreakDisplay();

        new Handler(Looper.getMainLooper()).postDelayed(this::showNextWord, 1200);
    }

    /**
     * 更新艾宾浩斯复习状态
     */
    private void updateWordReview(String wordId, boolean correct) {
        String bankId = getCurrentBankId();
        WordReview wr = db.wordReviewDao().getByWordId(wordId, bankId);
        if (wr == null && correct) {
            // 第一次答对 → 创建复习记录，进入阶段0
            wr = WordReview.createNew(wordId, bankId);
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
        String bankId = getCurrentBankId();
        int mastered = db.vocabularyDao().getMasteredCount(bankId);
        int total = db.vocabularyDao().getActiveCount(bankId);
        int dueCount = db.wordReviewDao().getDueCount(System.currentTimeMillis(), bankId);
        String mode = isReviewMode ? "🔄 复习" : "📚 学习";

        // 今日进度：已学新词 / 每日上限
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        int todayNew = db.wordReviewDao().getTodayCount(cal.getTimeInMillis(), bankId);
        int todayReview = db.wordReviewDao().getTodayCount(cal.getTimeInMillis(), bankId) - todayNew;
        String todayProgress = "  📝 " + Math.min(todayNew, dailyWordLimit) + "/" + dailyWordLimit;

        tvStats.setText(mode + todayProgress + "  |  ✅ " + mastered + "掌握  |  ⏰ " + dueCount + "待复习");
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
        SoundHelper.releaseInstance();
    }
}