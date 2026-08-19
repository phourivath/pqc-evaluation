package io.github.phourivath.pqcevaluation.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A normalized, private-material-free result emitted by one runner process. */
public record EvaluationResult(
    String schemaVersion,
    String runId,
    Instant generatedAt,
    Implementation implementation,
    RuntimeInfo runtime,
    List<ParameterSetResult> parameterSets,
    List<CheckResult> checks,
    List<InteropResult> interoperability,
    List<String> warnings) {

  public EvaluationResult {
    parameterSets = parameterSets == null ? List.of() : List.copyOf(parameterSets);
    checks = checks == null ? List.of() : List.copyOf(checks);
    interoperability = interoperability == null ? List.of() : List.copyOf(interoperability);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public record Implementation(
      String id,
      String displayName,
      String version,
      String engineLineageId,
      String distribution,
      String license,
      String assuranceStatus) {}

  public record RuntimeInfo(
      String javaVersion,
      String javaVendor,
      String osName,
      String osVersion,
      String architecture,
      Map<String, String> buildProperties) {
    public RuntimeInfo {
      buildProperties = buildProperties == null ? Map.of() : Map.copyOf(buildProperties);
    }
  }

  public record ParameterSetResult(
      String parameterSet,
      int securityLevel,
      int rawPublicKeyBytes,
      int rawPrivateSeedBytes,
      int rawPrivateExpandedBytes,
      int rawSignatureBytes,
      List<Capability> capabilities,
      List<Representation> representations) {
    public ParameterSetResult {
      capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
      representations = representations == null ? List.of() : List.copyOf(representations);
    }
  }

  public record Capability(
      String operation,
      String status,
      String origin,
      String evidence,
      String reason,
      CallSite callSite) {
    public Capability(
        String operation, String status, String origin, String evidence, String reason) {
      this(operation, status, origin, evidence, reason, null);
    }
  }

  public record CallSite(
      String sourceFile,
      String className,
      String methodName,
      int lineNumber,
      String snippet,
      int highlightLine,
      List<Argument> arguments,
      String usageExample) {
    public CallSite {
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public CallSite(
        String sourceFile,
        String className,
        String methodName,
        int lineNumber,
        String snippet,
        int highlightLine,
        List<Argument> arguments) {
      this(sourceFile, className, methodName, lineNumber, snippet, highlightLine, arguments, null);
    }
  }

  public record Argument(String name, String type, String value) {}

  public record Representation(
      String kind,
      String status,
      Integer byteLength,
      String sha256,
      String algorithmOid,
      Boolean parametersAbsent,
      String privateChoice,
      String origin,
      String reason) {}

  public record CheckResult(
      String id, String parameterSet, String category, String status, String message) {}

  public record InteropResult(
      String producer,
      String consumer,
      String parameterSet,
      String mode,
      String status,
      String message) {}
}
