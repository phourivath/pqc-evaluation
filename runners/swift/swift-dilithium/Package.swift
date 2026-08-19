// swift-tools-version: 6.1

import PackageDescription

let package = Package(
    name: "PqcEvaluationSwiftDilithiumRunner",
    products: [
        .executable(name: "swift-dilithium-runner", targets: ["SwiftDilithiumRunner"])
    ],
    dependencies: [
        .package(path: "../common"),
        .package(url: "https://github.com/leif-ibsen/SwiftDilithium.git", exact: "3.6.0"),
        .package(url: "https://github.com/leif-ibsen/ASN1.git", exact: "2.7.0"),
        .package(url: "https://github.com/leif-ibsen/BigInt.git", exact: "1.23.0"),
        .package(url: "https://github.com/leif-ibsen/Digest.git", exact: "1.13.0")
    ],
    targets: [
        .executableTarget(
            name: "SwiftDilithiumRunner",
            dependencies: [
                .product(name: "PqcEvaluationSwiftSupport", package: "PqcEvaluationSwiftSupport"),
                .product(name: "SwiftDilithium", package: "SwiftDilithium")
            ]),
        .testTarget(
            name: "SwiftDilithiumRunnerTests",
            dependencies: [
                .product(name: "SwiftDilithium", package: "SwiftDilithium")
            ])
    ])
