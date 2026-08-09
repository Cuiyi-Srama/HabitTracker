package com.sister.habits.child;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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

        // 答题推进的 Handler：Fragment 销毁时须移除，防止回调访问已销毁 View 导致闪退
    private final android.os.Handler quizHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable nextWordRunnable = this::showNextWord;

private AppDatabase db;
    private SyncManager syncManager;
    private SoundHelper soundHelper;

    private TextView tvStats, tvWord, tvPhonetic, tvPrompt, tvStreak, btnSpeak;
    private Button btnOption1, btnOption2, btnOption3, btnOption4;
    private Button btnDontKnow;
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

        btnDontKnow = view.findViewById(R.id.btn_dont_know);
        if (btnDontKnow != null) {
            btnDontKnow.setOnClickListener(v -> showDontKnowDialog());
        }
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
    public void onDestroyView() {
        // 移除待执行的答题推进回调（切 Tab 后不再访问已销毁 View）
        quizHandler.removeCallbacks(nextWordRunnable);
        super.onDestroyView();
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
            long todayStart = cal.getTimeInMillis();
            // 当天固定词表：首次进入随机锁定限额个词，当天不再变化
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
            String planKey = "plan_" + sdf.format(new java.util.Date()) + "_" + bankId;
            SharedPreferences prefs = requireContext().getSharedPreferences("wordbank_prefs", 0);
            String planJson = prefs.getString(planKey, null);
            java.util.List<String> planIds = new ArrayList<>();
            if (planJson == null || planJson.isEmpty()) {
                // 首次：从全库随机抽限额个（跳过今天已学过的）
                List<Vocabulary> allActive = db.vocabularyDao().getActiveUnmastered(bankId);
                Collections.shuffle(allActive);
                for (Vocabulary v : allActive) {
                    WordReview wr = db.wordReviewDao().getByWordId(v.id, bankId);
                    if (wr == null || wr.lastReviewedAt < todayStart) {
                        planIds.add(v.id);
                        if (planIds.size() >= dailyWordLimit) break;
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < planIds.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(planIds.get(i));
                }
                prefs.edit().putString(planKey, sb.toString()).apply();
            } else {
                String[] arr = planJson.split(",");
                for (String s : arr) if (!s.isEmpty()) planIds.add(s);
            }
            // 从词表中取今天未学过的词
            words = new ArrayList<>();
            for (String id : planIds) {
                WordReview wr = db.wordReviewDao().getByWordId(id, bankId);
                if (wr == null || wr.lastReviewedAt < todayStart) {
                    Vocabulary v = db.vocabularyDao().getById(id);
                    if (v != null && v.active && !v.mastered) words.add(v);
                }
            }
            tvPrompt.setText("📚 今日新词 (" + words.size() + "/" + planIds.size() + " 可学)  答对一题 +2分 🪙（家长审批到账）");
        }

        if (words.isEmpty()) {
            // 学习模式下先检查词库是否有词：区分"词库为空"与"今日已学完"
            boolean bankHasWords = false;
            if (!isReviewMode) {
                try {
                    bankHasWords = db.vocabularyDao().getActiveCount(bankId) > 0;
                } catch (Exception ignored) {}
            }
            if (!isReviewMode && !bankHasWords) {
                tvWord.setText("📭 词库暂无单词");
                tvPhonetic.setText("当前词库没有可用单词");
                tvPrompt.setText("请家长在「家长管理 → 设置 → 词库管理」中检查词库或切换到其他词库");
            } else {
                tvWord.setText("🎉 全部完成！");
                tvPhonetic.setText(isReviewMode ? "复习完了，真棒 🎉" : "今日新词已学完 🎉");
                tvPrompt.setText(isReviewMode
                        ? "所有单词都检测通过了！去逛逛商城吧 🏪"
                        : "今天的新词学完了，切到「复习检测」通关再 +2分/词 🪙");
            }
            for (Button btn : optionButtons) btn.setVisibility(View.GONE);
            updateStats();
            return;
        }

        Collections.shuffle(words);
        quizQueue = new ArrayList<>(words);
        if (btnNewWords != null) btnNewWords.setEnabled(false);
        if (btnReview != null) btnReview.setEnabled(false);
        updateStats();
        showNextWord();
    }

    private void showNextWord() {
        // Fragment 可能已销毁（切 Tab/退出）：直接返回，避免 NPE 闪退
        if (!isAdded() || getView() == null) return;
        if (quizQueue.isEmpty()) {
            if (isReviewMode) {
                // 复习模式：一轮结束，检查是否全部答对
                if (wrongInReviewSet.isEmpty()) {
                    // ✅ 全部答对 → 发放积分
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
                    // ❌ 有答错 → 错词循环：只重来答错的词，全部答对即通关（不再整组重来）
                    int wrongCount = wrongInReviewSet.size();
                    List<Vocabulary> wrongWords = new ArrayList<>();
                    if (reviewAllWords != null) {
                        for (Vocabulary v : reviewAllWords) {
                            if (wrongInReviewSet.contains(v.id)) {
                                wrongWords.add(v);
                            }
                        }
                    }
                    Collections.shuffle(wrongWords);
                    quizQueue = new ArrayList<>(wrongWords);
                    wrongInReviewSet.clear();
                    tvPrompt.setText("💪 答错 " + wrongCount + " 个～把这 " + wrongCount + " 个复习对就通关！加油！");
                    quizHandler.postDelayed(nextWordRunnable, 800);
                    return;
                }
            }
            tvWord.setText("🎉 本轮完成！");
            tvPhonetic.setText("");
            tvPrompt.setText("点击「📚新词」或「🔄复习」继续");
            for (Button btn : optionButtons) btn.setVisibility(View.GONE);
            // 恢复按钮
            if (btnNewWords != null) btnNewWords.setEnabled(true);
            if (btnReview != null) btnReview.setEnabled(true);
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
        if (btnDontKnow != null) btnDontKnow.setVisibility(View.VISIBLE);
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
        if (btnDontKnow != null) btnDontKnow.setVisibility(View.GONE);
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

            if (isReviewMode) {
                // 复习模式：不单独发积分，最后统一发
                tvPrompt.setText("✅ 答对了！继续加油 💪");
                soundHelper.playCorrectSound();
            } else {
                // 学习模式：每个词一次机会，答对得2积分（提交家长审批）
                int coinReward = 2;
                if (!EarningService.isWithinLimit(getContext(), coinReward)) {
                    tvPrompt.setText("⚠️ 今日积分已达上限 (" + EarningService.getDailySoftLimit(getContext()) + "分)");
                    soundHelper.playClickSound();
                } else {
                    CoinEarning earning = new CoinEarning();
                    earning.amount = coinReward;
                    earning.sourceType = "word_learn";
                    earning.sourceId = currentWord.id;
                    earning.description = "新词学习: " + currentWord.word;
                    earning.deviceId = com.sister.habits.sync.SyncManager.getInstance(getContext()).getDeviceId();
                    db.coinEarningDao().insert(earning);

                    // 实时刷新预估
                    if (getActivity() instanceof ChildActivity) {
                        ((ChildActivity) getActivity()).refreshCoinBalance();
                    }

                    tvPrompt.setText("📖 已记住: " + currentWord.word + "  +" + coinReward + "分（待家长审批）");
                    soundHelper.playClickSound();
                }
            }

            // 答对更新复习状态
            updateWordReview(currentWord.id, true);

        } else {
            // ❌ 答错
            clicked.setBackgroundColor(0xFFE53935);
            clicked.setTextColor(0xFFFFFFFF);
            streakCount = 0;
            soundHelper.playErrorVibration();

            if (isReviewMode) {
                // 复习模式：记录答错的词，纠错重来
                wrongInReviewSet.add(currentWord.id);
                tvPrompt.setText("🌸 答错了～" + currentWord.meaning + " 才是对的，纠错时间！💪");
            } else {
                // 学习模式：答错不扣分，直接进复习区
                tvPrompt.setText("🌸 " + currentWord.meaning + " 才对哦，已加入复习区～");
            }

            // 答错更新复习状态
            updateWordReview(currentWord.id, false);
        }

        syncManager.onDataChanged();
        updateStats();
        updateStreakDisplay();

        // 学习模式：答对答错都跳过该词（仅一次机会）
        quizHandler.postDelayed(nextWordRunnable, 1200);
    }

    /**
     * 更新复习状态
     * 
     * 核心逻辑：
     * - 学习模式答对 → 创建记录，7天后复习（标记已见）
     * - 学习模式答错 → 创建记录，立即进复习队列
     * - 复习模式答对 → 推进stage，加权间隔
     * - 复习模式答错 → nextReviewAt=现在，立即纠错
     */
    /** 😅 我不会：详情弹窗 + 加入复习 */
    private void showDontKnowDialog() {
        if (!isAnswering || currentWord == null) return;
        isAnswering = false;
        if (btnDontKnow != null) btnDontKnow.setVisibility(View.GONE);
        final Vocabulary word = currentWord;

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 16);

        TextView tvWord = new TextView(getContext());
        tvWord.setText(word.word);
        tvWord.setTextSize(34);
        tvWord.setGravity(android.view.Gravity.CENTER);
        tvWord.setTextColor(0xFF333333);
        tvWord.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvWord);

        TextView tvPhon = new TextView(getContext());
        tvPhon.setText(word.phonetic != null && !word.phonetic.isEmpty() ? word.phonetic : "");
        tvPhon.setTextSize(16);
        tvPhon.setGravity(android.view.Gravity.CENTER);
        tvPhon.setTextColor(0xFF888888);
        layout.addView(tvPhon);

        TextView tvMeaning = new TextView(getContext());
        tvMeaning.setText("📖 " + word.meaning);
        tvMeaning.setTextSize(22);
        tvMeaning.setGravity(android.view.Gravity.CENTER);
        tvMeaning.setTextColor(0xFFE65100);
        tvMeaning.setPadding(0, 12, 0, 16);
        layout.addView(tvMeaning);

        LinearLayout btnRow = new LinearLayout(getContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.CENTER);
        btnRow.setPadding(0, 8, 0, 8);
        Button btnSpeakEn = new Button(getContext());
        btnSpeakEn.setText("🔊 朗读单词");
        btnSpeakEn.setOnClickListener(v -> soundHelper.speakWord(word.word));
        Button btnSpeakCn = new Button(getContext());
        btnSpeakCn.setText("🔊 朗读中文");
        btnSpeakCn.setOnClickListener(v -> soundHelper.speakChinese(word.meaning));
        btnRow.addView(btnSpeakEn);
        btnRow.addView(btnSpeakCn);
        layout.addView(btnRow);

        new android.app.AlertDialog.Builder(getContext())
            .setCancelable(false)  // 禁止点空白处/返回键关闭，避免跳过单词
            .setTitle("😅 看一下再记")
            .setView(layout)
            .setPositiveButton("下一个吧 ➡️", (d, w) -> {
                updateWordReview(word.id, false);
                soundHelper.playClickSound();
                showNextWord();
            })
            .show();
        soundHelper.speakWord(word.word);
    }

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