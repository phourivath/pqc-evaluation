import CryptoKit
import Foundation
import Testing

@available(macOS 26.0, *)
@Test
func cryptoKitMldsa65SupportsSeedAndContextRoundTrip() throws {
    let key = try MLDSA65.PrivateKey()
    let seed = key.seedRepresentation
    let reconstructed = try MLDSA65.PrivateKey(seedRepresentation: seed, publicKey: key.publicKey)
    let message = Data("test message".utf8)
    let context = Data("test context".utf8)
    let signature = try reconstructed.signature(for: message, context: context)

    #expect(reconstructed.publicKey.rawRepresentation == key.publicKey.rawRepresentation)
    #expect(reconstructed.publicKey.isValidSignature(signature, for: message, context: context))
    #expect(signature.count == 3309)
}
