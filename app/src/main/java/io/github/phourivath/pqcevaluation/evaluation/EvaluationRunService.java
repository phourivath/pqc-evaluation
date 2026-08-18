package io.github.phourivath.pqcevaluation.evaluation;

import io.github.phourivath.pqcevaluation.contract.EvaluationResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.CheckResult;
import io.github.phourivath.pqcevaluation.contract.EvaluationResult.ParameterSetResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Validates and serves imported runner results. */
@Service
public class EvaluationRunService {

  private static final Map<String, Sizes> EXPECTED =
      Map.of(
          "ML-DSA-44", new Sizes(2, 1312, 32, 2560, 2420),
          "ML-DSA-65", new Sizes(3, 1952, 32, 4032, 3309),
          "ML-DSA-87", new Sizes(5, 2592, 32, 4896, 4627));

  private final Map<String, EvaluationResult> runs = new ConcurrentHashMap<>();

  public ImportOutcome importRun(EvaluationResult result) {
    validate(result);
    var previous = runs.putIfAbsent(result.runId(), result);
    if (previous == null) {
      return new ImportOutcome(result, false);
    }
    if (!previous.equals(result)) {
      throw new EvaluationRunException("runId already exists with different content");
    }
    return new ImportOutcome(previous, true);
  }

  public List<EvaluationResult> listRuns() {
    return runs.values().stream()
        .sorted(Comparator.comparing(EvaluationResult::generatedAt).reversed())
        .toList();
  }

  public EvaluationResult getRun(String runId) {
    var result = runs.get(runId);
    if (result == null) {
      throw new EvaluationRunNotFoundException(runId);
    }
    return result;
  }

  public List<ComparisonRow> comparisonRows() {
    var rows = new ArrayList<ComparisonRow>();
    for (var result : listRuns()) {
      for (var parameterSet : result.parameterSets()) {
        var checks =
            result.checks().stream()
                .filter(check -> check.parameterSet().equals(parameterSet.parameterSet()))
                .toList();
        var pass = countChecks(checks, "pass");
        var fail = countChecks(checks, "fail") + countChecks(checks, "error");
        var unsupported = countChecks(checks, "unsupported") + countChecks(checks, "skipped");
        var interop =
            result.interoperability().stream()
                .filter(item -> item.parameterSet().equals(parameterSet.parameterSet()))
                .toList();
        rows.add(
            new ComparisonRow(
                result.runId(),
                result.generatedAt(),
                result.implementation().id(),
                result.implementation().displayName(),
                result.implementation().version(),
                result.implementation().engineLineageId(),
                result.implementation().assuranceStatus(),
                parameterSet.parameterSet(),
                parameterSet.securityLevel(),
                parameterSet.rawPublicKeyBytes(),
                parameterSet.rawPrivateSeedBytes(),
                parameterSet.rawPrivateExpandedBytes(),
                parameterSet.rawSignatureBytes(),
                pass,
                fail,
                unsupported,
                interop.stream().filter(item -> "pass".equals(item.status())).count(),
                interop.stream().filter(item -> "fail".equals(item.status())).count(),
                interop.stream().filter(item -> "unsupported".equals(item.status())).count(),
                capabilityOperations(parameterSet, "supported"),
                capabilityOperations(parameterSet, "unsupported"),
                representationKinds(parameterSet, "pass"),
                representationKinds(parameterSet, "unsupported")));
      }
    }
    return rows;
  }

  private static long countChecks(List<CheckResult> values, String status) {
    return values.stream().filter(value -> status.equals(value.status())).count();
  }

  private static List<String> capabilityOperations(ParameterSetResult parameterSet, String status) {
    return parameterSet.capabilities().stream()
        .filter(capability -> status.equals(capability.status()))
        .map(EvaluationResult.Capability::operation)
        .toList();
  }

  private static List<String> representationKinds(ParameterSetResult parameterSet, String status) {
    return parameterSet.representations().stream()
        .filter(representation -> status.equals(representation.status()))
        .map(EvaluationResult.Representation::kind)
        .toList();
  }

