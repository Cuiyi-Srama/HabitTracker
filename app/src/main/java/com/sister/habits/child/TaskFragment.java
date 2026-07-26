package com.sister.habits.child;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sister.habits.R;
import com.sister.habits.data.AppDatabase;
import com.sister.habits.data.models.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务Fragment——孩子查看任务列表，完成任务
 * 工作流：完成任务 → 家长确认 → 获得积分
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
        List<Task> active = db.taskDao().getByStatus("active");
        List<Task> pending = db.taskDao().getByStatus("pending");
        List<Task> all = new ArrayList<>();
        all.addAll(active);
        all.addAll(pending);
        recyclerView.setAdapter(new TaskAdapter(all, this::markTaskDone));
    }

    private void markTaskDone(Task task) {
        if ("pending".equals(task.status)) {
            Toast.makeText(getContext(), "⏳ 任务已完成，等家长确认后获得金币～", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("confirmed".equals(task.status)) {
            Toast.makeText(getContext(), "✅ 任务已完成并获得奖励！", Toast.LENGTH_SHORT).show();
            return;
        }
        // 标记为"待家长确认"
        db.taskDao().markPending(task.id, System.currentTimeMillis());
        Toast.makeText(getContext(), "✅ 任务已完成！等待家长确认中～", Toast.LENGTH_SHORT).show();
        loadTasks();
    }

    private static class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {
        private final List<Task> tasks;
        private final OnTaskActionListener listener;

        interface OnTaskActionListener { void onAction(Task task); }

        TaskAdapter(List<Task> tasks, OnTaskActionListener listener) {
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
            String prefix, actionHint;
            if ("active".equals(task.status)) {
                prefix = "📋";
                actionHint = "点我完成任务";
            } else if ("pending".equals(task.status)) {
                prefix = "⏳";
                actionHint = "待家长确认";
            } else {
                prefix = "✅";
                actionHint = "已完成";
            }
            holder.text1.setText(prefix + " " + task.title + "  🪙+" + task.rewardCoins);
            holder.text2.setText(task.description + "  |  " + actionHint);
            holder.itemView.setOnClickListener(v -> listener.onAction(task));
        }

        @Override
        public int getItemCount() { return tasks.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) { super(v); text1 = v.findViewById(android.R.id.text1); text2 = v.findViewById(android.R.id.text2); }
        }
    }
}