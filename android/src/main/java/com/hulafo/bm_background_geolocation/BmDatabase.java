package com.hulafo.bm_background_geolocation;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Local queue of recorded locations that have not been uploaded yet. */
public class BmDatabase extends SQLiteOpenHelper {

    private static BmDatabase instance;

    public static synchronized BmDatabase get(Context ctx) {
        if (instance == null) instance = new BmDatabase(ctx.getApplicationContext());
        return instance;
    }

    private BmDatabase(Context ctx) {
        super(ctx, "bm_geolocation.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE locations (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT UNIQUE, ts INTEGER, json TEXT)");
        db.execSQL("CREATE INDEX idx_locations_ts ON locations(ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public synchronized void insert(JSONObject location, long ts) {
        ContentValues v = new ContentValues();
        v.put("uuid", location.optString("uuid"));
        v.put("ts", ts);
        v.put("json", location.toString());
        getWritableDatabase().insertWithOnConflict("locations", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** Oldest first. */
    public synchronized List<Row> oldest(int limit) {
        List<Row> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, json FROM locations ORDER BY id ASC LIMIT ?", new String[] { String.valueOf(limit) })) {
            while (c.moveToNext()) {
                try {
                    rows.add(new Row(c.getLong(0), new JSONObject(c.getString(1))));
                } catch (JSONException ignored) {}
            }
        }
        return rows;
    }

    public synchronized int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM locations", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public synchronized void deleteRows(List<Row> rows) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Row r : rows) db.delete("locations", "id = ?", new String[] { String.valueOf(r.id) });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void clear() {
        getWritableDatabase().delete("locations", null, null);
    }

    public synchronized void prune(int maxRecords, int maxDays) {
        SQLiteDatabase db = getWritableDatabase();
        if (maxDays > 0) {
            long cutoff = System.currentTimeMillis() - maxDays * 86400000L;
            db.delete("locations", "ts < ?", new String[] { String.valueOf(cutoff) });
        }
        if (maxRecords > 0) {
            db.execSQL("DELETE FROM locations WHERE id NOT IN (SELECT id FROM locations ORDER BY id DESC LIMIT ?)", new Object[] { maxRecords });
        }
    }

    public static class Row {

        public final long id;
        public final JSONObject location;

        Row(long id, JSONObject location) {
            this.id = id;
            this.location = location;
        }
    }
}
