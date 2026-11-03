// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BmBackgroundGeolocation",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "BmBackgroundGeolocation",
            targets: ["bm_geolocationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "bm_geolocationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/bm_geolocationPlugin"),
        .testTarget(
            name: "bm_geolocationPluginTests",
            dependencies: ["bm_geolocationPlugin"],
            path: "ios/Tests/bm_geolocationPluginTests")
    ]
)