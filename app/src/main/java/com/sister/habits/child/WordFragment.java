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
import com.sister.habits.data.models.CoinEarning;
import com.sister.habits.sync.EarningService;
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
    // 选项缓存：每个词的固定干扰项，防止复习重考时选项变化被孩子投机取巧
    private final java.util.Map<String, java.util.List<String> > cachedOptions = new java.util.HashMap<>();
    private boolean isReviewMode = false;
    private String currentBankId = "builtin";

    // 复习模式组批字段
    private int currentReviewTotal = 0;                    // 本轮复习总词数
    private List<Vocabulary> reviewAllWords = null;         // 本轮完整词列表（用于整组重排）
    private final List<String> wrongInReviewSet = new ArrayList<>();  // 本轮答错的词

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

    /** 由ChildActivity在切换到单词Tab时调用，强制刷新词库 */
    public void refreshWordBank() {
        loadConfig();
        SharedPreferences prefs = requireContext().getSharedPreferences("wordbank_prefs", 0);
        String newBankId = prefs.getString("active_bank_id", "builtin");
        android.util.Log.i("WordFragment", "refreshWordBank: current=" + currentBankId + " new=" + newBankId);
        if (!newBankId.equals(currentBankId)) {
            currentBankId = newBankId;
            startQuiz(isReviewMode);
        } else {
            updateStats();
        }
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
            // ===== 复习模式：获取所有待复习词，组批 =====
            List<WordReview> dueReviews = db.wordReviewDao().getDueReviews(System.currentTimeMillis(), bankId);
            words = new ArrayList<>();
            for (WordReview wr : dueReviews) {
                Vocabulary v = db.vocabularyDao().getById(wr.wordId);
                if (v != null && v.active && !v.mastered && bankId.equals(v.bankId)) {
                    words.add(v);
                }
            }
            Collections.shuffle(words);
            currentReviewTotal = words.size();
            reviewAllWords = new ArrayList<>(words);
            wrongInReviewSet.clear();
            if (currentReviewTotal > 0) {
                tvPrompt.setText("🔄 复习检测 (" + currentReviewTotal + "个)  全对得 🪙" + (currentReviewTotal * 2));
            } else {
                tvPrompt.setText("🔄 当前没有待复习词，去学新词吧");
            }
        } else {
            // ===== 学习模式：只出未学过的新词，今日限额 =====
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            int learnedToday = db.wordReviewDao().getNewCount(cal.getTimeInMillis(), bankId);
            int remainingNew = Math.max(0, dailyWordLimit - learnedToday);

            List<Vocabulary> allActive = db.vocabularyDao().getActiveUnmastered(bankId);
            words = new ArrayList<>();
            if (remainingNew > 0) {
                for (Vocabulary v : allActive) {
                    WordReview wr = db.wordReviewDao().getByWordId(v.id, bankId);
                    if (wr == null) {
                        words.add(v);
                        if (words.size() >= remainingNew) break;
                    }
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
            // 恢复按钮可用状态
            if (btnNewWords != null) btnNewWords.setEnabled(true);
            if (btnReview != null) btnReview.setEnabled(true);
            updateStats();
            return;
        }

        Collections.shuffle(words);
        quizQueue = new ArrayList<>(words);
        cachedOptions.clear(); // 新一轮答题，清空选项缓存
        // 答题中禁用模式切换按钮，防止刷新选项作弊
        if (btnNewWords != null) btnNewWords.setEnabled(false);
        if (btnReview != null) btnReview.setEnabled(false);
        updateStats();
        showNextWord();
    }

    private void showNextWord() {
        if (quizQueue.isEmpty()) {
            if (isReviewMode) {
                if (wrongInReviewSet.isEmpty()) {
                    int totalBonus = currentReviewTotal * 2;
                    grantReviewReward(totalBonus);
                    tvWord.setText("🎉 全部通关！+" + totalBonus + "金币！");
                    tvPhonetic.setText("");
                    tvPrompt.setText("太棒了！全部答对 💪 继续加油～");
                    for (Button btn : optionButtons) btn.setVisibility(View.GONE);
                    if (btnNewWords != null) btnNewWords.setEnabled(true);
                    if (btnReview != null) btnReview.setEnabled(true);
                    updateStats();
                    return;
                } else {
                    int wrongCount = wrongInReviewSet.size();
                    tvPrompt.setText("💪 答错了 " + wrongCount + " 个～整组重来，全部答对才算通关！加油！");
                    if (reviewAllWords != null) {
                        Collections.shuffle(reviewAllWords);
                        quizQueue = new ArrayList<>(reviewAllWords);
                    }
                    wrongInReviewSet.clear();
                    new Handler(Looper.getMainLooper()).postDelayed(this::showNextWord, 800);
                    return;
                }
            }
            tvWord.setText("🎉 本轮完成！");
            tvPhonetic.setText("");
            tvPrompt.setText("点击「📚新词」或「🔄复习」继续");
            for (Button btn : optionButtons) btn.setVisibility(View.GONE);
            if (btnNewWords != null) btnNewWords.setEnabled(true);
            if (btnReview != null) btnReview.setEnabled(true);
            updateStats();
            return;
        }

        currentWord = quizQueue.remove(0);
        tvWord.setText(currentWord.word);
        tvPhonetic.setText(currentWord.phonetic != null ? currentWord.phonetic : "");
        tvPrompt.setText("选出正确的中文意思 👇");
        soundHelper.speakWord(currentWord.word);

        // ★ 使用缓存：同一单词选项不变，防止复习重考时投机取巧
        java.util.List<String> options = cachedOptions.get(currentWord.id);
        if (options == null) {
            options = new java.util.ArrayList<>();
            options.add(currentWord.meaning);
            java.util.List<Vocabulary> distractors = db.vocabularyDao().getRandom(8, getCurrentBankId());
            java.util.Collections.shuffle(distractors);
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
            java.util.Collections.shuffle(options);
            cachedOptions.put(currentWord.id, options);
        }

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

    /**    /**
     * 更新复习状态
     * 
     * 核心逻辑：
     * - 学习模式答对 → 创建记录，7天后复习（标记已见）
     * - 学习模式答错 → 创建记录，立即进复习队列
     * - 复习模式答对 → 推进stage，加权间隔
     * - 复习模式答错 → nextReviewAt=现在，立即纠错
     */
    private void updateWordReview(String wordId, boolean correct) {
        String bankId = getCurrentBankId();
        WordReview wr = db.wordReviewDao().getByWordId(wordId, bankId);
        long now = System.currentTimeMillis();

        if (wr == null) {
            // 第一次见这个词
            wr = new WordReview();
            wr.wordId = wordId;
            wr.bankId = bankId;
            wr.stage = 0;
            wr.lastReviewedAt = now;
            wr.correctCount = 0;
            wr.wrongCount = 0;
            if (correct) {
                // ✅ 学习模式答对 → 标记已见，7天后再复习
                wr.nextReviewAt = now + 7L * 24 * 3600 * 1000;
            } else {
                // ❌ 学习模式答错 → 立即进复习队列
                wr.nextReviewAt = now;
            }
            db.wordReviewDao().insert(wr);
        } else {
            if (correct) {
                // ✅ 答对 → 阶段推进，使用加权间隔
                wr.correctCount++;
                if (wr.stage < WordReview.MAX_STAGE) {
                    wr.stage++;
                    // 使用加权间隔：错误率越高，间隔越短
                    wr.nextReviewAt = now + wr.getWeightedInterval(wr.stage);
                } else {
                    wr.nextReviewAt = Long.MAX_VALUE;
                    db.vocabularyDao().markMastered(wordId, now);
                    updateStats();  // 立即刷新"已掌握"计数
                }
                wr.lastReviewedAt = now;
            } else {
                // ❌ 答错 → 不降级，立即再次进入复习队列
                wr.wrongCount++;
                wr.nextReviewAt = now;
                wr.lastReviewedAt = now;
            }
            db.wordReviewDao().update(wr);
        }
    }

    /** 复习模式全通关后统一发放积分奖励 */
    private void grantReviewReward(int totalBonus) {
        if (totalBonus <= 0) return;
        CoinEarning earning = new CoinEarning();
        earning.amount = totalBonus;
        earning.sourceType = "word_learn";
        earning.description = "复习通关: 全对" + (currentReviewTotal > 0 ? currentReviewTotal + "词" : "");
        earning.deviceId = com.sister.habits.sync.SyncManager.getInstance(getContext()).getDeviceId();
        db.coinEarningDao().insert(earning);

        if (getActivity() instanceof ChildActivity) {
            ((ChildActivity) getActivity()).refreshCoinBalance();
        }
        soundHelper.playStreakSound(Math.min(streakCount, 15));
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
        int todayNew = db.wordReviewDao().getNewCount(cal.getTimeInMillis(), bankId);
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