package com.hulafo.bm_background_geolocation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Foreground service that keeps collecting locations while the app is in background or even terminated.
 * Locations are persisted and uploaded natively, so the WebView / JS does not need to be alive.
 */
public class BmTrackingService extends Service {

    private static final String CHANNEL_ID = "bm_geolocation_tracking";
    private static final int NOTIFICATION_ID = 7741;
    static final String EXTRA_WAKE = "wake";

    private FusedLocationProviderClient client;
    private LocationCallback callback;
    private LocationCallback heartbeatCallback;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean updating = false;

    public static void startTracking(Context ctx) {
        ContextCompat.startForegroundService(ctx, new Intent(ctx, BmTrackingService.class));
    }

    /** Starts the service and records a fresh point immediately (used by the FCM wake-up). */
    public static void wakeTracking(Context ctx) {
        ContextCompat.startForegroundService(ctx, new Intent(ctx, BmTrackingService.class).putExtra(EXTRA_WAKE, true));
    }

    public static void stopTracking(Context ctx) {
        ctx.stopService(new Intent(ctx, BmTrackingService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        client = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        BmConfig cfg = BmConfig.load(this);
        Notification n = buildNotification(cfg);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (RuntimeException e) {
            // e.g. ForegroundServiceStartNotAllowedException when started from background without permission
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.getBooleanExtra(EXTRA_WAKE, false) && cfg.enabled) {
            // Woken by a push from the server: record a point right away so the backend sees us alive.
            requestImmediateFix();
        }

        if (!cfg.enabled) {
            // System restarted the service after the user stopped tracking.
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        requestUpdates(cfg);
        io.execute(() -> BmSync.syncNow(getApplicationContext())); // flush backlog from previous session
        return START_STICKY;
    }

    @SuppressWarnings("MissingPermission")
    private void requestUpdates(BmConfig cfg) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            BmConfig.setEnabled(this, false);
            stopForeground(true);
            stopSelf();
            return;
        }
        removeUpdates();

        int priority;
        switch (cfg.desiredAccuracy) {
            case "low":
                priority = Priority.PRIORITY_LOW_POWER;
                break;
            case "balanced":
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
                break;
            default:
                priority = Priority.PRIORITY_HIGH_ACCURACY;
        }
        LocationRequest req = new LocationRequest.Builder(priority, cfg.locationUpdateInterval)
            .setMinUpdateIntervalMillis(cfg.fastestLocationUpdateInterval)
            .setMinUpdateDistanceMeters(cfg.distanceFilter)
            .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                for (Location l : result.getLocations()) handle(l, false);
            }
        };
        client.requestLocationUpdates(req, callback, Looper.getMainLooper());

        if (cfg.heartbeatInterval > 0) {
            // Second, time-driven request without distance filter: keeps ticking while the courier stands still.
            final long hbMs = cfg.heartbeatInterval * 1000L;
            LocationRequest hbReq = new LocationRequest.Builder(priority, hbMs).setMinUpdateIntervalMillis(hbMs / 2).build();
            heartbeatCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult result) {
                    Location l = result.getLastLocation();
                    if (l == null) return;
                    long age = System.currentTimeMillis() - BmConfig.getLastLocationAt(getApplicationContext());
                    if (age >= hbMs * 0.9) handle(l, true);
                }
            };
            client.requestLocationUpdates(hbReq, heartbeatCallback, Looper.getMainLooper());
        }
        updating = true;
    }

    @SuppressWarnings("MissingPermission")
    private void requestImmediateFix() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        CurrentLocationRequest req = new CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(20000)
            .setMaxUpdateAgeMillis(10000)
            .build();
        client.getCurrentLocation(req, null).addOnSuccessListener(l -> {
            if (l != null) handle(l, true);
        });
    }

    private void handle(Location l, boolean heartbeat) {
        final BmConfig cfg = BmConfig.load(this);
        if (cfg.maxAccuracy > 0 && l.hasAccuracy() && l.getAccuracy() > cfg.maxAccuracy) return;
        final JSONObject json = BmLocationUtil.toJson(this, l, false);
        if (heartbeat) {
            try {
                json.put("heartbeat", true);
            } catch (org.json.JSONException ignored) {}
        }
        io.execute(() -> {
            BmDatabase db = BmDatabase.get(getApplicationContext());
            db.insert(json, System.currentTimeMillis());
            BmConfig.setLastLocationAt(getApplicationContext(), System.currentTimeMillis());
            db.prune(cfg.maxRecordsToPersist, cfg.maxDaysToPersist);
            BmEvents.location(json);
            if (cfg.autoSync && cfg.url != null && !cfg.url.isEmpty() && db.count() >= Math.max(1, cfg.autoSyncThreshold)) {
                BmSync.syncNow(getApplicationContext());
            }
        });
    }

    private void removeUpdates() {
        if (updating && callback != null) {
            client.removeLocationUpdates(callback);
        }
        if (heartbeatCallback != null) {
            client.removeLocationUpdates(heartbeatCallback);
        }
        updating = false;
        callback = null;
        heartbeatCallback = null;
    }

    private Notification buildNotification(BmConfig cfg) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, cfg.notificationChannelName, NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        int icon = 0;
        if (cfg.notificationSmallIcon != null && !cfg.notificationSmallIcon.isEmpty()) {
            icon = getResources().getIdentifier(cfg.notificationSmallIcon, "drawable", getPackageName());
            if (icon == 0) icon = getResources().getIdentifier(cfg.notificationSmallIcon, "mipmap", getPackageName());
        }
        if (icon == 0) icon = android.R.drawable.ic_menu_mylocation;

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(cfg.notificationTitle)
            .setContentText(cfg.notificationText)
            .setSmallIcon(icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW);

        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            b.setContentIntent(PendingIntent.getActivity(this, 0, launch, piFlags));
        }
        return b.build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        BmConfig cfg = BmConfig.load(this);
        if (cfg.stopOnTerminate) {
            BmConfig.setEnabled(this, false);
            stopForeground(true);
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        removeUpdates();
        io.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
