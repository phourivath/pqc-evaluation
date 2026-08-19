import Foundation
import Testing
@testable import PqcEvaluationSwiftSupport

@Test
func sha256KnownVectors() {
    #expect(PqcSHA256.hex(Data()) == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    #expect(PqcSHA256.hex(Data("abc".utf8)) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
}

@Test
func parameterSetSizesMatchFips204() {
    #expect(ParameterSet.mldsa44.publicKeyBytes == 1312)
    #expect(ParameterSet.mldsa65.privateExpandedBytes == 4032)
    #expect(ParameterSet.mldsa87.signatureBytes == 4627)
}

@Test
func runtimeIncludesExplicitSwiftBuildProvenance() {
    let runtime = RunnerSupport.runtime(
        api: "test-api",
        backend: "test-backend",
        packageRevision: "test-revision",
        swiftToolsVersion: "6.1",
        deploymentTarget: "cross-platform",
        toolchain: SwiftToolchainMetadata(
            swiftVersion: "6.2.0",
            xcodeVersion: "not-applicable",
            sdkVersion: "not-applicable"))

    #expect(runtime.buildProperties["swiftVersion"] == "6.2.0")
    #expect(runtime.buildProperties["swiftToolsVersion"] == "6.1")
    #expect(runtime.buildProperties["xcodeVersion"] == "not-applicable")
    #expect(runtime.buildProperties["sdkVersion"] == "not-applicable")
    #expect(runtime.buildProperties["deploymentTarget"] == "cross-platform")
}

@Test
func derRoundTripsRawPublicAndSeed() throws {
    let publicKey = Data(repeating: 0x42, count: ParameterSet.mldsa65.publicKeyBytes)
    let spki = try PqcDER.subjectPublicKeyInfo(rawPublicKey: publicKey, oid: ParameterSet.mldsa65.algorithmOid)
    #expect(try PqcDER.subjectPublicKey(in: spki) == publicKey)
    let spkiIdentifier = try PqcDER.algorithmIdentifier(in: spki, privateKey: false)
    #expect(spkiIdentifier.oid == ParameterSet.mldsa65.algorithmOid)
    #expect(spkiIdentifier.parametersAbsent)

    let seed = Data(repeating: 0x24, count: 32)
    let pkcs8 = try PqcDER.pkcs8Seed(seed: seed, oid: ParameterSet.mldsa65.algorithmOid)
    #expect(try PqcDER.privateChoice(in: pkcs8) == "seed")
    let pkcs8Identifier = try PqcDER.algorithmIdentifier(in: pkcs8, privateKey: true)
    #expect(pkcs8Identifier.oid == ParameterSet.mldsa65.algorithmOid)
    #expect(pkcs8Identifier.parametersAbsent)
}
