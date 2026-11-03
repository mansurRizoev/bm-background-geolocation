import Foundation
import Security

/// Secrets (HTTP headers such as Authorization) live in the Keychain, never in UserDefaults.
enum BMKeychain {
    private static let service = "bm_geolocation"

    static func set(_ value: String?, account: String) {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(base as CFDictionary)
        guard let value = value, let data = value.data(using: .utf8) else { return }
        var item = base
        item[kSecValueData as String] = data
        // Readable after the first unlock so that tracking can upload while the phone is locked / after a reboot.
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }

    static func get(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

/// Host allow-list read from Info.plist (key `BMGeolocationAllowedHosts`, array of strings), so JavaScript cannot change it.
/// "example.com" matches exactly; "*.example.com" matches subdomains. Without the key any host is allowed.
enum BMHosts {
    static func isAllowed(_ urlString: String) -> Bool {
        if urlString.isEmpty { return true }
        guard let list = Bundle.main.object(forInfoDictionaryKey: "BMGeolocationAllowedHosts") as? [String], !list.isEmpty else {
            return true
        }
        guard let host = URL(string: urlString)?.host?.lowercased(), !host.isEmpty else { return false }
        for raw in list {
            let entry = raw.trimmingCharacters(in: .whitespaces).lowercased()
            if entry.isEmpty { continue }
            if entry.hasPrefix("*.") {
                if host.hasSuffix(String(entry.dropFirst())) && host.count > entry.count - 1 { return true }
            } else if host == entry {
                return true
            }
        }
        return false
    }
}
