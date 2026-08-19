# PQC Key Comparison Plan

## 1. Objective

Build a reproducible comparison platform for finalized FIPS 204 ML-DSA-44,
ML-DSA-65, and ML-DSA-87 implementations. The first scope is key material,
encoding, correctness, and interoperability. It does not include performance
benchmarking.

## 2. Repository Baseline

The Spring Boot application remains the API and import boundary. The React
Vite application remains an independent client. Cryptographic libraries belong
only to isolated runner processes. The Kotlin/JVM runner remains outside the
root application reactor and emits the existing dependency-free contract. It
uses Kotlin source with the BC Java JCA provider; it does not build the separate
upstream `bc-kotlin` adapter. Do not create a full Android application for this
comparison.

## 3. Normative Sources

Use NIST FIPS 204, RFC 9881, NIST ACVP ML-DSA vectors, Wycheproof ML-DSA
vectors, and the official JDK, Bouncy Castle Java, Bouncy Castle Kotlin,
RustCrypto `ml-dsa`, `liboqs`, and `liboqs-java` sources. Use current AOSP
`KeyProperties` and `NamedParameterSpec` sources when recording Android
platform availability. For the Apple phase, use the official CryptoKit API
and availability documentation, Apple `swift-crypto`, SwiftDilithium, and
`liboqs-swift` sources. Record source revisions, package versions, licenses,
and hashes with fixtures. The primary references are
`https://csrc.nist.gov/pubs/fips/204/final`,
`https://www.rfc-editor.org/rfc/rfc9881.html`,
`https://developer.apple.com/documentation/cryptokit/`,
`https://developer.apple.com/documentation/cryptokit/mldsa65`,
`https://github.com/apple/swift-crypto`,
`https://github.com/leif-ibsen/SwiftDilithium`, and
`https://github.com/DeveloperBeau/liboqs-swift`.

## 4. Scope

Evaluate key generation, raw and container representations, reconstruction,
round trips, signing, verification, negative cases, contexts, and
interoperability for Kotlin/JVM, app-bundled software providers, and selected
Swift/Apple software providers. Include runtime and distribution compatibility
metadata, but exclude Android Keystore, KeyMint, Secure Enclave, Keychain,
secure hardware, full iOS applications, physical-device testing, timing,
throughput, allocation, memory, and benchmark loops.

## 5. Candidate Matrix

The implemented comparison candidates are the JDK 25 SUN provider, Bouncy
Castle Base 1.85.2, and Bouncy Castle LTS 2.73.12.1. BC FIPS remains gated
because stable `bc-fips:2.1.3` does not provide ML-DSA. Bouncy Castle Base and
LTS share the `bouncycastle-java` engine lineage and must not be counted as
independent cryptographic engines. The Kotlin/Android research matrix and
selection decisions are in section 21. The Swift/Apple research matrix and
selection decisions are in sections 27 through 33. CryptoKit and the default
Apple-platform `swift-crypto` package path share an engine lineage and must not
be counted as independent engines.

## 6. Architecture

`evaluation-contract` contains dependency-free records and the JSON Schema.
`app` consumes only that contract. Runner builds remain outside the root
reactor and communicate through files and process exit status. Kotlin runners
must use the same process and file boundary. Native Android candidates, if
selected in a later phase, must be packaged per ABI and isolated from all other
providers. SwiftPM runners must also remain independent build roots and must
not add CryptoKit, SwiftPM, or native provider dependencies to the Maven
modules. Each Swift distribution is built and executed in its own process.

## 7. Runner Isolation

One provider distribution is loaded per runner process. No provider fallback is
allowed. Generated private bytes never enter normalized result files.

## 8. Shared Adapter

Runner adapters expose typed operations for key generation, import/export,
signing, verification, pure ML-DSA, context ML-DSA, HashML-DSA where available,
and cleanup. Handles remain opaque to the orchestrator and the backend. A
Swift adapter must map the public API boundary faithfully: a missing seed,
expanded-key, container, context, or HashML-DSA operation is evidence of an
unsupported capability, not a reason to derive or fabricate a provider API.

## 9. Result Contract

Result documents currently use schema version `1.0` and include run metadata,
implementation lineage, runtime details, parameter-set observations,
capabilities, representations, checks, interoperability, and warnings. Every
capability identifies whether evidence came from a native API, a standard
container, or an evaluator-derived operation. The current Java-only runtime
requirements are valid for the implemented JVM runners, but a Swift result is
the first selected non-Java runtime and creates a genuine contract boundary.

