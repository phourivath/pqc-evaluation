package io.github.phourivath.pqcevaluation.runner;

/** Domain failure for local runner discovery and execution. */
public class RunnerExecutionException extends RuntimeException {

  private final Kind kind;

  private RunnerExecutionException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public static RunnerExecutionException disabled() {
    return new RunnerExecutionException(Kind.DISABLED, "Local runner execution is disabled");
  }

  public static RunnerExecutionException notFound(String message) {
    return new RunnerExecutionException(Kind.NOT_FOUND, message);
  }

  public static RunnerExecutionException conflict(String message) {
    return new RunnerExecutionException(Kind.CONFLICT, message);
  }

  public static RunnerExecutionException unavailable(String message) {
    return new RunnerExecutionException(Kind.UNAVAILABLE, message);
  }

  public static RunnerExecutionException rejected(String message) {
    return new RunnerExecutionException(Kind.REJECTED, message);
  }

  public Kind kind() {
    return kind;
  }

  public enum Kind {
    DISABLED,
    NOT_FOUND,
    CONFLICT,
    UNAVAILABLE,
    REJECTED
  }
}
