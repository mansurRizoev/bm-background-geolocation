import Foundation
import SQLite3

/// Persistent configuration (UserDefaults), stored as a plain dictionary merged over the defaults.
enum BMConfig {
    private static let configKey = "bm_geolocation.config"
    private static let enabledKey = "bm_geolocation.enabled"

    static let defaults: [String: Any] = [
        "desiredAccuracy": "high",
        "distanceFilter": 10.0,
        "locationUpdateInterval": 5000,
        "fastestLocationUpdateInterval": 2000,
        "maxAccuracy": 100.0,
        "stopOnTerminate": false,
        "startOnBoot": true,
        "heartbeatInterval": 0,
        "url": "",
        "allowCleartext": false,
        "method": "POST",
        "headers": [String: Any](),
        "params": [String: Any](),
        "httpRootProperty": "location",
        "autoSync": true,
        "batchSync": false,
        "maxBatchSize": 100,
        "autoSyncThreshold": 0,
        "httpTimeout": 15000,
        "maxRecordsToPersist": 10000,
        "maxDaysToPersist": 3,
        "notification": [String: Any]()
    ]

    static let mask = "***"
    private static let headersAccount = "headers"
    private static var cachedHeaders: [String: Any]?
    private static let lock = NSLock()

    /// Full configuration including real header values (internal use only).
    static func load() -> [String: Any] {
        var cfg = defaults
        if let saved = UserDefaults.standard.dictionary(forKey: configKey) {
            for (k, v) in saved where k != "headers" { cfg[k] = v }
        }
        cfg["headers"] = headers()
        return cfg
    }

    private static func headers() -> [String: Any] {
        lock.lock(); defer { lock.unlock() }
        if let c = cachedHeaders { return c }
        var h: [String: Any] = [:]
        if let s = BMKeychain.get(account: headersAccount),
           let o = try? JSONSerialization.jsonObject(with: Data(s.utf8)) as? [String: Any] { h = o }
        cachedHeaders = h
        return h
    }

    private static func storeHeaders(_ patch: [String: Any]) {
        let old = headers()
        var merged: [String: Any] = [:]
        for (k, v) in patch {
            if let s = v as? String, s == mask { if let o = old[k] { merged[k] = o } } else { merged[k] = "\(v)" }
        }
        lock.lock(); cachedHeaders = merged; lock.unlock()
        if merged.isEmpty { BMKeychain.set(nil, account: headersAccount); return }
        if let d = try? JSONSerialization.data(withJSONObject: merged), let s = String(data: d, encoding: .utf8) {
            BMKeychain.set(s, account: headersAccount)
        }
    }

    @discardableResult
    static func apply(_ patch: [String: Any]) -> [String: Any] {
        var saved = UserDefaults.standard.dictionary(forKey: configKey) ?? [:]
        if let h = patch["headers"] as? [String: Any] { storeHeaders(h) }
        saved.removeValue(forKey: "headers") // never keep secrets in UserDefaults
        for (k, v) in patch where defaults[k] != nil && k != "headers" { saved[k] = v }
        UserDefaults.standard.set(saved, forKey: configKey)
        return load()
    }

    static var enabled: Bool {
        get { UserDefaults.standard.bool(forKey: enabledKey) }
        set { UserDefaults.standard.set(newValue, forKey: enabledKey) }
    }

    static func state() -> [String: Any] {
        var s = load()
        var masked: [String: Any] = [:]
        for k in (s["headers"] as? [String: Any] ?? [:]).keys { masked[k] = mask }
        s["headers"] = masked
        s["enabled"] = enabled
        s["lastLocationAt"] = UserDefaults.standard.double(forKey: "bm_geolocation.lastLocationAt")
        return s
    }
}

extension Dictionary where Key == String, Value == Any {
    func double(_ key: String, _ fallback: Double = 0) -> Double { (self[key] as? NSNumber)?.doubleValue ?? fallback }
    func int(_ key: String, _ fallback: Int = 0) -> Int { (self[key] as? NSNumber)?.intValue ?? fallback }
    func bool(_ key: String, _ fallback: Bool = false) -> Bool { (self[key] as? NSNumber)?.boolValue ?? fallback }
    func string(_ key: String, _ fallback: String = "") -> String { (self[key] as? String) ?? fallback }
}

/// SQLite queue of recorded locations not yet uploaded.
final class BMStore {
    static let shared = BMStore()

    private var db: OpaquePointer?
    private let queue = DispatchQueue(label: "bm_geolocation.store")
    private let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let path = dir.appendingPathComponent("bm_geolocation.sqlite").path
        if sqlite3_open(path, &db) == SQLITE_OK {
            exec("CREATE TABLE IF NOT EXISTS locations (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT UNIQUE, ts INTEGER, json TEXT)")
            exec("CREATE INDEX IF NOT EXISTS idx_locations_ts ON locations(ts)")
        }
    }

    private func exec(_ sql: String) {
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    func insert(_ location: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: location),
              let json = String(data: data, encoding: .utf8) else { return }
        let uuid = location["uuid"] as? String ?? UUID().uuidString
        queue.sync {
            var stmt: OpaquePointer?
            sqlite3_prepare_v2(db, "INSERT OR IGNORE INTO locations (uuid, ts, json) VALUES (?, ?, ?)", -1, &stmt, nil)
            sqlite3_bind_text(stmt, 1, uuid, -1, transient)
            sqlite3_bind_int64(stmt, 2, Int64(Date().timeIntervalSince1970 * 1000))
            sqlite3_bind_text(stmt, 3, json, -1, transient)
            sqlite3_step(stmt)
            sqlite3_finalize(stmt)
        }
    }

    /// Oldest first.
    func oldest(_ limit: Int) -> [(id: Int64, location: [String: Any])] {
        queue.sync {
            var rows: [(Int64, [String: Any])] = []
            var stmt: OpaquePointer?
            sqlite3_prepare_v2(db, "SELECT id, json FROM locations ORDER BY id ASC LIMIT ?", -1, &stmt, nil)
            sqlite3_bind_int(stmt, 1, Int32(limit))
            while sqlite3_step(stmt) == SQLITE_ROW {
                let id = sqlite3_column_int64(stmt, 0)
                if let c = sqlite3_column_text(stmt, 1),
                   let obj = try? JSONSerialization.jsonObject(with: Data(String(cString: c).utf8)) as? [String: Any] {
                    rows.append((id, obj))
                }
            }
            sqlite3_finalize(stmt)
            return rows
        }
    }

    func count() -> Int {
        queue.sync {
            var stmt: OpaquePointer?
            var n = 0
            sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM locations", -1, &stmt, nil)
            if sqlite3_step(stmt) == SQLITE_ROW { n = Int(sqlite3_column_int(stmt, 0)) }
            sqlite3_finalize(stmt)
            return n
        }
    }

    func delete(ids: [Int64]) {
        queue.sync {
            for id in ids { exec("DELETE FROM locations WHERE id = \(id)") }
        }
    }

    func clear() {
        queue.sync { exec("DELETE FROM locations") }
    }

    func prune(maxRecords: Int, maxDays: Int) {
        queue.sync {
            if maxDays > 0 {
                let cutoff = Int64((Date().timeIntervalSince1970 - Double(maxDays) * 86400) * 1000)
                exec("DELETE FROM locations WHERE ts < \(cutoff)")
            }
            if maxRecords > 0 {
                exec("DELETE FROM locations WHERE id NOT IN (SELECT id FROM locations ORDER BY id DESC LIMIT \(maxRecords))")
            }
        }
    }
}