The Swift plan therefore requires a schema `1.1` revision while retaining
`1.0` import compatibility. The existing `javaVersion` and `javaVendor`
fields remain legacy fields and may be omitted or null for a `1.1` Swift
result; Swift must not fabricate Java values. OS identity remains required, and Swift
language, Swift tools, SDK, Xcode, deployment target, package revision, and
native backend values belong in the existing string-valued
`runtime.buildProperties` map. The application validator must accept both
versions, requiring non-null Java fields for `1.0` and permitting omitted or
null legacy Java fields for `1.1`. The open-ended `Representation.kind` field already
expresses provider-specific forms such as `integrity-checked-private`, so no
new representation record field is justified.

## 10. Normative Sizes

| Parameter set | Public | Private seed | Private expanded | Signature |
| --- | ---: | ---: | ---: | ---: |
| ML-DSA-44 | 1,312 | 32 | 2,560 | 2,420 |
| ML-DSA-65 | 1,952 | 32 | 4,032 | 3,309 |
| ML-DSA-87 | 2,592 | 32 | 4,896 | 4,627 |

These are raw sizes and exclude ASN.1 overhead. Guard against the RFC 9881
Appendix A typo that lists 2,602 for the ML-DSA-87 public key.

## 11. Key Representations

Keep raw public keys, 32-byte seeds, expanded private keys, SPKI, PKCS#8, and
native objects distinct. RFC 9881 private-key choices are `seed`, `expandedKey`,
and `both`; a `both` value must be rejected when its seed and expanded bytes do
not match.

## 12. JDK Runner

The JDK runner uses `KeyPairGenerator`, `Signature`, and `KeyFactory` from Java
25. It validates all parameter sets, SPKI and PKCS#8 round trips, OIDs, absent
parameters, and self-sign/verify. JDK 25 does not expose application contexts or
HashML-DSA and reports those capabilities as unsupported.

## 13. Bouncy Castle Runners

Base and LTS use their raw, seed, expanded, SPKI, PKCS#8, and context APIs.
Separate Maven roots and executable JARs keep the two overlapping provider
packages in separate JVMs. The providers expose HashML-DSA through
`MLDSAParameterSpec.ml_dsa_*_with_sha512`, but the current runners do not yet
exercise that mode. The Kotlin/JVM runner calls the same BC Java JCA APIs from
Kotlin and records `language=kotlin` and `api=java-jca-from-kotlin` in runtime
metadata. The upstream BC Kotlin API mapping and future HashML-DSA tests remain
specified in sections 22 and 23.

## 14. Native Runner

`liboqs-java` is a JNI wrapper around native `liboqs`. It supports raw public and
expanded private keys plus pure/context signing, but the Java wrapper does not
provide seed reconstruction or PKCS#8/SPKI APIs. Native build flags and backend
selection are recorded in runtime metadata. The current research baseline is
`liboqs` 0.16.0, whose ML-DSA implementation defaults to `mldsa-native`, and
`liboqs-java` 0.3.0. The wrapper README documents Linux, macOS, and Windows
build profiles, not Android. It remains future native work and is not selected
for the current Kotlin/JVM comparison.

## 15. FIPS Gate

Stable `bc-fips:2.1.3` does not provide ML-DSA. The 2.2 stream is early access.
Do not score it as a stable or validated candidate until a stable ML-DSA-capable
artifact, module status, approved-mode behavior, and redistribution terms are
confirmed.

## 16. Fixtures

Commit a small provenance-tracked set of ACVP key-generation and signature
vectors, RFC 9881 seed/expanded/both examples, SPKI examples, context boundary
cases, HashML-DSA vectors where the candidate exposes them, modified
signatures, malformed lengths, and selected Wycheproof cases. Include Kotlin
runner and RustCrypto provenance when those candidates are implemented. For
Swift, record the Apple SDK/Xcode version, Swift toolchain version, package
revision, vendored native revision where applicable, and the exact vector
source revision.
Published private fixtures are test-only and must never be used as credentials.

## 17. Correctness

