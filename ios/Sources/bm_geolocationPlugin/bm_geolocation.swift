import Foundation
import CoreLocation
import UIKit

/// Core tracker: owns CLLocationManager, persists fixes and uploads them natively,
/// so tracking and sync keep working without the WebView.
@objc public class bm_geolocation: NSObject, CLLocationManagerDelegate {
    @objc public static let shared = bm_geolocation()

    var onLocation: (([String: Any]) -> Void)?
    var onHttp: (([String: Any]) -> Void)?

    private let manager = CLLocationManager()
    private let sampleManager = CLLocationManager()
    private let syncQueue = DispatchQueue(label: "bm_geolocation.sync")
    private var sampleCompletion: ((Result<CLLocation, Error>) -> Void)?
    private var authCompletion: (() -> Void)?
    private var wantsBackgroundAuth = false
    private var activeObserver: NSObjectProtocol?
    private var heartbeatTimer: Timer?
    private static let lastLocationKey = "bm_geolocation.lastLocationAt"

    private static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    override init() {
        super.init()
        manager.delegate = self
        sampleManager.delegate = self
        UIDevice.current.isBatteryMonitoringEnabled = true
    }

    // MARK: - Lifecycle

    /// Called when the plugin loads (also when the app is relaunched in background by a significant-change event).
    func resumeIfEnabled() {
        if BMConfig.enabled && hasAuthorization {
            beginUpdates()
        }
    }

    func start() {
        BMConfig.enabled = true
        beginUpdates()
    }

    func stop() {
        BMConfig.enabled = false
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        DispatchQueue.main.async {
            self.heartbeatTimer?.invalidate()
            self.heartbeatTimer = nil
        }
    }

    func applyConfigIfRunning() {
        if BMConfig.enabled && hasAuthorization { beginUpdates() }
    }

