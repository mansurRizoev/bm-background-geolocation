import Foundation
import Capacitor
import UIKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(bm_geolocationPlugin)
public class bm_geolocationPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "bm_geolocationPlugin"
    public let jsName = "bm_geolocation"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setConfig", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getState", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCurrentPosition", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getLocations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCount", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "destroyLocations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "sync", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isIgnoringBatteryOptimizations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestIgnoreBatteryOptimizations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openVendorSettings", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = bm_geolocation.shared

    override public func load() {
        implementation.onLocation = { [weak self] loc in self?.notifyListeners("location", data: loc) }
        implementation.onHttp = { [weak self] ev in self?.notifyListeners("http", data: ev) }
        implementation.resumeIfEnabled()
    }

    @objc func configure(_ call: CAPPluginCall) { applyConfig(call) }
    @objc func setConfig(_ call: CAPPluginCall) { applyConfig(call) }

    private func applyConfig(_ call: CAPPluginCall) {
        BMConfig.apply(call.options as? [String: Any] ?? [:])
        implementation.applyConfigIfRunning()
        call.resolve(BMConfig.state())
    }

    @objc func start(_ call: CAPPluginCall) {
        guard implementation.hasAuthorization else {
            call.reject("Location permission not granted. Call requestPermissions() first.", "PERMISSION_DENIED")
            return
        }
        implementation.start()
        notifyListeners("enabledchange", data: ["enabled": true])
        call.resolve(BMConfig.state())
    }

    @objc func stop(_ call: CAPPluginCall) {
        implementation.stop()
        notifyListeners("enabledchange", data: ["enabled": false])
        call.resolve(BMConfig.state())
    }

    @objc func getState(_ call: CAPPluginCall) {
        call.resolve(BMConfig.state())
    }

    @objc func getCurrentPosition(_ call: CAPPluginCall) {
        let timeout = Double(call.getInt("timeout") ?? 30000) / 1000
        implementation.getCurrentPosition(timeout: timeout) { result in
            switch result {
            case .success(let loc): call.resolve(loc)
            case .failure(let err): call.reject(err.localizedDescription)
            }
        }
    }

    @objc func getLocations(_ call: CAPPluginCall) {
        let limit = call.getInt("limit") ?? 1000
        DispatchQueue.global().async {
            call.resolve(["locations": self.implementation.locations(limit: limit)])
        }
    }

    @objc func getCount(_ call: CAPPluginCall) {
        DispatchQueue.global().async { call.resolve(["count": self.implementation.count()]) }
    }

    @objc func destroyLocations(_ call: CAPPluginCall) {
        DispatchQueue.global().async {
            self.implementation.destroyLocations()
            call.resolve()
        }
    }

    @objc func sync(_ call: CAPPluginCall) {
        implementation.sync { count, success in
            call.resolve(["count": count, "success": success])
        }
    }

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        call.resolve(implementation.permissionState())
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        let background = call.getBool("background") ?? false
        implementation.requestPermissions(background: background) {
            call.resolve(self.implementation.permissionState())
        }
    }

    // Battery optimization does not exist on iOS; kept for a uniform JS API.
    @objc func isIgnoringBatteryOptimizations(_ call: CAPPluginCall) {
        call.resolve(["ignoring": true, "manufacturer": "apple"])
    }

    @objc func requestIgnoreBatteryOptimizations(_ call: CAPPluginCall) {
        call.resolve(["shown": false])
    }

    @objc func openVendorSettings(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
            }
            call.resolve(["opened": "app"])
        }
    }
}