Every runner validates normative lengths, self-sign/verify, deterministic seed
reconstruction where supported, raw/DER round trips, malformed inputs, wrong
messages, wrong keys, context limits, ACVP vectors, HashML-DSA vectors where
supported, and negative verification cases. A provider or wrapper that lacks a
capability must report `unsupported`, never a zero-valued success. Swift
implementations must also test integrity-checked private representations and
must not compare signatures byte-for-byte when the API does not expose a
deterministic signing mode.

## 18. Interoperability

Runners exchange public keys, messages, contexts, and signatures only. A
producer-by-consumer matrix is stored in normalized results. Private keys stay
inside the producing process. BC Base, BC LTS, and the Kotlin runner exchange
standard containers and signatures only when the underlying provider exposes the
same encoding. Raw-only native candidates are marked unsupported for container
interoperability rather than being compared as empty results. CryptoKit raw
keys and signatures may participate in evaluator-derived RFC 9881 container
tests, but CryptoKit must not be marked as natively importing or exporting SPKI
or PKCS#8 when its public API does not do so.

## 19. Backend And Frontend

The API imports validated result documents, deduplicates by `runId`, projects
comparison rows, returns RFC 9457 errors, and locally launches only fixed
allowlisted runner artifacts. The execution service launches Java JARs and
native Swift executables through typed fixed command kinds while retaining fixed
catalog paths, no shell interpolation, result-size limits, timeouts,
cancellation, and process-tree cleanup. The frontend uses TanStack Query and Table with
Shadcn UI components to show one latest row per implementation and parameter
set, runner availability, execution status, raw sizes, lineage, assurance,
correctness, and interoperability counts.

The current backend projection is intentionally in-memory so the vertical slice
works without a database. PostgreSQL/Flyway persistence is the next backend
phase, with the original validated JSON retained for auditability.

## 20. Verification And Delivery

Run the contract and backend Maven tests, build and execute the isolated JDK,
BC Base, Kotlin, and BC LTS runners, import their results, and run frontend type,
lint, and build checks. Build SwiftDilithium on Linux or macOS, and build
CryptoKit on macOS/Xcode 26, then validate schema `1.1`, import their results,
and verify CryptoKit gating in the runner catalog. Add native runners, pairwise
aggregation, persistent storage, and multi-platform CI incrementally. Never
turn unsupported or gated capabilities into zero-valued successes.

## 21. Kotlin And Android Candidate Matrix

| Candidate | Current evidence | ML-DSA-44/65/87 | Decision |
| --- | --- | --- | --- |
| JDK 25 SUN | Existing Java runner; no application context or HashML-DSA API | All three | Keep as the baseline, not an Android provider |
| BC Base 1.85.2 | Official `bcprov-jdk18on` and `bcprov-jdk15to18` artifacts; pure Java/lightweight API | All three | Include as the primary app-bundled software provider |
| BC LTS 2.73.12.1 | Official `bcprov-lts8on` artifact; LTS documentation lists ML-KEM, ML-DSA, and SLH-DSA | All three | Include as a second distribution of the same BC Java engine |
| Kotlin/JVM BC Base runner | `runners/kotlin/bc-kotlin`; Kotlin source calls the BC Java 1.85.2 JCA provider directly | All three | Implement as a Kotlin adapter surface over `bouncycastle-java`, not an independent engine row |
| BC Kotlin `bc-kcrypto-0.0.9` | `bcgit/bc-kotlin` main revision `59c2ddbc70253f6387460d65ea6b34cb71e8931f`; Kotlin API and DSL over BC Java jars; no GitHub releases | All three, plus HashML-DSA identifier mappings | Test as an adapter/API surface, not as an independent engine row |
| Android platform JCA/Keystore | Current AOSP `KeyProperties` lists RSA, EC, XDH, AES, and HMAC; `NamedParameterSpec` lists X25519, X448, Ed25519, and Ed448; no ML-DSA | None | Exclude from software comparison and hardware scope |
| RustCrypto `ml-dsa` 0.1.1 | Pure Rust, `no_std`, FIPS 204 final, Rust 1.85, Apache-2.0 OR MIT, no independent audit | All three | Record as a credible future native Android candidate; do not select now |
| `liboqs` 0.16.0 plus `liboqs-java` 0.3.0 | C implementation with JNI wrapper; OQS explicitly describes both as prototyping software; custom native build required | All three | Keep as future native work, not a current Android row |
| BC FIPS `2.1.3` | Stable FIPS artifact has no ML-DSA implementation; ML-DSA validation and approved-mode status are absent | None | Keep gated and exclude |

