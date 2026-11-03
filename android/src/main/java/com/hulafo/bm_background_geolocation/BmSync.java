package com.hulafo.bm_background_geolocation;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Uploads queued locations. Rows are deleted only after a 2xx answer, so nothing is lost while offline. */
public final class BmSync {

    private static final Object LOCK = new Object();

    private BmSync() {}

    public static class Result {

        public int count;
        public boolean success = true;
    }

    /** Blocking; call from a background thread. */
    public static Result syncNow(Context ctx) {
        synchronized (LOCK) {
            Result result = new Result();
            BmConfig cfg = BmConfig.load(ctx);
            if (cfg.url == null || cfg.url.isEmpty()) return result;
            if (!cfg.allowCleartext && !cfg.url.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
                JSONObject ev = new JSONObject();
                try {
                    ev.put("success", false).put("status", 0).put("count", 0);
                    ev.put("responseText", "Cleartext HTTP is not allowed. Use https:// or set allowCleartext for development.");
                } catch (Exception ignored) {}
                BmEvents.http(ev);
                result.success = false;
                return result;
            }
            if (!BmHosts.isAllowed(ctx, cfg.url)) {
                JSONObject ev = new JSONObject();
                try {
                    ev.put("success", false).put("status", 0).put("count", 0);
                    ev.put("responseText", "Host is not in the native allow-list (ALLOWED_HOSTS).");
                } catch (Exception ignored) {}
                BmEvents.http(ev);
                result.success = false;
                return result;
            }
            BmDatabase db = BmDatabase.get(ctx);

            while (true) {
                List<BmDatabase.Row> rows = db.oldest(cfg.batchSync ? cfg.maxBatchSize : 1);
                if (rows.isEmpty()) break;

                int status = 0;
                String text = "";
                boolean ok = false;
                try {
                    JSONObject body = new JSONObject(cfg.params.toString());
                    if (cfg.batchSync) {
                        JSONArray arr = new JSONArray();
                        for (BmDatabase.Row r : rows) arr.put(r.location);
                        body.put(cfg.httpRootProperty, arr);
                    } else {
                        body.put(cfg.httpRootProperty, rows.get(0).location);
                    }
                    HttpURLConnection c = (HttpURLConnection) new URL(cfg.url).openConnection();
                    try {
                        // Never follow redirects: they could forward the Authorization header / location data to another host.
                        c.setInstanceFollowRedirects(false);
                        c.setConnectTimeout(cfg.httpTimeout);
                        c.setReadTimeout(cfg.httpTimeout);
                        c.setRequestMethod("PUT".equalsIgnoreCase(cfg.method) ? "PUT" : "POST");
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json");
                        Iterator<String> keys = cfg.headers.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            c.setRequestProperty(k, cfg.headers.optString(k));
                        }
                        try (OutputStream os = c.getOutputStream()) {
                            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                        }
                        status = c.getResponseCode();
                        InputStream is = status >= 400 ? c.getErrorStream() : c.getInputStream();
                        text = is == null ? "" : read(is);
                        ok = status >= 200 && status < 300;
                    } finally {
                        c.disconnect();
                    }
                } catch (Exception e) {
                    text = String.valueOf(e.getMessage());
                }

                JSONObject event = new JSONObject();
                try {
                    event.put("success", ok);
                    event.put("status", status);
                    event.put("responseText", text);
                    event.put("count", rows.size());
                } catch (Exception ignored) {}
                BmEvents.http(event);

                if (!ok) {
                    // Keep the data and retry on the next location / sync() (e.g. after the token is refreshed via setConfig()).
                    result.success = false;
                    break;
                }
                db.deleteRows(rows);
                result.count += rows.size();
            }
            return result;
        }
    }

    private static String read(InputStream is) throws IOException {
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 65536) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
