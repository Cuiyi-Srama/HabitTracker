package com.sister.habits.sync;

import android.content.Context;
import android.util.Log;

public class RemoteSync {
    private static final String TAG = "RemoteSync";

    public RemoteSync(Context context, Object db, Object merger, Object gson) {
        Log.d(TAG, "Remote sync not configured, using LAN/QR sync");
    }

    public void syncAll() {
        Log.d(TAG, "Remote sync skipped");
    }
}