The official references for this matrix are `https://csrc.nist.gov/pubs/fips/204/final`,
`https://www.bouncycastle.org/download/bouncy-castle-java/`,
`https://www.bouncycastle.org/download/bouncy-castle-java-lts/`,
`https://github.com/bcgit/bc-kotlin`,
`https://github.com/RustCrypto/signatures`,
`https://github.com/open-quantum-safe/liboqs`, and
`https://github.com/open-quantum-safe/liboqs-java`. Android platform evidence is
from the AOSP sources `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/keystore/java/android/security/keystore/KeyProperties.java`
and `https://android.googlesource.com/platform/libcore/+/refs/heads/main/ojluni/src/main/java/java/security/spec/NamedParameterSpec.java`.

## 22. Kotlin API And Runtime Boundary

The implemented Kotlin/JVM runner calls the BC Java JCA provider directly through
`KeyPairGenerator`, `Signature`, and `KeyFactory`. It exercises the provider's
raw key interfaces, standard containers, and `ContextParameterSpec` from Kotlin.
The runner therefore has the same cryptographic capability boundary as the BC
Java Base runner while exposing a separate language/runtime surface.

The upstream BC Kotlin adapter delegates key generation to the BC Java JCA provider through
`KCryptoServices.signingKeyPair` and uses the underlying `PrivateKey` and
`PublicKey`. Its public `SigningKey.encoding` and `VerificationKey.encoding`
properties expose the underlying DER encodings. `KCryptoServices.signingKey`
and `verificationKey` import PKCS#8 and SPKI, respectively. The Kotlin API does
not expose BC Java's raw public data, seed, or expanded private data interfaces;
those operations require the underlying provider API and must be reported as
adapter-unsupported if the adapter does not deliberately use that API.

`MLDSASigSpec.kt` maps the pure ML-DSA and HashML-DSA OIDs for all three
parameter sets. This proves identifier and parameter mapping, not end-to-end
pre-hash behavior. `SignatureCalculator` exposes only a stream and signature
result; it has no public context parameter setter. Context support therefore
comes from the underlying BC Java `ContextParameterSpec`, not from the current
Kotlin DSL, and must be tested as a separate capability.

The BC Kotlin README requires BC 1.83 or later, builds `bc-kcrypto-0.0.9` from
locally supplied BC jars, uses Kotlin 1.3.40, and reports testing with Gradle
6.8 through 7.3. The old build and lack of a published Kotlin release are
maintenance and Android Gradle compatibility risks. Version pinning is
therefore a repository-revision decision. The official BC Java download page
provides `bcprov-jdk15to18-1.85.2` compiled for Java 1.5 through 1.8 and
`bcprov-jdk18on-1.85.2` as a multi-release artifact. The Android
distribution choice must be validated by a future compile and runtime smoke
test; no minimum Android API level is claimed by this plan.

The selected BC path is pure JVM/application-bundled software and does not
genuinely require Android. The evaluator should therefore be a Kotlin/JVM
isolated runner using the existing contract. A minimal Android library or
instrumentation evaluator is only reopened if a native candidate such as
RustCrypto or liboqs is selected later.

## 23. Representation And Capability Matrix

