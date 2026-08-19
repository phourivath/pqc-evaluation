package io.github.phourivath.pqcevaluation.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.phourivath.pqcevaluation.contract.EvaluationResult
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.Capability
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.CheckResult
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.Implementation
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.ParameterSetResult
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.Representation
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.RuntimeInfo
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Files
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Security
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import org.bouncycastle.jcajce.interfaces.MLDSAPrivateKey
import org.bouncycastle.jcajce.interfaces.MLDSAPublicKey
import org.bouncycastle.jcajce.spec.ContextParameterSpec
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec
import org.bouncycastle.jce.provider.BouncyCastleProvider

internal class EvaluationRunner(private val output: Path) {
    fun run() {
        Security.removeProvider(PROVIDER_NAME)
        val provider = BouncyCastleProvider()
        Security.addProvider(provider)

        val checks = ArrayList<CheckResult>()
        val results = PARAMETERS.map { evaluate(it, provider.name, checks) }
        val result =
            EvaluationResult(
                "1.0",
                "bc-" + UUID.randomUUID(),
                Instant.now(),
                Implementation(
                    IMPLEMENTATION_ID,
                    "Bouncy Castle Base ML-DSA (Kotlin/JVM)",
                    PROVIDER_VERSION,
                    ENGINE_LINEAGE,
                    provider.info,
                    "Bouncy Castle License",
                    "third-party provider, not FIPS validated"),
                RuntimeInfo(
                    System.getProperty("java.version"),
                    System.getProperty("java.vendor"),
                    System.getProperty("os.name"),
                    System.getProperty("os.version"),
                    System.getProperty("os.arch"),
                    mapOf(
                        "language" to "kotlin",
                        "api" to "java-jca-from-kotlin",
                        "provider" to provider.name,
                        "algorithm" to "ML-DSA")),
                results,
                checks,
                emptyList(),
                emptyList())

        val mapper =
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper.writeValue(output.toFile(), result)
    }

