import CryptoKit
import Darwin
import Foundation
import PqcEvaluationSwiftSupport

@main
struct Main {
    static func main() {
        do {
            guard CommandLine.arguments.count == 2 else {
                throw RunnerError.usage
            }
            guard #available(macOS 26.0, *) else {
                throw RunnerError.unsupportedOS
            }
            try CryptoKitRunner().run(outputPath: CommandLine.arguments[1])
        } catch {
            FileHandle.standardError.write(Data("cryptokit-runner: \(error)\n".utf8))
            Darwin.exit(1)
        }
    }
}

private enum RunnerError: Error, CustomStringConvertible {
    case usage
    case unsupportedOS

    var description: String {
        switch self {
        case .usage:
            return "usage: cryptokit-runner <output-path>"
        case .unsupportedOS:
            return "CryptoKit ML-DSA requires macOS 26 or later"
        }
    }
}

@available(macOS 26.0, *)
private struct CryptoKitRunner {
    private let message = RunnerSupport.message
    private let context = RunnerSupport.context

    private let keyGenerationSnippet = """
                // [evidence:key-generation] evaluate(generate:) delegates to MLDSA65.PrivateKey() / MLDSA87.PrivateKey()
                let key = try generate()
        """

    private let signSnippet = """
                // [evidence:sign] evaluate(sign:) delegates to PrivateKey.signature(for:)
                let signature = try sign(key, message)
        """

    private let verifySnippet = """
                // [evidence:verify] evaluate(verify:) delegates to PublicKey.isValidSignature(_:for:)
                let verified = verify(publicKeyValue, signature, message)
        """

    private func usageExample(for parameterSet: ParameterSet) -> String {
        let keyType: String
        switch parameterSet {
        case .mldsa65:
            keyType = "MLDSA65"
        case .mldsa87:
            keyType = "MLDSA87"
        case .mldsa44:
            preconditionFailure("CryptoKit does not support ML-DSA-44")
        }
        return """
        import CryptoKit
        import Foundation

        @available(macOS 26.0, *)
        func signAndVerify() throws {
            let privateKey = try \(keyType).PrivateKey()
            let message = Data("PQC evaluation message".utf8)
            let signature = try privateKey.signature(for: message)
            let verified = privateKey.publicKey.isValidSignature(signature, for: message)
        }
        """
    }

    func run(outputPath: String) throws {
        var checks: [CheckResult] = []
        let parameterSets = [
            evaluateUnsupported(.mldsa44, checks: &checks),
            try evaluate65(checks: &checks),
            try evaluate87(checks: &checks)
        ]
        let result = EvaluationResult(
            schemaVersion: "1.1",
            runId: "cryptokit-\(UUID().uuidString)",
            generatedAt: RunnerSupport.generatedAt(),
            implementation: Implementation(
                id: "apple-cryptokit-mldsa",
                displayName: "Apple CryptoKit ML-DSA",
                version: "macOS 26",
                engineLineageId: "apple-cryptokit",
                distribution: "CryptoKit system framework",
                license: "Apple platform SDK",
                assuranceStatus: "Apple platform implementation, not FIPS validated"),
            runtime: RunnerSupport.runtime(
                api: "CryptoKit",
                backend: "Apple CryptoKit",
                packageRevision: "Apple SDK",
                swiftToolsVersion: "6.1",
                deploymentTarget: "macOS 13.0",
                toolchain: try RunnerSupport.toolchainMetadataFromEnvironment(),
                additional: [
                    "algorithm": "ML-DSA",
                    "sdkRequirement": "macOS 26 / Xcode 26",
                    "minimumRuntimeOS": "macOS 26.0"
                ]),
            parameterSets: parameterSets,
            checks: checks,
            interoperability: [],
            warnings: [
                "CryptoKit exposes ML-DSA-65 and ML-DSA-87; ML-DSA-44 is explicitly unsupported.",
                "SPKI and PKCS#8 records are evaluator-derived and are not native CryptoKit APIs."
            ])
        try RunnerSupport.write(result, to: outputPath)
    }

