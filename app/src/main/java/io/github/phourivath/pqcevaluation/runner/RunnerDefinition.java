package io.github.phourivath.pqcevaluation.runner;

import java.nio.file.Path;
import java.util.List;

record RunnerDefinition(
    String id,
    String displayName,
    String implementationId,
    String version,
    String engineLineageId,
    String lifecycle,
    String gatedReason,
    Path artifact,
    RunnerLaunchKind launchKind,
    List<String> parameterSets) {

  boolean executable() {
    return artifact != null && lifecycle.equals("IMPLEMENTED");
  }
}
