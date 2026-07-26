package com.sister.habits.child;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.Task;

import java.util.List;

/**
 * 任务Fragment——孩子查看任务列表，完成任务
 */
public class TaskFragment extends Fragment {

    private AppDatabase db;
    private RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task, container, false);
        db = AppDatabase.getInstance(requireContext());

        recyclerView = view.findViewById(R.id.recycler_tasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        loadTasks();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTasks();
    }

    private void loadTasks() {
        List<Task> tasks = db.taskDao().getByStatus("active");
        recyclerView.setAdapter(new TaskAdapter(tasks, this::completeTask));
    }

    private void completeTask(Task task) {
        db.taskDao().complete(task.id, System.currentTimeMillis());

        // 发放金币奖励
        Integer balance = db.coinTransactionDao().getBalance("sister");
        int newBalance = (balance != null ? balance : 0) + task.rewardCoins;
        com.sister.habits.data.models.CoinTransaction ct =
                new com.sister.habits.data.models.CoinTransaction(
                        "sister", task.rewardCoins, newBalance,
                        "task_reward", "完成任务: " + task.title,
                        "");
        db.coinTransactionDao().insert(ct);

        loadTasks();
    }

    private static class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {
        private final List<Task> tasks;
        private final OnTaskCompleteListener listener;

        interface OnTaskCompleteListener { void onComplete(Task task); }

        TaskAdapter(List<Task> tasks, OnTaskCompleteListener listener) {
            this.tasks = tasks;
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
            Task task = tasks.get(position);
            holder.text1.setText("📋 " + task.title + "  🪙+" + task.rewardCoins);
            holder.text2.setText(task.description);
            holder.itemView.setOnClickListener(v -> listener.onComplete(task));
        }

        @Override
        public int getItemCount() { return tasks.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) { super(v); text1 = v.findViewById(android.R.id.text1); text2 = v.findViewById(android.R.id.text2); }
        }
    }
}