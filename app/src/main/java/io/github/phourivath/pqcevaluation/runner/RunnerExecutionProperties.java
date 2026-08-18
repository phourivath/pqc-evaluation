package io.github.phourivath.pqcevaluation.runner;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local-only controls for launching pre-built, allowlisted runner artifacts. */
@ConfigurationProperties(prefix = "pqc.runner-execution")
public record RunnerExecutionProperties(
    boolean enabled,
    Path runnerRoot,
    Path executionRoot,
    int maxConcurrent,
    int queueCapacity,
    Duration timeout,
    long maxResultBytes,
    int maxRetainedExecutions) {

  public RunnerExecutionProperties {
    if (maxConcurrent < 1 || queueCapacity < 1 || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Runner execution limits must be positive");
    }
    if (maxResultBytes < 1) {
      throw new IllegalArgumentException("maxResultBytes must be positive");
    }
    if (maxRetainedExecutions < 1) {
      throw new IllegalArgumentException("maxRetainedExecutions must be positive");
    }
  }
}
