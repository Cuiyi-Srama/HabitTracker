package com.sister.habits.utils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.PixelCopy;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Window snapshot tool (PixelCopy, zero permission).
 *
 * Principle: the app reads back its own window via PixelCopy from its EGL
 * pipeline, bypassing the system screenshot service (SurfaceControl.captureDisplay).
 * Therefore it is NOT affected by vivo's screenshot block on overlay virtual
 * displays -- a window on the virtual display can be captured the same way.
 *
 * Usage:
 *   1. Call SnapshotHelper.register(this) in Activity.onResume();
 *   2. SnapshotServer calls capture() automatically on GET /snapshot.png
 */
public class SnapshotHelper {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private static File lastFile = null;

    private SnapshotHelper() {}

    /** Register the current foreground activity (call in onResume). */
    public static void register(Activity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    /** Get the most recent successful snapshot file. */
    public static File getLastFile() {
        return lastFile;
    }

    /**
     * Capture the current window and save it as PNG (blocking, max 1.5s).
     * @return the file on success, null on failure
     */
    public static File capture() {
        final Activity activity = currentActivity.get();
        if (activity == null || Build.VERSION.SDK_INT < 26) {
            return null;
        }
        final File out = new File(activity.getFilesDir(), "snapshot.png");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean ok = new AtomicBoolean(false);

        MAIN.post(() -> {
            try {
                final View decor = activity.getWindow().getDecorView();
                final Bitmap bmp = Bitmap.createBitmap(
                        Math.max(decor.getWidth(), 1),
                        Math.max(decor.getHeight(), 1),
                        Bitmap.Config.ARGB_8888);
                PixelCopy.request(activity.getWindow(), bmp, result -> {
                    if (result == PixelCopy.SUCCESS) {
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
                            ok.set(true);
                        } catch (Exception ignored) {
                        }
                    }
                    bmp.recycle();
                    latch.countDown();
                }, MAIN);
            } catch (Exception e) {
                latch.countDown();
            }
        });

        try {
            latch.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }

        if (ok.get() && out.exists() && out.length() > 0) {
            lastFile = out;
            return out;
        }
        return null;
    }
}