  private static void validate(EvaluationResult result) {
    if (result == null) {
      throw new EvaluationRunException("Result document is required");
    }
    if (!"1.0".equals(result.schemaVersion())) {
      throw new EvaluationRunException("Unsupported schemaVersion: " + result.schemaVersion());
    }
    if (isBlank(result.runId())
        || result.generatedAt() == null
        || result.implementation() == null
        || result.runtime() == null) {
      throw new EvaluationRunException(
          "runId, generatedAt, implementation, and runtime are required");
    }
    var implementation = result.implementation();
    if (isBlank(implementation.id())
        || isBlank(implementation.displayName())
        || isBlank(implementation.version())
        || isBlank(implementation.engineLineageId())
        || isBlank(implementation.distribution())
        || isBlank(implementation.license())
        || isBlank(implementation.assuranceStatus())) {
      throw new EvaluationRunException("implementation identity fields are required");
    }
    var runtime = result.runtime();
    if (isBlank(runtime.javaVersion())
        || isBlank(runtime.javaVendor())
        || isBlank(runtime.osName())
        || isBlank(runtime.osVersion())
        || isBlank(runtime.architecture())) {
      throw new EvaluationRunException("runtime identity fields are required");
    }
    if (result.parameterSets().isEmpty()) {
      throw new EvaluationRunException("At least one parameter set is required");
    }
    var seen = new java.util.HashSet<String>();
    for (var parameterSet : result.parameterSets()) {
      validateParameterSet(parameterSet, seen);
    }
    for (var check : result.checks()) {
      if (check == null
          || isBlank(check.id())
          || isBlank(check.parameterSet())
          || !EXPECTED.containsKey(check.parameterSet())
          || isBlank(check.category())
          || !SetOfStatuses.CHECK.contains(check.status())
          || isBlank(check.message())) {
        throw new EvaluationRunException("Invalid check definition");
      }
    }
    for (var interop : result.interoperability()) {
      if (interop == null
          || isBlank(interop.producer())
          || isBlank(interop.consumer())
          || isBlank(interop.parameterSet())
          || !EXPECTED.containsKey(interop.parameterSet())
          || isBlank(interop.mode())
          || !SetOfStatuses.INTEROP.contains(interop.status())
          || isBlank(interop.message())) {
        throw new EvaluationRunException("Invalid interoperability definition");
      }
    }
  }

  private static void validateParameterSet(
      ParameterSetResult parameterSet, java.util.Set<String> seen) {
    if (parameterSet == null || !EXPECTED.containsKey(parameterSet.parameterSet())) {
      throw new EvaluationRunException("Unknown or missing parameter set");
    }
    if (!seen.add(parameterSet.parameterSet())) {
      throw new EvaluationRunException("Duplicate parameter set: " + parameterSet.parameterSet());
    }
    var expected = EXPECTED.get(parameterSet.parameterSet());
    if (parameterSet.securityLevel() != expected.securityLevel()
        || parameterSet.rawPublicKeyBytes() != expected.publicBytes()
        || parameterSet.rawPrivateSeedBytes() != expected.seedBytes()
        || parameterSet.rawPrivateExpandedBytes() != expected.privateBytes()
        || parameterSet.rawSignatureBytes() != expected.signatureBytes()) {
      throw new EvaluationRunException(
          "Normative size mismatch for " + parameterSet.parameterSet());
    }
    for (var capability : parameterSet.capabilities()) {
      if (capability == null
          || isBlank(capability.operation())
          || isBlank(capability.status())
          || !SetOfStatuses.CAPABILITY.contains(capability.status())
          || isBlank(capability.origin())
          || isBlank(capability.evidence())) {
        throw new EvaluationRunException("Invalid capability definition");
      }
    }
    for (var representation : parameterSet.representations()) {
      if (representation == null
          || isBlank(representation.kind())
          || isBlank(representation.status())
          || !SetOfStatuses.REPRESENTATION.contains(representation.status())
          || isBlank(representation.origin())
          || (representation.byteLength() != null && representation.byteLength() < 0)
          || (representation.sha256() != null && isBlank(representation.sha256()))
          || (representation.algorithmOid() != null && isBlank(representation.algorithmOid()))
          || (representation.privateChoice() != null && isBlank(representation.privateChoice()))) {
        throw new EvaluationRunException("Invalid representation definition");
      }
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public record ImportOutcome(EvaluationResult result, boolean duplicate) {}

  public record ComparisonRow(
      String runId,
      java.time.Instant generatedAt,
      String implementationId,
      String implementationName,
      String implementationVersion,
      String engineLineageId,
      String assuranceStatus,
      String parameterSet,
      int securityLevel,
      int rawPublicKeyBytes,
      int rawPrivateSeedBytes,
      int rawPrivateExpandedBytes,
      int rawSignatureBytes,
      long checksPassed,
      long checksFailed,
      long checksUnsupported,
      long interopPassed,
      long interopFailed,
      long interopUnsupported,
      List<String> supportedCapabilities,
      List<String> unsupportedCapabilities,
      List<String> availableRepresentations,
      List<String> unavailableRepresentations) {}

  private record Sizes(
      int securityLevel, int publicBytes, int seedBytes, int privateBytes, int signatureBytes) {}

  private static final class SetOfStatuses {
    private static final java.util.Set<String> CAPABILITY =
        java.util.Set.of("supported", "unsupported");
    private static final java.util.Set<String> CHECK =
        java.util.Set.of("pass", "fail", "error", "skipped", "unsupported");
    private static final java.util.Set<String> INTEROP =
        java.util.Set.of("pass", "fail", "unsupported");
    private static final java.util.Set<String> REPRESENTATION =
        java.util.Set.of("pass", "fail", "unsupported");

    private SetOfStatuses() {}
  }
}
