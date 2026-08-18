# PQC Key Comparison Plan

## 1. Objective

Build a reproducible comparison platform for finalized FIPS 204 ML-DSA-44,
ML-DSA-65, and ML-DSA-87 implementations. The first scope is key material,
encoding, correctness, and interoperability. It does not include performance
benchmarking.

## 2. Repository Baseline

The Spring Boot application remains the API and import boundary. The React
Vite application remains an independent client. Cryptographic libraries belong
only to isolated runner processes.

## 3. Normative Sources

Use NIST FIPS 204, RFC 9881, NIST ACVP ML-DSA vectors, Wycheproof ML-DSA
vectors, and the official JDK, Bouncy Castle, `liboqs`, and `liboqs-java`
sources. Record source revisions and hashes with fixtures.

## 4. Scope

Evaluate key generation, raw and container representations, reconstruction,
round trips, signing, verification, negative cases, contexts, and
interoperability. Exclude timing, throughput, allocation, memory, and benchmark
loops.

## 5. Candidate Matrix

The implemented candidates are the JDK 25 SUN provider, Bouncy Castle Base
1.85.2, and Bouncy Castle LTS 2.73.12.1. BC FIPS remains gated because stable
`bc-fips:2.1.3` does not provide ML-DSA. `liboqs-java` 0.3.0 with a pinned
`liboqs` release remains future work. BC Base and LTS share the
`bouncycastle-java` engine lineage and must not be counted as independent
cryptographic engines.

## 6. Architecture

`evaluation-contract` contains dependency-free records and the JSON Schema.
`app` consumes only that contract. Runner builds remain outside the root
reactor and communicate through files and process exit status.

## 7. Runner Isolation

One provider distribution is loaded per runner process. No provider fallback is
allowed. Generated private bytes never enter normalized result files.

## 8. Shared Adapter

Runner adapters expose typed operations for key generation, import/export,
signing, verification, contexts, and cleanup. Handles remain opaque to the
orchestrator and the backend.

## 9. Result Contract

Result documents use schema version `1.0` and include run metadata, implementation
lineage, runtime details, parameter-set observations, capabilities,
representations, checks, interoperability, and warnings. Every capability
identifies whether evidence came from a native API, a standard container, or an
evaluator-derived operation.

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
packages in separate JVMs. HashML-DSA is not included in the pure ML-DSA
comparison yet.

## 14. Native Runner

`liboqs-java` is a JNI wrapper around native `liboqs`. It supports raw public and
expanded private keys plus pure/context signing, but the Java wrapper does not
provide seed reconstruction or PKCS#8/SPKI APIs. Native build flags and backend
selection are recorded in runtime metadata.

## 15. FIPS Gate

Stable `bc-fips:2.1.3` does not provide ML-DSA. The 2.2 stream is early access.
Do not score it as a stable or validated candidate until a stable ML-DSA-capable
artifact, module status, approved-mode behavior, and redistribution terms are
confirmed.

## 16. Fixtures

Commit a small provenance-tracked set of ACVP key-generation and signature
vectors, RFC 9881 seed/expanded/both examples, SPKI examples, context boundary
cases, modified signatures, malformed lengths, and selected Wycheproof cases.
Published private fixtures are test-only and must never be used as credentials.

## 17. Correctness

Every runner validates normative lengths, self-sign/verify, deterministic seed
reconstruction where supported, raw/DER round trips, malformed inputs, wrong
messages, wrong keys, context limits, ACVP vectors, and negative verification
cases.

## 18. Interoperability

Runners exchange public keys, messages, contexts, and signatures only. A
producer-by-consumer matrix is stored in normalized results. Private keys stay
inside the producing process.

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
BC Base, and BC LTS runners, import their results, and run frontend type, lint,
and build checks. Add native runners, pairwise aggregation, persistent storage,
and multi-platform CI incrementally. Never turn unsupported or gated
capabilities into zero-valued successes.

Next phase: pairwise interoperability and persistent run history
