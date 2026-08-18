package io.github.phourivath.pqcevaluation.evaluation;

/** Raised when a runner result violates the shared contract. */
public class EvaluationRunException extends RuntimeException {

  public EvaluationRunException(String message) {
    super(message);
  }
}
