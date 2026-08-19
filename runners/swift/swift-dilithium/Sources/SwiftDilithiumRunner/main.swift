import Foundation
import PqcEvaluationSwiftSupport
import SwiftDilithium

#if canImport(Darwin)
import Darwin
#elseif canImport(Glibc)
import Glibc
#endif

@main
struct Main {
    static func main() {
        do {
            guard CommandLine.arguments.count == 2 else {
                throw RunnerError.usage
            }
            try SwiftDilithiumRunner().run(outputPath: CommandLine.arguments[1])
        } catch {
            FileHandle.standardError.write(Data("swift-dilithium-runner: \(error)\n".utf8))
            #if canImport(Darwin)
            Darwin.exit(1)
            #elseif canImport(Glibc)
            Glibc.exit(1)
            #else
            fatalError("process exit is unavailable on this platform")
            #endif
        }
    }
}

private enum RunnerError: Error, CustomStringConvertible {
    case usage

    var description: String {
        switch self {
        case .usage:
            return "usage: swift-dilithium-runner <output-path>"
        }
    }
}

private struct SwiftDilithiumRunner {
    private let message = [UInt8](RunnerSupport.message)
    private let context = [UInt8](RunnerSupport.context)

    private let keyGenerationSnippet = """
                // [evidence:key-generation] SwiftDilithium.Dilithium.GenerateKeyPair(kind:) -> (sk: SecretKey, pk: PublicKey)
                let keyPair: (sk: SecretKey, pk: PublicKey) = Dilithium.GenerateKeyPair(kind: kind(for: parameterSet))
        """

    private let signSnippet = """
                // [evidence:sign] SwiftDilithium.SecretKey.Sign(message:randomize:) -> [UInt8] (randomize: false is deterministic)
                let deterministicSignature: [UInt8] = secretKey.Sign(message: message, randomize: false)
        """

    private let verifySnippet = """
                // [evidence:verify] SwiftDilithium.PublicKey.Verify(message:signature:) -> Bool
                let deterministicVerified: Bool = publicKey.Verify(message: message, signature: deterministicSignature)
        """

    func run(outputPath: String) throws {
        var checks: [CheckResult] = []
        let parameterSets = try ParameterSet.allCases.map {
            try evaluate($0, checks: &checks)
        }
        let result = EvaluationResult(
            schemaVersion: "1.1",
            runId: "swift-dilithium-\(UUID().uuidString)",
            generatedAt: RunnerSupport.generatedAt(),
            implementation: Implementation(
                id: "swift-dilithium",
                displayName: "SwiftDilithium ML-DSA",
                version: "3.6.0",
                engineLineageId: "swift-dilithium",
                distribution: "Swift Package Manager",
                license: "MIT",
                assuranceStatus: "Third-party pure Swift implementation with ACVP and Wycheproof provenance, not FIPS validated"),
            runtime: RunnerSupport.runtime(
                api: "SwiftDilithium",
                backend: "SwiftDilithium pure Swift",
                packageRevision: "452e507c68879a4a584502e1ef55605efb224e79",
                swiftToolsVersion: "6.1",
                deploymentTarget: "cross-platform",
                toolchain: try RunnerSupport.toolchainMetadataFromEnvironment(),
                additional: [
                    "upstreamSwiftToolsVersion": "5.10",
                    "dependencyPortabilityPatch": "SystemRandomNumberGenerator",
                    "algorithm": "ML-DSA",
                    "vectorProvenance": "NIST ACVP-server 1.1.0.38 and Wycheproof"
                ]),
            parameterSets: parameterSets,
            checks: checks,
            interoperability: [],
            warnings: [
                "SwiftDilithium exposes expanded private keys and PEM containers, but no separate seed reconstruction API."
            ])
        try RunnerSupport.write(result, to: outputPath)
    }

