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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bouncycastle.jcajce.interfaces.MLDSAPrivateKey;
import org.bouncycastle.jcajce.interfaces.MLDSAPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jcajce.spec.ContextParameterSpec;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;

/** Emits one normalized result using only the Bouncy Castle LTS provider in this process. */
public final class BouncyCastleRunner {

  private static final String PROVIDER_VERSION = "2.73.12.1";
  private static final String IMPLEMENTATION_ID = "bc-lts-ml-dsa";
  private static final String ENGINE_LINEAGE = "bouncycastle-java";
  private static final HexFormat HEX = HexFormat.of();
  private static final byte[] MESSAGE = "PQC evaluation message".getBytes(StandardCharsets.UTF_8);
  private static final byte[] CONTEXT = "pqc-evaluation".getBytes(StandardCharsets.UTF_8);
  private static final List<Parameters> PARAMETERS =
      List.of(
          new Parameters("ML-DSA-44", 2, 1312, 2560, 2420),
          new Parameters("ML-DSA-65", 3, 1952, 4032, 3309),
          new Parameters("ML-DSA-87", 5, 2592, 4896, 4627));

  private BouncyCastleRunner() {}

  public static void main(String[] args) throws Exception {
    var output = args.length == 0 ? Path.of("build", "evaluation-result.json") : Path.of(args[0]);
    Files.createDirectories(output.toAbsolutePath().getParent());

    Security.removeProvider("BC");
    var provider = new BouncyCastleProvider();
    Security.addProvider(provider);
    var providerName = provider.getName();
    var checks = new ArrayList<CheckResult>();
    var results = new ArrayList<ParameterSetResult>();
    for (var parameters : PARAMETERS) {
      results.add(evaluate(parameters, providerName, checks));
    }

    var result =
        new EvaluationResult(
            "1.0",
            "bc-lts-" + UUID.randomUUID(),
            Instant.now(),
            new Implementation(
                IMPLEMENTATION_ID,
                "Bouncy Castle LTS ML-DSA",
                PROVIDER_VERSION,
                ENGINE_LINEAGE,
                provider.getInfo(),
                "Bouncy Castle License",
                "third-party provider, not FIPS validated"),
            new RuntimeInfo(
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                Map.of("provider", providerName, "algorithm", "ML-DSA")),
            results,
            checks,
            List.of(),
            List.of());

    var mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.writeValue(output.toFile(), result);
    System.out.println(output.toAbsolutePath());
  }

  private static ParameterSetResult evaluate(
      Parameters parameters, String providerName, List<CheckResult> checks) throws Exception {
    var parameterSpec = MLDSAParameterSpec.fromName(parameters.name());
    var generator = KeyPairGenerator.getInstance("ML-DSA", providerName);
    generator.initialize(parameterSpec);
    var keyPair = generator.generateKeyPair();
    var publicKey = (MLDSAPublicKey) keyPair.getPublic();
    var privateKey = (MLDSAPrivateKey) keyPair.getPrivate();
    var rawPublic = publicKey.getPublicData();
    var rawSeed = privateKey.getSeed();
    var rawExpanded = privateKey.getPrivateData();
    var spki = publicKey.getEncoded();
    var pkcs8 = privateKey.getEncoded();

    var signer = Signature.getInstance("ML-DSA", providerName);
    signer.initSign(privateKey);
    signer.update(MESSAGE);
    var signatureBytes = signer.sign();
    var verifier = Signature.getInstance("ML-DSA", providerName);
    verifier.initVerify(publicKey);
    verifier.update(MESSAGE);
    var verified = verifier.verify(signatureBytes);

    var keyFactory = KeyFactory.getInstance("ML-DSA", providerName);
    var importedPublic = (MLDSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(spki));
    var importedPrivate = (MLDSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    var roundTrip =
        Arrays.equals(rawPublic, importedPublic.getPublicData())
            && Arrays.equals(rawSeed, importedPrivate.getSeed())
            && Arrays.equals(rawExpanded, importedPrivate.getPrivateData());

    var contextSupported = false;
    String contextMessage;
    try {
      var contextSigner = Signature.getInstance("ML-DSA", providerName);
      contextSigner.initSign(privateKey);
      contextSigner.setParameter(new ContextParameterSpec(CONTEXT));
      contextSigner.update(MESSAGE);
      var contextSignature = contextSigner.sign();
      var contextVerifier = Signature.getInstance("ML-DSA", providerName);
      contextVerifier.initVerify(publicKey);
      contextVerifier.setParameter(new ContextParameterSpec(CONTEXT));
      contextVerifier.update(MESSAGE);
      contextSupported = contextVerifier.verify(contextSignature);
      contextMessage = "Non-empty context signs and verifies";
    } catch (Exception exception) {
      contextMessage = "Context API unavailable: " + exception.getClass().getSimpleName();
    }

    checks.add(check(parameters.name(), "key-generation", "correctness", "pass", "Generated key pair"));
    checks.add(
        check(
            parameters.name(),
            "raw-public-length",
            "encoding",
            rawPublic.length == parameters.publicBytes() ? "pass" : "fail",
            "Raw public key is " + rawPublic.length + " bytes"));
    checks.add(
        check(
            parameters.name(),
            "raw-private-seed-length",
            "encoding",
            rawSeed.length == parameters.seedBytes() ? "pass" : "fail",
            "Raw seed is " + rawSeed.length + " bytes"));
    checks.add(
        check(
            parameters.name(),
            "raw-private-expanded-length",
            "encoding",
            rawExpanded.length == parameters.privateBytes() ? "pass" : "fail",
            "Expanded private key is " + rawExpanded.length + " bytes"));
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
            "standard-key-round-trip",
            "encoding",
            roundTrip ? "pass" : "fail",
            "SPKI and PKCS#8 preserve raw key material"));
    checks.add(
        check(
            parameters.name(),
            "application-context",
            "capability",
            contextSupported ? "pass" : "unsupported",
            contextMessage));

