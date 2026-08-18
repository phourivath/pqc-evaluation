package io.github.phourivath.pqcevaluation.runner;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps runner lifecycle failures to stable problem responses. */
@RestControllerAdvice
public class RunnerExceptionHandler {

  @ExceptionHandler(RunnerExecutionException.class)
  public ProblemDetail handleRunnerException(RunnerExecutionException exception) {
    var status =
        switch (exception.kind()) {
          case DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case CONFLICT, UNAVAILABLE -> HttpStatus.CONFLICT;
          case REJECTED -> HttpStatus.TOO_MANY_REQUESTS;
        };
    var problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setTitle("Runner execution unavailable");
    problem.setProperty("code", exception.kind().name().toLowerCase());
    return problem;
  }
}