| Candidate | Raw public | Seed private | Expanded private | SPKI | PKCS#8 | Context | HashML-DSA/pre-hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BC Java Base/LTS | Native `MLDSAPublicKey.getPublicData` | Native `MLDSAPrivateKey.getSeed` | Native `getPrivateData` | Supported; validate FIPS 204 OID and absent parameters | Supported; test `seed`, `expandedKey`, and `both` choices | Supported by `ContextParameterSpec`, including empty, non-empty, 255-byte, and rejected 256-byte contexts | Provider exposes `MLDSAParameterSpec.ml_dsa_*_with_sha512`; future runner capability, not current runner evidence |
| Kotlin/JVM BC Java runner | Native `MLDSAPublicKey.getPublicData` | Native `MLDSAPrivateKey.getSeed` | Native `getPrivateData` | Supported; validate FIPS 204 OID and absent parameters | Supported; test `seed`, `expandedKey`, and `both` choices | Supported by `ContextParameterSpec` | Provider exposes the operation through BC Java; current runner does not exercise HashML-DSA |
| BC Kotlin adapter | Not exposed by public Kotlin key interface | Not exposed by public Kotlin key interface | Not exposed by public Kotlin key interface | Supported through `VerificationKey.encoding` and `verificationKey` | Supported through `SigningKey.encoding` and `signingKey` | No public Kotlin setter; mark unsupported unless underlying BC Java API is intentionally used | OID and parameter mapping exists; end-to-end signing and verification must be proven before marking supported |
| RustCrypto `ml-dsa` | `VerifyingKey.encode/decode` fixed raw bytes | `SigningKey.from_seed`, `as_seed`, and `to_seed` | `ExpandedSigningKey.to_expanded/from_expanded`, with malformed import validation limitations and deprecation | Supported through `EncodePublicKey` and `DecodePublicKey` when `pkcs8` is enabled | Supported as seed-only private encoding with no expanded/both choice | `sign_deterministic`, `sign_randomized`, and `verify_with_context`; 256-byte context rejected | No HashML-DSA API. Its digest/precomputed-mu traits are not HashML-DSA and must not be counted as pre-hash support |
| `liboqs-java` | Supported as raw byte buffers | Not exposed | Supported as raw expanded bytes | Unsupported by wrapper API | Unsupported by wrapper API | Context API added in wrapper 0.2.0 | liboqs 0.16.0 has pre-hash ACVP work, but the Java wrapper does not expose a HashML-DSA operation |
| Android platform | No ML-DSA API | No ML-DSA API | No ML-DSA API | No ML-DSA API | No ML-DSA API | Not applicable | Not applicable |

All representation records must retain the origin (`native-api`,
`standard-container`, or `evaluator-derived`) and use `unsupported` when the
public API does not expose an operation. Private bytes and private hashes stay
inside the producing process.

## 24. Identity And Schema Decision

The implemented Kotlin/JVM runner retains `implementation.id = bc-ml-dsa`,
`version = 1.85.2`, and `engineLineageId = bouncycastle-java`. The underlying
BC Base distribution remains the comparison identity; runner facts such as
`language=kotlin` and `api=java-jca-from-kotlin` belong in
`runtime.buildProperties`. Its display name identifies the Kotlin/JVM adapter
surface for dashboard rows, but a separate implementation identity is not
justified because the runner deliberately exercises the same BC Java provider
behavior.

The upstream BC Kotlin adapter remains a separate research candidate. It would
retain the same lineage if implemented later, with `api=bc-kotlin` and its pinned
source revision recorded in runtime metadata.

The shared `EvaluationResult` and `evaluation-result.schema.json` remain at
version `1.0`. Their existing implementation, runtime, capability,
representation, check, and interoperability fields are sufficient for the
selected pure-JVM path. No contract or backend change is justified in this
phase. A future Android-specific runner must first prove that the existing Java
runtime fields plus string-valued `buildProperties` cannot represent its
runtime; only then should a schema revision be proposed.

## 25. Kotlin/Android Correctness And Interoperability Tests

For every selected distribution and each of ML-DSA-44, ML-DSA-65, and
ML-DSA-87:

- Run provenance-tracked FIPS 204 and ACVP key-generation/signature vectors.
- Check raw public, seed, expanded private, and signature lengths against section 10.
- Generate and reconstruct keys from every representation the public adapter exposes.
- Round-trip SPKI and PKCS#8 DER, including OIDs, absent parameters, private-key choices, and malformed choices.
- Verify BC Base, BC LTS, and the Kotlin runner exchange standard containers and signatures without provider fallback.
- Test empty, non-empty, 255-byte, and rejected 256-byte contexts; changing the context must invalidate verification.
- Test HashML-DSA with SHA-512 where the provider or adapter exposes it; pure ML-DSA and HashML-DSA signatures must not be silently treated as interchangeable.
- Test modified messages, signatures, public keys, wrong contexts, wrong parameter sets, malformed lengths, and rejected DER.
- Test RustCrypto raw and container interop only if its future native adapter is selected; keep its deprecated expanded-key import validation limitation explicit.
- Record unsupported context, pre-hash, raw, seed, expanded, or container operations as capability evidence rather than failures caused by missing APIs.

The test plan remains correctness-only. It does not add benchmark loops or
physical Android-device requirements. A future native Android evaluator would
add ABI and runtime smoke coverage, not hardware-backed key testing.

## 26. Risks And Handoff

