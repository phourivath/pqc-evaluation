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
platform availability. Record source revisions and hashes with fixtures.

## 4. Scope

Evaluate key generation, raw and container representations, reconstruction,
round trips, signing, verification, negative cases, contexts, and
interoperability for Kotlin/JVM and app-bundled software providers. Include
runtime and distribution compatibility metadata, but exclude Android Keystore,
KeyMint, secure hardware, physical-device testing, timing, throughput,
allocation, memory, and benchmark loops.

## 5. Candidate Matrix

The implemented comparison candidates are the JDK 25 SUN provider, Bouncy
Castle Base 1.85.2, and Bouncy Castle LTS 2.73.12.1. BC FIPS remains gated
because stable `bc-fips:2.1.3` does not provide ML-DSA. Bouncy Castle Base and
LTS share the `bouncycastle-java` engine lineage and must not be counted as
independent cryptographic engines. The Kotlin/Android research matrix and
selection decisions are in section 21.

## 6. Architecture

`evaluation-contract` contains dependency-free records and the JSON Schema.
`app` consumes only that contract. Runner builds remain outside the root
reactor and communicate through files and process exit status. Kotlin runners
must use the same process and file boundary. Native Android candidates, if
selected in a later phase, must be packaged per ABI and isolated from all other
providers.

## 7. Runner Isolation

One provider distribution is loaded per runner process. No provider fallback is
allowed. Generated private bytes never enter normalized result files.

## 8. Shared Adapter

Runner adapters expose typed operations for key generation, import/export,
signing, verification, pure ML-DSA, context ML-DSA, HashML-DSA where available,
and cleanup. Handles remain opaque to the orchestrator and the backend.

## 9. Result Contract

Result documents use schema version `1.0` and include run metadata, implementation
lineage, runtime details, parameter-set observations, capabilities,
representations, checks, interoperability, and warnings. Every capability
identifies whether evidence came from a native API, a standard container, or an
evaluator-derived operation. No schema change is justified for the current
Kotlin/JVM selection. If a future Android result is needed, use
`runtime.buildProperties` for values such as `runtimeFamily`, `androidApiLevel`,
`androidRelease`, `abi`, `kotlinVersion`, `ndkVersion`, and native backend. Do
not invent Java runtime values; revisit the schema only when an Android-specific
runner is actually selected.

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
runner and RustCrypto provenance when those candidates are implemented.
Published private fixtures are test-only and must never be used as credentials.

## 17. Correctness

Every runner validates normative lengths, self-sign/verify, deterministic seed
reconstruction where supported, raw/DER round trips, malformed inputs, wrong
messages, wrong keys, context limits, ACVP vectors, HashML-DSA vectors where
supported, and negative verification cases. A provider or wrapper that lacks a
capability must report `unsupported`, never a zero-valued success.

## 18. Interoperability

Runners exchange public keys, messages, contexts, and signatures only. A
producer-by-consumer matrix is stored in normalized results. Private keys stay
inside the producing process. BC Base, BC LTS, and the Kotlin runner exchange
standard containers and signatures only when the underlying provider exposes the
same encoding. Raw-only native candidates are marked unsupported for container
interoperability rather than being compared as empty results.

## 19. Backend And Frontend

The API imports validated result documents, deduplicates by `runId`, projects
comparison rows, returns RFC 9457 errors, and locally launches only fixed
allowlisted runner JARs. The frontend uses TanStack Query and Table with
Shadcn UI components to show one latest row per implementation and parameter
set, runner availability, execution status, raw sizes, lineage, assurance,
correctness, and interoperability counts.

The current backend projection is intentionally in-memory so the vertical slice
works without a database. PostgreSQL/Flyway persistence is the next backend
phase, with the original validated JSON retained for auditability.

## 20. Verification And Delivery

Run the contract and backend Maven tests, build and execute the isolated JDK,
BC Base, Kotlin, and BC LTS runners, import their results, and run frontend type,
lint, and build checks. Add native runners, pairwise aggregation, persistent
storage, and multi-platform CI incrementally. Never turn unsupported or gated
capabilities into zero-valued successes.

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

The main risks are the upstream BC Kotlin adapter's absence of a released
artifact and legacy Gradle/Kotlin toolchain, the lack of a documented Android
API floor for the BC Java distributions, RustCrypto's unaudited status, and the
native packaging and prototype status of liboqs. BC 1.85.2 release notes include an Android
compatibility fix for an API below 33, but that is not evidence of a complete
ML-DSA Android compatibility guarantee. Recheck artifact selection and compile
compatibility before future adapter implementation.

This phase is complete when the candidate matrix, recommendation and
exclusions, engine lineage rules, parameter sets, raw/container
representations, API/runtime/native requirements, evaluator decision, test
coverage, schema decision, risks, and source references above are preserved in
this document. The Kotlin/JVM runner integration is the selected implementation
for this phase; the upstream BC Kotlin adapter remains future work.

Next phase: Swift and iOS
