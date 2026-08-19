package io.github.phourivath.pqcevaluation.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RunnerCatalogTest {

  @Test
  void exposesImplementedAndGatedCandidatesWithoutScanningCommands() throws Exception {
    var root = Files.createTempDirectory("runner-catalog");
    var properties =
        new RunnerExecutionProperties(
            true, root, root.resolve("executions"), 1, 1, Duration.ofSeconds(1), 1024, 50);

    var descriptors =
        new RunnerCatalog(properties, new RunnerCatalog.HostEnvironment("Linux", "6.8"))
            .descriptors();

    assertThat(descriptors)
        .extracting(RunnerDescriptor::id)
        .containsExactly(
            "bc-base",
            "bc-fips",
            "bc-kotlin",
            "bc-lts",
            "jdk25",
            "swift-cryptokit",
            "swift-dilithium");
    assertThat(descriptors)
        .filteredOn(descriptor -> descriptor.id().equals("bc-kotlin"))
        .singleElement()
        .satisfies(
            descriptor -> {
              assertThat(descriptor.implementationId()).isEqualTo("bc-ml-dsa");
              assertThat(descriptor.engineLineageId()).isEqualTo("bouncycastle-java");
              assertThat(descriptor.executionSupported()).isFalse();
              assertThat(descriptor.reason()).contains("Build the isolated runner artifact");
            });
    assertThat(
            descriptors.stream()
                .filter(descriptor -> descriptor.id().equals("bc-fips"))
                .findFirst())
        .get()
        .satisfies(
            descriptor -> {
              assertThat(descriptor.availability()).isEqualTo("GATED");
              assertThat(descriptor.executionSupported()).isFalse();
              assertThat(descriptor.reason()).contains("does not provide ML-DSA");
            });
    assertThat(descriptors)
        .filteredOn(descriptor -> descriptor.id().equals("swift-cryptokit"))
        .singleElement()
        .satisfies(
            descriptor -> {
              assertThat(descriptor.availability()).isEqualTo("GATED");
              assertThat(descriptor.executionSupported()).isFalse();
              assertThat(descriptor.reason()).contains("macOS 26");
            });
    assertThat(descriptors)
        .filteredOn(descriptor -> descriptor.id().equals("swift-dilithium"))
        .singleElement()
        .satisfies(
            descriptor -> {
              assertThat(descriptor.availability()).isEqualTo("MISSING_ARTIFACT");
              assertThat(descriptor.executionSupported()).isFalse();
              assertThat(descriptor.reason()).contains("Build the isolated runner artifact");
              assertThat(descriptor.lifecycle()).isEqualTo("IMPLEMENTED");
            });
  }

  @Test
  void gatesCryptoKitOnMacOsBeforeVersion26() throws Exception {
    var root = Files.createTempDirectory("runner-catalog-old-macos");
    var properties =
        new RunnerExecutionProperties(
            true, root, root.resolve("executions"), 1, 1, Duration.ofSeconds(1), 1024, 50);

    var descriptor =
        new RunnerCatalog(properties, new RunnerCatalog.HostEnvironment("Mac OS X", "25.6.0"))
            .descriptors().stream()
                .filter(value -> value.id().equals("swift-cryptokit"))
                .findFirst()
                .orElseThrow();

    assertThat(descriptor.availability()).isEqualTo("GATED");
    assertThat(descriptor.executionSupported()).isFalse();
    assertThat(descriptor.reason()).contains("macOS 26");
  }

  @Test
  void allowsCryptoKitOnMacOs26WhenExecutableExists() throws Exception {
    var root = Files.createTempDirectory("runner-catalog-macos26");
    var artifact = root.resolve("runners/swift/cryptokit/.build/release/cryptokit-runner");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, "#!/bin/sh\nexit 0\n");
    assertThat(artifact.toFile().setExecutable(true, false)).isTrue();
    var properties =
        new RunnerExecutionProperties(
            true, root, root.resolve("executions"), 1, 1, Duration.ofSeconds(1), 1024, 50);

    var descriptor =
        new RunnerCatalog(properties, new RunnerCatalog.HostEnvironment("Mac OS X", "26.0.0"))
            .descriptors().stream()
                .filter(value -> value.id().equals("swift-cryptokit"))
                .findFirst()
                .orElseThrow();

    assertThat(descriptor.availability()).isEqualTo("READY");
    assertThat(descriptor.executionSupported()).isTrue();
  }
}