The current JVM risks are the upstream BC Kotlin adapter's absence of a released
artifact and legacy Gradle/Kotlin toolchain, the lack of a documented Android
API floor for the BC Java distributions, RustCrypto's unaudited status, and the
native packaging and prototype status of liboqs. BC 1.85.2 release notes include
an Android compatibility fix for an API below 33, but that is not evidence of a
complete ML-DSA Android compatibility guarantee.

The Swift risks are the macOS 26/Xcode 26 requirement for CryptoKit ML-DSA, the
absence of a verified CryptoKit ML-DSA-44 API, CryptoKit's lack of native
SPKI/PKCS#8 interfaces, the default Swift Crypto facade sharing CryptoKit's
engine lineage, SwiftDilithium's third-party pure-Swift status and pinned
dependency portability patch, and the Swift 6.3 requirement and prototype
status of `liboqs-swift`. Recheck artifact selection, deployment targets,
package licenses, and compile compatibility on the selected Swift hosts before
selecting any additional runner.

This document phase is complete when the candidate matrices, recommendations
and exclusions, engine lineage rules, parameter sets, raw/container
representations, API/runtime requirements, evaluator structure, test coverage,
schema compatibility, packaging constraints, risks, and source references
below are preserved. The Kotlin/JVM and Swift runner sources are implemented;
Swift artifact compilation and execution still require the documented host
toolchains.

## 27. Swift And Apple Candidate Matrix

| Candidate | Current evidence | ML-DSA-44/65/87 | Decision |
| --- | --- | --- | --- |
| Apple CryptoKit | Official MLDSA65 and MLDSA87 APIs; Apple platform availability begins at OS 26.0; FIPS 204 raw public and seed APIs | 65/87 only | Primary Apple-platform runner; emit an explicit unsupported ML-DSA-44 capability and never infer an MLDSA44 API |
| Apple `swift-crypto` 4.5.1 | Revision `47d3869a7291f085c1fb9fb1e6d3b97a793f45c6`; Swift tools 6.1; Apple package path re-exports CryptoKit; vendored BoringSSL commit `0226f30467f540a3f62ef48d453f93927da199b6` is used by its separate forced-build configuration | 65/87 only | Record as an API/distribution surface with `apple-cryptokit` lineage, not as an independent default engine |
| SwiftDilithium 3.6.0 | Revision `452e507c68879a4a584502e1ef55605efb224e79`; final FIPS 204 implementation; ACVP-server 1.1.0.38 KAT provenance; Wycheproof cases; MIT license | All three | Include as the independent pure-Swift software candidate after Linux/macOS compilation and dependency-license review |
| `liboqs-swift` 0.16.0 | Revision `5f4787277d53ca2d078e3f1fd5a235071ff6ca80`; vendored liboqs 0.16.0; Swift tools 6.3; MIT license; C implementation described as prototype-oriented upstream software | All three | Gate for future work; latest package is not compatible with the Xcode 26 Swift 6.2 baseline and has a narrower API surface |
| `mldsa-native` 2.0.0 | Finalized C implementation with no SwiftPM package or wrapper selected in this repository | All three | Future C-interop research only, not a current Swift-compatible candidate |

No other actively maintained finalized Swift-compatible implementation was
credible enough to add to the primary comparison without a reproducible build,
clear final-spec provenance, and a documented key representation boundary.

## 28. Swift API And Runtime Boundary

CryptoKit exposes `CryptoKit.MLDSA65` and `CryptoKit.MLDSA87` namespaces. Each
private key supports random generation, FIPS 204 `seedRepresentation` input and
output, an associated raw public key, pure signing, and context signing. The
32-byte seed initializer implements `ML-DSA.KeyGen_internal` and can validate a
supplied public key. `integrityCheckedRepresentation` is a separate 64-byte
representation containing the seed and a SHA3-256 public-key hash. Public keys
use the FIPS 204 raw serialization. CryptoKit does not expose expanded private
bytes, private PKCS#8 import/export, public SPKI import/export, or HashML-DSA.

The Apple-platform `swift-crypto` 4.5.1 `Package.swift` keeps its development
flag disabled. On Apple platforms its default settings define
`CRYPTO_IN_SWIFTPM` without forcing the build API, and `MLDSA.swift` re-exports
CryptoKit. Its source aliases CoreCrypto and OpenSSL/BoringSSL implementations
only across different build configurations. A separately controlled local
development or forked configuration may be evaluated as a BoringSSL engine,
but it must be built, identified, and tested as a distinct distribution before
receiving a separate engine lineage.

