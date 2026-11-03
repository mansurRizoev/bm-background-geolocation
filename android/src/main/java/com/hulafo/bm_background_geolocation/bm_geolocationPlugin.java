package com.hulafo.bm_background_geolocation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "bm_geolocation",
    permissions = {
        @Permission(strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, alias = "location"),
        @Permission(strings = { Manifest.permission.ACCESS_BACKGROUND_LOCATION }, alias = "backgroundLocation"),
        @Permission(strings = { Manifest.permission.POST_NOTIFICATIONS }, alias = "notifications")
    }
)
public class bm_geolocationPlugin extends Plugin implements BmEvents.Listener {

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void load() {
        BmEvents.setListener(this);
        // App process was (re)created while tracking should be running: make sure the service is alive.
        BmConfig cfg = BmConfig.load(getContext());
        if (cfg.enabled && hasLocationPermission()) {
            try {
                BmTrackingService.startTracking(getContext());
            } catch (RuntimeException ignored) {}
        }
    }

    @Override
    protected void handleOnDestroy() {
        BmEvents.setListener(null);
        super.handleOnDestroy();
    }

    // ---- BmEvents.Listener ------------------------------------------------------------------

    @Override
    public void onLocation(JSONObject location) {
        notifyListeners("location", toJs(location));
    }

    @Override
    public void onHttp(JSONObject event) {
        notifyListeners("http", toJs(event));
    }

    // ---- Configuration / lifecycle ----------------------------------------------------------

    @PluginMethod
    public void configure(PluginCall call) {
        applyConfig(call);
    }

    @PluginMethod
    public void setConfig(PluginCall call) {
        applyConfig(call);
    }

    private void applyConfig(PluginCall call) {
        BmConfig cfg = BmConfig.load(getContext());
        cfg.apply(call.getData());
        cfg.save(getContext());
        // Restart location request with new parameters if tracking is active.
        if (cfg.enabled && hasLocationPermission()) {
            try {
                BmTrackingService.startTracking(getContext());
            } catch (RuntimeException ignored) {}
        }
        call.resolve(toJs(cfg.toJson(true)));
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (!hasLocationPermission()) {
            call.reject("Location permission not granted. Call requestPermissions() first.", "PERMISSION_DENIED");
            return;
        }
        BmConfig.setEnabled(getContext(), true);
        try {
            BmTrackingService.startTracking(getContext());
        } catch (RuntimeException e) {
            BmConfig.setEnabled(getContext(), false);
            call.reject("Unable to start tracking service: " + e.getMessage());
            return;
        }
        notifyListeners("enabledchange", new JSObject().put("enabled", true));
        call.resolve(state());
    }

    @PluginMethod
    public void stop(PluginCall call) {
        BmConfig.setEnabled(getContext(), false);
        BmTrackingService.stopTracking(getContext());
        notifyListeners("enabledchange", new JSObject().put("enabled", false));
        call.resolve(state());
    }

    @PluginMethod
    public void getState(PluginCall call) {
        call.resolve(state());
    }

    private JSObject state() {
        JSObject s = toJs(BmConfig.load(getContext()).toJson(true));
        s.put("lastLocationAt", BmConfig.getLastLocationAt(getContext()));
        return s;
    }

    // ---- One-shot position ------------------------------------------------------------------

    @PluginMethod
    @SuppressWarnings("MissingPermission")
    public void getCurrentPosition(PluginCall call) {
        if (!hasLocationPermission()) {
            call.reject("Location permission not granted.", "PERMISSION_DENIED");
            return;
        }
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(getContext());
        long timeout = call.getInt("timeout", 30000);
        CurrentLocationRequest req = new CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(timeout)
            .setMaxUpdateAgeMillis(5000)
            .build();
        client
            .getCurrentLocation(req, null)
            .addOnSuccessListener(l -> {
                if (l == null) call.reject("Location unavailable", "LOCATION_UNAVAILABLE");
                else call.resolve(toJs(BmLocationUtil.toJson(getContext(), l, true)));
            })
            .addOnFailureListener(e -> call.reject("Location failed: " + e.getMessage()));
    }

