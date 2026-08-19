// swift-tools-version: 6.1

import PackageDescription

let package = Package(
    name: "PqcEvaluationCryptoKitRunner",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "cryptokit-runner", targets: ["CryptoKitRunner"])
    ],
    dependencies: [
        .package(path: "../common")
    ],
    targets: [
        .executableTarget(
            name: "CryptoKitRunner",
            dependencies: [
                .product(name: "PqcEvaluationSwiftSupport", package: "PqcEvaluationSwiftSupport")
            ]),
        .testTarget(
            name: "CryptoKitRunnerTests",
            dependencies: [])
    ])
