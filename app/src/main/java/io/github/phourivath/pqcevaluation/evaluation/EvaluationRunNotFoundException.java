package io.github.phourivath.pqcevaluation.evaluation;

/** Raised when a requested run does not exist. */
public class EvaluationRunNotFoundException extends RuntimeException {

  public EvaluationRunNotFoundException(String runId) {
    super("Evaluation run not found: " + runId);
  }
}
