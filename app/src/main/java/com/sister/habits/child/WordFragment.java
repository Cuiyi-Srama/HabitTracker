package com.sister.habits.child;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.Vocabulary;
import com.sister.habits.sync.SyncManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 单词学习Fragment——显示当前单词列表，背诵后标记为已掌握
 * 三年级词库
 */
public class WordFragment extends Fragment {

    private AppDatabase db;
    private SyncManager syncManager;
    private RecyclerView recyclerView;
    private TextView tvStats;
    private List<Vocabulary> currentWords = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_word, container, false);
        db = AppDatabase.getInstance(requireContext());
        syncManager = SyncManager.getInstance(requireContext());

        tvStats = view.findViewById(R.id.tv_word_stats);
        recyclerView = view.findViewById(R.id.recycler_words);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        Button btnNewWords = view.findViewById(R.id.btn_new_words);
        btnNewWords.setOnClickListener(v -> loadNewWords());

        Button btnReview = view.findViewById(R.id.btn_review);
        btnReview.setOnClickListener(v -> loadReviewWords());

        loadNewWords();
        return view;
    }

    private void loadNewWords() {
        currentWords = db.vocabularyDao().getUnmastered();
        if (currentWords.size() > 10) currentWords = currentWords.subList(0, 10);
        updateStats();
        recyclerView.setAdapter(new WordAdapter(currentWords, this::markMastered));
    }

    private void loadReviewWords() {
        currentWords = db.vocabularyDao().getRandom(5);
        updateStats();
        recyclerView.setAdapter(new WordAdapter(currentWords, this::markMastered));
    }

    private void updateStats() {
        int mastered = db.vocabularyDao().getMasteredCount();
        int total = db.vocabularyDao().getUnmasteredCount() + mastered;
        tvStats.setText("📖 已掌握 " + mastered + "/" + total + " 个单词");
    }

    private void markMastered(Vocabulary word) {
        db.vocabularyDao().markMastered(word.id, System.currentTimeMillis());

        // 发放金币奖励
        Integer balance = db.coinTransactionDao().getBalance("sister");
        int reward = 5; // 每个词5金币
        int newBalance = (balance != null ? balance : 0) + reward;
        com.sister.habits.data.models.CoinTransaction ct =
                new com.sister.habits.data.models.CoinTransaction(
                        "sister", reward, newBalance,
                        "word_learn", "学会单词: " + word.word,
                        syncManager.getDeviceId());
        db.coinTransactionDao().insert(ct);
        syncManager.onDataChanged();

        loadNewWords();
    }

    private static class WordAdapter extends RecyclerView.Adapter<WordAdapter.ViewHolder> {
        private final List<Vocabulary> words;
        private final OnWordClickListener listener;

        interface OnWordClickListener { void onClick(Vocabulary word); }

        WordAdapter(List<Vocabulary> words, OnWordClickListener listener) {
            this.words = words;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Vocabulary word = words.get(position);
            holder.text1.setText("📝 " + word.word + "  " + word.phonetic);
            holder.text2.setText(word.meaning + "  [难度" + word.level + "]");
            holder.itemView.setOnClickListener(v -> listener.onClick(word));
        }

        @Override
        public int getItemCount() { return words.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) { super(v); text1 = v.findViewById(android.R.id.text1); text2 = v.findViewById(android.R.id.text2); }
        }
    }
}