    var capabilities =
        List.of(
            new Capability("key-generation", "supported", "native-api", "KeyPairGenerator ML-DSA", null),
            new Capability("sign", "supported", "native-api", "Signature ML-DSA", null),
            new Capability("verify", "supported", "native-api", "Signature ML-DSA", null),
            new Capability("raw-public", "supported", "native-api", "MLDSAPublicKey.getPublicData", null),
            new Capability(
                "raw-private-seed", "supported", "native-api", "MLDSAPrivateKey.getSeed", null),
            new Capability(
                "raw-private-expanded",
                "supported",
                "native-api",
                "MLDSAPrivateKey.getPrivateData",
                null),
            new Capability("spki", "supported", "standard-container", "X.509 SubjectPublicKeyInfo", null),
            new Capability("pkcs8", "supported", "standard-container", "PKCS#8 OneAsymmetricKey", null),
            new Capability(
                "context",
                contextSupported ? "supported" : "unsupported",
                "native-api",
                "ContextParameterSpec",
                contextSupported ? null : contextMessage));
    var parametersAbsent = Der.algorithmParametersAbsent(spki);
    var pkcs8PrivateChoice = Der.privateChoice(pkcs8);
    var representations =
        List.of(
            new Representation(
                "raw-public",
                "pass",
                rawPublic.length,
                sha256(rawPublic),
                oid(parameters.name()),
                null,
                null,
                "native-api",
                null),
            new Representation(
                "raw-private-seed",
                "pass",
                rawSeed.length,
                null,
                null,
                null,
                "seed",
                "native-api",
                "Private bytes are not retained in the result"),
            new Representation(
                "raw-private-expanded",
                "pass",
                rawExpanded.length,
                null,
                null,
                null,
                "expandedKey",
                "native-api",
                "Private bytes are not retained in the result"),
            new Representation(
                "spki",
                "pass",
                spki.length,
                sha256(spki),
                oid(parameters.name()),
                parametersAbsent,
                null,
                "standard-container",
                null),
            new Representation(
                "pkcs8",
                "pass",
                pkcs8.length,
                 sha256(pkcs8),
                 oid(parameters.name()),
                 parametersAbsent,
                 pkcs8PrivateChoice,
                 "standard-container",
                 null));
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

  private static String sha256(byte[] bytes) throws Exception {
    return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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

    static boolean algorithmParametersAbsent(byte[] encoded) {
      var outer = read(encoded, 0);
      var algorithm = read(encoded, outer.valueStart());
      var oid = read(encoded, algorithm.valueStart());
      return oid.nextOffset() == algorithm.nextOffset();
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

    private record Tlv(int tag, int length, int valueStart, int nextOffset) {}
  }
}
