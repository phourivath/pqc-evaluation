package io.github.phourivath.pqcevaluation.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.Argument;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.CallSite;
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
  private static final String KEY_GENERATION_SNIPPET =
      """
          // [evidence:key-generation] java.security.KeyPairGenerator (BC provider): getInstance("ML-DSA", providerName), initialize(MLDSAParameterSpec); generateKeyPair() -> java.security.KeyPair
          KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-DSA", providerName);
          generator.initialize(parameterSpec);
          KeyPair keyPair = generator.generateKeyPair();
          """;
  private static final String SIGN_SNIPPET =
      """
          // [evidence:sign] java.security.Signature (BC provider): getInstance("ML-DSA", providerName), initSign(PrivateKey), update(byte[]); sign() -> byte[]
          Signature signer = Signature.getInstance("ML-DSA", providerName);
          signer.initSign(privateKey);
          signer.update(MESSAGE);
          byte[] signatureBytes = signer.sign();
          """;
  private static final String VERIFY_SNIPPET =
      """
          // [evidence:verify] java.security.Signature (BC provider): getInstance("ML-DSA", providerName), initVerify(PublicKey), update(byte[]); verify(byte[]) -> boolean
          Signature verifier = Signature.getInstance("ML-DSA", providerName);
          verifier.initVerify(publicKey);
          verifier.update(MESSAGE);
          boolean verified = verifier.verify(signatureBytes);
          """;

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
    MLDSAParameterSpec parameterSpec = MLDSAParameterSpec.fromName(parameters.name());
    var keyGenSite = Evidence.capture();
    // [evidence:key-generation] java.security.KeyPairGenerator (BC provider): getInstance("ML-DSA", providerName), initialize(MLDSAParameterSpec); generateKeyPair() -> java.security.KeyPair
    KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-DSA", providerName);
    generator.initialize(parameterSpec);
    KeyPair keyPair = generator.generateKeyPair();
    var keyGenCallSite =
        keyGenSite.with(
            KEY_GENERATION_SNIPPET,
            4,
            List.of(
                new Argument("algorithm", "java.lang.String", "ML-DSA"),
                new Argument("provider", "java.lang.String", providerName),
                new Argument(
                    "parameterSpec",
                    "org.bouncycastle.jcajce.spec.MLDSAParameterSpec",
                    parameters.name()),
                new Argument(
                    "keyPair",
                    "java.security.KeyPair",
                    keyPair.getPublic().getClass().getSimpleName()
                        + " + "
                        + keyPair.getPrivate().getClass().getSimpleName())));
    var publicKey = (MLDSAPublicKey) keyPair.getPublic();
    var privateKey = (MLDSAPrivateKey) keyPair.getPrivate();
    var rawPublic = publicKey.getPublicData();
    var rawSeed = privateKey.getSeed();
    var rawExpanded = privateKey.getPrivateData();
    var spki = publicKey.getEncoded();
    var pkcs8 = privateKey.getEncoded();

    var signSite = Evidence.capture();
    // [evidence:sign] java.security.Signature (BC provider): getInstance("ML-DSA", providerName), initSign(PrivateKey), update(byte[]); sign() -> byte[]
    Signature signer = Signature.getInstance("ML-DSA", providerName);
    signer.initSign(privateKey);
    signer.update(MESSAGE);
    byte[] signatureBytes = signer.sign();
    var signCallSite =
        signSite.with(
            SIGN_SNIPPET,
            5,
            List.of(
                new Argument("algorithm", "java.lang.String", "ML-DSA"),
                new Argument("provider", "java.lang.String", providerName),
                new Argument(
                    "key", "java.security.PrivateKey", privateKey.getClass().getName()),
                new Argument(
                    "message",
                    "byte[]",
                    new String(MESSAGE, StandardCharsets.UTF_8)
                        + " ("
                        + MESSAGE.length
                        + " bytes UTF-8)"),
                new Argument("signature", "byte[]", signatureBytes.length + " bytes")));
    var verifySite = Evidence.capture();
    // [evidence:verify] java.security.Signature (BC provider): getInstance("ML-DSA", providerName), initVerify(PublicKey), update(byte[]); verify(byte[]) -> boolean
    Signature verifier = Signature.getInstance("ML-DSA", providerName);
    verifier.initVerify(publicKey);
    verifier.update(MESSAGE);
    boolean verified = verifier.verify(signatureBytes);
    var verifyCallSite =
        verifySite.with(
            VERIFY_SNIPPET,
            5,
            List.of(
                new Argument("algorithm", "java.lang.String", "ML-DSA"),
                new Argument("provider", "java.lang.String", providerName),
                new Argument("key", "java.security.PublicKey", publicKey.getClass().getName()),
                new Argument(
                    "message",
                    "byte[]",
                    new String(MESSAGE, StandardCharsets.UTF_8)
                        + " ("
                        + MESSAGE.length
                        + " bytes UTF-8)"),
                new Argument("signature", "byte[]", signatureBytes.length + " bytes"),
                new Argument("verified", "boolean", String.valueOf(verified))));

    var keyFactory = KeyFactory.getInstance("ML-DSA", providerName);
    var importedPublic = (MLDSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(spki));
    var importedPrivate = (MLDSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    var roundTrip =
        Arrays.equals(rawPublic, importedPublic.getPublicData())
            && Arrays.equals(rawSeed, importedPrivate.getSeed())
            && Arrays.equals(rawExpanded, importedPrivate.getPrivateData());
    var expectedOid = oid(parameters.name());
    var spkiAlgorithm = Der.algorithmIdentifier(spki, false);
    var pkcs8Algorithm = Der.algorithmIdentifier(pkcs8, true);
    var spkiAlgorithmValid =
        expectedOid.equals(spkiAlgorithm.oid()) && spkiAlgorithm.parametersAbsent();
    var pkcs8AlgorithmValid =
        expectedOid.equals(pkcs8Algorithm.oid()) && pkcs8Algorithm.parametersAbsent();
    var algorithmIdentifierValid = spkiAlgorithmValid && pkcs8AlgorithmValid;

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
            roundTrip && algorithmIdentifierValid ? "pass" : "fail",
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
            new Capability(
                "key-generation", "supported", "native-api", "KeyPairGenerator ML-DSA", null, keyGenCallSite),
            new Capability("sign", "supported", "native-api", "Signature ML-DSA", null, signCallSite),
            new Capability("verify", "supported", "native-api", "Signature ML-DSA", null, verifyCallSite),
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
                 null,
                 pkcs8Algorithm.oid(),
                 pkcs8Algorithm.parametersAbsent(),
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

  /** Captures the runner's own call-site coordinates (file, class, method, line). */
  private static final class Evidence {
    private Evidence() {}

    static Coordinates capture() {
      var owner = BouncyCastleRunner.class.getName();
      for (var frame : Thread.currentThread().getStackTrace()) {
        if (owner.equals(frame.getClassName()) && !"capture".equals(frame.getMethodName())) {
          return new Coordinates(
              frame.getFileName(),
              frame.getClassName(),
              frame.getMethodName(),
              frame.getLineNumber());
        }
      }
      throw new IllegalStateException("No runner frame on the call stack");
    }

    record Coordinates(
        String sourceFile, String className, String methodName, int lineNumber) {
      CallSite with(String snippet, int highlightLine, List<Argument> arguments) {
        return new CallSite(
            sourceFile,
            className,
            methodName,
            lineNumber + highlightLine,
            snippet,
            highlightLine,
            arguments);
      }
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
