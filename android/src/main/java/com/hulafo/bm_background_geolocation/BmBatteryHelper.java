package com.hulafo.bm_background_geolocation;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/** Battery-optimization helpers, including vendor-specific autostart / background screens. */
public final class BmBatteryHelper {

    private BmBatteryHelper() {}

    /** {package, class} pairs per manufacturer, tried in order. */
    private static final String[][] XIAOMI = {
        { "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity" },
        { "com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity" }
    };
    private static final String[][] HUAWEI = {
        { "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity" },
        { "com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity" },
        { "com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity" }
    };
    private static final String[][] SAMSUNG = {
        { "com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity" },
        { "com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity" },
        { "com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity" }
    };
    private static final String[][] OPPO = {
        { "com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity" },
        { "com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity" }
    };
    private static final String[][] VIVO = {
        { "com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity" },
        { "com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity" }
    };

    public static String manufacturer() {
        return Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
    }

    public static boolean isIgnoringBatteryOptimizations(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    /** Shows the system "allow app to always run in background" dialog. Falls back to the settings list. */
    @SuppressLint("BatteryLife")
    public static boolean requestIgnoreBatteryOptimizations(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return false;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /** @return "vendor" if a manufacturer screen was opened, otherwise "app" (app details screen). */
    public static String openVendorSettings(Context ctx) {
        String m = manufacturer();
        String[][] candidates = null;
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) candidates = XIAOMI;
        else if (m.contains("huawei") || m.contains("honor")) candidates = HUAWEI;
        else if (m.contains("samsung")) candidates = SAMSUNG;
        else if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) candidates = OPPO;
        else if (m.contains("vivo")) candidates = VIVO;

        if (candidates != null) {
            for (String[] c : candidates) {
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName(c[0], c[1]));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    return "vendor";
                } catch (Exception ignored) {
                    // screen not present on this ROM version; try next
                }
            }
        }
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return "app";
    }
}