    // ---- Local queue ------------------------------------------------------------------------

    @PluginMethod
    public void getLocations(PluginCall call) {
        int limit = call.getInt("limit", 1000);
        io.execute(() -> {
            JSArray arr = new JSArray();
            List<BmDatabase.Row> rows = BmDatabase.get(getContext()).oldest(limit);
            for (BmDatabase.Row r : rows) arr.put(r.location);
            call.resolve(new JSObject().put("locations", arr));
        });
    }

    @PluginMethod
    public void getCount(PluginCall call) {
        io.execute(() -> call.resolve(new JSObject().put("count", BmDatabase.get(getContext()).count())));
    }

    @PluginMethod
    public void destroyLocations(PluginCall call) {
        io.execute(() -> {
            BmDatabase.get(getContext()).clear();
            call.resolve();
        });
    }

    @PluginMethod
    public void sync(PluginCall call) {
        io.execute(() -> {
            BmSync.Result r = BmSync.syncNow(getContext());
            call.resolve(new JSObject().put("count", r.count).put("success", r.success));
        });
    }

    // ---- Permissions ------------------------------------------------------------------------

    private boolean hasLocationPermission() {
        Context c = getContext();
        return (
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        );
    }

    private JSObject permissionResult() {
        PermissionState loc = getPermissionState("location");
        PermissionState bg = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? getPermissionState("backgroundLocation") : loc;
        PermissionState notif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? getPermissionState("notifications") : PermissionState.GRANTED;
        return new JSObject().put("location", loc.toString()).put("backgroundLocation", bg.toString()).put("notifications", notif.toString());
    }

    @PluginMethod
    @Override
    public void checkPermissions(PluginCall call) {
        call.resolve(permissionResult());
    }

    @PluginMethod
    @Override
    public void requestPermissions(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "locationPermsCallback");
        } else {
            continueAfterLocation(call);
        }
    }

    @PermissionCallback
    private void locationPermsCallback(PluginCall call) {
        continueAfterLocation(call);
    }

    private void continueAfterLocation(PluginCall call) {
        boolean wantsBackground = Boolean.TRUE.equals(call.getBoolean("background", false));
        if (
            wantsBackground &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            getPermissionState("location") == PermissionState.GRANTED &&
            getPermissionState("backgroundLocation") != PermissionState.GRANTED
        ) {
            requestPermissionForAlias("backgroundLocation", call, "backgroundPermsCallback");
        } else {
            continueAfterBackground(call);
        }
    }

    @PermissionCallback
    private void backgroundPermsCallback(PluginCall call) {
        continueAfterBackground(call);
    }

    private void continueAfterBackground(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "notificationsPermsCallback");
        } else {
            call.resolve(permissionResult());
        }
    }

    @PermissionCallback
    private void notificationsPermsCallback(PluginCall call) {
        call.resolve(permissionResult());
    }

    // ---- Battery optimization / vendor settings ---------------------------------------------

    @PluginMethod
    public void isIgnoringBatteryOptimizations(PluginCall call) {
        call.resolve(
            new JSObject()
                .put("ignoring", BmBatteryHelper.isIgnoringBatteryOptimizations(getContext()))
                .put("manufacturer", BmBatteryHelper.manufacturer())
        );
    }

    @PluginMethod
    public void requestIgnoreBatteryOptimizations(PluginCall call) {
        boolean shown = BmBatteryHelper.requestIgnoreBatteryOptimizations(getContext());
        call.resolve(new JSObject().put("shown", shown));
    }

    @PluginMethod
    public void openVendorSettings(PluginCall call) {
        try {
            call.resolve(new JSObject().put("opened", BmBatteryHelper.openVendorSettings(getContext())));
        } catch (Exception e) {
            call.reject("Unable to open settings: " + e.getMessage());
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static JSObject toJs(JSONObject o) {
        try {
            return new JSObject(o.toString());
        } catch (JSONException e) {
            return new JSObject();
        }
    }
}
