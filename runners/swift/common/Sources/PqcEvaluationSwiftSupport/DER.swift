import Foundation

public struct DerAlgorithmIdentifier: Sendable {
    public let oid: String
    public let parametersAbsent: Bool

    public init(oid: String, parametersAbsent: Bool) {
        self.oid = oid
        self.parametersAbsent = parametersAbsent
    }
}

public enum PqcDER {
    public enum Error: Swift.Error {
        case invalidPEM
        case invalidTag
        case invalidLength
        case invalidStructure
        case invalidOID
    }

    public static func decodePEM(_ pem: String, label: String) throws -> Data {
        let begin = "-----BEGIN \(label)-----"
        let end = "-----END \(label)-----"
        guard let beginRange = pem.range(of: begin), let endRange = pem.range(of: end) else {
            throw Error.invalidPEM
        }
        let body = pem[beginRange.upperBound..<endRange.lowerBound]
            .filter { !$0.isWhitespace }
        guard let data = Data(base64Encoded: String(body)) else {
            throw Error.invalidPEM
        }
        return data
    }

    public static func algorithmIdentifier(
        in data: Data,
        privateKey: Bool) throws -> DerAlgorithmIdentifier {
        let bytes = Array(data)
        let outer = try read(bytes, at: 0)
        let algorithmOffset: Int
        if privateKey {
            algorithmOffset = try read(bytes, at: outer.valueStart).end
        } else {
            algorithmOffset = outer.valueStart
        }
        let algorithm = try read(bytes, at: algorithmOffset)
        guard algorithm.tag == 0x30 else { throw Error.invalidTag }
        let oid = try read(bytes, at: algorithm.valueStart)
        guard oid.tag == 0x06 else { throw Error.invalidTag }
        return DerAlgorithmIdentifier(
            oid: try decodeOID(bytes, tlv: oid),
            parametersAbsent: oid.end == algorithm.end)
    }

    public static func subjectPublicKey(in data: Data) throws -> Data {
        let bytes = Array(data)
        let outer = try read(bytes, at: 0)
        let algorithm = try read(bytes, at: outer.valueStart)
        let bitString = try read(bytes, at: algorithm.end)
        guard bitString.tag == 0x03, bitString.length > 0, bytes[bitString.valueStart] == 0 else {
            throw Error.invalidStructure
        }
        return Data(bytes[(bitString.valueStart + 1)..<bitString.end])
    }

    public static func privateChoice(in data: Data) throws -> String {
        let bytes = Array(data)
        let outer = try read(bytes, at: 0)
        let version = try read(bytes, at: outer.valueStart)
        let algorithm = try read(bytes, at: version.end)
        let privateKey = try read(bytes, at: algorithm.end)
        guard privateKey.tag == 0x04 else { throw Error.invalidStructure }
        let choice = try read(bytes, at: privateKey.valueStart)
        guard choice.end == privateKey.end else { throw Error.invalidStructure }
        switch choice.tag {
        case 0x80: return "seed"
        case 0x04: return "expandedKey"
        case 0x30: return "both"
        default: throw Error.invalidStructure
        }
    }

    public static func subjectPublicKeyInfo(rawPublicKey: Data, oid: String) throws -> Data {
        let algorithm = sequence(oidValue(oid))
        let bitString = tlv(0x03, [0] + Array(rawPublicKey))
        return Data(sequence(algorithm + bitString))
    }

    public static func pkcs8Seed(seed: Data, oid: String) throws -> Data {
        let algorithm = sequence(oidValue(oid))
        let choice = tlv(0x80, Array(seed))
        let privateKey = tlv(0x04, choice)
        let version = tlv(0x02, [0])
        return Data(sequence(version + algorithm + privateKey))
    }

    private struct TLV {
        let tag: Int
        let length: Int
        let valueStart: Int
        let end: Int
    }

    private static func read(_ bytes: [UInt8], at offset: Int) throws -> TLV {
        guard offset >= 0, offset + 2 <= bytes.count else { throw Error.invalidLength }
        let tag = Int(bytes[offset])
        let lengthByte = Int(bytes[offset + 1])
        let length: Int
        let valueStart: Int
        if lengthByte < 0x80 {
            length = lengthByte
            valueStart = offset + 2
        } else {
            let count = lengthByte & 0x7f
            guard count > 0, count <= 8, offset + 2 + count <= bytes.count else {
                throw Error.invalidLength
            }
            var parsed = 0
            for index in 0..<count {
                parsed = (parsed << 8) | Int(bytes[offset + 2 + index])
            }
            length = parsed
            valueStart = offset + 2 + count
        }
        guard valueStart <= bytes.count, length <= bytes.count - valueStart else {
            throw Error.invalidLength
        }
        return TLV(tag: tag, length: length, valueStart: valueStart, end: valueStart + length)
    }

    private static func decodeOID(_ bytes: [UInt8], tlv: TLV) throws -> String {
        guard tlv.length > 0 else { throw Error.invalidOID }
        var values: [UInt64] = []
        var value: UInt64 = 0
        for byte in bytes[tlv.valueStart..<tlv.end] {
            guard value <= (UInt64.max >> 7) else { throw Error.invalidOID }
            value = (value << 7) | UInt64(byte & 0x7f)
            if byte & 0x80 == 0 {
                values.append(value)
                value = 0
            }
        }
        guard value == 0, let first = values.first else { throw Error.invalidOID }
        let firstArc = min(first / 40, 2)
        let secondArc = first - firstArc * 40
        return ([firstArc, secondArc] + Array(values.dropFirst())).map { String($0) }.joined(separator: ".")
    }

    private static func oidValue(_ oid: String) -> [UInt8] {
        let components = oid.split(separator: ".").compactMap { UInt64($0) }
        guard components.count >= 2 else { return [] }
        var output = encodeBase128(components[0] * 40 + components[1])
        for component in components.dropFirst(2) {
            output += encodeBase128(component)
        }
        return tlv(0x06, output)
    }

    private static func encodeBase128(_ value: UInt64) -> [UInt8] {
        var value = value
        var output = [UInt8(value & 0x7f)]
        value >>= 7
        while value > 0 {
            output.insert(UInt8(value & 0x7f) | 0x80, at: 0)
            value >>= 7
        }
        return output
    }

    private static func sequence(_ value: [UInt8]) -> [UInt8] {
        tlv(0x30, value)
    }

    private static func tlv(_ tag: UInt8, _ value: [UInt8]) -> [UInt8] {
        [tag] + length(value.count) + value
    }

    private static func length(_ value: Int) -> [UInt8] {
        guard value >= 0 else { return [] }
        if value < 0x80 { return [UInt8(value)] }
        var value = value
        var bytes: [UInt8] = []
        while value > 0 {
            bytes.insert(UInt8(value & 0xff), at: 0)
            value >>= 8
        }
        return [0x80 | UInt8(bytes.count)] + bytes
    }
}
