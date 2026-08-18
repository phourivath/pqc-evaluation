package io.github.phourivath.pqcevaluation.evaluation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts domain validation errors into stable RFC 9457 responses. */
@RestControllerAdvice
public class EvaluationRunExceptionHandler {

  @ExceptionHandler(EvaluationRunException.class)
  public ProblemDetail handleEvaluationRunException(EvaluationRunException exception) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setTitle("Invalid evaluation result");
    return problem;
  }

  @ExceptionHandler(EvaluationRunNotFoundException.class)
  public ProblemDetail handleEvaluationRunNotFound(EvaluationRunNotFoundException exception) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setTitle("Evaluation run not found");
    return problem;
  }
}
