package com.sister.habits.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.sister.habits.parent.ParentActivity;

public class NotificationHelper {
    private static final String CHANNEL_ID = "parent_approval";
    private static final String CHANNEL_NAME = "家长审批";

    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("孩子提交兑换申请或完成任务时通知家长");
            channel.setShowBadge(true);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    public static void notifyRedemption(Context ctx, String itemName, String redemptionId) {
        Intent intent = new Intent(ctx, ParentActivity.class);
        intent.putExtra("open_approval", "redemption");
        intent.putExtra("item_id", redemptionId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, redemptionId.hashCode(),
            intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\ud83d\uded2 新的兑换申请")
            .setContentText("孩子想兑换：" + itemName)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1000 + redemptionId.hashCode(), builder.build());
    }

    public static void notifyTaskCompleted(Context ctx, String taskName, String taskId) {
        Intent intent = new Intent(ctx, ParentActivity.class);
        intent.putExtra("open_approval", "task");
        intent.putExtra("item_id", taskId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 2000 + taskId.hashCode(),
            intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\ud83d\uddcb 任务待确认")
            .setContentText("孩子完成了：" + taskName)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(2000 + taskId.hashCode(), builder.build());
    }

    /** 📝 孩子提交作业通知家长 */
    public static void notifyGateSubmission(Context ctx, String date) {
        Intent intent = new Intent(ctx, ParentActivity.class);
        intent.putExtra("open_approval", "gate");
        intent.putExtra("gate_date", date);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 3000 + date.hashCode(),
            intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📝 作业提交")
            .setContentText("孩子已提交今日作业，请审核")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(3000 + date.hashCode(), builder.build());
    }
}