    private func evaluateUnsupported(
        _ parameterSet: ParameterSet,
        checks: inout [CheckResult]) -> ParameterSetResult {
        let reason = "CryptoKit does not expose \(parameterSet.rawValue)"
        checks.append(
            RunnerSupport.check(
                "key-generation",
                parameterSet: parameterSet,
                category: "capability",
                status: "unsupported",
                message: reason))
        let capabilities = [
            RunnerSupport.capability("key-generation", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason),
            RunnerSupport.capability("sign", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason),
            RunnerSupport.capability("verify", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason),
            RunnerSupport.capability("raw-public", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason),
            RunnerSupport.capability("raw-private-seed", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason),
            RunnerSupport.capability("raw-private-expanded", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason),
            RunnerSupport.capability("integrity-checked-private", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason),
            RunnerSupport.capability("context", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason),
            RunnerSupport.capability("hashml-dsa", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA namespace", reason: reason)
        ]
        let representations = [
            RunnerSupport.unsupportedRepresentation("raw-public", origin: "native-api", reason: reason),
            RunnerSupport.unsupportedRepresentation("raw-private-seed", origin: "native-api", reason: reason),
            RunnerSupport.unsupportedRepresentation("raw-private-expanded", origin: "native-api", reason: reason),
            RunnerSupport.unsupportedRepresentation("integrity-checked-private", origin: "native-api", reason: reason),
            RunnerSupport.unsupportedRepresentation("spki", origin: "native-api", reason: reason),
            RunnerSupport.unsupportedRepresentation("pkcs8", origin: "native-api", reason: reason)
        ]
        return ParameterSetResult(
            parameterSet: parameterSet.rawValue,
            securityLevel: parameterSet.securityLevel,
            rawPublicKeyBytes: parameterSet.publicKeyBytes,
            rawPrivateSeedBytes: parameterSet.privateSeedBytes,
            rawPrivateExpandedBytes: parameterSet.privateExpandedBytes,
            rawSignatureBytes: parameterSet.signatureBytes,
            capabilities: capabilities,
            representations: representations)
    }

    private func evaluate65(checks: inout [CheckResult]) throws -> ParameterSetResult {
        try evaluate(
            .mldsa65,
            generate: { try MLDSA65.PrivateKey() },
            publicKey: { $0.publicKey },
            rawPublic: { $0.publicKey.rawRepresentation },
            seed: { $0.seedRepresentation },
            integrity: { $0.integrityCheckedRepresentation },
            sign: { try $0.signature(for: $1) },
            signContext: { try $0.signature(for: $1, context: $2) },
            verify: { $0.isValidSignature($1, for: $2) },
            verifyContext: { $0.isValidSignature($1, for: $2, context: $3) },
            reconstruct: { try MLDSA65.PrivateKey(seedRepresentation: $0, publicKey: $1) },
            reconstructIntegrity: { try MLDSA65.PrivateKey(integrityCheckedRepresentation: $0) },
            publicKeyFromRaw: { try MLDSA65.PublicKey(rawRepresentation: $0) },
            checks: &checks)
    }

    private func evaluate87(checks: inout [CheckResult]) throws -> ParameterSetResult {
        try evaluate(
            .mldsa87,
            generate: { try MLDSA87.PrivateKey() },
            publicKey: { $0.publicKey },
            rawPublic: { $0.publicKey.rawRepresentation },
            seed: { $0.seedRepresentation },
            integrity: { $0.integrityCheckedRepresentation },
            sign: { try $0.signature(for: $1) },
            signContext: { try $0.signature(for: $1, context: $2) },
            verify: { $0.isValidSignature($1, for: $2) },
            verifyContext: { $0.isValidSignature($1, for: $2, context: $3) },
            reconstruct: { try MLDSA87.PrivateKey(seedRepresentation: $0, publicKey: $1) },
            reconstructIntegrity: { try MLDSA87.PrivateKey(integrityCheckedRepresentation: $0) },
            publicKeyFromRaw: { try MLDSA87.PublicKey(rawRepresentation: $0) },
            checks: &checks)
    }

    private func evaluate<Key, PublicKey>(
        _ parameterSet: ParameterSet,
        generate: () throws -> Key,
        publicKey: (Key) -> PublicKey,
        rawPublic: (Key) -> Data,
        seed: (Key) -> Data,
        integrity: (Key) -> Data,
        sign: (Key, Data) throws -> Data,
        signContext: (Key, Data, Data) throws -> Data,
        verify: (PublicKey, Data, Data) -> Bool,
        verifyContext: (PublicKey, Data, Data, Data) -> Bool,
        reconstruct: (Data, PublicKey) throws -> Key,
        reconstructIntegrity: (Data) throws -> Key,
        publicKeyFromRaw: (Data) throws -> PublicKey,
        checks: inout [CheckResult]) throws -> ParameterSetResult {
        let keyGenSite = RunnerSupport.captureCallSiteLocation(className: String(describing: Self.self))
        // [evidence:key-generation] evaluate(generate:) delegates to MLDSA65.PrivateKey() / MLDSA87.PrivateKey()
        let key = try generate()
        let keyGenCallSite = keyGenSite.with(
            snippet: keyGenerationSnippet,
            highlightLine: 2,
            arguments: [
                Argument(name: "algorithm", type: "String", value: "ML-DSA"),
                Argument(name: "key", type: String(describing: type(of: key)), value: parameterSet.rawValue)
            ],
            usageExample: usageExample(for: parameterSet))
        let publicKeyValue = publicKey(key)
        let rawPublicValue = rawPublic(key)
        let seedValue = seed(key)
        let integrityValue = integrity(key)
        let signSite = RunnerSupport.captureCallSiteLocation(className: String(describing: Self.self))
        // [evidence:sign] evaluate(sign:) delegates to PrivateKey.signature(for:)
        let signature = try sign(key, message)
        let signCallSite = signSite.with(
            snippet: signSnippet,
            highlightLine: 2,
            arguments: [
                Argument(name: "algorithm", type: "String", value: "ML-DSA"),
                Argument(name: "key", type: String(describing: type(of: key)), value: parameterSet.rawValue),
                Argument(name: "message", type: "Data", value: String(decoding: message, as: UTF8.self) + " (\(message.count) bytes UTF-8)"),
                Argument(name: "signature", type: "Data", value: "\(signature.count) bytes")
            ],
            usageExample: usageExample(for: parameterSet))
        let verifySite = RunnerSupport.captureCallSiteLocation(className: String(describing: Self.self))
        // [evidence:verify] evaluate(verify:) delegates to PublicKey.isValidSignature(_:for:)
        let verified = verify(publicKeyValue, signature, message)
        let verifyCallSite = verifySite.with(
            snippet: verifySnippet,
            highlightLine: 2,
            arguments: [
                Argument(name: "algorithm", type: "String", value: "ML-DSA"),
                Argument(name: "publicKey", type: String(describing: type(of: publicKeyValue)), value: parameterSet.rawValue),
                Argument(name: "signature", type: "Data", value: "\(signature.count) bytes"),
                Argument(name: "message", type: "Data", value: String(decoding: message, as: UTF8.self) + " (\(message.count) bytes UTF-8)"),
                Argument(name: "verified", type: "Bool", value: "\(verified)")
            ],
            usageExample: usageExample(for: parameterSet))
        let contextSignature = try signContext(key, message, context)
        let contextVerified = verifyContext(publicKeyValue, contextSignature, message, context)

        let reconstructed = try reconstruct(seedValue, publicKeyValue)
        let seedRoundTrip = rawPublic(reconstructed) == rawPublicValue && seed(reconstructed) == seedValue
        let integrityRoundTrip = try reconstructIntegrity(integrityValue)
        let integrityKeyRoundTrip = rawPublic(integrityRoundTrip) == rawPublicValue

        var corruptedIntegrity = integrityValue
        corruptedIntegrity[corruptedIntegrity.index(before: corruptedIntegrity.endIndex)] ^= 1
        let corruptionRejected: Bool
        do {
            _ = try reconstructIntegrity(corruptedIntegrity)
            corruptionRejected = false
        } catch {
            corruptionRejected = true
        }

        let wrongMessageVerified = verify(publicKeyValue, signature, Data("wrong message".utf8))
        var corruptedSignature = signature
        corruptedSignature[corruptedSignature.startIndex] ^= 1
        let corruptedSignatureVerified = verify(publicKeyValue, corruptedSignature, message)
        let wrongContextVerified = verifyContext(publicKeyValue, contextSignature, message, Data("wrong-context".utf8))
        let malformedPublicRejected: Bool
        do {
            _ = try publicKeyFromRaw(Data(repeating: 0, count: rawPublicValue.count - 1))
            malformedPublicRejected = false
        } catch {
            malformedPublicRejected = true
        }

        let contextBoundary = contextBoundaryCheck(signContext: { try signContext(key, message, $0) })
        let spki = try PqcDER.subjectPublicKeyInfo(rawPublicKey: rawPublicValue, oid: parameterSet.algorithmOid)
        let pkcs8 = try PqcDER.pkcs8Seed(seed: seedValue, oid: parameterSet.algorithmOid)
        let spkiIdentifier = try PqcDER.algorithmIdentifier(in: spki, privateKey: false)
        let pkcs8Identifier = try PqcDER.algorithmIdentifier(in: pkcs8, privateKey: true)

        checks.append(RunnerSupport.check("key-generation", parameterSet: parameterSet, category: "correctness", status: "pass", message: "Generated CryptoKit key pair"))
        checks.append(RunnerSupport.check("raw-public-length", parameterSet: parameterSet, category: "encoding", status: RunnerSupport.pass(rawPublicValue.count == parameterSet.publicKeyBytes), message: "Raw public key is \(rawPublicValue.count) bytes"))
        checks.append(RunnerSupport.check("raw-private-seed-length", parameterSet: parameterSet, category: "encoding", status: RunnerSupport.pass(seedValue.count == parameterSet.privateSeedBytes), message: "Raw seed is \(seedValue.count) bytes"))
        checks.append(RunnerSupport.check("integrity-private-length", parameterSet: parameterSet, category: "encoding", status: RunnerSupport.pass(integrityValue.count == 64), message: "Integrity-checked private representation is \(integrityValue.count) bytes"))
        checks.append(RunnerSupport.check("signature-length", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(signature.count == parameterSet.signatureBytes), message: "Signature is \(signature.count) bytes"))
        checks.append(RunnerSupport.check("self-sign-verify", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(verified), message: "Generated signature verifies"))
        checks.append(RunnerSupport.check("seed-reconstruction", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(seedRoundTrip), message: "Seed reconstruction preserves raw key material"))
        checks.append(RunnerSupport.check("integrity-round-trip", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(integrityKeyRoundTrip), message: "Integrity-checked private representation reconstructs the public key"))
        checks.append(RunnerSupport.check("integrity-corruption", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(corruptionRejected), message: "Corrupted integrity-checked representation is rejected"))
        checks.append(RunnerSupport.check("application-context", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(contextVerified), message: "Non-empty context signs and verifies"))
        checks.append(RunnerSupport.check("context-boundary", parameterSet: parameterSet, category: "capability", status: RunnerSupport.pass(contextBoundary), message: "A 256-byte context is rejected"))
        checks.append(RunnerSupport.check("wrong-message", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(!wrongMessageVerified), message: "A signature does not verify for a changed message"))
        checks.append(RunnerSupport.check("wrong-context", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(!wrongContextVerified), message: "A context signature does not verify for a changed context"))
        checks.append(RunnerSupport.check("modified-signature", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(!corruptedSignatureVerified), message: "A modified signature is rejected"))
        checks.append(RunnerSupport.check("malformed-public", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(malformedPublicRejected), message: "A malformed raw public key is rejected"))
        checks.append(RunnerSupport.check("evaluator-derived-containers", parameterSet: parameterSet, category: "encoding", status: RunnerSupport.pass(spkiIdentifier.oid == parameterSet.algorithmOid && pkcs8Identifier.oid == parameterSet.algorithmOid && spkiIdentifier.parametersAbsent && pkcs8Identifier.parametersAbsent), message: "Evaluator-derived SPKI and PKCS#8 use the expected OID and absent parameters"))

        let capabilities = [
            RunnerSupport.capability("key-generation", status: "supported", origin: "native-api", evidence: "CryptoKit MLDSA PrivateKey initializer", callSite: keyGenCallSite),
            RunnerSupport.capability("sign", status: "supported", origin: "native-api", evidence: "CryptoKit MLDSA PrivateKey.signature(for:)", callSite: signCallSite),
            RunnerSupport.capability("verify", status: "supported", origin: "native-api", evidence: "CryptoKit MLDSA PublicKey.isValidSignature(_:for:)", callSite: verifyCallSite),
            RunnerSupport.capability("raw-public", status: "supported", origin: "native-api", evidence: "CryptoKit PublicKey.rawRepresentation"),
            RunnerSupport.capability("raw-private-seed", status: "supported", origin: "native-api", evidence: "CryptoKit PrivateKey.seedRepresentation"),
            RunnerSupport.capability("raw-private-expanded", status: "unsupported", origin: "native-api", evidence: "CryptoKit private key API", reason: "CryptoKit does not expose expanded private bytes"),
            RunnerSupport.capability("integrity-checked-private", status: "supported", origin: "native-api", evidence: "CryptoKit PrivateKey.integrityCheckedRepresentation"),
            RunnerSupport.capability("context", status: "supported", origin: "native-api", evidence: "CryptoKit MLDSA context signing overloads"),
            RunnerSupport.capability("spki", status: "unsupported", origin: "native-api", evidence: "CryptoKit key API", reason: "CryptoKit does not import or export SPKI"),
            RunnerSupport.capability("pkcs8", status: "unsupported", origin: "native-api", evidence: "CryptoKit key API", reason: "CryptoKit does not import or export PKCS#8"),
            RunnerSupport.capability("hashml-dsa", status: "unsupported", origin: "native-api", evidence: "CryptoKit MLDSA API", reason: "No public HashML-DSA API")
        ]
        let representations = [
            Representation(kind: "raw-public", status: "pass", byteLength: rawPublicValue.count, sha256: PqcSHA256.hex(rawPublicValue), origin: "native-api"),
            Representation(kind: "raw-private-seed", status: "pass", byteLength: seedValue.count, privateChoice: "seed", origin: "native-api", reason: "Private bytes are not retained in the result"),
            Representation(kind: "integrity-checked-private", status: "pass", byteLength: integrityValue.count, privateChoice: "integrityChecked", origin: "native-api", reason: "Private bytes are not retained in the result"),
            Representation(kind: "raw-private-expanded", status: "unsupported", origin: "native-api", reason: "CryptoKit does not expose expanded private bytes"),
            Representation(kind: "spki", status: "pass", byteLength: spki.count, sha256: PqcSHA256.hex(spki), algorithmOid: spkiIdentifier.oid, parametersAbsent: spkiIdentifier.parametersAbsent, origin: "evaluator-derived", reason: "DER generated from CryptoKit raw public bytes"),
            Representation(kind: "pkcs8", status: "pass", byteLength: pkcs8.count, algorithmOid: pkcs8Identifier.oid, parametersAbsent: pkcs8Identifier.parametersAbsent, privateChoice: "seed", origin: "evaluator-derived", reason: "DER generated from CryptoKit seed bytes; not a native CryptoKit container; private bytes are not retained in the result")
        ]
        return ParameterSetResult(
            parameterSet: parameterSet.rawValue,
            securityLevel: parameterSet.securityLevel,
            rawPublicKeyBytes: parameterSet.publicKeyBytes,
            rawPrivateSeedBytes: parameterSet.privateSeedBytes,
            rawPrivateExpandedBytes: parameterSet.privateExpandedBytes,
            rawSignatureBytes: parameterSet.signatureBytes,
            capabilities: capabilities,
            representations: representations)
    }

    private func contextBoundaryCheck(signContext: (Data) throws -> Data) -> Bool {
        do {
            _ = try signContext(Data(repeating: 0, count: 256))
            return false
        } catch {
            return true
        }
    }
}
