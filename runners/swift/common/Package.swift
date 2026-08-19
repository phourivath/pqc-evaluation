// swift-tools-version: 6.1

import PackageDescription

let package = Package(
    name: "PqcEvaluationSwiftSupport",
    products: [
        .library(
            name: "PqcEvaluationSwiftSupport",
            targets: ["PqcEvaluationSwiftSupport"])
    ],
    targets: [
        .target(name: "PqcEvaluationSwiftSupport"),
        .testTarget(
            name: "PqcEvaluationSwiftSupportTests",
            dependencies: ["PqcEvaluationSwiftSupport"])
    ])
