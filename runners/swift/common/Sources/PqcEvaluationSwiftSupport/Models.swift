import Foundation

public struct EvaluationResult: Codable, Sendable {
    public let schemaVersion: String
    public let runId: String
    public let generatedAt: String
    public let implementation: Implementation
    public let runtime: RuntimeInfo
    public let parameterSets: [ParameterSetResult]
    public let checks: [CheckResult]
    public let interoperability: [InteropResult]
    public let warnings: [String]

    public init(
        schemaVersion: String,
        runId: String,
        generatedAt: String,
        implementation: Implementation,
        runtime: RuntimeInfo,
        parameterSets: [ParameterSetResult],
        checks: [CheckResult],
        interoperability: [InteropResult],
        warnings: [String]) {
        self.schemaVersion = schemaVersion
        self.runId = runId
        self.generatedAt = generatedAt
        self.implementation = implementation
        self.runtime = runtime
        self.parameterSets = parameterSets
        self.checks = checks
        self.interoperability = interoperability
        self.warnings = warnings
    }
}

public struct Implementation: Codable, Sendable {
    public let id: String
    public let displayName: String
    public let version: String
    public let engineLineageId: String
    public let distribution: String
    public let license: String
    public let assuranceStatus: String

    public init(
        id: String,
        displayName: String,
        version: String,
        engineLineageId: String,
        distribution: String,
        license: String,
        assuranceStatus: String) {
        self.id = id
        self.displayName = displayName
        self.version = version
        self.engineLineageId = engineLineageId
        self.distribution = distribution
        self.license = license
        self.assuranceStatus = assuranceStatus
    }
}

public struct RuntimeInfo: Codable, Sendable {
    public let javaVersion: String?
    public let javaVendor: String?
    public let osName: String
    public let osVersion: String
    public let architecture: String
    public let buildProperties: [String: String]

    public init(
        javaVersion: String?,
        javaVendor: String?,
        osName: String,
        osVersion: String,
        architecture: String,
        buildProperties: [String: String]) {
        self.javaVersion = javaVersion
        self.javaVendor = javaVendor
        self.osName = osName
        self.osVersion = osVersion
        self.architecture = architecture
        self.buildProperties = buildProperties
    }

    private enum CodingKeys: String, CodingKey {
        case javaVersion
        case javaVendor
        case osName
        case osVersion
        case architecture
        case buildProperties
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(javaVersion, forKey: .javaVersion)
        try container.encode(javaVendor, forKey: .javaVendor)
        try container.encode(osName, forKey: .osName)
        try container.encode(osVersion, forKey: .osVersion)
        try container.encode(architecture, forKey: .architecture)
        try container.encode(buildProperties, forKey: .buildProperties)
    }
}

public struct ParameterSetResult: Codable, Sendable {
    public let parameterSet: String
    public let securityLevel: Int
    public let rawPublicKeyBytes: Int
    public let rawPrivateSeedBytes: Int
    public let rawPrivateExpandedBytes: Int
    public let rawSignatureBytes: Int
    public let capabilities: [Capability]
    public let representations: [Representation]

    public init(
        parameterSet: String,
        securityLevel: Int,
        rawPublicKeyBytes: Int,
        rawPrivateSeedBytes: Int,
        rawPrivateExpandedBytes: Int,
        rawSignatureBytes: Int,
        capabilities: [Capability],
        representations: [Representation]) {
        self.parameterSet = parameterSet
        self.securityLevel = securityLevel
        self.rawPublicKeyBytes = rawPublicKeyBytes
        self.rawPrivateSeedBytes = rawPrivateSeedBytes
        self.rawPrivateExpandedBytes = rawPrivateExpandedBytes
        self.rawSignatureBytes = rawSignatureBytes
        self.capabilities = capabilities
        self.representations = representations
    }
}

public struct Capability: Codable, Sendable {
    public let operation: String
    public let status: String
    public let origin: String
    public let evidence: String
    public let reason: String?

    public init(
        operation: String,
        status: String,
        origin: String,
        evidence: String,
        reason: String? = nil) {
        self.operation = operation
        self.status = status
        self.origin = origin
        self.evidence = evidence
        self.reason = reason
    }
}

public struct Representation: Codable, Sendable {
    public let kind: String
    public let status: String
    public let byteLength: Int?
    public let sha256: String?
    public let algorithmOid: String?
    public let parametersAbsent: Bool?
    public let privateChoice: String?
    public let origin: String
    public let reason: String?

    public init(
        kind: String,
        status: String,
        byteLength: Int? = nil,
        sha256: String? = nil,
        algorithmOid: String? = nil,
        parametersAbsent: Bool? = nil,
        privateChoice: String? = nil,
        origin: String,
        reason: String? = nil) {
        self.kind = kind
        self.status = status
        self.byteLength = byteLength
        self.sha256 = sha256
        self.algorithmOid = algorithmOid
        self.parametersAbsent = parametersAbsent
        self.privateChoice = privateChoice
        self.origin = origin
        self.reason = reason
    }
}

public struct CheckResult: Codable, Sendable {
    public let id: String
    public let parameterSet: String
    public let category: String
    public let status: String
    public let message: String

    public init(
        id: String,
        parameterSet: String,
        category: String,
        status: String,
        message: String) {
        self.id = id
        self.parameterSet = parameterSet
        self.category = category
        self.status = status
        self.message = message
    }
}

public struct InteropResult: Codable, Sendable {
    public let producer: String
    public let consumer: String
    public let parameterSet: String
    public let mode: String
    public let status: String
    public let message: String

    public init(
        producer: String,
        consumer: String,
        parameterSet: String,
        mode: String,
        status: String,
        message: String) {
        self.producer = producer
        self.consumer = consumer
        self.parameterSet = parameterSet
        self.mode = mode
        self.status = status
        self.message = message
    }
}

public enum ParameterSet: String, CaseIterable, Sendable {
    case mldsa44 = "ML-DSA-44"
    case mldsa65 = "ML-DSA-65"
    case mldsa87 = "ML-DSA-87"

    public var securityLevel: Int {
        switch self {
        case .mldsa44: return 2
        case .mldsa65: return 3
        case .mldsa87: return 5
        }
    }

    public var publicKeyBytes: Int {
        switch self {
        case .mldsa44: return 1312
        case .mldsa65: return 1952
        case .mldsa87: return 2592
        }
    }

    public var privateSeedBytes: Int { 32 }

    public var privateExpandedBytes: Int {
        switch self {
        case .mldsa44: return 2560
        case .mldsa65: return 4032
        case .mldsa87: return 4896
        }
    }

    public var signatureBytes: Int {
        switch self {
        case .mldsa44: return 2420
        case .mldsa65: return 3309
        case .mldsa87: return 4627
        }
    }

    public var algorithmOid: String {
        switch self {
        case .mldsa44: return "2.16.840.1.101.3.4.3.17"
        case .mldsa65: return "2.16.840.1.101.3.4.3.18"
        case .mldsa87: return "2.16.840.1.101.3.4.3.19"
        }
    }
}