    private fun evaluate(
        parameters: Parameters,
        providerName: String,
        checks: MutableList<CheckResult>
    ): ParameterSetResult {
        val parameterSpec = MLDSAParameterSpec.fromName(parameters.name)
        val generator = KeyPairGenerator.getInstance("ML-DSA", providerName)
        generator.initialize(parameterSpec)
        val keyPair = generator.generateKeyPair()
        val publicKey = keyPair.public as MLDSAPublicKey
        val privateKey = keyPair.private as MLDSAPrivateKey
        val rawPublic = publicKey.publicData
        val rawSeed = privateKey.seed
        val rawExpanded = privateKey.privateData
        val spki = publicKey.encoded
        val pkcs8 = privateKey.encoded

        val signer = Signature.getInstance("ML-DSA", providerName)
        signer.initSign(privateKey)
        signer.update(MESSAGE)
        val signatureBytes = signer.sign()

        val verifier = Signature.getInstance("ML-DSA", providerName)
        verifier.initVerify(publicKey)
        verifier.update(MESSAGE)
        val verified = verifier.verify(signatureBytes)

        val keyFactory = KeyFactory.getInstance("ML-DSA", providerName)
        val importedPublic =
            keyFactory.generatePublic(X509EncodedKeySpec(spki)) as MLDSAPublicKey
        val importedPrivate =
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as MLDSAPrivateKey
        val roundTrip =
            rawPublic.contentEquals(importedPublic.publicData) &&
                rawSeed.contentEquals(importedPrivate.seed) &&
                rawExpanded.contentEquals(importedPrivate.privateData)

        val expectedOid = oid(parameters.name)
        val spkiAlgorithm = Der.algorithmIdentifier(spki, false)
        val pkcs8Algorithm = Der.algorithmIdentifier(pkcs8, true)
        val spkiAlgorithmValid =
            expectedOid == spkiAlgorithm.oid && spkiAlgorithm.parametersAbsent
        val pkcs8AlgorithmValid =
            expectedOid == pkcs8Algorithm.oid && pkcs8Algorithm.parametersAbsent
        val algorithmIdentifierValid = spkiAlgorithmValid && pkcs8AlgorithmValid

        val contextResult = evaluateContext(privateKey, publicKey, providerName)

        checks += check(parameters.name, "key-generation", "correctness", "pass", "Generated key pair")
        checks +=
            check(
                parameters.name,
                "raw-public-length",
                "encoding",
                status(rawPublic.size == parameters.publicBytes),
                "Raw public key is ${rawPublic.size} bytes")
        checks +=
            check(
                parameters.name,
                "raw-private-seed-length",
                "encoding",
                status(rawSeed.size == parameters.seedBytes),
                "Raw seed is ${rawSeed.size} bytes")
        checks +=
            check(
                parameters.name,
                "raw-private-expanded-length",
                "encoding",
                status(rawExpanded.size == parameters.privateBytes),
                "Expanded private key is ${rawExpanded.size} bytes")
        checks +=
            check(
                parameters.name,
                "signature-length",
                "correctness",
                status(signatureBytes.size == parameters.signatureBytes),
                "Signature is ${signatureBytes.size} bytes")
        checks +=
            check(
                parameters.name,
                "self-sign-verify",
                "correctness",
                status(verified),
                "Generated signature verifies")
        checks +=
            check(
                parameters.name,
                "algorithm-identifier",
                "encoding",
                status(algorithmIdentifierValid),
                "SPKI OID ${spkiAlgorithm.oid} (parameters ${parameterState(spkiAlgorithm.parametersAbsent)}), " +
                    "PKCS#8 OID ${pkcs8Algorithm.oid} (parameters ${parameterState(pkcs8Algorithm.parametersAbsent)})")
        checks +=
            check(
                parameters.name,
                "standard-key-round-trip",
                "encoding",
                status(roundTrip && algorithmIdentifierValid),
                "SPKI and PKCS#8 preserve raw key material")
        checks +=
            check(
                parameters.name,
                "application-context",
                "capability",
                if (contextResult.supported) "pass" else "unsupported",
                contextResult.message)

        val capabilities =
            listOf(
                Capability("key-generation", "supported", "native-api", "KeyPairGenerator ML-DSA", null),
                Capability("sign", "supported", "native-api", "Signature ML-DSA", null),
                Capability("verify", "supported", "native-api", "Signature ML-DSA", null),
                Capability("raw-public", "supported", "native-api", "MLDSAPublicKey.publicData", null),
                Capability(
                    "raw-private-seed",
                    "supported",
                    "native-api",
                    "MLDSAPrivateKey.seed",
                    null),
                Capability(
                    "raw-private-expanded",
                    "supported",
                    "native-api",
                    "MLDSAPrivateKey.privateData",
                    null),
                Capability("spki", "supported", "standard-container", "X.509 SubjectPublicKeyInfo", null),
                Capability("pkcs8", "supported", "standard-container", "PKCS#8 OneAsymmetricKey", null),
                Capability(
                    "context",
                    if (contextResult.supported) "supported" else "unsupported",
                    "native-api",
                    "ContextParameterSpec",
                    if (contextResult.supported) null else contextResult.message))

        val representations =
            listOf(
                Representation(
                    "raw-public",
                    "pass",
                    rawPublic.size,
                    sha256(rawPublic),
                    null,
                    null,
                    null,
                    "native-api",
                    null),
                Representation(
                    "raw-private-seed",
                    "pass",
                    rawSeed.size,
                    null,
                    null,
                    null,
                    "seed",
                    "native-api",
                    "Private bytes are not retained in the result"),
                Representation(
                    "raw-private-expanded",
                    "pass",
                    rawExpanded.size,
                    null,
                    null,
                    null,
                    "expandedKey",
                    "native-api",
                    "Private bytes are not retained in the result"),
                Representation(
                    "spki",
                    status(spkiAlgorithmValid),
                    spki.size,
                    sha256(spki),
                    spkiAlgorithm.oid,
                    spkiAlgorithm.parametersAbsent,
                    null,
                    "standard-container",
                    null),
                Representation(
                    "pkcs8",
                    status(pkcs8AlgorithmValid),
                    pkcs8.size,
                    null,
                    pkcs8Algorithm.oid,
                    pkcs8Algorithm.parametersAbsent,
                    Der.privateChoice(pkcs8),
                    "standard-container",
                    null))

        return ParameterSetResult(
            parameters.name,
            parameters.securityLevel,
            parameters.publicBytes,
            parameters.seedBytes,
            parameters.privateBytes,
            parameters.signatureBytes,
            capabilities,
            representations)
    }

    private fun evaluateContext(
        privateKey: MLDSAPrivateKey,
        publicKey: MLDSAPublicKey,
        providerName: String
    ): ContextResult {
        return try {
            val contextSigner = Signature.getInstance("ML-DSA", providerName)
            contextSigner.initSign(privateKey)
            contextSigner.setParameter(ContextParameterSpec(CONTEXT))
            contextSigner.update(MESSAGE)
            val contextSignature = contextSigner.sign()

            val contextVerifier = Signature.getInstance("ML-DSA", providerName)
            contextVerifier.initVerify(publicKey)
            contextVerifier.setParameter(ContextParameterSpec(CONTEXT))
            contextVerifier.update(MESSAGE)
            ContextResult(contextVerifier.verify(contextSignature), "Non-empty context signs and verifies")
        } catch (exception: Exception) {
            ContextResult(false, "Context API unavailable: ${exception::class.simpleName}")
        }
    }

    private fun check(
        parameterSet: String,
        id: String,
        category: String,
        status: String,
        message: String
    ) = CheckResult(id, parameterSet, category, status, message)

    private fun status(passed: Boolean) = if (passed) "pass" else "fail"

