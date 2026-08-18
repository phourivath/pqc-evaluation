package io.github.phourivath.pqcevaluation.runner;

import io.github.phourivath.pqcevaluation.contract.EvaluationResult;
import io.github.phourivath.pqcevaluation.evaluation.EvaluationRunService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Launches only fixed runner artifacts on the loopback development server. */
@Service
public class RunnerExecutionService {

  private static final Map<String, String> TERMINAL_FAILURES =
      Map.of("FAILED", "Runner process failed", "TIMED_OUT", "Runner exceeded its timeout");

  private final RunnerExecutionProperties properties;
  private final RunnerCatalog catalog;
  private final EvaluationRunService evaluationRunService;
  private final ObjectMapper objectMapper;
  private final ThreadPoolExecutor executor;
  private final Map<UUID, MutableExecution> executions = new ConcurrentHashMap<>();
  private final java.util.Set<String> activeRunners = ConcurrentHashMap.newKeySet();
  private final String serverAddress;

  public RunnerExecutionService(
      RunnerExecutionProperties properties,
      RunnerCatalog catalog,
      EvaluationRunService evaluationRunService,
      ObjectMapper objectMapper,
      @Value("${server.address:127.0.0.1}") String serverAddress) {
    this.properties = properties;
    this.catalog = catalog;
    this.evaluationRunService = evaluationRunService;
    this.objectMapper = objectMapper;
    this.serverAddress = serverAddress;
    this.executor =
        new ThreadPoolExecutor(
            properties.maxConcurrent(),
            properties.maxConcurrent(),
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(properties.queueCapacity()),
            Thread.ofPlatform().name("pqc-runner-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
  }

  public List<RunnerExecutionSnapshot> list() {
    return executions.values().stream()
        .map(MutableExecution::snapshot)
        .sorted(Comparator.comparing(RunnerExecutionSnapshot::submittedAt).reversed())
        .limit(50)
        .toList();
  }

  public RunnerExecutionSnapshot start(String runnerId) {
    ensureExecutionEnabled();
    var definition = catalog.require(runnerId);
    if (!definition.executable()
        || !Files.isRegularFile(definition.artifact())
        || Files.isSymbolicLink(definition.artifact())) {
      throw RunnerExecutionException.unavailable("Runner artifact is not available: " + runnerId);
    }
    if (!activeRunners.add(runnerId)) {
      throw RunnerExecutionException.conflict("Runner is already executing: " + runnerId);
    }

    var execution = new MutableExecution(UUID.randomUUID(), runnerId);
    executions.put(execution.id, execution);
    try {
      execution.future = executor.submit(() -> execute(execution, definition));
      return execution.snapshot();
    } catch (RuntimeException exception) {
      executions.remove(execution.id);
      activeRunners.remove(runnerId);
      throw RunnerExecutionException.rejected("Runner queue is full");
    }
  }

  public RunnerExecutionSnapshot get(UUID executionId) {
    var execution = executions.get(executionId);
    if (execution == null) {
      throw RunnerExecutionException.notFound("Unknown runner execution: " + executionId);
    }
    return execution.snapshot();
  }

  public RunnerExecutionSnapshot cancel(UUID executionId) {
    var execution = executions.get(executionId);
    if (execution == null) {
      throw RunnerExecutionException.notFound("Unknown runner execution: " + executionId);
    }
    execution.cancel();
    var snapshot = execution.snapshot();
    if ("CANCELLED".equals(snapshot.status())) {
      activeRunners.remove(snapshot.runnerId());
    }
    return snapshot;
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  private void execute(MutableExecution execution, RunnerDefinition definition) {
    Path output = null;
    try {
      execution.markStarting();
      var workspace =
          properties.executionRoot().toAbsolutePath().normalize().resolve(execution.id.toString());
      Files.createDirectories(workspace);
      output = workspace.resolve("evaluation-result.json");
      var javaExecutable =
          Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().normalize();
      if (!Files.isRegularFile(javaExecutable)) {
        throw new IOException("Java executable is unavailable");
      }

      var processBuilder =
          new ProcessBuilder(
                  List.of(
                      javaExecutable.toString(),
                      "--enable-native-access=ALL-UNNAMED",
                      "-Xmx256m",
                      "-jar",
                      definition.artifact().toString(),
                      output.toString()))
              .directory(properties.runnerRoot().toAbsolutePath().normalize().toFile())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.PIPE);
      var process = processBuilder.start();
      execution.markRunning(process);
      var errorDrainer = drain(process);
      var finished = process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
        execution.finish("TIMED_OUT", null, null, TERMINAL_FAILURES.get("TIMED_OUT"));
        return;
      }
      errorDrainer.join(1000);
      if (execution.cancelRequested) {
        execution.finish("CANCELLED", process.exitValue(), null, "Execution cancelled");
        return;
      }
      var exitCode = process.exitValue();
      if (exitCode != 0) {
        execution.finish("FAILED", exitCode, null, TERMINAL_FAILURES.get("FAILED"));
        return;
      }
      if (Files.isSymbolicLink(output)
          || !Files.isRegularFile(output)
          || Files.size(output) > properties.maxResultBytes()) {
        execution.finish("FAILED", exitCode, null, "Runner result was missing or too large");
        return;
      }
      var result = objectMapper.readValue(output, EvaluationResult.class);
      if (!definition.implementationId().equals(result.implementation().id())) {
        execution.finish(
            "FAILED", exitCode, null, "Runner identity did not match its catalog entry");
        return;
      }
      evaluationRunService.importRun(result);
      execution.finish(
          "SUCCEEDED", exitCode, result.runId(), "/api/v1/evaluation-runs/" + result.runId());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      execution.finish("CANCELLED", null, null, "Execution interrupted");
    } catch (Exception exception) {
      execution.finish("FAILED", null, null, "Runner result could not be imported");
    } finally {
      activeRunners.remove(execution.runnerId);
    }
  }

  private static Thread drain(Process process) {
    return Thread.ofVirtual()
        .start(
            () -> {
              try (var error = process.getErrorStream()) {
                error.transferTo(OutputStream.nullOutputStream());
              } catch (IOException ignored) {
                // The process result determines lifecycle state; diagnostics are intentionally
                // discarded.
              }
            });
  }

  private void ensureExecutionEnabled() {
    if (!properties.enabled()) {
      throw RunnerExecutionException.disabled();
    }
    if (!"127.0.0.1".equals(serverAddress) && !"::1".equals(serverAddress)) {
      throw RunnerExecutionException.unavailable(
          "Runner execution requires a loopback server address");
    }
  }

  private static final class MutableExecution {
    private final UUID id;
    private final String runnerId;
    private final Instant submittedAt = Instant.now();
    private volatile String status = "QUEUED";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile Integer exitCode;
    private volatile String resultRunId;
    private volatile String resultUrl;
    private volatile String failure;
    private volatile Process process;
    private volatile Future<?> future;
    private volatile boolean cancelRequested;

    private MutableExecution(UUID id, String runnerId) {
      this.id = id;
      this.runnerId = runnerId;
    }

    private void markStarting() {
      status = "STARTING";
      startedAt = Instant.now();
    }

    private void markRunning(Process process) {
      this.process = process;
      status = "RUNNING";
    }

    private void cancel() {
      cancelRequested = true;
      var current = process;
      if (current == null && "QUEUED".equals(status) && future != null && future.cancel(false)) {
        status = "CANCELLED";
        finishedAt = Instant.now();
        return;
      }
      if (current != null && current.isAlive()) {
        status = "CANCELLING";
        current.destroy();
      }
    }

    private void finish(String status, Integer exitCode, String resultRunId, String value) {
      this.status = status;
      this.exitCode = exitCode;
      this.resultRunId = resultRunId;
      this.resultUrl = resultRunId == null ? null : value;
      this.failure = resultRunId == null ? value : null;
      this.finishedAt = Instant.now();
    }

    private RunnerExecutionSnapshot snapshot() {
      return new RunnerExecutionSnapshot(
          id.toString(),
          runnerId,
          status,
          submittedAt,
          startedAt,
          finishedAt,
          exitCode,
          resultRunId,
          resultUrl,
          failure);
    }
  }
}
