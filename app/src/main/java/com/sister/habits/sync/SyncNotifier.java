package com.sister.habits.sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * v3.0.73：同步通知工具（后台同步时通知栏显示进度）
 * - notifySyncStart：同步开始 → "正在同步中"（持续通知）
 * - notifyDone：同步完成 → "已完成 ✅ / 失败 ❌ + 原因"
 */
public class SyncNotifier {
    private static final String CHANNEL_ID = "habit_sync_channel";
    private static final int NOTIFY_ID = 1001;

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "同步通知", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("后台同步进度通知");
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
    }

    public static void notifySyncStart(Context ctx) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID) : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("🔄 正在同步")
                    .setContentText("数据正在同步到中心服务器，请在后台稍候…")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(openAppPendingIntent(ctx));
            nm.notify(NOTIFY_ID, b.build());
        } catch (Exception ignored) {}
    }

    public static void notifyDone(Context ctx, boolean ok, String detail) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID) : new Notification.Builder(ctx);
            String title = ok ? "✅ 同步已完成" : "❌ 同步失败";
            String text = ok ? "所有数据已同步，各设备保持一致"
                    : (detail == null || detail.isEmpty() ? "请检查网络或服务器是否开启" : "原因: " + detail);
            b.setSmallIcon(ok ? android.R.drawable.stat_notify_sync : android.R.drawable.stat_notify_error)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(openAppPendingIntent(ctx));
            nm.notify(NOTIFY_ID, b.build());
        } catch (Exception ignored) {}
    }

    private static PendingIntent openAppPendingIntent(Context ctx) {
        try {
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
            return i != null ? PendingIntent.getActivity(ctx, 0, i, flags) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
