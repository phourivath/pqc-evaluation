package io.github.phourivath.pqcevaluation.runner;

import io.github.phourivath.pqcevaluation.contract.EvaluationResult;
import io.github.phourivath.pqcevaluation.evaluation.EvaluationRunService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
  private static final long OUTPUT_CHECK_INTERVAL_MILLIS = 50;
  private static final long PROCESS_GRACE_SECONDS = 2;

  private final RunnerExecutionProperties properties;
  private final RunnerCatalog catalog;
  private final EvaluationRunService evaluationRunService;
  private final ObjectMapper objectMapper;
  private final ThreadPoolExecutor executor;
  private final Map<UUID, MutableExecution> executions = new ConcurrentHashMap<>();
  private final java.util.Set<String> activeRunners = ConcurrentHashMap.newKeySet();
  private final String serverAddress;
  private volatile boolean shuttingDown;

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
    if (!definition.executable() || !artifactAvailable(definition)) {
      throw RunnerExecutionException.unavailable("Runner artifact is not available: " + runnerId);
    }
    if (!activeRunners.add(runnerId)) {
      throw RunnerExecutionException.conflict("Runner is already executing: " + runnerId);
    }

    var execution = new MutableExecution(UUID.randomUUID(), runnerId);
    executions.put(execution.id, execution);
    try {
      execution.setFuture(executor.submit(() -> execute(execution, definition)));
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
    execution.requestCancellation();
    terminateProcess(execution.currentProcess());
    var snapshot = execution.snapshot();
    if ("CANCELLED".equals(snapshot.status())) {
      activeRunners.remove(snapshot.runnerId());
      pruneExecutions();
    }
    return snapshot;
  }

  @PreDestroy
  void shutdown() {
    shuttingDown = true;
    var executionsAtShutdown = List.copyOf(executions.values());
    executionsAtShutdown.forEach(
        execution -> {
          execution.requestCancellation();
          terminateProcess(execution.currentProcess());
        });
    executor.shutdownNow();
    executionsAtShutdown.forEach(
        execution -> {
          terminateProcess(execution.currentProcess());
          if (execution.currentProcess() == null && execution.isNonTerminal()) {
            execution.finish(
                "CANCELLED", null, null, "Execution interrupted by application shutdown");
          }
        });
    try {
      if (!executor.awaitTermination(PROCESS_GRACE_SECONDS + 3, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException exception) {
      executor.shutdownNow();
      executionsAtShutdown.forEach(execution -> terminateProcess(execution.currentProcess()));
      Thread.currentThread().interrupt();
    } finally {
      activeRunners.clear();
    }
  }

  private void execute(MutableExecution execution, RunnerDefinition definition) {
    Path workspace = null;
    try {
      if (!execution.markStarting()) {
        execution.finish("CANCELLED", null, null, "Execution cancelled");
        return;
      }
      workspace =
          properties.executionRoot().toAbsolutePath().normalize().resolve(execution.id.toString());
      Files.createDirectories(workspace);
      var output = workspace.resolve("evaluation-result.json");
      if (execution.isCancellationRequested()) {
        execution.finish("CANCELLED", null, null, "Execution cancelled");
        return;
      }
      var processBuilder =
          new ProcessBuilder(command(definition, output))
              .directory(properties.runnerRoot().toAbsolutePath().normalize().toFile())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.PIPE);
      var process = processBuilder.start();
      if (!execution.markRunning(process)) {
        terminateProcess(process);
      }
      var errorDrainer = drain(process);
      var waitResult = waitForProcess(process, output);
      if (waitResult == ProcessWaitResult.RESULT_TOO_LARGE) {
        terminateProcess(process);
        errorDrainer.join(1000);
        execution.finish(
            "FAILED", exitCode(process), null, "Runner result exceeded the configured size limit");
        return;
      }
      if (waitResult == ProcessWaitResult.TIMED_OUT) {
        terminateProcess(process);
        errorDrainer.join(1000);
        if (execution.isCancellationRequested()) {
          execution.finish("CANCELLED", exitCode(process), null, "Execution cancelled");
        } else {
          execution.finish(
              "TIMED_OUT", exitCode(process), null, TERMINAL_FAILURES.get("TIMED_OUT"));
        }
        return;
      }
      errorDrainer.join(1000);
      if (execution.isCancellationRequested()) {
        execution.finish("CANCELLED", exitCode(process), null, "Execution cancelled");
        return;
      }
      var processExitCode = process.exitValue();
      if (processExitCode != 0) {
        execution.finish("FAILED", processExitCode, null, TERMINAL_FAILURES.get("FAILED"));
        return;
      }
      if (Files.isSymbolicLink(output)
          || !Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
          || Files.size(output) > properties.maxResultBytes()) {
        execution.finish("FAILED", processExitCode, null, "Runner result was missing or too large");
        return;
      }
      if (execution.isCancellationRequested()) {
        execution.finish("CANCELLED", processExitCode, null, "Execution cancelled");
        return;
      }
      var result = objectMapper.readValue(output, EvaluationResult.class);
      if (!definition.implementationId().equals(result.implementation().id())) {
        execution.finish(
            "FAILED", processExitCode, null, "Runner identity did not match its catalog entry");
        return;
      }
      if (execution.isCancellationRequested()) {
        execution.finish("CANCELLED", processExitCode, null, "Execution cancelled");
        return;
      }
      evaluationRunService.importRun(result);
      execution.finish(
          "SUCCEEDED",
          processExitCode,
          result.runId(),
          "/api/v1/evaluation-runs/" + result.runId());
    } catch (InterruptedException exception) {
      terminateProcess(execution.currentProcess());
      Thread.currentThread().interrupt();
      execution.finish("CANCELLED", null, null, "Execution interrupted");
    } catch (Exception exception) {
      terminateProcess(execution.currentProcess());
      execution.finish("FAILED", null, null, "Runner result could not be imported");
    } finally {
      terminateProcess(execution.currentProcess());
      deleteWorkspace(workspace);
      activeRunners.remove(execution.runnerId);
      pruneExecutions();
    }
  }

  private ProcessWaitResult waitForProcess(Process process, Path output)
      throws InterruptedException, IOException {
    var deadline = System.nanoTime() + properties.timeout().toNanos();
    while (process.isAlive()) {
      if (resultExceedsLimit(output)) {
        return ProcessWaitResult.RESULT_TOO_LARGE;
      }
      var remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        return ProcessWaitResult.TIMED_OUT;
      }
      var waitNanos =
          Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(OUTPUT_CHECK_INTERVAL_MILLIS));
      if (process.waitFor(waitNanos, TimeUnit.NANOSECONDS)) {
        return ProcessWaitResult.FINISHED;
      }
    }
    return ProcessWaitResult.FINISHED;
  }

  private boolean resultExceedsLimit(Path output) throws IOException {
    if (Files.isSymbolicLink(output)) {
      return true;
    }
    if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    return !Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
        || Files.size(output) > properties.maxResultBytes();
  }

  private static boolean artifactAvailable(RunnerDefinition definition) {
    if (definition.artifact() == null
        || !Files.isRegularFile(definition.artifact())
        || Files.isSymbolicLink(definition.artifact())) {
      return false;
    }
    return definition.launchKind() != RunnerLaunchKind.EXECUTABLE
        || Files.isExecutable(definition.artifact());
  }

  private static List<String> command(RunnerDefinition definition, Path output) throws IOException {
    if (definition.launchKind() == RunnerLaunchKind.EXECUTABLE) {
      return List.of(definition.artifact().toString(), output.toString());
    }
    var javaExecutable =
        Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().normalize();
    if (!Files.isRegularFile(javaExecutable)) {
      throw new IOException("Java executable is unavailable");
    }
    return List.of(
        javaExecutable.toString(),
        "--enable-native-access=ALL-UNNAMED",
        "-Xmx256m",
        "-jar",
        definition.artifact().toString(),
        output.toString());
  }

  private void pruneExecutions() {
    var completed =
        executions.values().stream()
            .filter(MutableExecution::isTerminal)
            .sorted(Comparator.comparing(execution -> execution.submittedAt))
            .toList();
    var excess = completed.size() - properties.maxRetainedExecutions();
    for (var index = 0; index < excess; index++) {
      var execution = completed.get(index);
      executions.remove(execution.id, execution);
    }
  }

  private static void terminateProcess(Process process) {
    if (process == null || !process.isAlive()) {
      return;
    }
    destroyProcessTree(process, false);
    try {
      if (!process.waitFor(PROCESS_GRACE_SECONDS, TimeUnit.SECONDS)) {
        destroyProcessTree(process, true);
        process.waitFor(PROCESS_GRACE_SECONDS, TimeUnit.SECONDS);
      }
    } catch (InterruptedException exception) {
      destroyProcessTree(process, true);
      Thread.currentThread().interrupt();
    }
  }

  private static void destroyProcessTree(Process process, boolean forcibly) {
    process
        .descendants()
        .forEach(
            descendant -> {
              if (forcibly) {
                descendant.destroyForcibly();
              } else {
                descendant.destroy();
              }
            });
    if (forcibly) {
      process.destroyForcibly();
    } else {
      process.destroy();
    }
  }

  private static void deleteWorkspace(Path workspace) {
    if (workspace == null) {
      return;
    }
    try (var paths = Files.walk(workspace)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                  // Cleanup must not replace the execution result.
                }
              });
    } catch (IOException ignored) {
      // Cleanup must not replace the execution result.
    }
  }

  private static Integer exitCode(Process process) {
    try {
      return process.exitValue();
    } catch (IllegalThreadStateException exception) {
      return null;
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
    if (shuttingDown) {
      throw RunnerExecutionException.unavailable("Runner execution is shutting down");
    }
    if (!"127.0.0.1".equals(serverAddress) && !"::1".equals(serverAddress)) {
      throw RunnerExecutionException.unavailable(
          "Runner execution requires a loopback server address");
    }
  }

  private enum ProcessWaitResult {
    FINISHED,
    TIMED_OUT,
    RESULT_TOO_LARGE
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

    private synchronized void setFuture(Future<?> future) {
      this.future = future;
      if (cancelRequested) {
        future.cancel(false);
      }
    }

    private synchronized boolean markStarting() {
      if (cancelRequested || isTerminal()) {
        if (!isTerminal()) {
          status = "CANCELLING";
        }
        return false;
      }
      status = "STARTING";
      startedAt = Instant.now();
      return true;
    }

    private synchronized boolean markRunning(Process process) {
      this.process = process;
      if (cancelRequested || isTerminal()) {
        if (!isTerminal()) {
          status = "CANCELLING";
        }
        return false;
      }
      status = "RUNNING";
      return true;
    }

    private synchronized void requestCancellation() {
      if (isTerminal()) {
        return;
      }
      cancelRequested = true;
      if ("QUEUED".equals(status) && future != null && future.cancel(false)) {
        status = "CANCELLED";
        finishedAt = Instant.now();
        return;
      }
      status = "CANCELLING";
    }

    private boolean isCancellationRequested() {
      return cancelRequested;
    }

    private Process currentProcess() {
      return process;
    }

    private synchronized void finish(
        String status, Integer exitCode, String resultRunId, String value) {
      if (isTerminal()) {
        return;
      }
      this.status = status;
      this.exitCode = exitCode;
      this.resultRunId = resultRunId;
      this.resultUrl = resultRunId == null ? null : value;
      this.failure = resultRunId == null ? value : null;
      this.finishedAt = Instant.now();
    }

    private boolean isTerminal() {
      return isTerminal(status);
    }

    private boolean isNonTerminal() {
      return !isTerminal();
    }

    private static boolean isTerminal(String status) {
      return "SUCCEEDED".equals(status)
          || "FAILED".equals(status)
          || "TIMED_OUT".equals(status)
          || "CANCELLED".equals(status);
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