    private fun parameterState(absent: Boolean) = if (absent) "absent" else "present"

    private fun sha256(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun oid(parameterSet: String) =
        when (parameterSet) {
            "ML-DSA-44" -> "2.16.840.1.101.3.4.3.17"
            "ML-DSA-65" -> "2.16.840.1.101.3.4.3.18"
            "ML-DSA-87" -> "2.16.840.1.101.3.4.3.19"
            else -> error("Unknown parameter set: $parameterSet")
        }

    private data class ContextResult(val supported: Boolean, val message: String)

    private data class Parameters(
        val name: String,
        val securityLevel: Int,
        val publicBytes: Int,
        val privateBytes: Int,
        val signatureBytes: Int
    ) {
        val seedBytes = 32
    }

    private data class AlgorithmIdentifier(val oid: String, val parametersAbsent: Boolean)

    private data class OidComponent(val value: Long, val nextOffset: Int)

    private data class Tlv(val tag: Int, val length: Int, val valueStart: Int, val nextOffset: Int)

    private object Der {
        fun algorithmIdentifier(encoded: ByteArray, pkcs8: Boolean): AlgorithmIdentifier {
            val outer = read(encoded, 0)
            val algorithmOffset =
                if (pkcs8) read(encoded, outer.valueStart).nextOffset else outer.valueStart
            val algorithm = read(encoded, algorithmOffset)
            val oid = read(encoded, algorithm.valueStart)
            require(algorithm.tag == 0x30 && oid.tag == 0x06) { "Invalid AlgorithmIdentifier" }
            return AlgorithmIdentifier(
                objectIdentifier(encoded, oid),
                oid.nextOffset == algorithm.nextOffset)
        }

        fun privateChoice(encoded: ByteArray): String {
            val outer = read(encoded, 0)
            val version = read(encoded, outer.valueStart)
            val algorithm = read(encoded, version.nextOffset)
            val privateKey = read(encoded, algorithm.nextOffset)
            require(privateKey.tag == 0x04) { "Invalid PKCS#8 private key" }
            val choice = read(encoded, privateKey.valueStart)
            require(choice.nextOffset == privateKey.nextOffset) { "Invalid ML-DSA private key choice" }
            return when (choice.tag) {
                0x80 -> "seed"
                0x04 -> "expandedKey"
                0x30 -> "both"
                else -> "unknown"
            }
        }

        private fun objectIdentifier(encoded: ByteArray, value: Tlv): String {
            val first = readOidComponent(encoded, value.valueStart, value.nextOffset)
            val firstArc = when {
                first.value < 40 -> 0
                first.value < 80 -> 1
                else -> 2
            }
            val builder = StringBuilder("$firstArc.${first.value - firstArc * 40L}")
            var offset = first.nextOffset
            while (offset < value.nextOffset) {
                val component = readOidComponent(encoded, offset, value.nextOffset)
                builder.append('.').append(component.value)
                offset = component.nextOffset
            }
            return builder.toString()
        }

        private fun readOidComponent(encoded: ByteArray, start: Int, end: Int): OidComponent {
            var offset = start
            var value = 0L
            while (offset < end) {
                val part = encoded[offset++].toInt() and 0xff
                require(value <= (Long.MAX_VALUE ushr 7)) { "OID component is too large" }
                value = (value shl 7) or (part and 0x7f).toLong()
                if ((part and 0x80) == 0) {
                    return OidComponent(value, offset)
                }
            }
            error("Truncated object identifier")
        }

        private fun read(bytes: ByteArray, offset: Int): Tlv {
            val tag = bytes[offset].toInt() and 0xff
            val lengthByte = bytes[offset + 1].toInt() and 0xff
            val lengthBytes = if (lengthByte < 128) 0 else lengthByte and 0x7f
            var length = if (lengthByte < 128) lengthByte else 0
            for (index in 0 until lengthBytes) {
                length = (length shl 8) or (bytes[offset + 2 + index].toInt() and 0xff)
            }
            val valueStart = offset + 2 + lengthBytes
            return Tlv(tag, length, valueStart, valueStart + length)
        }
    }

    private companion object {
        const val PROVIDER_NAME = "BC"
        const val PROVIDER_VERSION = "1.85.2"
        const val IMPLEMENTATION_ID = "bc-ml-dsa"
        const val ENGINE_LINEAGE = "bouncycastle-java"
        val MESSAGE = "PQC evaluation message".toByteArray(StandardCharsets.UTF_8)
        val CONTEXT = "pqc-evaluation".toByteArray(StandardCharsets.UTF_8)
        val PARAMETERS =
            listOf(
                Parameters("ML-DSA-44", 2, 1312, 2560, 2420),
                Parameters("ML-DSA-65", 3, 1952, 4032, 3309),
                Parameters("ML-DSA-87", 5, 2592, 4896, 4627))
    }
}
