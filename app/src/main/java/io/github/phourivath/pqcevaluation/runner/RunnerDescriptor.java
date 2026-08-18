package io.github.phourivath.pqcevaluation.runner;

import java.util.List;

/** Public runner catalog data; command lines and filesystem paths are intentionally omitted. */
public record RunnerDescriptor(
    String id,
    String displayName,
    String implementationId,
    String version,
    String engineLineageId,
    String lifecycle,
    String availability,
    boolean executionSupported,
    String reason,
    List<String> parameterSets) {}
