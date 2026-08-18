package io.github.phourivath.pqcevaluation.runner;

import java.time.Instant;

/** Public lifecycle state for one runner process. */
public record RunnerExecutionSnapshot(
    String executionId,
    String runnerId,
    String status,
    Instant submittedAt,
    Instant startedAt,
    Instant finishedAt,
    Integer exitCode,
    String resultRunId,
    String resultUrl,
    String failure) {}
