package com.hulafo.bm_background_geolocation;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.net.URL;
import java.util.Locale;

/**
 * Host allow-list for the upload endpoint. It is read from the host app's AndroidManifest, so JavaScript
 * (e.g. injected by an XSS) cannot change it:
 *
 * <pre>
 * &lt;meta-data android:name="com.hulafo.bm_geolocation.ALLOWED_HOSTS" android:value="api.example.com,*.example.org" /&gt;
 * </pre>
 *
 * "example.com" matches exactly that host; "*.example.com" matches its subdomains. If the entry is absent, any host is allowed.
 */
public final class BmHosts {

    static final String META_KEY = "com.hulafo.bm_geolocation.ALLOWED_HOSTS";

    private BmHosts() {}

    public static boolean isAllowed(Context ctx, String url) {
        if (url == null || url.isEmpty()) return true;
        String list = configured(ctx);
        if (list == null || list.trim().isEmpty()) return true;
        String host;
        try {
            host = new URL(url).getHost();
        } catch (Exception e) {
            return false;
        }
        if (host == null || host.isEmpty()) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String raw : list.split(",")) {
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) continue;
            if (entry.startsWith("*.")) {
                if (host.endsWith(entry.substring(1)) && host.length() > entry.length() - 1) return true;
            } else if (host.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    private static String configured(Context ctx) {
        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA);
            Bundle md = ai.metaData;
            return md == null ? null : md.getString(META_KEY);
        } catch (Exception e) {
            return null;
        }
    }
}
