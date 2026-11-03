package com.hulafo.bm_background_geolocation;

import org.json.JSONObject;

/** In-process bridge from the native service to the Capacitor plugin (only while the WebView is alive). */
public final class BmEvents {

    public interface Listener {
        void onLocation(JSONObject location);

        void onHttp(JSONObject event);
    }

    private static volatile Listener listener;

    private BmEvents() {}

    public static void setListener(Listener l) {
        listener = l;
    }

    static void location(JSONObject o) {
        Listener l = listener;
        if (l != null) l.onLocation(o);
    }

    static void http(JSONObject o) {
        Listener l = listener;
        if (l != null) l.onHttp(o);
    }
}
