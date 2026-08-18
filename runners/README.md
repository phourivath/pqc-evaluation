# Runner Layout

Each cryptographic runner is an independent build root and process. Provider
dependencies must not be added to `app` or the root Maven reactor.

## Available Runners

| Runner | Provider | Version | Status |
| --- | --- | --- | --- |
| `java/jdk25` | JDK SUN ML-DSA | Java 25 | executable |
| `java/bc-base` | Bouncy Castle Base | 1.85.2 | executable |
| `kotlin/bc-kotlin` | Bouncy Castle Java ML-DSA through Kotlin/JVM | 1.85.2 | executable |
| `java/bc-lts` | Bouncy Castle LTS | 2.73.12.1 | executable |
| `bc-fips` | Bouncy Castle FIPS | 2.1.3 | gated; stable artifact has no ML-DSA |

BC Base and BC LTS must remain separate JVMs because their provider packages
overlap. They share the `bouncycastle-java` engine lineage in normalized
results, but are recorded as separate distributions.

## Build

Install the shared contract, then build each runner from its own Maven root:

```bash
./mvnw -pl evaluation-contract -am install -DskipTests
mvn -f runners/java/jdk25/pom.xml package
mvn -f runners/java/bc-base/pom.xml package
mvn -f runners/kotlin/bc-kotlin/pom.xml package
mvn -f runners/java/bc-lts/pom.xml package
```

Each build produces a self-contained executable JAR. The Maven Shade plugin
removes dependency signatures from the assembled archive so Java can verify
the final JAR correctly.

## Manual Execution

```bash
java -jar runners/java/jdk25/target/jdk25-runner-0.0.1-SNAPSHOT.jar \
  runners/java/jdk25/target/evaluation-result.json

java -jar runners/java/bc-base/target/bc-base-runner-0.0.1-SNAPSHOT.jar \
  runners/java/bc-base/target/evaluation-result.json

java -jar runners/kotlin/bc-kotlin/target/bc-kotlin-runner-0.0.1-SNAPSHOT.jar \
  runners/kotlin/bc-kotlin/target/evaluation-result.json

java -jar runners/java/bc-lts/target/bc-lts-runner-0.0.1-SNAPSHOT.jar \
  runners/java/bc-lts/target/evaluation-result.json
```

Each invocation evaluates ML-DSA-44, ML-DSA-65, and ML-DSA-87, so one result
document contains three parameter-set observations. The application projects
one comparison row per parameter set.

The Kotlin runner is Kotlin source calling the BC Java JCA provider directly. It
does not package or build the separate upstream `bc-kotlin` adapter.

## Dashboard Execution

The Spring Boot app exposes a local-only runner catalog and asynchronous
execution API. After building the JARs, start the backend normally and use the
dashboard's `Run` or `Run all available` button. The API launches only the
fixed, catalogued JAR paths; it never runs Maven or a shell command from an
HTTP request.

The catalog reports BC FIPS as gated instead of producing fabricated rows.
