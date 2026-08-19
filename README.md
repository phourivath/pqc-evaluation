# PQC Evaluation

A tool for comparing the API surface of post-quantum cryptography (PQC) algorithms across different cryptography libraries.

## System Requirements

| Requirement | Version | Notes |
|---|---|---|
| JDK | 25 | Required to build and run the core app and Java runners |
| Node.js | >= 22 | Required for the JavaScript (noble) runner and frontend |
| pnpm | latest | Package manager for the frontend |
| Swift toolchain | via [swiftly](https://www.swift.org/install/) | Required to build the `swift-dilithium` runner |

## Build

Run from the project root:

```bash
./mvnw -pl evaluation-contract -am install -DskipTests
./mvnw -pl app -am install -DskipTests

bash runners/swift/swift-dilithium/build.sh

mvn -f runners/java/jdk25/pom.xml package
mvn -f runners/java/bc-base/pom.xml package
mvn -f runners/java/bc-lts/pom.xml package
mvn -f runners/kotlin/bc-kotlin/pom.xml package

npm --prefix runners/javascript/noble ci
npm --prefix runners/javascript/noble run build
```

## Run the backend

```bash
export PQC_SWIFT_VERSION=6.3.3   # change to your actual installed Swift version
./mvnw -pl app spring-boot:run
```

## Run the frontend

```bash
cd frontend
pnpm run dev
```
