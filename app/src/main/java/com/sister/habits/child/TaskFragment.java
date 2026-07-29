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
import com.sister.habits.data.models.CoinEarning;
import com.sister.habits.sync.EarningService;
import com.sister.habits.sync.SyncManager;
import com.sister.habits.utils.NotificationHelper;

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
        List<Task> allTasks = db.taskDao().getAll();
        autoResetRecurringTasks(allTasks);
        List<Task> active = db.taskDao().getByStatus("active");
        List<Task> pending = db.taskDao().getByStatus("pending");

        // 过滤已过期的限时任务
        long now = System.currentTimeMillis();
        List<Task> filteredActive = new ArrayList<>();
        for (Task t : active) {
            if ("timed".equals(t.recurrenceType) && t.deadline > 0 && t.deadline < now) {
                // 已过期，自动标记为expired
                t.status = "expired";
                db.taskDao().update(t);
                continue;
            }
            filteredActive.add(t);
        }

        List<Task> all = new ArrayList<>();
        all.addAll(filteredActive);
        all.addAll(pending);
        recyclerView.setAdapter(new TaskAdapter(all, this::markTaskDone));
    }

    /**
     * 自动重置循环任务
     * - permanent: 确认后立即重置为active
     * - weekly: 新的一周开始后重置
     * - monthly: 新的一月开始后重置
     */
    private void autoResetRecurringTasks(List<Task> tasks) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (Task task : tasks) {
            if (!"confirmed".equals(task.status)) continue;

            boolean shouldReset = false;
            if ("permanent".equals(task.recurrenceType)) {
                shouldReset = true;
            } else if ("weekly".equals(task.recurrenceType)) {
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                if (task.confirmedAt < cal.getTimeInMillis()) shouldReset = true;
            } else if ("monthly".equals(task.recurrenceType)) {
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                if (task.confirmedAt < cal.getTimeInMillis()) shouldReset = true;
            }

            if (shouldReset) {
                task.status = "active";
                task.completedAt = 0;
                task.confirmedAt = 0;
                db.taskDao().update(task);
            }
        }
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

        // 发送通知给家长
        NotificationHelper.createChannel(requireContext());
        NotificationHelper.notifyTaskCompleted(requireContext(), task.title, task.id);

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
            String prefix, actionHint, extraInfo = "";
            if ("active".equals(task.status)) {
                if ("timed".equals(task.recurrenceType) && task.deadline > 0) {
                    prefix = "⏰";
                    long remaining = task.deadline - System.currentTimeMillis();
                    long days = remaining / (24L * 3600 * 1000);
                    extraInfo = " 剩余" + (days > 0 ? days + "天" : "今天截止");
                    actionHint = "点我完成任务";
                } else {
                    prefix = "📋";
                    actionHint = "点我完成任务";
                }
            } else if ("pending".equals(task.status)) {
                prefix = "⏳";
                actionHint = "待家长确认";
            } else {
                prefix = "✅";
                actionHint = "已完成";
            }
            holder.text1.setText(prefix + " " + task.title + "  🪙+" + task.rewardCoins);
            holder.text2.setText(task.description + "  |  " + actionHint + extraInfo);
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