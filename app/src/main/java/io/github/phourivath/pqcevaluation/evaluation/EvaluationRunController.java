package io.github.phourivath.pqcevaluation.evaluation;

import io.github.phourivath.pqcevaluation.contract.EvaluationResult;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for importing and viewing normalized runner results. */
@RestController
@RequestMapping("/api/v1")
public class EvaluationRunController {

  private final EvaluationRunService service;

  public EvaluationRunController(EvaluationRunService service) {
    this.service = service;
  }

  @PostMapping("/evaluation-runs/import")
  public ResponseEntity<EvaluationResult> importRun(@RequestBody EvaluationResult result) {
    var outcome = service.importRun(result);
    return ResponseEntity.status(outcome.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
        .body(outcome.result());
  }

  @GetMapping("/evaluation-runs")
  public List<EvaluationResult> listRuns() {
    return service.listRuns();
  }

  @GetMapping("/evaluation-runs/{runId}")
  public EvaluationResult getRun(@PathVariable String runId) {
    return service.getRun(runId);
  }

  @GetMapping("/comparisons")
  public List<EvaluationRunService.ComparisonRow> comparisons() {
    return service.comparisonRows();
  }
}
