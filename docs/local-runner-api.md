# Local Runner API

Runner execution is intended for the local development console only. The
application binds to `127.0.0.1` by default and launches only pre-built,
allowlisted runner artifacts. Java runners are executable JARs; Swift runners
are direct native executables.

## Start The Backend

Build the contract and isolated runner artifacts first:

```bash
./mvnw -pl evaluation-contract -am install -DskipTests
mvn -f runners/java/jdk25/pom.xml package
mvn -f runners/java/bc-base/pom.xml package
mvn -f runners/kotlin/bc-kotlin/pom.xml package
mvn -f runners/java/bc-lts/pom.xml package
./mvnw -pl app spring-boot:run
```

Build SwiftDilithium on Linux or macOS before starting the backend:

```bash
bash runners/swift/swift-dilithium/build.sh
```

On macOS 26 with Xcode 26, build the CryptoKit artifact as well:

```bash
swift build --package-path runners/swift/common -c release
swift build --package-path runners/swift/cryptokit -c release
```

Before starting the backend, provide the Swift toolchain metadata used for the
runner build. `PQC_SWIFT_VERSION` is required for every Swift runner;
`PQC_XCODE_VERSION` and `PQC_SDK_VERSION` are required on macOS and are
recorded as `not-applicable` on Linux.

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
workspace, a bounded result limit, and a hard timeout. Provider libraries stay inside
their runner processes and never enter the Spring Boot application classpath.
CryptoKit is gated unless the host is macOS 26 or later. SwiftDilithium uses
software-only, OS-backed randomness and does not require Secure Enclave or other
hardware-backed services.

Execution state and imported results are currently in memory and disappear
when the application restarts. Terminal execution history is bounded by
`pqc.runner-execution.max-retained-executions` (50 by default), and each
completed execution workspace is removed after its result is processed.
