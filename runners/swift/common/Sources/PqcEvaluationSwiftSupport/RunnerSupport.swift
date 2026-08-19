import Foundation

public struct SwiftToolchainMetadata: Sendable {
    public let swiftVersion: String
    public let xcodeVersion: String
    public let sdkVersion: String

    public init(swiftVersion: String, xcodeVersion: String, sdkVersion: String) {
        self.swiftVersion = swiftVersion
        self.xcodeVersion = xcodeVersion
        self.sdkVersion = sdkVersion
    }
}

public enum RunnerSupport {
    public static let message = Data("PQC evaluation message".utf8)
    public static let context = Data("pqc-evaluation".utf8)

    public static func generatedAt() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: Date())
    }

    public static func runtime(
        api: String,
        backend: String,
        packageRevision: String,
        swiftToolsVersion: String,
        deploymentTarget: String,
        toolchain: SwiftToolchainMetadata,
        additional: [String: String] = [:]) -> RuntimeInfo {
        var properties = [
            "language": "swift",
            "api": api,
            "backend": backend,
            "packageRevision": packageRevision,
            "swiftToolsVersion": swiftToolsVersion,
            "swiftVersion": toolchain.swiftVersion,
            "xcodeVersion": toolchain.xcodeVersion,
            "sdkVersion": toolchain.sdkVersion,
            "deploymentTarget": deploymentTarget
        ]
        properties.merge(additional) { _, new in new }
        return RuntimeInfo(
            javaVersion: nil,
            javaVendor: nil,
            osName: operatingSystemName,
            osVersion: operatingSystemVersion(),
            architecture: architecture,
            buildProperties: properties)
    }

    public static func toolchainMetadataFromEnvironment() throws -> SwiftToolchainMetadata {
        let environment = ProcessInfo.processInfo.environment
        guard let swiftVersion = nonBlank(environment["PQC_SWIFT_VERSION"]) else {
            throw RunnerSupportError.missingMetadata("PQC_SWIFT_VERSION")
        }
        let xcodeVersion = try platformMetadata(environment, key: "PQC_XCODE_VERSION")
        let sdkVersion = try platformMetadata(environment, key: "PQC_SDK_VERSION")
        return SwiftToolchainMetadata(
            swiftVersion: swiftVersion,
            xcodeVersion: xcodeVersion,
            sdkVersion: sdkVersion)
    }

    public static func write(_ result: EvaluationResult, to path: String) throws {
        let url = URL(fileURLWithPath: path)
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(result)
        try data.write(to: url, options: .atomic)
    }

    public static func check(
        _ id: String,
        parameterSet: ParameterSet,
        category: String,
        status: String,
        message: String) -> CheckResult {
        CheckResult(
            id: id,
            parameterSet: parameterSet.rawValue,
            category: category,
            status: status,
            message: message)
    }

    public static func capability(
        _ operation: String,
        status: String,
        origin: String,
        evidence: String,
        reason: String? = nil) -> Capability {
        Capability(
            operation: operation,
            status: status,
            origin: origin,
            evidence: evidence,
            reason: reason)
    }

    public static func pass(_ value: Bool) -> String { value ? "pass" : "fail" }

    public static func unsupportedRepresentation(
        _ kind: String,
        origin: String,
        reason: String) -> Representation {
        Representation(
            kind: kind,
            status: "unsupported",
            origin: origin,
            reason: reason)
    }

    private static var architecture: String {
        #if arch(arm64)
        return "arm64"
        #elseif arch(x86_64)
        return "x86_64"
        #else
        return "unknown"
        #endif
    }

    private static var operatingSystemName: String {
        #if os(macOS)
        return "macOS"
        #elseif os(Linux)
        return "Linux"
        #elseif os(Windows)
        return "Windows"
        #else
        return "unknown"
        #endif
    }

    private static func operatingSystemVersion() -> String {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }

    private static func platformMetadata(
        _ environment: [String: String],
        key: String) throws -> String {
        if let value = nonBlank(environment[key]) {
            return value
        }
        if operatingSystemName == "macOS" {
            throw RunnerSupportError.missingMetadata(key)
        }
        return "not-applicable"
    }

    private static func nonBlank(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

private enum RunnerSupportError: Error, CustomStringConvertible {
    case missingMetadata(String)

    var description: String {
        switch self {
        case .missingMetadata(let key):
            return "missing required runner metadata: \(key)"
        }
    }
}
