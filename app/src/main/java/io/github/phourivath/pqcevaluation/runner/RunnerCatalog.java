package io.github.phourivath.pqcevaluation.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Fixed catalog of runners that the local execution service is permitted to launch. */
@Service
public class RunnerCatalog {

  private static final List<String> PARAMETER_SETS = List.of("ML-DSA-44", "ML-DSA-65", "ML-DSA-87");

  private final Map<String, RunnerDefinition> definitions;

  @Autowired
  public RunnerCatalog(RunnerExecutionProperties properties) {
    this(properties, HostEnvironment.current());
  }

  RunnerCatalog(RunnerExecutionProperties properties, HostEnvironment hostEnvironment) {
    var root = resolveRunnerRoot(properties.runnerRoot());
    definitions =
        Map.of(
            "jdk25",
            new RunnerDefinition(
                "jdk25",
                "JDK 25 SUN ML-DSA",
                "jdk-sun-ml-dsa",
                "25",
                "jdk-sun",
                "IMPLEMENTED",
                null,
                root.resolve("runners/java/jdk25/target/jdk25-runner-0.0.1-SNAPSHOT.jar"),
                RunnerLaunchKind.JAVA_JAR,
                PARAMETER_SETS),
            "bc-base",
            new RunnerDefinition(
                "bc-base",
                "Bouncy Castle Base ML-DSA",
                "bc-ml-dsa",
                "1.85.2",
                "bouncycastle-java",
                "IMPLEMENTED",
                null,
                root.resolve("runners/java/bc-base/target/bc-base-runner-0.0.1-SNAPSHOT.jar"),
                RunnerLaunchKind.JAVA_JAR,
                PARAMETER_SETS),
            "bc-kotlin",
            new RunnerDefinition(
                "bc-kotlin",
                "Bouncy Castle Base ML-DSA (Kotlin/JVM)",
                "bc-ml-dsa",
                "1.85.2",
                "bouncycastle-java",
                "IMPLEMENTED",
                null,
                root.resolve("runners/kotlin/bc-kotlin/target/bc-kotlin-runner-0.0.1-SNAPSHOT.jar"),
                RunnerLaunchKind.JAVA_JAR,
                PARAMETER_SETS),
            "bc-lts",
            new RunnerDefinition(
                "bc-lts",
                "Bouncy Castle LTS ML-DSA",
                "bc-lts-ml-dsa",
                "2.73.12.1",
                "bouncycastle-java",
                "IMPLEMENTED",
                null,
                root.resolve("runners/java/bc-lts/target/bc-lts-runner-0.0.1-SNAPSHOT.jar"),
                RunnerLaunchKind.JAVA_JAR,
                PARAMETER_SETS),
            "bc-fips",
            new RunnerDefinition(
                "bc-fips",
                "Bouncy Castle FIPS ML-DSA",
                "bc-fips-ml-dsa",
                "2.1.3",
                "bouncycastle-fips",
                "GATED",
                "Stable bc-fips 2.1.3 does not provide ML-DSA",
                null,
                RunnerLaunchKind.JAVA_JAR,
                PARAMETER_SETS),
            "swift-cryptokit",
            swiftDefinition(
                root,
                "swift-cryptokit",
                "Apple CryptoKit ML-DSA",
                "apple-cryptokit-mldsa",
                "macOS 26",
                "apple-cryptokit",
                true,
                hostEnvironment,
                "runners/swift/cryptokit/.build/release/cryptokit-runner"),
            "swift-dilithium",
            swiftDefinition(
                root,
                "swift-dilithium",
                "SwiftDilithium ML-DSA",
                "swift-dilithium",
                "3.6.0",
                "swift-dilithium",
                false,
                hostEnvironment,
                "runners/swift/swift-dilithium/.build/release/swift-dilithium-runner"));
  }

  private static RunnerDefinition swiftDefinition(
      Path root,
      String id,
      String displayName,
      String implementationId,
      String version,
      String engineLineageId,
      boolean macOsOnly,
      HostEnvironment hostEnvironment,
      String artifactPath) {
    var gated = macOsOnly && !hostEnvironment.supportsMacOs(26);
    return new RunnerDefinition(
        id,
        displayName,
        implementationId,
        version,
        engineLineageId,
        gated ? "GATED" : "IMPLEMENTED",
        gated ? "CryptoKit requires a macOS 26 or later host" : null,
        root.resolve(artifactPath),
        RunnerLaunchKind.EXECUTABLE,
        PARAMETER_SETS);
  }

  private static Path resolveRunnerRoot(Path configuredRoot) {
    var root = configuredRoot.toAbsolutePath().normalize();
    if (Files.isDirectory(root.resolve("runners"))) {
      return root;
    }
    var parent = root.getParent();
    if (parent != null && Files.isDirectory(parent.resolve("runners"))) {
      return parent;
    }
    return root;
  }

  public List<RunnerDescriptor> descriptors() {
    return definitions.values().stream()
        .sorted(java.util.Comparator.comparing(RunnerDefinition::id))
        .map(this::describe)
        .toList();
  }

  public RunnerDefinition require(String id) {
    var definition = definitions.get(id);
    if (definition == null) {
      throw RunnerExecutionException.notFound("Unknown runner: " + id);
    }
    return definition;
  }

  private RunnerDescriptor describe(RunnerDefinition definition) {
    if (!definition.executable()) {
      return new RunnerDescriptor(
          definition.id(),
          definition.displayName(),
          definition.implementationId(),
          definition.version(),
          definition.engineLineageId(),
          definition.lifecycle(),
          "GATED",
          false,
          definition.gatedReason(),
          definition.parameterSets());
    }
    var availability = artifactReady(definition) ? "READY" : "MISSING_ARTIFACT";
    return new RunnerDescriptor(
        definition.id(),
        definition.displayName(),
        definition.implementationId(),
        definition.version(),
        definition.engineLineageId(),
        definition.lifecycle(),
        availability,
        availability.equals("READY"),
        availability.equals("READY") ? null : "Build the isolated runner artifact first",
        definition.parameterSets());
  }

  private static boolean artifactReady(RunnerDefinition definition) {
    if (definition.artifact() == null
        || !Files.isRegularFile(definition.artifact())
        || Files.isSymbolicLink(definition.artifact())) {
      return false;
    }
    return definition.launchKind() != RunnerLaunchKind.EXECUTABLE
        || Files.isExecutable(definition.artifact());
  }

  record HostEnvironment(String osName, String osVersion) {

    static HostEnvironment current() {
      return new HostEnvironment(
          System.getProperty("os.name", ""), System.getProperty("os.version", ""));
    }

    boolean supportsMacOs(int minimumMajorVersion) {
      if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("mac")) {
        return false;
      }
      if (osVersion == null) {
        return false;
      }
      var version = osVersion.strip();
      var separator = version.indexOf('.');
      if (separator >= 0) {
        version = version.substring(0, separator);
      }
      try {
        return Integer.parseInt(version) >= minimumMajorVersion;
      } catch (NumberFormatException exception) {
        return false;
      }
    }
  }
}
