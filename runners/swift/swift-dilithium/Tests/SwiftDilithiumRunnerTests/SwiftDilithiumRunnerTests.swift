import SwiftDilithium
import Testing

@Test
func swiftDilithiumSupportsAllParameterSetsAndPreHash() throws {
    for kind in Kind.allCases {
        let keyPair = Dilithium.GenerateKeyPair(kind: kind)
        let message: [UInt8] = [1, 2, 3]
        let signature = keyPair.sk.Sign(message: message, randomize: false)
        let preHashSignature = keyPair.sk.Sign(message: message, ph: .SHAKE128, randomize: false)

        #expect(keyPair.pk.Verify(message: message, signature: signature))
        #expect(keyPair.pk.Verify(message: message, signature: preHashSignature, ph: .SHAKE128))
    }
}
