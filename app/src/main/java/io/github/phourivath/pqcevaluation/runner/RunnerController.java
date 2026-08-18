package io.github.phourivath.pqcevaluation.runner;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local runner catalog and asynchronous execution endpoints. */
@RestController
@RequestMapping("/api/v1")
public class RunnerController {

  private final RunnerCatalog catalog;
  private final RunnerExecutionService executionService;

  public RunnerController(RunnerCatalog catalog, RunnerExecutionService executionService) {
    this.catalog = catalog;
    this.executionService = executionService;
  }

  @GetMapping("/runners")
  public List<RunnerDescriptor> runners() {
    return catalog.descriptors();
  }

  @PostMapping("/runners/{runnerId}/executions")
  public ResponseEntity<RunnerExecutionSnapshot> start(@PathVariable String runnerId) {
    var snapshot = executionService.start(runnerId);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header("Location", "/api/v1/runner-executions/" + snapshot.executionId())
        .header("Retry-After", "1")
        .body(snapshot);
  }

  @GetMapping("/runner-executions")
  public List<RunnerExecutionSnapshot> executions() {
    return executionService.list();
  }

  @GetMapping("/runner-executions/{executionId}")
  public RunnerExecutionSnapshot execution(@PathVariable java.util.UUID executionId) {
    return executionService.get(executionId);
  }

  @DeleteMapping("/runner-executions/{executionId}")
  public ResponseEntity<RunnerExecutionSnapshot> cancel(@PathVariable java.util.UUID executionId) {
    return ResponseEntity.accepted().body(executionService.cancel(executionId));
  }
}
