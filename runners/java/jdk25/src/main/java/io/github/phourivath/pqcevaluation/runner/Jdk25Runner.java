package io.github.phourivath.pqcevaluation.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.Capability;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.CheckResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.Implementation;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.ParameterSetResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.Representation;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.RuntimeInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Emits one normalized result using only the Java 25 SUN ML-DSA provider. */
public final class Jdk25Runner {

  private static final HexFormat HEX = HexFormat.of();
  private static final List<Parameters> PARAMETERS =
      List.of(
          new Parameters("ML-DSA-44", 2, 1312, 2560, 2420),
          new Parameters("ML-DSA-65", 3, 1952, 4032, 3309),
          new Parameters("ML-DSA-87", 5, 2592, 4896, 4627));

  private Jdk25Runner() {}

  public static void main(String[] args) throws Exception {
    var output = args.length == 0 ? Path.of("build", "evaluation-result.json") : Path.of(args[0]);
    Files.createDirectories(output.toAbsolutePath().getParent());
    var checks = new ArrayList<CheckResult>();
    var results = new ArrayList<ParameterSetResult>();

    for (var parameters : PARAMETERS) {
      results.add(evaluate(parameters, checks));
    }

    var result =
        new EvaluationResult(
            "1.0",
            "jdk25-" + UUID.randomUUID(),
            Instant.now(),
            new Implementation(
                "jdk-sun-ml-dsa",
                "JDK SUN ML-DSA",
                Runtime.version().toString(),
                "jdk-sun",
                "java.base",
                "GPLv2 with Classpath Exception",
                "JDK platform implementation"),
            new RuntimeInfo(
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                Map.of("provider", "SUN", "algorithm", "ML-DSA")),
            results,
            checks,
            List.of(),
            List.of(
                "JDK ML-DSA exposes standard containers but no supported public raw-key API.",
                "JDK 25 does not expose application contexts or HashML-DSA."));

    var mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.writeValue(output.toFile(), result);
    System.out.println(output.toAbsolutePath());
  }