    private func evaluate(
        _ parameterSet: ParameterSet,
        checks: inout [CheckResult]) throws -> ParameterSetResult {
        let keyGenSite = RunnerSupport.captureCallSiteLocation(className: String(describing: Self.self))
        // [evidence:key-generation] SwiftDilithium.Dilithium.GenerateKeyPair(kind:) -> (sk: SecretKey, pk: PublicKey)
        let keyPair: (sk: SecretKey, pk: PublicKey) = Dilithium.GenerateKeyPair(kind: kind(for: parameterSet))
        let keyGenCallSite = keyGenSite.with(
            snippet: keyGenerationSnippet,
            highlightLine: 2,
            arguments: [
                Argument(name: "algorithm", type: "String", value: "ML-DSA"),
                Argument(name: "kind", type: "Kind", value: parameterSet.rawValue),
                Argument(name: "keyPair", type: String(describing: type(of: keyPair)), value: "generated")
            ])
        let secretKey = keyPair.sk
        let publicKey = keyPair.pk
        let rawPublic = Data(publicKey.keyBytes)
        let rawPrivate = Data(secretKey.keyBytes)
        let signSite = RunnerSupport.captureCallSiteLocation(className: String(describing: Self.self))
        // [evidence:sign] SwiftDilithium.SecretKey.Sign(message:randomize:) -> [UInt8] (randomize: false is deterministic)
        let deterministicSignature: [UInt8] = secretKey.Sign(message: message, randomize: false)
        let signCallSite = signSite.with(
            snippet: signSnippet,
            highlightLine: 2,
            arguments: [
                Argument(name: "algorithm", type: "String", value: "ML-DSA"),
                Argument(name: "key", type: String(describing: type(of: secretKey)), value: parameterSet.rawValue),
                Argument(name: "message", type: "[UInt8]", value: String(decoding: message, as: UTF8.self) + " (\(message.count) bytes UTF-8)"),
                Argument(name: "randomize", type: "Bool", value: "false"),
                Argument(name: "signature", type: "[UInt8]", value: "\(deterministicSignature.count) bytes")
            ])
        let randomizedSignature = secretKey.Sign(message: message, randomize: true)
        let verifySite = RunnerSupport.captureCallSiteLocation(className: String(describing: Self.self))
        // [evidence:verify] SwiftDilithium.PublicKey.Verify(message:signature:) -> Bool
        let deterministicVerified: Bool = publicKey.Verify(message: message, signature: deterministicSignature)
        let verifyCallSite = verifySite.with(
            snippet: verifySnippet,
            highlightLine: 2,
            arguments: [
                Argument(name: "algorithm", type: "String", value: "ML-DSA"),
                Argument(name: "publicKey", type: String(describing: type(of: publicKey)), value: parameterSet.rawValue),
                Argument(name: "signature", type: "[UInt8]", value: "\(deterministicSignature.count) bytes"),
                Argument(name: "message", type: "[UInt8]", value: String(decoding: message, as: UTF8.self) + " (\(message.count) bytes UTF-8)"),
                Argument(name: "verified", type: "Bool", value: "\(deterministicVerified)")
            ])
        let randomizedVerified = publicKey.Verify(message: message, signature: randomizedSignature)
        let contextSignature = try secretKey.Sign(message: message, context: context, randomize: false)
        let contextVerified = publicKey.Verify(message: message, signature: contextSignature, context: context)
        let preHashSignature = secretKey.Sign(message: message, ph: .SHAKE128, randomize: false)
        let preHashVerified = publicKey.Verify(message: message, signature: preHashSignature, ph: .SHAKE128)

        let importedPublic = try PublicKey(pem: publicKey.pem)
        let importedSecret = try SecretKey(pem: secretKey.pem)
        let publicPem = try PqcDER.decodePEM(publicKey.pem, label: "PUBLIC KEY")
        let privatePem = try PqcDER.decodePEM(secretKey.pem, label: "PRIVATE KEY")
        let spkiIdentifier = try PqcDER.algorithmIdentifier(in: publicPem, privateKey: false)
        let pkcs8Identifier = try PqcDER.algorithmIdentifier(in: privatePem, privateKey: true)
        let privateChoice = try PqcDER.privateChoice(in: privatePem)

        let contextBoundary = contextBoundaryCheck(secretKey: secretKey)
        let wrongMessageVerified = publicKey.Verify(message: [UInt8](Data("wrong message".utf8)), signature: deterministicSignature)
        var corruptedSignature = deterministicSignature
        corruptedSignature[corruptedSignature.startIndex] ^= 1
        let corruptedSignatureVerified = publicKey.Verify(message: message, signature: corruptedSignature)
        let wrongContextVerified = publicKey.Verify(message: message, signature: contextSignature, context: [UInt8](Data("wrong-context".utf8)))
        let malformedPublicRejected: Bool
        do {
            _ = try PublicKey(keyBytes: Array(repeating: 0, count: rawPublic.count - 1))
            malformedPublicRejected = false
        } catch {
            malformedPublicRejected = true
        }

        checks.append(RunnerSupport.check("key-generation", parameterSet: parameterSet, category: "correctness", status: "pass", message: "Generated SwiftDilithium key pair"))
        checks.append(RunnerSupport.check("raw-public-length", parameterSet: parameterSet, category: "encoding", status: RunnerSupport.pass(rawPublic.count == parameterSet.publicKeyBytes), message: "Raw public key is \(rawPublic.count) bytes"))
        checks.append(RunnerSupport.check("raw-private-expanded-length", parameterSet: parameterSet, category: "encoding", status: RunnerSupport.pass(rawPrivate.count == parameterSet.privateExpandedBytes), message: "Expanded private key is \(rawPrivate.count) bytes"))
        checks.append(RunnerSupport.check("signature-length", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(deterministicSignature.count == parameterSet.signatureBytes), message: "Signature is \(deterministicSignature.count) bytes"))
        checks.append(RunnerSupport.check("deterministic-sign-verify", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(deterministicVerified), message: "Deterministic signature verifies"))
        checks.append(RunnerSupport.check("randomized-sign-verify", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(randomizedVerified), message: "Randomized signature verifies"))
        checks.append(RunnerSupport.check("application-context", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(contextVerified), message: "Non-empty context signs and verifies"))
        checks.append(RunnerSupport.check("hashml-dsa", parameterSet: parameterSet, category: "correctness", status: RunnerSupport.pass(preHashVerified), message: "SHAKE128 pre-hash signature verifies"))
        checks.append(RunnerSupport.check("context-boundary", parameterSet: parameterSet, category: "capability", status: RunnerSupport.pass(contextBoundary), message: "A 256-byte context is rejected"))
        checks.append(RunnerSupport.check("standard-key-round-trip", parameterSet: parameterSet, category: "encoding", status: RunnerSupport.pass(importedPublic.keyBytes == publicKey.keyBytes && importedSecret.keyBytes == secretKey.keyBytes), message: "PEM public and private keys preserve raw key material"))
        checks.append(RunnerSupport.check("algorithm-identifier", parameterSet: parameterSet, category: "encoding", status: RunnerSupport.pass(spkiIdentifier.oid == parameterSet.algorithmOid && pkcs8Identifier.oid == parameterSet.algorithmOid && spkiIdentifier.parametersAbsent && pkcs8Identifier.parametersAbsent && privateChoice == "expandedKey"), message: "PEM containers use the expected OID, absent parameters, and expandedKey choice"))
        checks.append(RunnerSupport.check("wrong-message", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(!wrongMessageVerified), message: "A signature does not verify for a changed message"))
        checks.append(RunnerSupport.check("wrong-context", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(!wrongContextVerified), message: "A context signature does not verify for a changed context"))
        checks.append(RunnerSupport.check("modified-signature", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(!corruptedSignatureVerified), message: "A modified signature is rejected"))
        checks.append(RunnerSupport.check("malformed-public", parameterSet: parameterSet, category: "negative", status: RunnerSupport.pass(malformedPublicRejected), message: "A malformed raw public key is rejected"))

        let capabilities = [
            RunnerSupport.capability("key-generation", status: "supported", origin: "native-api", evidence: "Dilithium.GenerateKeyPair(kind:)", callSite: keyGenCallSite),
            RunnerSupport.capability("sign", status: "supported", origin: "native-api", evidence: "SecretKey.Sign(message:randomize:)", callSite: signCallSite),
            RunnerSupport.capability("verify", status: "supported", origin: "native-api", evidence: "PublicKey.Verify(message:signature:)", callSite: verifyCallSite),
            RunnerSupport.capability("raw-public", status: "supported", origin: "native-api", evidence: "PublicKey.keyBytes"),
            RunnerSupport.capability("raw-private-seed", status: "unsupported", origin: "native-api", evidence: "SecretKey API", reason: "No separate seed constructor or seed export is exposed"),
            RunnerSupport.capability("raw-private-expanded", status: "supported", origin: "native-api", evidence: "SecretKey.keyBytes"),
            RunnerSupport.capability("integrity-checked-private", status: "unsupported", origin: "native-api", evidence: "SecretKey API", reason: "No CryptoKit-style integrity-checked representation is exposed"),
            RunnerSupport.capability("context", status: "supported", origin: "native-api", evidence: "SecretKey.Sign and PublicKey.Verify context overloads"),
            RunnerSupport.capability("spki", status: "supported", origin: "standard-container", evidence: "PublicKey.pem"),
            RunnerSupport.capability("pkcs8", status: "supported", origin: "standard-container", evidence: "SecretKey.pem"),
            RunnerSupport.capability("hashml-dsa", status: "supported", origin: "native-api", evidence: "SecretKey.Sign and PublicKey.Verify PreHash overloads")
        ]
        let representations = [
            Representation(kind: "raw-public", status: "pass", byteLength: rawPublic.count, sha256: PqcSHA256.hex(rawPublic), origin: "native-api"),
            Representation(kind: "raw-private-seed", status: "unsupported", privateChoice: "seed", origin: "native-api", reason: "SwiftDilithium does not expose seed reconstruction"),
            Representation(kind: "raw-private-expanded", status: "pass", byteLength: rawPrivate.count, privateChoice: "expandedKey", origin: "native-api", reason: "Private bytes are not retained in the result"),
            Representation(kind: "integrity-checked-private", status: "unsupported", origin: "native-api", reason: "SwiftDilithium does not expose an integrity-checked private representation"),
            Representation(kind: "spki", status: "pass", byteLength: publicPem.count, sha256: PqcSHA256.hex(publicPem), algorithmOid: spkiIdentifier.oid, parametersAbsent: spkiIdentifier.parametersAbsent, origin: "standard-container"),
            Representation(kind: "pkcs8", status: "pass", byteLength: privatePem.count, algorithmOid: pkcs8Identifier.oid, parametersAbsent: pkcs8Identifier.parametersAbsent, privateChoice: privateChoice, origin: "standard-container", reason: "Private bytes are not retained in the result")
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

    private func kind(for parameterSet: ParameterSet) -> Kind {
        switch parameterSet {
        case .mldsa44: return .ML_DSA_44
        case .mldsa65: return .ML_DSA_65
        case .mldsa87: return .ML_DSA_87
        }
    }

    private func contextBoundaryCheck(secretKey: SecretKey) -> Bool {
        do {
            _ = try secretKey.Sign(
                message: message,
                context: [UInt8](repeating: 0, count: 256),
                randomize: false)
            return false
        } catch {
            return true
        }
    }
}
