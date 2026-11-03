package com.hulafo.bm_background_geolocation;

import android.content.Context;
import java.util.Map;

/**
 * Handles "wake_tracking" data pushes sent by the backend watchdog.
 * Works without the WebView / JS: if tracking should be running, the foreground service is (re)started
 * and a fresh point is recorded and uploaded immediately.
 */
public final class BmFcm {

    /** Value of data["type"] that wakes the tracker. */
    public static final String WAKE_TYPE = "wake_tracking";

    private BmFcm() {}

    /**
     * Call this from your own FirebaseMessagingService.onMessageReceived() if the app already has one:
     * {@code if (BmFcm.handleMessage(this, remoteMessage.getData())) return;}
     *
     * @return true if the message was a wake-up for the tracker.
     */
    public static boolean handleMessage(Context ctx, Map<String, String> data) {
        if (data == null || !WAKE_TYPE.equals(data.get("type"))) return false;
        BmConfig cfg = BmConfig.load(ctx);
        if (!cfg.enabled) return true; // courier is off shift: nothing to wake
        try {
            // High-priority FCM messages are allowed to start a foreground service from the background.
            BmTrackingService.wakeTracking(ctx.getApplicationContext());
        } catch (RuntimeException ignored) {
            // Missing background-location permission or OS restriction.
        }
        return true;
    }
}