The direct CryptoKit runner requires a macOS 26 host, Xcode 26, the macOS 26
SDK, and the Swift 6.2 toolchain. Apple documentation lists the corresponding
OS 26 availability across iOS, iPadOS, macCatalyst, macOS, tvOS, visionOS, and
watchOS, but this evaluator is a macOS command-line process and does not become
an iOS application. SwiftDilithium requires 64-bit Swift and declares Swift
tools 5.10, so it can target the Xcode 26 Swift toolchain. Its build wrapper
patches the pinned upstream RNG calls to `SystemRandomNumberGenerator` so the
runner can also target Linux. `liboqs-swift`
declares Swift tools 6.3 and requires a newer toolchain than the current Apple
baseline.

Swift runtime metadata must use `osName`, `osVersion`, and `architecture` for
the host, omit or leave `javaVersion` and `javaVendor` null under schema `1.1`, and
record `language=swift`, `api`, `swiftVersion`, `swiftToolsVersion`,
`xcodeVersion`, `sdkVersion`, `deploymentTarget`, package revision, and
`backend` in `runtime.buildProperties`.

## 29. Swift Representation And Capability Matrix

| Candidate | Raw public | Private seed | Expanded private | Provider-specific private | SPKI/PKCS#8 | Context | HashML-DSA/pre-hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CryptoKit | Native FIPS 204 raw bytes | Native 32-byte seed | Unsupported by native API | Native 64-byte `integrityCheckedRepresentation` | Unsupported by native API; evaluator-derived DER may be tested | Native pure/context signing | Unsupported by public API |
| Default `swift-crypto` on Apple | Same CryptoKit API surface | Same CryptoKit API surface | Unsupported by default facade | Same 64-byte integrity-checked representation | Unsupported by default facade | Same CryptoKit API surface | Unsupported by default facade |
| SwiftDilithium | `PublicKey.keyBytes` | No separate seed constructor | `SecretKey.keyBytes` with normative expanded sizes | None | PEM `PUBLIC KEY` SPKI and `PRIVATE KEY` PKCS#8-style expanded-key encoding | Pure and context APIs; 255-byte maximum and 256-byte rejection | Pure pre-hash API via `PreHash`, including SHA-2, SHA-3, and SHAKE variants |
| `liboqs-swift` | Native raw `Data` | Unsupported | Native raw secret `Data` | None | Unsupported by package API | Pure signing only | Unsupported by package API |

Representation records must use `origin=native-api` for the APIs above,
`origin=standard-container` for containers actually accepted or emitted by a
provider, and `origin=evaluator-derived` for DER generated outside the provider.
Use `kind=integrity-checked-private` for the CryptoKit-specific 64-byte form.
Private bytes and private hashes remain inside the runner process. The
`ParameterSetResult` size fields continue to contain the normative values from
section 10 even when a provider reports an individual capability as
unsupported.

## 30. Swift Runner Structure

Create independent SwiftPM build roots with no Maven dependency:

| Runner root | Product | Dependency boundary | Initial status |
| --- | --- | --- | --- |
| `runners/swift/cryptokit` | `cryptokit-runner` | System `CryptoKit` only | Source implemented; macOS validation pending |
| `runners/swift/swift-dilithium` | `swift-dilithium-runner` | Pinned SwiftDilithium 3.6.0 and its declared dependencies with a revision-checked RNG portability patch | Source implemented; Linux/macOS validation pending |
| `runners/swift/swift-crypto-boringssl` | `swift-crypto-boringssl-runner` | Only if a controlled forced-build configuration is maintained | Gated research |
| `runners/swift/liboqs-swift` | `liboqs-swift-runner` | Pinned `liboqs-swift` and vendored liboqs | Gated future runner |

Each executable accepts the result output path as its only orchestration input,
evaluates one result document containing all supported parameter sets, and
writes only the normalized private-material-free JSON. A small Foundation
`Codable` contract mirror may live inside each Swift build root or a local
support package with no cryptographic dependencies; it must remain
schema-compatible with the shared JSON contract and be checked against the
schema fixtures. On Linux or macOS, build SwiftDilithium with
`runners/swift/swift-dilithium/build.sh`; on macOS, build CryptoKit with
`swift build -c release`. Then invoke the fixed `.build/release/<runner>
<output>` executable.

