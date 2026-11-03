package com.hulafo.bm_background_geolocation;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

/** Persistent plugin configuration (shared between the plugin, the service and the boot receiver). */
public class BmConfig {

    private static final String PREFS = "bm_geolocation";
    private static final String KEY_CONFIG = "config";
    private static final String KEY_ENABLED = "enabled";

    public String desiredAccuracy = "high";
    public float distanceFilter = 10;
    public long locationUpdateInterval = 5000;
    public long fastestLocationUpdateInterval = 2000;
    public float maxAccuracy = 100;
    public boolean stopOnTerminate = false;
    public boolean startOnBoot = true;
    /** Seconds. If >0 and no location was recorded for this long (courier stands still), a heartbeat point is recorded. */
    public int heartbeatInterval = 0;

    public String url = "";
    public String method = "POST";
    public JSONObject headers = new JSONObject();
    public JSONObject params = new JSONObject();
    public String httpRootProperty = "location";
    public boolean autoSync = true;
    public boolean batchSync = false;
    public int maxBatchSize = 100;
    public int autoSyncThreshold = 0;
    public int httpTimeout = 15000;

    public int maxRecordsToPersist = 10000;
    public int maxDaysToPersist = 3;

    public String notificationTitle = "Tracking location";
    public String notificationText = "Location tracking is active";
    public String notificationChannelName = "Location tracking";
    public String notificationSmallIcon = "";

    public boolean enabled = false;

    public static BmConfig load(Context ctx) {
        BmConfig c = new BmConfig();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_CONFIG, null);
        if (raw != null) {
            try {
                c.apply(new JSONObject(raw));
            } catch (JSONException ignored) {}
        }
        c.enabled = sp.getBoolean(KEY_ENABLED, false);
        return c;
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CONFIG, toJson(false).toString()).apply();
    }

    public static void setLastLocationAt(Context ctx, long ts) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("lastLocationAt", ts).apply();
    }

    public static long getLastLocationAt(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lastLocationAt", 0);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    /** Merges the keys present in {@code o} over the current values. */
    public void apply(JSONObject o) {
        if (o == null) return;
        desiredAccuracy = o.optString("desiredAccuracy", desiredAccuracy);
        distanceFilter = (float) o.optDouble("distanceFilter", distanceFilter);
        locationUpdateInterval = o.optLong("locationUpdateInterval", locationUpdateInterval);
        fastestLocationUpdateInterval = o.optLong("fastestLocationUpdateInterval", fastestLocationUpdateInterval);
        maxAccuracy = (float) o.optDouble("maxAccuracy", maxAccuracy);
        stopOnTerminate = o.optBoolean("stopOnTerminate", stopOnTerminate);
        startOnBoot = o.optBoolean("startOnBoot", startOnBoot);
        heartbeatInterval = Math.max(0, o.optInt("heartbeatInterval", heartbeatInterval));
        url = o.optString("url", url);
        method = o.optString("method", method);
        if (o.optJSONObject("headers") != null) headers = o.optJSONObject("headers");
        if (o.optJSONObject("params") != null) params = o.optJSONObject("params");
        httpRootProperty = o.optString("httpRootProperty", httpRootProperty);
        autoSync = o.optBoolean("autoSync", autoSync);
        batchSync = o.optBoolean("batchSync", batchSync);
        maxBatchSize = Math.max(1, o.optInt("maxBatchSize", maxBatchSize));
        autoSyncThreshold = o.optInt("autoSyncThreshold", autoSyncThreshold);
        httpTimeout = o.optInt("httpTimeout", httpTimeout);
        maxRecordsToPersist = o.optInt("maxRecordsToPersist", maxRecordsToPersist);
        maxDaysToPersist = o.optInt("maxDaysToPersist", maxDaysToPersist);
        JSONObject n = o.optJSONObject("notification");
        if (n != null) {
            notificationTitle = n.optString("title", notificationTitle);
            notificationText = n.optString("text", notificationText);
            notificationChannelName = n.optString("channelName", notificationChannelName);
            notificationSmallIcon = n.optString("smallIcon", notificationSmallIcon);
        }
    }

    public JSONObject toJson(boolean withEnabled) {
        JSONObject o = new JSONObject();
        try {
            o.put("desiredAccuracy", desiredAccuracy);
            o.put("distanceFilter", distanceFilter);
            o.put("locationUpdateInterval", locationUpdateInterval);
            o.put("fastestLocationUpdateInterval", fastestLocationUpdateInterval);
            o.put("maxAccuracy", maxAccuracy);
            o.put("stopOnTerminate", stopOnTerminate);
            o.put("startOnBoot", startOnBoot);
            o.put("heartbeatInterval", heartbeatInterval);
            o.put("url", url);
            o.put("method", method);
            o.put("headers", headers);
            o.put("params", params);
            o.put("httpRootProperty", httpRootProperty);
            o.put("autoSync", autoSync);
            o.put("batchSync", batchSync);
            o.put("maxBatchSize", maxBatchSize);
            o.put("autoSyncThreshold", autoSyncThreshold);
            o.put("httpTimeout", httpTimeout);
            o.put("maxRecordsToPersist", maxRecordsToPersist);
            o.put("maxDaysToPersist", maxDaysToPersist);
            JSONObject n = new JSONObject();
            n.put("title", notificationTitle);
            n.put("text", notificationText);
            n.put("channelName", notificationChannelName);
            n.put("smallIcon", notificationSmallIcon);
            o.put("notification", n);
            if (withEnabled) o.put("enabled", enabled);
        } catch (JSONException ignored) {}
        return o;
    }
}
