package io.github.phourivath.pqcevaluation.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.phourivath.pqcevaluation.contract.EvaluationResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.CheckResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.Implementation;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.ParameterSetResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.RuntimeInfo;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationRunServiceTest {

  private final EvaluationRunService service = new EvaluationRunService();

  @Test
  void importsValidResultAndProjectsComparisonRows() {
    var result = result("run-1");

    var outcome = service.importRun(result);

    assertThat(outcome.duplicate()).isFalse();
    assertThat(service.listRuns()).containsExactly(result);
    assertThat(service.comparisonRows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.implementationId()).isEqualTo("jdk-sun-ml-dsa");
              assertThat(row.checksPassed()).isEqualTo(1);
              assertThat(row.checksUnsupported()).isZero();
              assertThat(row.availableRepresentations()).isEmpty();
              assertThat(row.unsupportedCapabilities()).isEmpty();
            });
  }

  @Test
  void acceptsIdenticalDuplicateAndRejectsDifferentDuplicate() {
    var result = result("run-2");
    service.importRun(result);

    assertThat(service.importRun(result).duplicate()).isTrue();
    var changed =
        new EvaluationResult(
            result.schemaVersion(),
            result.runId(),
            result.generatedAt(),
            result.implementation(),
            result.runtime(),
            result.parameterSets(),
            result.checks(),
            result.interoperability(),
            List.of("changed"));
    assertThatThrownBy(() -> service.importRun(changed))
        .isInstanceOf(EvaluationRunException.class)
        .hasMessageContaining("different content");
  }

  @Test
  void rejectsNormativeSizeMismatch() {
    var result = result("run-3");
    var invalid =
        new EvaluationResult(
            result.schemaVersion(),
            result.runId(),
            result.generatedAt(),
            result.implementation(),
            result.runtime(),
            List.of(
                new ParameterSetResult("ML-DSA-44", 2, 1, 32, 2560, 2420, List.of(), List.of())),
            result.checks(),
            result.interoperability(),
            result.warnings());

    assertThatThrownBy(() -> service.importRun(invalid))
        .isInstanceOf(EvaluationRunException.class)
        .hasMessageContaining("Normative size mismatch");
  }

  @Test
  void rejectsMalformedCheckWithoutThrowingNullPointerException() {
    var result = result("run-4");
    var invalid =
        new EvaluationResult(
            result.schemaVersion(),
            result.runId(),
            result.generatedAt(),
            result.implementation(),
            result.runtime(),
            result.parameterSets(),
            List.of(new CheckResult("", "ML-DSA-44", "correctness", null, "bad")),
            result.interoperability(),
            result.warnings());

    assertThatThrownBy(() -> service.importRun(invalid))
        .isInstanceOf(EvaluationRunException.class)
        .hasMessage("Invalid check definition");
  }

  private static EvaluationResult result(String runId) {
    return new EvaluationResult(
        "1.0",
        runId,
        Instant.parse("2026-08-18T00:00:00Z"),
        new Implementation(
            "jdk-sun-ml-dsa",
            "JDK SUN ML-DSA",
            "25.0.4",
            "jdk-sun",
            "java.base",
            "GPL",
            "platform"),
        new RuntimeInfo("25.0.4", "OpenJDK", "Linux", "1", "amd64", Map.of()),
        List.of(new ParameterSetResult("ML-DSA-44", 2, 1312, 32, 2560, 2420, List.of(), List.of())),
        List.of(new CheckResult("key-generation", "ML-DSA-44", "correctness", "pass", "ok")),
        List.of(),
        List.of());
  }
}
