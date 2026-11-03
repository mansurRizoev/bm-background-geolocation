package com.hulafo.bm_background_geolocation;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

public final class BmLocationUtil {

    private BmLocationUtil() {}

    public static JSONObject toJson(Context ctx, Location l, boolean sample) {
        JSONObject o = new JSONObject();
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            o.put("uuid", UUID.randomUUID().toString());
            o.put("timestamp", f.format(new Date(l.getTime())));

            double speed = l.hasSpeed() ? l.getSpeed() : -1;
            JSONObject c = new JSONObject();
            c.put("latitude", l.getLatitude());
            c.put("longitude", l.getLongitude());
            c.put("accuracy", l.hasAccuracy() ? l.getAccuracy() : -1);
            c.put("altitude", l.hasAltitude() ? l.getAltitude() : 0);
            c.put("speed", speed);
            c.put("heading", l.hasBearing() ? l.getBearing() : -1);
            o.put("coords", c);

            // DeliveryApp/SendGPS ожидает формат Transistorsoft: массив location[]
            // с is_moving + activity (без них отвечает Code 999).
            boolean moving = speed >= 0.5;
            o.put("is_moving", moving);
            o.put("odometer", 0);
            JSONObject activity = new JSONObject();
            activity.put("type", moving ? "in_vehicle" : "still");
            activity.put("confidence", moving ? 75 : 100);
            o.put("activity", activity);
            o.put("extras", new JSONObject());

            double level = -1;
            boolean charging = false;
            Intent i = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (lv >= 0 && sc > 0) level = lv / (double) sc;
                int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            }
            JSONObject b = new JSONObject();
            // Порядок как у Transistorsoft
            b.put("is_charging", charging);
            b.put("level", level);
            o.put("battery", b);
            if (sample) o.put("sample", true);
        } catch (JSONException ignored) {}
        return o;
    }
}
