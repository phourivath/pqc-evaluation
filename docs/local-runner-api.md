# Local Runner API

Runner execution is intended for the local development console only. The
application binds to `127.0.0.1` by default and launches only pre-built,
allowlisted runner JARs.

## Start The Backend

Build the contract and isolated runner artifacts first:

```bash
./mvnw -pl evaluation-contract -am install -DskipTests
mvn -f runners/java/jdk25/pom.xml package
mvn -f runners/java/bc-base/pom.xml package
mvn -f runners/java/bc-lts/pom.xml package
./mvnw -pl app spring-boot:run
```

Set `PQC_RUNNER_ROOT` when artifacts are installed outside the repository.
Set `PQC_EXECUTION_ROOT` to change the private per-execution workspace.

## Endpoints

```text
GET  /api/v1/runners
POST /api/v1/runners/{runnerId}/executions
GET  /api/v1/runner-executions
GET  /api/v1/runner-executions/{executionId}
DELETE /api/v1/runner-executions/{executionId}
```

`POST` accepts no request body and returns `202 Accepted`. The response has an
execution ID; poll the status resource until it reaches `SUCCEEDED`, `FAILED`,
`TIMED_OUT`, or `CANCELLED`.

`SUCCEEDED` means the process exited successfully, emitted a bounded result,
the implementation identity matched the catalog, and the result passed the
same import validation used by manual imports. It does not mean every
cryptographic check passed. A valid result can contain explicit unsupported
capabilities.

## Security Boundary

The execution service rejects non-loopback server addresses. Runner IDs are
looked up in a fixed catalog and are never interpolated into a shell command.
The child process receives a server-generated output path in an isolated
workspace, a bounded heap, and a hard timeout. Provider libraries stay inside
their runner JVMs and never enter the Spring Boot application classpath.

Execution state and imported results are currently in memory and disappear
when the application restarts. Terminal execution history is bounded by
`pqc.runner-execution.max-retained-executions` (50 by default), and each
completed execution workspace is removed after its result is processed.
