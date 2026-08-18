package io.github.phourivath.pqcevaluation.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.phourivath.pqcevaluation.evaluation.EvaluationRunService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RunnerExecutionServiceTest {

  @Test
  void stopsAProcessWhenItsResultExceedsTheConfiguredLimit() throws Exception {
    var root = Files.createTempDirectory("runner-execution-limit");
    var artifact = createRunnerJar(root.resolve("large-runner.jar"), LargeRunner.class);
    var executionRoot = root.resolve("executions");
    var service = service(root, executionRoot, artifact, "large", 16, 50);

    try {
      var execution = service.start("large");
      var completed = awaitTerminal(service, UUID.fromString(execution.executionId()));

      assertThat(completed.status()).isEqualTo("FAILED");
      assertThat(completed.failure()).contains("size limit");
      awaitWorkspaceCleanup(executionRoot);
      assertThat(workspaceEmpty(executionRoot)).isTrue();
    } finally {
      service.shutdown();
    }
  }

  @Test
  void cancelsARunningProcessAndCleansItsWorkspace() throws Exception {
    var root = Files.createTempDirectory("runner-execution-cancel");
    var artifact = createRunnerJar(root.resolve("sleep-runner.jar"), SleepingRunner.class);
    var executionRoot = root.resolve("executions");
    var service = service(root, executionRoot, artifact, "sleep", 1024, 50);

    try {
      var execution = service.start("sleep");
      var id = UUID.fromString(execution.executionId());
      awaitStatus(service, id, "RUNNING");

      service.cancel(id);
      var completed = awaitTerminal(service, id);

      assertThat(completed.status()).isEqualTo("CANCELLED");
      awaitWorkspaceCleanup(executionRoot);
      assertThat(workspaceEmpty(executionRoot)).isTrue();
    } finally {
      service.shutdown();
    }
  }

  @Test
  void shutsDownAndReapsARunningProcess() throws Exception {
    var root = Files.createTempDirectory("runner-execution-shutdown");
    var artifact = createRunnerJar(root.resolve("sleep-runner.jar"), SleepingRunner.class);
    var executionRoot = root.resolve("executions");
    var service = service(root, executionRoot, artifact, "sleep", 1024, 50);

    try {
      var execution = service.start("sleep");
      var id = UUID.fromString(execution.executionId());
      awaitStatus(service, id, "RUNNING");

      service.shutdown();

      assertThat(service.get(id).status()).isEqualTo("CANCELLED");
      awaitWorkspaceCleanup(executionRoot);
      assertThat(workspaceEmpty(executionRoot)).isTrue();
    } finally {
      service.shutdown();
    }
  }

  @Test
  void retainsOnlyTheConfiguredNumberOfTerminalExecutions() throws Exception {
    var root = Files.createTempDirectory("runner-execution-retention");
    var artifact = createRunnerJar(root.resolve("failing-runner.jar"), FailingRunner.class);
    var executionRoot = root.resolve("executions");
    var service = service(root, executionRoot, artifact, "fail", 1024, 1);

    try {
      for (var index = 0; index < 3; index++) {
        var execution = service.start("fail");
        awaitTerminal(service, UUID.fromString(execution.executionId()));
      }

      assertThat(service.list()).hasSize(1);
    } finally {
      service.shutdown();
    }
  }

  private static RunnerExecutionService service(
      Path root,
      Path executionRoot,
      Path artifact,
      String runnerId,
      long maxResultBytes,
      int maxRetainedExecutions) {
    var catalog = mock(RunnerCatalog.class);
    when(catalog.require(runnerId))
        .thenReturn(
            new RunnerDefinition(
                runnerId,
                runnerId,
                runnerId,
                "test",
                "test",
                "IMPLEMENTED",
                null,
                artifact,
                List.of()));
    var properties =
        new RunnerExecutionProperties(
            true,
            root,
            executionRoot,
            1,
            1,
            Duration.ofSeconds(5),
            maxResultBytes,
            maxRetainedExecutions);
    return new RunnerExecutionService(
        properties, catalog, new EvaluationRunService(), mock(ObjectMapper.class), "127.0.0.1");
  }

  private static RunnerExecutionSnapshot awaitTerminal(
      RunnerExecutionService service, UUID executionId) throws Exception {
    for (var attempt = 0; attempt < 200; attempt++) {
      var snapshot = service.get(executionId);
      if (List.of("SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED").contains(snapshot.status())) {
        return snapshot;
      }
      Thread.sleep(25);
    }
    fail("Execution did not reach a terminal state: " + service.get(executionId));
    return null;
  }

  private static void awaitStatus(
      RunnerExecutionService service, UUID executionId, String expectedStatus) throws Exception {
    for (var attempt = 0; attempt < 200; attempt++) {
      var status = service.get(executionId).status();
      if (expectedStatus.equals(status)) {
        return;
      }
      if (List.of("SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED").contains(status)) {
        fail("Execution reached an unexpected terminal state: " + status);
      }
      Thread.sleep(25);
    }
    fail("Execution did not reach status " + expectedStatus);
  }

  private static void awaitWorkspaceCleanup(Path executionRoot) throws Exception {
    for (var attempt = 0; attempt < 200; attempt++) {
      if (workspaceEmpty(executionRoot)) {
        return;
      }
      Thread.sleep(25);
    }
    fail("Execution workspace was not cleaned up: " + executionRoot);
  }

  private static boolean workspaceEmpty(Path executionRoot) throws IOException {
    if (!Files.exists(executionRoot)) {
      return true;
    }
    try (var paths = Files.list(executionRoot)) {
      return paths.findAny().isEmpty();
    }
  }

  private static Path createRunnerJar(Path artifact, Class<?> mainClass) throws IOException {
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass.getName());
    var resourceName = mainClass.getName().replace('.', '/') + ".class";
    try (var input = RunnerExecutionServiceTest.class.getResourceAsStream("/" + resourceName);
        var output = new JarOutputStream(Files.newOutputStream(artifact), manifest)) {
      assertThat(input).as("compiled runner fixture").isNotNull();
      output.putNextEntry(new JarEntry(resourceName));
      input.transferTo(output);
      output.closeEntry();
    }
    return artifact;
  }

  public static final class LargeRunner {
    private LargeRunner() {}

    public static void main(String[] args) throws Exception {
      Files.write(Path.of(args[0]), new byte[128]);
      Thread.sleep(Duration.ofSeconds(30));
    }
  }

  public static final class SleepingRunner {
    private SleepingRunner() {}

    public static void main(String[] args) throws Exception {
      Thread.sleep(Duration.ofSeconds(30));
    }
  }

  public static final class FailingRunner {
    private FailingRunner() {}

    public static void main(String[] args) {
      System.exit(1);
    }
  }
}