  private static ParameterSetResult evaluate(Parameters parameters, List<CheckResult> checks)
      throws Exception {
    var generator = KeyPairGenerator.getInstance("ML-DSA");
    generator.initialize(new NamedParameterSpec(parameters.name()));
    var keyPair = generator.generateKeyPair();
    var message = "PQC evaluation message".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var signature = Signature.getInstance("ML-DSA");
    signature.initSign(keyPair.getPrivate());
    signature.update(message);
    var signatureBytes = signature.sign();
    var verifier = Signature.getInstance("ML-DSA");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(message);
    var verified = verifier.verify(signatureBytes);

    var spki = keyPair.getPublic().getEncoded();
    var pkcs8 = keyPair.getPrivate().getEncoded();
    var rawPublic = Der.subjectPublicKey(spki);
    var keyFactory = KeyFactory.getInstance("ML-DSA");
    keyFactory.generatePublic(new X509EncodedKeySpec(spki));
    keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    var expectedOid = oid(parameters.name());
    var spkiAlgorithm = Der.algorithmIdentifier(spki, false);
    var pkcs8Algorithm = Der.algorithmIdentifier(pkcs8, true);
    var spkiAlgorithmValid =
        expectedOid.equals(spkiAlgorithm.oid()) && spkiAlgorithm.parametersAbsent();
    var pkcs8AlgorithmValid =
        expectedOid.equals(pkcs8Algorithm.oid()) && pkcs8Algorithm.parametersAbsent();
    var algorithmIdentifierValid = spkiAlgorithmValid && pkcs8AlgorithmValid;

    checks.add(check(parameters.name(), "key-generation", "correctness", "pass", "Generated key pair"));
    checks.add(
        check(
            parameters.name(),
            "raw-public-length",
            "encoding",
            rawPublic.length == parameters.publicBytes() ? "pass" : "fail",
            "SPKI BIT STRING payload is " + rawPublic.length + " bytes"));
    checks.add(
        check(
            parameters.name(),
            "signature-length",
            "correctness",
            signatureBytes.length == parameters.signatureBytes() ? "pass" : "fail",
            "Signature is " + signatureBytes.length + " bytes"));
    checks.add(
        check(
            parameters.name(),
            "self-sign-verify",
            "correctness",
            verified ? "pass" : "fail",
            "Generated signature verifies"));
    checks.add(
        check(
            parameters.name(),
            "algorithm-identifier",
            "encoding",
            algorithmIdentifierValid ? "pass" : "fail",
            "SPKI OID "
                + spkiAlgorithm.oid()
                + " (parameters "
                + (spkiAlgorithm.parametersAbsent() ? "absent" : "present")
                + "), PKCS#8 OID "
                + pkcs8Algorithm.oid()
                + " (parameters "
                + (pkcs8Algorithm.parametersAbsent() ? "absent" : "present")
                + ")"));
    checks.add(
        check(
            parameters.name(),
            "standard-key-round-trip",
            "encoding",
            algorithmIdentifierValid ? "pass" : "fail",
            "SPKI and PKCS#8 re-imported through KeyFactory"));
    checks.add(
        check(
            parameters.name(),
            "raw-expanded-private",
            "capability",
            "unsupported",
            "SUN provider does not expose expanded raw private bytes"));
    checks.add(
        check(
            parameters.name(),
            "application-context",
            "capability",
            "unsupported",
            "JDK 25 ML-DSA API uses the empty context only"));

    var capabilities =
        List.of(
            new Capability("key-generation", "supported", "native-api", "KeyPairGenerator ML-DSA", null),
            new Capability("sign", "supported", "native-api", "Signature ML-DSA", null),
            new Capability("verify", "supported", "native-api", "Signature ML-DSA", null),
            new Capability(
                "raw-public",
                "supported",
                "evaluator-derived",
                "SPKI BIT STRING extraction",
                "The JDK does not expose a raw public-key interface"),
            new Capability("spki", "supported", "standard-container", "X.509 SubjectPublicKeyInfo", null),
            new Capability("pkcs8", "supported", "standard-container", "PKCS#8 OneAsymmetricKey", null),
            new Capability(
                "raw-private-seed",
                "unsupported",
                "native-api",
                "No public raw-key interface",
                "Use a provider with an explicit seed API"),
            new Capability(
                "raw-private-expanded",
                "unsupported",
                "native-api",
                "No public raw-key interface",
                "Use a provider with an explicit expanded-key API"),
            new Capability(
                "context",
                "unsupported",
                "native-api",
                "JEP 497 non-goal",
                "Application-specific context strings are not exposed"));
    var pkcs8PrivateChoice = Der.privateChoice(pkcs8);
    var representations =
        List.of(
            new Representation(
                "raw-public",
                "pass",
                 rawPublic.length,
                 sha256(rawPublic),
                 null,
                 null,
                 null,
                "evaluator-derived",
                "Extracted from SPKI for size and fingerprint validation"),
             new Representation(
                 "spki",
                 spkiAlgorithmValid ? "pass" : "fail",
                 spki.length,
                 sha256(spki),
                 spkiAlgorithm.oid(),
                 spkiAlgorithm.parametersAbsent(),
                 null,
                 "standard-container",
                 null),
             new Representation(
                 "pkcs8",
                 pkcs8AlgorithmValid ? "pass" : "fail",
                 pkcs8.length,
                 sha256(pkcs8),
                 pkcs8Algorithm.oid(),
                 pkcs8Algorithm.parametersAbsent(),
                 pkcs8PrivateChoice,
                 "standard-container",
                 null),
            new Representation(
                "raw-private-seed",
                "unsupported",
                null,
                null,
                null,
                null,
                null,
                "native-api",
                "JDK API does not expose the seed as raw bytes"),
            new Representation(
                "raw-private-expanded",
                "unsupported",
                null,
                null,
                null,
                null,
                null,
                "native-api",
                "JDK API does not expose expanded private bytes"));
    return new ParameterSetResult(
        parameters.name(),
        parameters.securityLevel(),
        parameters.publicBytes(),
        parameters.seedBytes(),
        parameters.privateBytes(),
        parameters.signatureBytes(),
        capabilities,
        representations);
  }

  private static CheckResult check(
      String parameterSet, String id, String category, String status, String message) {
    return new CheckResult(id, parameterSet, category, status, message);
  }

