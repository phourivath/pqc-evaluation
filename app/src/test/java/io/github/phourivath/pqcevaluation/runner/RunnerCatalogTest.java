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

    var descriptors = new RunnerCatalog(properties).descriptors();

    assertThat(descriptors)
        .extracting(RunnerDescriptor::id)
        .containsExactly("bc-base", "bc-fips", "bc-kotlin", "bc-lts", "jdk25");
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
  }
}