    private func beginUpdates() {
        DispatchQueue.main.async {
            let cfg = BMConfig.load()
            switch cfg.string("desiredAccuracy") {
            case "low": self.manager.desiredAccuracy = kCLLocationAccuracyKilometer
            case "balanced": self.manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
            default: self.manager.desiredAccuracy = kCLLocationAccuracyBest
            }
            self.manager.distanceFilter = max(cfg.double("distanceFilter", 10), 0) == 0 ? kCLDistanceFilterNone : cfg.double("distanceFilter", 10)
            self.manager.activityType = .automotiveNavigation
            self.manager.pausesLocationUpdatesAutomatically = false
            // Requires "location" in UIBackgroundModes, otherwise this raises an exception.
            if Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") is [String] {
                self.manager.allowsBackgroundLocationUpdates = true
                self.manager.showsBackgroundLocationIndicator = true
            }
            self.manager.startUpdatingLocation()
            if !cfg.bool("stopOnTerminate") && CLLocationManager.significantLocationChangeMonitoringAvailable() {
                // Lets iOS relaunch the app after it was terminated.
                self.manager.startMonitoringSignificantLocationChanges()
            }
            self.scheduleHeartbeat(cfg.double("heartbeatInterval"))
            self.sync(completion: nil) // flush backlog
        }
    }

    // MARK: - Heartbeat

    /// While the courier stands still (distanceFilter produces no updates) record a point every `interval` seconds.
    private func scheduleHeartbeat(_ interval: Double) {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
        guard interval > 0 else { return }
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            self?.heartbeatTick(interval)
        }
    }

    private func heartbeatTick(_ interval: Double) {
        let last = UserDefaults.standard.double(forKey: bm_geolocation.lastLocationKey)
        guard BMConfig.enabled, Date().timeIntervalSince1970 * 1000 - last >= interval * 1000 * 0.9 else { return }
        getCurrentPosition(timeout: 15) { result in
            guard case .success(var json) = result else { return }
            json.removeValue(forKey: "sample")
            json["heartbeat"] = true
            self.record(json, cfg: BMConfig.load())
        }
    }

    // MARK: - Permissions

    var authorizationStatus: CLAuthorizationStatus {
        if #available(iOS 14.0, *) { return manager.authorizationStatus }
        return CLLocationManager.authorizationStatus()
    }

    var hasAuthorization: Bool {
        let s = authorizationStatus
        return s == .authorizedAlways || s == .authorizedWhenInUse
    }

    func permissionState() -> [String: String] {
        let loc: String
        var bg: String
        switch authorizationStatus {
        case .notDetermined: loc = "prompt"; bg = "prompt"
        case .restricted, .denied: loc = "denied"; bg = "denied"
        case .authorizedWhenInUse: loc = "granted"; bg = "prompt"
        case .authorizedAlways: loc = "granted"; bg = "granted"
        @unknown default: loc = "prompt"; bg = "prompt"
        }
        return ["location": loc, "backgroundLocation": bg, "notifications": "granted"]
    }

    func requestPermissions(background: Bool, completion: @escaping () -> Void) {
        DispatchQueue.main.async {
            self.wantsBackgroundAuth = background
            self.authCompletion = completion
            let status = self.authorizationStatus
            if status == .notDetermined {
                self.manager.requestWhenInUseAuthorization()
            } else if background && status == .authorizedWhenInUse {
                self.manager.requestAlwaysAuthorization()
                self.finishWhenAlertDismissed()
            } else {
                self.finishAuth()
            }
        }
    }

    /// The upgrade prompt may not trigger a delegate callback if the user keeps the current choice.
    private func finishWhenAlertDismissed() {
        activeObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
        ) { [weak self] _ in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { self?.finishAuth() }
        }
    }

    private func finishAuth() {
        if let o = activeObserver { NotificationCenter.default.removeObserver(o); activeObserver = nil }
        let c = authCompletion
        authCompletion = nil
        c?()
    }

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager === self.manager else { return }
        let status = authorizationStatus
        if authCompletion != nil {
            if wantsBackgroundAuth && status == .authorizedWhenInUse {
                wantsBackgroundAuth = false
                self.manager.requestAlwaysAuthorization()
                finishWhenAlertDismissed()
            } else if status != .notDetermined {
                finishAuth()
            }
        }
        if BMConfig.enabled && hasAuthorization { beginUpdates() }
    }

    // MARK: - One-shot

    func getCurrentPosition(timeout: TimeInterval, completion: @escaping (Result<[String: Any], Error>) -> Void) {
        DispatchQueue.main.async {
            guard self.hasAuthorization else {
                completion(.failure(NSError(domain: "bm_geolocation", code: 1, userInfo: [NSLocalizedDescriptionKey: "Location permission not granted."])))
                return
            }
            var finished = false
            let finish: (Result<CLLocation, Error>) -> Void = { result in
                if finished { return }
                finished = true
                self.sampleCompletion = nil
                switch result {
                case .success(let l): completion(.success(self.json(for: l, sample: true)))
                case .failure(let e): completion(.failure(e))
                }
            }
            self.sampleCompletion = finish
            self.sampleManager.desiredAccuracy = kCLLocationAccuracyBest
            self.sampleManager.requestLocation()
            DispatchQueue.main.asyncAfter(deadline: .now() + timeout) {
                finish(.failure(NSError(domain: "bm_geolocation", code: 2, userInfo: [NSLocalizedDescriptionKey: "Location timeout"])))
            }
        }
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if manager === sampleManager {
            if let l = locations.last { sampleCompletion?(.success(l)) }
            return
        }
        let cfg = BMConfig.load()
        let maxAcc = cfg.double("maxAccuracy")
        for l in locations {
            if maxAcc > 0 && l.horizontalAccuracy >= 0 && l.horizontalAccuracy > maxAcc { continue }
            if l.horizontalAccuracy < 0 { continue } // invalid fix
            record(self.json(for: l, sample: false), cfg: cfg)
        }
    }

    private func record(_ json: [String: Any], cfg: [String: Any]) {
        BMStore.shared.insert(json)
        UserDefaults.standard.set(Date().timeIntervalSince1970 * 1000, forKey: bm_geolocation.lastLocationKey)
        BMStore.shared.prune(maxRecords: cfg.int("maxRecordsToPersist"), maxDays: cfg.int("maxDaysToPersist"))
        onLocation?(json)
        if cfg.bool("autoSync"), !cfg.string("url").isEmpty,
           BMStore.shared.count() >= max(1, cfg.int("autoSyncThreshold")) {
            sync(completion: nil)
        }
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        if manager === sampleManager { sampleCompletion?(.failure(error)) }
        // For the continuous manager errors (e.g. kCLErrorLocationUnknown) are transient: keep updating.
    }

    private func json(for l: CLLocation, sample: Bool) -> [String: Any] {
        let level = UIDevice.current.batteryLevel
        let state = UIDevice.current.batteryState
        var o: [String: Any] = [
            "uuid": UUID().uuidString,
            "timestamp": bm_geolocation.isoFormatter.string(from: l.timestamp),
            "coords": [
                "latitude": l.coordinate.latitude,
                "longitude": l.coordinate.longitude,
                "accuracy": l.horizontalAccuracy,
                "altitude": l.altitude,
                "speed": l.speed >= 0 ? l.speed : -1,
                "heading": l.course >= 0 ? l.course : -1
            ],
            "battery": ["level": level >= 0 ? Double(level) : -1.0, "is_charging": state == .charging || state == .full]
        ]
        if sample { o["sample"] = true }
        return o
    }

    // MARK: - Sync

    func sync(completion: ((_ count: Int, _ success: Bool) -> Void)?) {
        syncQueue.async {
            var bg = UIBackgroundTaskIdentifier.invalid
            DispatchQueue.main.sync { bg = UIApplication.shared.beginBackgroundTask(withName: "bm_geolocation.sync") {} }
            defer { DispatchQueue.main.async { if bg != .invalid { UIApplication.shared.endBackgroundTask(bg) } } }

            let cfg = BMConfig.load()
            let urlString = cfg.string("url")
            guard !urlString.isEmpty, let url = URL(string: urlString) else {
                completion?(0, true)
                return
            }
            let batch = cfg.bool("batchSync")
            let root = cfg.string("httpRootProperty", "location")
            var sent = 0

            while true {
                let rows = BMStore.shared.oldest(batch ? max(1, cfg.int("maxBatchSize", 100)) : 1)
                if rows.isEmpty { break }

                var body = (cfg["params"] as? [String: Any]) ?? [:]
                body[root] = batch ? rows.map { $0.location } : rows[0].location

                var req = URLRequest(url: url, timeoutInterval: cfg.double("httpTimeout", 15000) / 1000)
                req.httpMethod = cfg.string("method", "POST") == "PUT" ? "PUT" : "POST"
                req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                for (k, v) in (cfg["headers"] as? [String: Any]) ?? [:] { req.setValue("\(v)", forHTTPHeaderField: k) }
                req.httpBody = try? JSONSerialization.data(withJSONObject: body)

                var status = 0
                var text = ""
                let sem = DispatchSemaphore(value: 0)
                URLSession.shared.dataTask(with: req) { data, response, error in
                    status = (response as? HTTPURLResponse)?.statusCode ?? 0
                    if let d = data { text = String(data: d.prefix(65536), encoding: .utf8) ?? "" }
                    else if let e = error { text = e.localizedDescription }
                    sem.signal()
                }.resume()
                sem.wait()

                let ok = (200..<300).contains(status)
                self.onHttp?(["success": ok, "status": status, "responseText": text, "count": rows.count])
                if !ok {
                    // Keep data, retry on the next location / sync().
                    completion?(sent, false)
                    return
                }
                BMStore.shared.delete(ids: rows.map { $0.id })
                sent += rows.count
            }
            completion?(sent, true)
        }
    }

    // MARK: - Local queue helpers

    func locations(limit: Int) -> [[String: Any]] { BMStore.shared.oldest(limit).map { $0.location } }
    func count() -> Int { BMStore.shared.count() }
    func destroyLocations() { BMStore.shared.clear() }
}