  private static String sha256(byte[] bytes) {
    try {
      return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private static String oid(String parameterSet) {
    return switch (parameterSet) {
      case "ML-DSA-44" -> "2.16.840.1.101.3.4.3.17";
      case "ML-DSA-65" -> "2.16.840.1.101.3.4.3.18";
      case "ML-DSA-87" -> "2.16.840.1.101.3.4.3.19";
      default -> throw new IllegalArgumentException("Unknown parameter set: " + parameterSet);
    };
  }

  private record Parameters(
      String name, int securityLevel, int publicBytes, int privateBytes, int signatureBytes) {
    int seedBytes() {
      return 32;
    }
  }

  private static final class Der {
    private Der() {}

    static byte[] subjectPublicKey(byte[] encoded) {
      var outer = read(encoded, 0);
      var algorithm = read(encoded, outer.valueStart());
      var bitString = read(encoded, algorithm.nextOffset());
      if (bitString.tag() != 0x03 || encoded[bitString.valueStart()] != 0) {
        throw new IllegalArgumentException("Invalid SubjectPublicKeyInfo");
      }
      return java.util.Arrays.copyOfRange(
          encoded, bitString.valueStart() + 1, bitString.valueStart() + bitString.length());
    }

    static AlgorithmIdentifier algorithmIdentifier(byte[] encoded, boolean pkcs8) {
      var outer = read(encoded, 0);
      var algorithmOffset =
          pkcs8 ? read(encoded, outer.valueStart()).nextOffset() : outer.valueStart();
      var algorithm = read(encoded, algorithmOffset);
      var oid = read(encoded, algorithm.valueStart());
      if (algorithm.tag() != 0x30 || oid.tag() != 0x06) {
        throw new IllegalArgumentException("Invalid AlgorithmIdentifier");
      }
      return new AlgorithmIdentifier(
          objectIdentifier(encoded, oid), oid.nextOffset() == algorithm.nextOffset());
    }

    private static String objectIdentifier(byte[] encoded, Tlv value) {
      var first = readOidComponent(encoded, value.valueStart(), value.nextOffset());
      var firstArc = first.value() < 40 ? 0 : first.value() < 80 ? 1 : 2;
      var builder = new StringBuilder(firstArc + "." + (first.value() - firstArc * 40L));
      var offset = first.nextOffset();
      while (offset < value.nextOffset()) {
        var component = readOidComponent(encoded, offset, value.nextOffset());
        builder.append('.').append(component.value());
        offset = component.nextOffset();
      }
      return builder.toString();
    }

    private static OidComponent readOidComponent(byte[] encoded, int offset, int end) {
      var value = 0L;
      while (offset < end) {
        var part = encoded[offset++] & 0xff;
        if (value > (Long.MAX_VALUE >>> 7)) {
          throw new IllegalArgumentException("OID component is too large");
        }
        value = (value << 7) | (part & 0x7f);
        if ((part & 0x80) == 0) {
          return new OidComponent(value, offset);
        }
      }
      throw new IllegalArgumentException("Truncated object identifier");
    }

    static String privateChoice(byte[] encoded) {
      var outer = read(encoded, 0);
      var version = read(encoded, outer.valueStart());
      var algorithm = read(encoded, version.nextOffset());
      var privateKey = read(encoded, algorithm.nextOffset());
      if (privateKey.tag() != 0x04) {
        throw new IllegalArgumentException("Invalid PKCS#8 private key");
      }
      var choice = read(encoded, privateKey.valueStart());
      if (choice.nextOffset() != privateKey.nextOffset()) {
        throw new IllegalArgumentException("Invalid ML-DSA private key choice");
      }
      return switch (choice.tag()) {
        case 0x80 -> "seed";
        case 0x04 -> "expandedKey";
        case 0x30 -> "both";
        default -> "unknown";
      };
    }

    private static Tlv read(byte[] bytes, int offset) {
      var tag = bytes[offset] & 0xff;
      var lengthByte = bytes[offset + 1] & 0xff;
      var lengthBytes = lengthByte < 128 ? 0 : lengthByte & 0x7f;
      var length = lengthByte < 128 ? lengthByte : 0;
      for (var index = 0; index < lengthBytes; index++) {
        length = (length << 8) | (bytes[offset + 2 + index] & 0xff);
      }
      var valueStart = offset + 2 + lengthBytes;
      return new Tlv(tag, length, valueStart, valueStart + length);
    }

    private record AlgorithmIdentifier(String oid, boolean parametersAbsent) {}

    private record OidComponent(long value, int nextOffset) {}

    private record Tlv(int tag, int length, int valueStart, int nextOffset) {}
  }
}
