package com.hulafo.bm_background_geolocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Resumes tracking after the device was rebooted or the app was updated. */
public class BmBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        BmConfig cfg = BmConfig.load(context);
        if (!cfg.enabled || !cfg.startOnBoot || cfg.stopOnTerminate) return;
        try {
            BmTrackingService.startTracking(context);
        } catch (RuntimeException ignored) {
            // Background start may be refused by the OS (missing background-location permission).
        }
    }
}