Use stable identities such as `apple-cryptokit-mldsa` with lineage
`apple-cryptokit` and `swift-dilithium` with lineage `swift-dilithium`. The
default Swift Crypto facade uses the Apple lineage and is not a second engine
row. A forced BoringSSL build, if ever selected, receives a separate identity
only after its actual backend and vendored revision are recorded.

## 31. Swift Correctness And Interoperability Tests

For each supported set and runner:

- Check the normative public, seed, expanded-private, and signature lengths.
- Generate, export, re-import, and compare raw representations without writing private bytes to the result.
- Reconstruct CryptoKit keys from fixed 32-byte seeds, validate matching and mismatching public-key arguments, and round-trip the 64-byte integrity-checked representation including corruption rejection.
- Run pure sign/verify, modified message, modified signature, wrong public key, malformed key length, and wrong parameter-set cases.
- Test empty, ordinary, 255-byte, and 256-byte contexts; record API-specific rejection behavior and ensure changing context invalidates verification.
- Exercise SwiftDilithium deterministic and randomized signing separately; do not require byte-identical signatures from randomized APIs or CryptoKit APIs without randomness control.
- Run HashML-DSA/pre-hash vectors only for SwiftDilithium. CryptoKit and default Swift Crypto report this capability as unsupported rather than treating pure ML-DSA as pre-hashed ML-DSA.
- Use FIPS 204 and ACVP vectors wherever the API supplies the required seed, randomness, or verification inputs. CryptoKit can validate externally supplied public keys and signatures, but its public API does not provide deterministic signing randomness control.
- Validate SwiftDilithium SPKI and PKCS#8-style PEM OIDs, absent parameters, expanded-key choice, malformed DER, and round trips with the Java providers where the encodings match RFC 9881.
- Test evaluator-derived RFC 9881 DER around CryptoKit raw material separately from native CryptoKit container capability.
- Store producer-by-consumer results for raw public keys and signatures only; keep private-key reconstruction inside the producing process.

No performance loops, Secure Enclave tests, Keychain tests, iOS application
tests, or physical-device requirements are added. A missing API is recorded as
`unsupported`, and a failed vector or malformed-input check remains a failure.

## 32. Contract And Backend Follow-Up

The shared contract change is limited to the genuine platform requirement:
schema `1.1` accepts a Swift runtime without Java identity values while keeping
the existing normative sizes, capability statuses, representation records, and
private-material-free boundary. Keep schema `1.0` validation and import
behavior for existing Java results. Update the contract schema resource and
`EvaluationRunService` validation together; do not silently reinterpret a
schema `1.0` document as a Swift document.

The current `RunnerDefinition` stores one artifact path and
`RunnerExecutionService` uses a typed fixed argv builder for Java JARs and native
executables. `RunnerCatalog` should gate CryptoKit unless the host is macOS with
the required SDK, while SwiftDilithium may be executed on Linux or macOS once a
non-symbolic executable artifact exists. `RunnerDescriptor` must continue
omitting filesystem paths and command lines.

## 33. Swift Packaging And Delivery Risks

CryptoKit is an Apple system framework distributed through the Apple SDK and
requires the target Apple runtime; it is not an independently redistributable
Swift package. `swift-crypto` is Apache-2.0 with its required notice and
vendored BoringSSL licensing obligations. SwiftDilithium is MIT, and its ASN1,
BigInt, and Digest dependencies require their own pinned-license review.
`liboqs-swift` and upstream liboqs are MIT, but the wrapper's toolchain and
prototype-oriented upstream status keep it gated. The SwiftDilithium build
wrapper applies a small source patch to the pinned MIT dependencies and records
that fact in runtime metadata. No candidate receives a FIPS validation claim
merely from implementing final FIPS 204.

This Swift plan is complete when the candidate and representation matrices,
CryptoKit/Swift Crypto lineage decision, Apple toolchain requirements, runner
layout, schema compatibility, executable-launch boundary, test coverage,
license constraints, unsupported capabilities, risks, and source URLs are
preserved here. CryptoKit implementation and execution require a macOS/Xcode
host; SwiftDilithium can be built and executed on Linux or macOS with the
documented portability patch.

Next phase: JavaScript and TypeScript
