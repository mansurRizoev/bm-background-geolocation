import XCTest
@testable import bm_geolocationPlugin

class bm_geolocationTests: XCTestCase {
    func testConfigDefaultsAndPatch() {
        let cfg = BMConfig.apply(["distanceFilter": 25, "url": "https://example.com/track", "unknown": 1])
        XCTAssertEqual(cfg.double("distanceFilter"), 25)
        XCTAssertEqual(cfg.string("url"), "https://example.com/track")
        XCTAssertNil(cfg["unknown"])
        XCTAssertEqual(cfg.string("httpRootProperty"), "location")
    }

    func testStoreQueue() {
        BMStore.shared.clear()
        BMStore.shared.insert(["uuid": "a", "coords": ["latitude": 1.0, "longitude": 2.0]])
        BMStore.shared.insert(["uuid": "b", "coords": ["latitude": 3.0, "longitude": 4.0]])
        XCTAssertEqual(BMStore.shared.count(), 2)
        let rows = BMStore.shared.oldest(1)
        XCTAssertEqual(rows.first?.location["uuid"] as? String, "a")
        BMStore.shared.delete(ids: rows.map { $0.id })
        XCTAssertEqual(BMStore.shared.count(), 1)
        BMStore.shared.clear()
    }
}
