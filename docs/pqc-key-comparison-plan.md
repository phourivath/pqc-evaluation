# PQC Key Comparison Plan

## 1. Objective And Non-Goals

Build a reproducible comparison platform for finalized FIPS 204 ML-DSA-44,
ML-DSA-65, and ML-DSA-87 implementations across JVM, Kotlin/JVM, Apple/Swift,
Node.js, WebAssembly, and browser API surfaces. The first scope is key
material, encoding, correctness, and interoperability. It is not a performance
benchmark.

The comparison evaluates key generation, raw and container representations,
seed reconstruction, signing, verification, contexts, HashML-DSA or pre-hash
operations where a public API exposes them, negative cases, and producer to
consumer interoperability. It records runtime and distribution metadata so a
result can be reproduced.

The following are outside this phase:

- Timing, throughput, allocation, memory, statistical sampling, and benchmark
  loops.
- Android Keystore, KeyMint, Secure Enclave, Keychain, secure hardware, and
  physical-device testing.
- Full Android and iOS applications.
- Treating a browser capability proposal or an unavailable browser API as a
  stable platform baseline.
- Adding benchmark fields to key-comparison result documents.

Generated private material never enters a normalized result. Published private
fixtures are test-only and must never be used as credentials.

## 2. Repository Baseline And Boundaries

The repository boundaries are fixed by `AGENTS.md`:

- `evaluation-contract/` and `app/` are Maven modules. `app/` is a Spring Boot
  4.1 Web MVC application requiring Java `>=25,<26`.
- `frontend/` is an independent pnpm React 19 Vite SPA. Maven neither builds
  nor serves it. The active route is `index.html` -> `src/main.tsx` ->
  `src/routeTree.gen.ts`; `src/router.tsx` is unused.
- Provider runners under `runners/` are independent builds. Cryptographic
  dependencies remain in runner processes, never in `app`.
- The backend currently provides an in-memory result import, comparison, and
  local-only orchestration API. Persistence and remote orchestration are future
  work.

The current contract boundary is
`evaluation-contract/src/main/java/io/github/phourivath/pqcevaluation/contract/EvaluationResult.java`.
The current backend boundary is
`app/src/main/java/io/github/phourivath/pqcevaluation/evaluation/EvaluationRunController.java`
and `EvaluationRunService.java`. Results are imported through
`POST /api/v1/evaluation-runs/import`, listed through
`GET /api/v1/evaluation-runs`, and projected through
`GET /api/v1/comparisons`.

The existing runner catalog includes JDK 25 SUN, Bouncy Castle Base, Bouncy
Castle LTS, Kotlin/JVM over Bouncy Castle Base, and gated or implemented Swift
runners. There is no JavaScript or TypeScript runner yet. New Node runners must
live under an independent `runners/javascript/` build root. A browser probe is
not a backend-launched process; it must emit an importable result from a browser
harness.

## 3. Evidence Policy And Normative Sources

Every material claim in this document uses one of these labels:

- `[Verified]` means the claim was checked against an official specification,
  official API or source, pinned package metadata, or a reproducible repository
  revision.
- `[Inferred]` means the conclusion follows from verified evidence but is not
  itself a vendor or standards statement.
- `[Unresolved]` means the claim requires a future build, host, source audit,
  or version-specific experiment before it can become a selection decision.

Use finalized FIPS 204, RFC 9881, NIST ACVP ML-DSA vectors, and Wycheproof
vectors as the normative and correctness sources. Record source revisions,
package versions, licenses, toolchain versions, build flags, artifact hashes,
and fixture hashes.

Primary normative and existing-platform references:

- `[Verified]` FIPS 204: `https://csrc.nist.gov/pubs/fips/204/final`
- `[Verified]` RFC 9881: `https://www.rfc-editor.org/rfc/rfc9881.html`
- `[Verified]` NIST ACVP: `https://github.com/usnistgov/ACVP-Server`
- `[Verified]` Wycheproof: `https://github.com/C2SP/wycheproof`
- `[Verified]` JDK security documentation: `https://docs.oracle.com/en/java/javase/25/docs/api/`
- `[Verified]` Bouncy Castle Java downloads: `https://www.bouncycastle.org/download/bouncy-castle-java/`
- `[Verified]` Bouncy Castle Java LTS downloads: `https://www.bouncycastle.org/download/bouncy-castle-java-lts/`
- `[Verified]` BC Kotlin: `https://github.com/bcgit/bc-kotlin`
- `[Verified]` RustCrypto signatures: `https://github.com/RustCrypto/signatures`
- `[Verified]` LibOQS: `https://github.com/open-quantum-safe/liboqs`
- `[Verified]` liboqs-java: `https://github.com/open-quantum-safe/liboqs-java`
- `[Verified]` AOSP `KeyProperties`: `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/keystore/java/android/security/keystore/KeyProperties.java`
- `[Verified]` AOSP `NamedParameterSpec`: `https://android.googlesource.com/platform/libcore/+/refs/heads/main/ojluni/src/main/java/java/security/spec/NamedParameterSpec.java`
- `[Verified]` Apple CryptoKit: `https://developer.apple.com/documentation/cryptokit/`
- `[Verified]` Apple MLDSA65: `https://developer.apple.com/documentation/cryptokit/mldsa65`
- `[Verified]` Apple MLDSA87: `https://developer.apple.com/documentation/cryptokit/mldsa87`
- `[Verified]` swift-crypto: `https://github.com/apple/swift-crypto`
- `[Verified]` SwiftDilithium: `https://github.com/leif-ibsen/SwiftDilithium`
- `[Verified]` liboqs-swift: `https://github.com/DeveloperBeau/liboqs-swift`

JavaScript, WebAssembly, and browser references:

- `[Verified]` Node crypto API: `https://nodejs.org/api/crypto.html`
- `[Verified]` Node WebCrypto API: `https://nodejs.org/api/webcrypto.html`
- `[Verified]` Node v24.7.0 release: `https://nodejs.org/en/blog/release/v24.7.0`
- `[Verified]` Node v24.19.0 crypto source: `https://github.com/nodejs/node/blob/v24.19.0/doc/api/crypto.md`
- `[Verified]` Node v24.19.0 WebCrypto source: `https://github.com/nodejs/node/blob/v24.19.0/doc/api/webcrypto.md`
- `[Verified]` OpenSSL documentation: `https://www.openssl.org/docs/`
- `[Verified]` liboqs-js: `https://github.com/open-quantum-safe/liboqs-js`
- `[Verified]` liboqs-js package: `https://www.npmjs.com/package/@oqs/liboqs-js`
- `[Verified]` W3C Web Cryptography API: `https://www.w3.org/TR/webcrypto/`
- `[Verified]` WICG Modern Algorithms: `https://wicg.github.io/webcrypto-modern-algos/`
- `[Verified]` Chromium feature status: `https://chromestatus.com/feature/5198951632470016`
- `[Verified]` Mozilla Bug 2060300: `https://bugzilla.mozilla.org/show_bug.cgi?id=2060300`
- `[Verified]` WebKit standards position 641: `https://github.com/WebKit/standards-positions/issues/641`
- `[Verified]` noble post-quantum: `https://github.com/paulmillr/noble-post-quantum`
- `[Verified]` noble post-quantum package: `https://www.npmjs.com/package/@noble/post-quantum`
- `[Verified]` QRL ML-DSA-87 package: `https://www.npmjs.com/package/@theqrl/mldsa87`

Do not infer FIPS validation, security certification, or an independent
cryptographic engine from implementation of the finalized FIPS 204 algorithm.

## 4. FIPS 204 Parameter-Set Baseline

The comparison includes all three finalized parameter sets:

| Parameter set | NIST level | Raw public key | Private seed | Expanded private key | Raw signature |
| --- | ---: | ---: | ---: | ---: | ---: |
| ML-DSA-44 | 2 | 1,312 | 32 | 2,560 | 2,420 |
| ML-DSA-65 | 3 | 1,952 | 32 | 4,032 | 3,309 |
| ML-DSA-87 | 5 | 2,592 | 32 | 4,896 | 4,627 |

`[Verified]` The public-key algorithm OIDs are:

| Parameter set | OID |
| --- | --- |
| ML-DSA-44 | `2.16.840.1.101.3.4.3.17` |
| ML-DSA-65 | `2.16.840.1.101.3.4.3.18` |
| ML-DSA-87 | `2.16.840.1.101.3.4.3.19` |

These are raw sizes and exclude ASN.1, PEM, JWK, or other wrapper overhead.
Guard against the RFC 9881 Appendix A typo that lists `2,602` for the
ML-DSA-87 public key. A runner must report these normative values even when its
public API does not expose the corresponding private representation.

Pure ML-DSA and HashML-DSA are different modes. An API that exposes a digest,
precomputed message representative, or generic pre-hash option must not be
marked as exposing HashML-DSA without a vector-backed proof of the FIPS 204
HashML-DSA operation.

## 5. Final Candidate Matrix

The decision column describes the comparison scope, not a security ranking.

| Candidate | Version or revision | Parameter sets | Engine or API surface | Decision |
| --- | --- | --- | --- | --- |
| JDK 25 SUN provider | JDK 25 | All three | JDK provider | `[Verified]` Keep as the JVM baseline. No public application context or HashML-DSA API. |
| Bouncy Castle Base | 1.85.2 | All three | `bouncycastle-java` | `[Verified]` Include as the primary app-bundled software provider. |
| Bouncy Castle LTS | 2.73.12.1 | All three | `bouncycastle-java` | `[Verified]` Include as a second BC distribution, not a second engine lineage. |
| Kotlin/JVM BC runner | Repository runner, BC 1.85.2 | All three | BC Java JCA called from Kotlin | `[Verified]` Include as a language/runtime adapter, not an independent engine. |
| BC Kotlin adapter | Revision `59c2ddbc70253f6387460d65ea6b34cb71e8931f` | All three mappings | `bc-kotlin` over BC Java | `[Verified]` Research as an adapter/API surface; no released artifact is selected. |
| BC FIPS | 2.1.3 | None | `bouncycastle-fips` | `[Verified]` Gate. The stable artifact does not provide ML-DSA. |
| RustCrypto `ml-dsa` | 0.1.1 | All three | Pure Rust | `[Verified]` Record as credible future native work; do not select for the current runner set. |
| `liboqs-java` plus LibOQS | 0.3.0 plus LibOQS 0.16.0 | All three | JNI/native LibOQS | `[Verified]` Keep as future native work; custom native packaging is outside the current Kotlin/JVM scope. |
| Android platform JCA/Keystore | Current AOSP sources | None verified | Android platform | `[Verified]` Exclude from the software comparison and hardware scope. |
| Apple CryptoKit | OS 26.0 and Xcode 26 baseline | 65/87 | Apple system CryptoKit | `[Verified]` Include as the primary Apple runner; record ML-DSA-44 as unsupported. |
| Apple `swift-crypto` | 4.5.1, revision `47d3869a7291f085c1fb9fb1e6d3b97a793f45c6` | 65/87 on Apple | CryptoKit facade by default | `[Verified]` Record as an API/distribution surface sharing the Apple lineage. |
| SwiftDilithium | 3.6.0, revision `452e507c68879a4a584502e1ef55605efb224e79` | All three | Pure Swift | `[Verified]` Include as the independent pure-Swift software candidate after host validation. |
| `liboqs-swift` | 0.16.0, revision `5f4787277d53ca2d078e3f1fd5a235071ff6ca80` | All three | Swift wrapper over vendored LibOQS | `[Verified]` Gate as future work; its Swift tools requirement exceeds the current Apple baseline. |
| Node `node:crypto` | First target Node 24.19.0; pin the exact Node 24 LTS patch | All three | Node `KeyObject` over OpenSSL | `[Verified]` Include as the primary Node native runner. |
| Node WebCrypto | Node 24.7.0 feature; target Node 24.19.0 | All three | Node `CryptoKey` over OpenSSL | `[Verified]` Include as a separate API surface with the Node OpenSSL lineage. |
| `@oqs/liboqs-js` | 0.15.1; package metadata head `f65a4985d88fa5252d5f3440b53363f7a1f3ce6a` | All three | LibOQS through Emscripten/WASM | `[Verified]` Include as the browser/WASM fallback and a Node WASM runner after source pinning. |
| `@noble/post-quantum` | 0.7.0, npm integrity pinned | All three | Pure JS/TypeScript | `[Verified]` Select as the first third-party JavaScript runner. Node `>=20.19.0` covers Node 22 LTS and Node 24. |
| `@theqrl/mldsa87` | 2.1.3 | ML-DSA-87 only | JavaScript package | `[Verified]` Exclude from the all-parameter-set baseline. |
| Browser-native Modern Algorithms | WICG proposal | No stable cross-browser baseline | Browser `SubtleCrypto` | `[Verified]` Probe only; never make availability a required baseline. |

`[Inferred]` The first implementation set is JDK 25, BC Base, BC LTS,
Kotlin/JVM, SwiftDilithium, CryptoKit where available, `@noble/post-quantum`,
and later Node native, Node WebCrypto, and LibOQS WASM surfaces. Gated and
optional candidates remain visible in the catalog or research matrix without
being treated as passing rows.

## 6. Engine Lineage And Identity Decisions

An implementation identity describes a distribution and API surface. An engine
lineage describes the underlying cryptographic implementation. Rows with the
same lineage remain useful for distribution, language, packaging, and API
comparison but must not be counted as independent cryptographic engines.

| Surfaces | Lineage decision |
| --- | --- |
| BC Base, BC LTS, Kotlin/JVM BC, and the default BC Kotlin adapter | `[Verified]` Use `bouncycastle-java`; distribution and adapter facts belong in metadata. |
| CryptoKit and default Apple-platform `swift-crypto` | `[Verified]` Use `apple-cryptokit`; a forced BoringSSL build receives a new lineage only after its actual backend is built and verified. |
| Node `node:crypto` and Node WebCrypto | `[Verified]` Use `node-openssl`; they share Node's OpenSSL engine and are not independent cryptographic engines. |
| LibOQS native, liboqs-java, liboqs-swift, and liboqs-js | `[Inferred]` Record the upstream LibOQS revision and target distribution separately. Do not award independent-engine credit without proving a distinct backend revision. |
| JDK SUN, SwiftDilithium, and `@noble/post-quantum` | `[Inferred]` Treat as separate engine lineages if the pinned implementations remain distinct. |

Use stable `implementation.id` values such as:

- `jdk-sun-ml-dsa`, lineage `jdk-sun`
- `bc-ml-dsa`, lineage `bouncycastle-java`
- `apple-cryptokit-mldsa`, lineage `apple-cryptokit`
- `swift-dilithium`, lineage `swift-dilithium`
- `node-crypto-ml-dsa`, lineage `node-openssl`
- `node-webcrypto-ml-dsa`, lineage `node-openssl`
- `liboqs-js-ml-dsa`, lineage `liboqs-wasm`
- `noble-ml-dsa`, lineage `noble-js`, only if selected

Language, API, toolchain, SDK, package revision, and backend facts belong in
`runtime.buildProperties`. Do not create a new implementation identity solely
for `language=kotlin`, `api=java-jca-from-kotlin`, or `api=bc-kotlin` when the
underlying provider is unchanged.

`assuranceStatus` describes evidence status such as platform, third-party,
prototype, or gated. It must never imply a FIPS validation claim merely because
an implementation follows finalized FIPS 204.

## 7. Key Representation Taxonomy

Representations must identify both the key role and the provider boundary. Do
not put opaque objects or expanded private bytes under a generic `raw` label.

Canonical representation kinds are:

| Kind family | Meaning | Byte-length semantics |
| --- | --- | --- |
| `raw-public` | FIPS 204 serialized public key bytes | Normative raw bytes. |
| `raw-private-seed` | The 32-byte FIPS 204 key-generation seed | Normative seed bytes; never emitted in a result. |
| `raw-private-expanded` | The packed expanded private key | Normative expanded bytes; never emitted in a result. |
| `raw-signature` | FIPS 204 signature bytes | Normative signature bytes. |
| `spki-der` and `spki-pem` | SubjectPublicKeyInfo public-key containers | Observed serialized bytes, including encoding overhead. |
| `pkcs8-der` and `pkcs8-pem` | Private-key containers with `privateChoice=seed`, `expandedKey`, or `both` | Observed serialized bytes; private bytes never enter the result. |
| `jwk-public` and `jwk-private` | JWK wrapper with ML-DSA `alg`, `pub`, and optionally seed `priv` members | Observed UTF-8 JSON serialization where exported. |
| `crypto-key-public` and `crypto-key-private` | Opaque WebCrypto `CryptoKey` handles | `byteLength=null`; serialized length is not applicable until an export succeeds. |
| `key-object-public` and `key-object-private` | Opaque Node `KeyObject` handles | `byteLength=null`; use separate export records for bytes. |
| `wasm-public-bytes` and `wasm-expanded-private-bytes` | LibOQS WASM wrapper byte arrays | Observed `Uint8Array` bytes, explicitly marked as a WASM API surface. |
| `integrity-checked-private` | CryptoKit's 64-byte seed plus public-key-hash representation | Provider-specific private representation; never emitted in a result. |

Each representation records `origin` as `native-api`, `standard-container`, or
`evaluator-derived`. A DER wrapper generated outside a provider around raw
CryptoKit or WASM bytes is evaluator-derived, not native SPKI or PKCS#8 support.

The existing `Representation.byteLength` field has these semantics:

- Raw byte kinds report the raw byte length.
- DER, PEM, and JWK kinds report the observed serialized byte length.
- Opaque `CryptoKey` and `KeyObject` kinds report null because the object is
  not itself a portable byte encoding.
- Unsupported and not-applicable records explain the absence in `reason`.

Private hashes and private bytes stay inside the runner. Existing backend
validation rejects private `sha256` values; retain that rule. Public hashes may
be used for evidence and interop correlation when they do not expose private
material.

## 8. Capability And API Matrix

The following matrix is the planned JS/TS boundary. Existing JVM and Swift
surfaces remain part of the same normalized contract.

| Capability | Node `node:crypto` | Node WebCrypto | `@oqs/liboqs-js` | `@noble/post-quantum` | Browser Modern Algorithms |
| --- | --- | --- | --- | --- | --- |
| Key generation | `[Verified]` All three through `KeyObject` APIs | `[Verified]` All three through `CryptoKey` APIs | `[Verified]` All three through LibOQS WASM | `[Verified]` `keygen(seed?)` for all three | `[Verified]` Proposed for all three, not a stable browser baseline |
| Raw public export/import | `[Verified]` `raw-public` | `[Verified]` `raw-public` | `[Verified]` Public `Uint8Array` | `[Verified]` Raw `Uint8Array` | `[Verified]` Proposed `raw-public` |
| Seed import/export | `[Verified]` `raw-seed`; seed reconstructs key material | `[Verified]` `raw-seed`; exact 32-byte seed | `[Verified]` Not exposed by the generic wrapper | `[Verified]` 32-byte seeded key generation; seed is caller-owned | `[Verified]` Proposed 32-byte `raw-seed` |
| Expanded private bytes | `[Verified]` Not exposed as ML-DSA `raw-private` | `[Verified]` Not exposed as ML-DSA `raw-private` | `[Verified]` Expanded private byte array | `[Verified]` `secretKey` is the expanded FIPS 204 private representation | `[Verified]` Not proposed as `raw-private` |
| SPKI | `[Verified]` PEM/DER | `[Verified]` SPKI | `[Verified]` Unsupported by wrapper | `[Verified]` Unsupported by package | `[Verified]` Proposed |
| PKCS#8 | `[Verified]` PEM/DER; seed-based ML-DSA import rules apply | `[Verified]` PEM/DER; seed-only private export/import boundary | `[Verified]` Unsupported by wrapper | `[Verified]` Unsupported by package | `[Verified]` Proposed seed-only private encoding |
| JWK | `[Verified]` Supported | `[Verified]` Supported | `[Verified]` Unsupported by wrapper | `[Verified]` Unsupported by package | `[Verified]` Proposed |
| Opaque native object | `[Verified]` `KeyObject` | `[Verified]` `CryptoKey` | Not applicable | Not applicable | `[Verified]` Proposed `CryptoKey` |
| Context | `[Verified]` Non-empty context from Node 24.8; maximum 255 bytes | `[Verified]` Context option; maximum 255 bytes | `[Verified]` Generic JS wrapper uses empty context only | `[Verified]` Context up to 255 bytes | `[Verified]` Proposed; maximum 255 bytes |
| HashML-DSA/pre-hash | `[Verified]` Not exposed | `[Verified]` Not exposed | `[Verified]` Not exposed by generic wrapper | `[Verified]` `prehash(approvedHash)` | `[Verified]` Not proposed |

The retained existing-platform boundary is:

- `[Verified]` JDK 25 supports all parameter sets, standard containers, and
  self-sign/verify but exposes no public application context or HashML-DSA API.
- `[Verified]` BC Base and LTS expose raw public, seed, expanded private, SPKI,
  PKCS#8, and context APIs. Their `MLDSAParameterSpec` values expose
  HashML-DSA identifiers, but the current runners do not yet exercise the
  operation.
- `[Verified]` The Kotlin/JVM runner calls BC Java JCA directly. The public BC
  Kotlin adapter exposes DER keys but not the BC Java raw interfaces and has no
  public context setter in its current DSL.
- `[Verified]` CryptoKit exposes ML-DSA-65 and ML-DSA-87 raw public keys, seeds,
  pure signing, and context signing. It does not expose expanded private bytes,
  native SPKI/PKCS#8, or HashML-DSA.
- `[Verified]` SwiftDilithium exposes all three parameter sets, expanded private
  bytes, SPKI/PKCS#8-style containers, contexts, and pre-hash APIs. Its
  pre-hash API must not be conflated with every provider's HashML-DSA mode.
- `[Verified]` `liboqs-java` exposes raw public and expanded private bytes and
  context signing but not seed reconstruction or standard containers.
- `[Verified]` `@noble/post-quantum` exposes all three parameter sets as raw
  `Uint8Array` APIs, seeded key generation, context signing, deterministic or
  hedged signing, and HashML-DSA pre-hashing. It does not expose standard key
  containers or an opaque key object.

Every unsupported capability must be reported as evidence of the public API
boundary. A runner must not derive or fabricate an API that the provider does
not expose.

## 9. Runtime, Version, And Distribution Requirements

### Node

`[Verified]` Node native ML-DSA key APIs were added in Node 24.6.0. Non-empty
context support was added in Node 24.8.0. Node WebCrypto ML-DSA support was
announced in Node 24.7.0. The first runner target is Node 24.19.0, or a later
explicitly pinned Node 24 LTS patch after the same capability checks pass.

`[Verified]` The Node implementation requires an OpenSSL version with ML-DSA
support, documented as OpenSSL `>=3.5`. Record `process.version`,
`process.versions.openssl`, architecture, OS, and the exact Node artifact in
`runtime.buildProperties`. Never infer the OpenSSL version from the Node major
version alone.

Use a lockfile and a reproducible Node distribution. Do not download packages
or a different Node binary during a runner execution. Record the Node license
and the OpenSSL license/notice obligations in the runner distribution metadata.

### LibOQS WASM

`[Verified]` `@oqs/liboqs-js` 0.15.1 uses Emscripten-generated WebAssembly and
provides separate runtime modules for Node, browsers, and Deno. The researched
build requires Emscripten, Git, CMake `>=3.20`, Python 3, `jq`, Bash, and the
Emscripten `emcc`, `emcmake`, and `emmake` tools. The package uses options such
as `SINGLE_FILE=1`, SIMD support, and a bounded WASM heap; exact flags must be
recorded with the pinned build.

`[Unresolved]` The package build script clones LibOQS `main` by default while
the package documentation describes a LibOQS 0.15.1 baseline. The runner must
pin an upstream LibOQS commit, record the resulting WASM SHA-256, and reject an
unreviewed network build. The NPM package integrity value and source revision
must be retained with fixtures.

Browser bundlers must include the WASM module and its loader under a controlled
asset policy. Browser support requires a secure context for WebCrypto, but the
WASM fallback must not depend on browser-native ML-DSA.

### Noble JavaScript

`[Verified]` `@noble/post-quantum@0.7.0` declares Node `>=20.19.0`, is MIT
licensed, and publishes an ESM subpath at
`@noble/post-quantum/ml-dsa.js`. The runner pins its NPM integrity value and
uses `ml_dsa44`, `ml_dsa65`, and `ml_dsa87`.

`[Verified]` Each variant exposes `keygen(seed?)`, `getPublicKey(secretKey)`,
`sign`, `verify`, and `prehash`. The 32-byte seed and expanded private key stay
inside the process. Standard SPKI, PKCS#8, and JWK support is not provided by
the package and is reported as unsupported native capability.

### Existing JVM, Kotlin, Android, And Swift Hosts

- `[Verified]` Maven modules use Java `>=25,<26`; provider runners remain
  independent Maven builds and executable JARs.
- `[Verified]` The BC Kotlin adapter has no released artifact, uses an old
  Gradle/Kotlin toolchain, and is a research adapter rather than the current
  Android implementation.
- `[Verified]` Current AOSP `KeyProperties` and `NamedParameterSpec` sources do
  not provide an ML-DSA Android platform API. Do not create a full Android app
  for this comparison.
- `[Verified]` CryptoKit requires a macOS 26 host, Xcode 26, the macOS 26 SDK,
  and Swift 6.2 for the selected command-line runner.
- `[Verified]` SwiftDilithium 3.6.0 can target Linux or macOS with the recorded
  RNG portability patch. `liboqs-swift` 0.16.0 requires Swift tools 6.3 and is
  gated.

## 10. Pure, Context, And HashML-DSA Semantics

Every selected runner must distinguish these operations:

| Operation | Required evidence |
| --- | --- |
| Pure ML-DSA | Key generation, signing, verification, and negative verification using the FIPS 204 pure mode. |
| Context ML-DSA | Empty, ordinary, 255-byte, and rejected 256-byte contexts where the API exposes a context. Changing the context must invalidate verification. |
| HashML-DSA | Only a provider-specific operation backed by FIPS 204 or ACVP vectors. A generic SHA-2, SHA-3, SHAKE, digest, or precomputed-message API is not sufficient. |
| Seed reconstruction | Generate or import from a fixed 32-byte seed, compare the resulting public key and expanded representation where visible, and test mismatching public-key arguments. |
| Expanded-key import | Test only where the public API exposes it. Malformed lengths and inconsistent seed/expanded `both` values must be rejected. |
| Signature determinism | Compare signatures byte-for-byte only when the API controls signing randomness or explicitly documents deterministic signing. Randomized APIs are tested by verification and vector semantics instead. |

`[Verified]` Node native and Node WebCrypto expose context options with a
255-byte maximum but do not expose HashML-DSA/pre-hash. The generic
liboqs-js wrapper signs and verifies with an empty context and does not expose
the lower-level LibOQS or `mldsa-native` context/pre-hash controls.

`[Verified]` BC exposes context APIs and HashML-DSA parameter identifiers. The
current runner must add actual HashML-DSA vectors before marking the operation
supported. `[Verified]` SwiftDilithium exposes pure, context, and pre-hash
operations; each mode must retain a distinct operation identity.

## 11. Runner Isolation And JavaScript Architecture

One provider distribution is loaded per runner process. There is no provider
fallback. The orchestrator sees only an opaque process and a normalized result
file. A runner accepts a fixed output path as its only orchestration input and
emits one result covering all supported parameter sets.

The planned runner roots are:

| Runner root | Surface | Initial role |
| --- | --- | --- |
| `runners/javascript/noble` | `@noble/post-quantum` raw `Uint8Array` API | First third-party Node 22-compatible implementation. |
| `runners/javascript/node-native` | Node `node:crypto` `KeyObject` | Later native Node API comparison. |
| `runners/javascript/node-webcrypto` | Node `globalThis.crypto.subtle` `CryptoKey` | Separate API runner with the `node-openssl` lineage. |
| `runners/javascript/liboqs-wasm` | `@oqs/liboqs-js` in Node | Portable WASM result and interop source. |
| `frontend` or a separately served browser harness | Browser WebCrypto probe and WASM fallback | Manual or CI-capable browser result export; never assumed available. |

The backend currently has `RunnerLaunchKind.JAVA_JAR` and `EXECUTABLE`. A future
Node execution path may add a typed `NODE_SCRIPT` kind or package Node as a
fixed executable artifact. It must use fixed argument construction, explicit
artifact paths, output-size limits, timeouts, cancellation, and process-tree
cleanup. No shell interpolation or arbitrary user-supplied command is allowed.

The browser harness is not launched by the local Spring process. It reports its
actual browser, OS, secure-context state, WebCrypto probe result, WASM package
revision, and output artifact hash. A browser with no native API produces either
a WASM result or a clearly missing/gated probe result; it does not produce a
false native unsupported row.

Each process writes only private-material-free JSON. Private keys, seeds,
expanded bytes, and private hashes are deleted or held only for the lifetime of
the runner operation.

## 12. Shared Contract And Schema Versioning

The current `EvaluationResult` contains:

- `schemaVersion`, `runId`, `generatedAt`, and implementation identity.
- Runtime identity with Java fields, OS fields, and string-valued
  `buildProperties`.
- Normative parameter-set sizes, capabilities, representations, checks,
  interoperability records, and warnings.
- `[Verified]` Capabilities may include a `callSite` with source coordinates,
  an API snippet, and safe argument descriptions. Call-site evidence must never
  include private seed, expanded-key, or private-hash values.

The current JSON Schema is
`evaluation-contract/src/main/resources/evaluation-result.schema.json`. It
accepts schema `1.0` and `1.1`. Schema `1.0` requires Java runtime identity;
schema `1.1` permits null or omitted legacy Java fields while requiring OS and
build metadata. Existing Java results remain importable and Swift uses `1.1`.

The JS phase uses the following compatibility decision:

- `[Verified]` Node and browser API surfaces need runtime-neutral metadata;
  existing `runtime.buildProperties` can carry language, API, Node, browser,
  OpenSSL, WASM, SDK, and package details.
- `[Verified]` `CryptoKey` and `KeyObject` are opaque supported objects whose
  serialized byte length is not applicable. The current representation status
  enum cannot state that distinction cleanly.
- `[Inferred]` Add the smallest schema `1.2` revision needed for the JS phase.
  Retain `1.0` and `1.1` validation and import behavior. The likely change is
  adding `not-applicable` to representation status and defining canonical JS
  representation kinds; do not add timing or benchmark fields.
- `missing` is a result availability state, not a provider capability. A
  missing run or parameter-set observation belongs in the backend comparison
  projection and frontend model, not as fabricated runner evidence.
- `unsupported` means the provider API does not expose the requested
  operation. `not-applicable` means the operation or measurement has no meaning
  for the representation, such as byte length for an opaque object. `fail` or
  `error` means an attempted operation failed. `pass` means the attempted check
  succeeded.
- Future benchmark results use a separate schema and result type. They do not
  append timing fields to `EvaluationResult`.

The future schema change must preserve:

- Normative size validation for all three parameter sets.
- Private-material rejection, including private hashes.
- Idempotent `runId` import behavior.
- Explicit evidence origin for native API, standard container, and
  evaluator-derived operations.
- Compatibility tests for schema `1.0`, `1.1`, and `1.2` documents.

## 13. Fixtures And Correctness Tests

Commit a small provenance-tracked fixture set containing:

- FIPS 204 and NIST ACVP key-generation and signature vectors.
- RFC 9881 `seed`, `expandedKey`, and `both` private-key examples.
- SPKI, PKCS#8, PEM, and JWK examples where the standard API supports them.
- Empty, ordinary, 255-byte, and rejected 256-byte context cases.
- HashML-DSA vectors only for candidates that expose the operation.
- Modified signatures, messages, public keys, parameter-set identifiers, and
  malformed lengths.
- Selected Wycheproof cases.
- Node native and WebCrypto seed reconstruction and export/import fixtures.
- LibOQS WASM raw public and expanded-private fixture metadata without storing
  private bytes or private hashes in normalized results.
- Browser probe records that capture both native success and native absence.

Every runner checks normative lengths, self-sign/verify, seed reconstruction
where supported, raw and DER round trips, malformed input rejection, wrong
messages and keys, context boundaries, and negative verification. A missing API
is `unsupported`; a failed vector or malformed-input check is `fail` or
`error`; a runner that did not run is `missing` at the comparison layer.

The existing test targets that future contract and backend changes must update
are:

- `evaluation-contract/src/test/java/io/github/phourivath/pqcevaluation/contract/EvaluationResultContractTest.java`
- `app/src/test/java/io/github/phourivath/pqcevaluation/evaluation/EvaluationRunServiceTest.java`
- `app/src/test/java/io/github/phourivath/pqcevaluation/evaluation/EvaluationRunControllerTest.java`
- `app/src/test/java/io/github/phourivath/pqcevaluation/runner/RunnerCatalogTest.java`
- `app/src/test/java/io/github/phourivath/pqcevaluation/runner/RunnerExecutionServiceTest.java`

No test may require private material to appear in a result file.

## 14. Interoperability Matrix And Private-Material Boundary

Interop records are producer-by-consumer observations with parameter set, mode,
status, and message. Runners exchange public keys, messages, contexts, and
signatures only. Private keys remain inside the producing process.

The planned interop matrix is:

| Producer or consumer surface | Raw public | Seed private | Expanded private | SPKI | PKCS#8 | JWK | Signature/context |
| --- | --- | --- | --- | --- | --- | --- | --- |
| JDK / BC / Kotlin | `[Verified]` Native or standard where exposed | `[Verified]` BC native; JDK API boundary varies | `[Verified]` BC native; JDK API boundary varies | `[Verified]` Test OID and absent parameters | `[Verified]` Test RFC 9881 choices where exposed | `[Inferred]` Not a baseline JVM format | `[Verified]` Test pure/context; HashML-DSA only where proven |
| SwiftDilithium | `[Verified]` Raw | `[Verified]` Provider-specific seed behavior | `[Verified]` Raw expanded | `[Verified]` Provider PEM/SPKI-style API | `[Verified]` Provider PEM/PKCS#8-style API | `[Unresolved]` Not a selected format | `[Verified]` Pure/context/pre-hash distinct |
| CryptoKit | `[Verified]` Raw | `[Verified]` 32-byte seed | `[Verified]` Unsupported | `[Verified]` Evaluator-derived only | `[Verified]` Evaluator-derived only | `[Verified]` Unsupported | `[Verified]` Pure/context for 65/87 |
| Node native / Node WebCrypto | `[Verified]` `raw-public` and standard exports | `[Verified]` `raw-seed` and seed-based PKCS#8 | `[Verified]` No ML-DSA raw-private import | `[Verified]` SPKI | `[Verified]` Seed-only boundary must be tested | `[Verified]` JWK | `[Verified]` Pure/context, no HashML-DSA |
| LibOQS WASM | `[Verified]` Raw `Uint8Array` | `[Verified]` Unsupported at wrapper surface | `[Verified]` Expanded bytes | `[Verified]` Evaluator-derived only | `[Verified]` Evaluator-derived only | `[Verified]` Unsupported | `[Verified]` Generic empty-context pure API |
| `@noble/post-quantum` | `[Unresolved]` Verify pinned API | `[Unresolved]` Verify pinned API | `[Unresolved]` Verify pinned API | `[Unresolved]` Verify | `[Unresolved]` Verify | `[Unresolved]` Verify | `[Unresolved]` Verify contexts and pre-hash |

Interop is marked `unsupported` when a public API cannot import or export the
requested form. It is not represented as an empty success. A standard
container generated by the evaluator around a raw key is explicitly marked
`origin=evaluator-derived` and does not prove native provider support.

The first interoperability tests should cover:

- Node native <-> Node WebCrypto through SPKI, PKCS#8 seed, JWK, raw public,
  and raw seed where both APIs accept the same format. This is API interop over
  a shared OpenSSL lineage, not independent-engine evidence.
- Node native/WebCrypto <-> BC and SwiftDilithium through standards-compliant
  SPKI, PKCS#8 seed, and signatures.
- Raw public keys and signatures between LibOQS WASM, SwiftDilithium, BC, Node,
  and CryptoKit where the parameter set and mode match.
- Context interop only when both producer and consumer expose the same pure
  context operation. Do not compare pure signatures with HashML-DSA or
  pre-hash signatures as if they were interchangeable.

## 15. Backend Import, Orchestration, And Persistence

The Spring application remains a dependency-free result consumer. It validates
the schema and semantic invariants at the import boundary, rejects private
material, enforces normative sizes, deduplicates identical `runId` documents,
and projects comparison rows.

The current in-memory `EvaluationRunService` is sufficient for the first
vertical slice. Future backend work may add PostgreSQL/Flyway persistence while
retaining the original validated JSON for auditability. Persistence must not
change the result contract or make the backend responsible for cryptographic
operations.

The runner catalog remains fixed and allowlisted:

- Artifact paths are configured or repository-resolved, never supplied by the
  request.
- Java JARs, native executables, and future fixed Node artifacts use typed
  launch paths.
- Result size limits, timeouts, cancellation, and process-tree cleanup remain
  enforced.
- CryptoKit remains gated unless the host is macOS 26 or later with the
  required SDK. SwiftDilithium may run on Linux or macOS after its executable
  exists. Browser probes are imported rather than launched by this service.
- `RunnerDescriptor` continues to omit filesystem paths and command lines.

The backend comparison projection must add enough information for the frontend
to distinguish a missing runner or parameter set from a provider that ran and
reported `unsupported`. It must not synthesize zero sizes or passing checks.

## 16. Frontend Comparison Model And Table

The frontend remains a client-only React 19 Vite application. The current
comparison model is `frontend/src/lib/comparison.ts`; the table and detail panel
are in `frontend/src/routes/index.tsx`.

The main view remains table-first with one latest row per implementation surface
and parameter set. It should show:

- Normative raw public, seed, expanded-private, and signature sizes in a
  dedicated FIPS expected column.
- Provider-produced representations separately from normative sizes.
- DER, PEM, and JWK observed serialized sizes as encoding overhead data, never
  as replacement raw sizes.
- `CryptoKey` and `KeyObject` as opaque supported wrappers with an explicit
  `not applicable` byte-size label.
- WASM public and expanded-private byte arrays with a WASM-specific label.
- Seed and expanded-private capability status separately, even when neither
  private value is displayed.
- Engine lineage, distribution, version, license, assurance status, runtime,
  and source revision in the detail panel.

Status presentation must distinguish:

- `unsupported`: the candidate API does not expose the operation.
- `missing`: no result or no parameter-set observation exists yet.
- `not applicable`: the measurement has no meaning for the representation,
  such as serialized length of an opaque object.
- `pass`, `fail`, and `error`: an attempted check outcome.

The selected-run detail panel should retain complete capability,
representation, check, interoperability, warning, and reason data. A missing
result should use a placeholder row or runner state, not a fabricated result
with zero values. Browser native absence and WASM fallback provenance should be
visible in the same detail model.

The frontend is not modified in this documentation phase. Future route changes
must use the generated route workflow, and `frontend/src/routeTree.gen.ts` must
never be edited directly.

## 17. Packaging, CI, Risks, And Unresolved Claims

### Packaging And Licensing

- `[Verified]` Bouncy Castle Base and LTS remain separately packaged but share
  the BC Java lineage. BC FIPS stays gated until a stable ML-DSA-capable
  artifact, approved-mode behavior, and redistribution terms are confirmed.
- `[Verified]` `swift-crypto` is Apache-2.0 with notice and vendored BoringSSL
  obligations. CryptoKit is an Apple system framework, not an independently
  redistributable Swift package. SwiftDilithium is MIT, with dependency licenses
  requiring a pinned review. LibOQS and liboqs-swift are MIT with prototype and
  toolchain risks.
- `[Verified]` `@oqs/liboqs-js` and LibOQS are MIT. Emscripten and all build
  dependencies require license and notice retention. `@noble/post-quantum`
  is MIT. Node is MIT and its OpenSSL distribution notices must be retained.
- `[Unresolved]` Complete the license review for every pinned JS/WASM transitive
  package before redistribution.

### Reproducibility And CI

- Pin Node version, OpenSSL version observed at runtime, NPM package integrity,
  LibOQS source commit, Emscripten version, build flags, WASM hash, and browser
  harness revision.
- Use a reproducible lockfile and a prebuilt WASM artifact or a pinned
  container. Never clone an unpinned upstream branch during CI or evaluation.
- Run JVM and contract gates with `./mvnw -pl app -am clean verify`.
- Build independent Java, Kotlin, and Swift runners according to
  `runners/README.md` and host requirements.
- Run frontend `pnpm check`, `pnpm exec tsc --noEmit`, focused tests or
  `pnpm exec vitest run --passWithNoTests`, and `pnpm build` when frontend code
  changes.
- Browser-native probes are opt-in and non-blocking when a browser lacks the
  proposed API. WASM fallback correctness remains blocking for the browser
  portable path.
- CI must include at least one Linux Node/WASM job. macOS is required for
  CryptoKit. Android device CI is not part of this phase.

### Risks And Unresolved Claims

- `[Unresolved]` Node's active-development modern algorithm APIs and exact
  patch-level export behavior must be pinned and tested, especially PKCS#8
  seed-only behavior and JWK details.
- `[Unresolved]` Browser support has no reliable stable Firefox/Safari baseline;
  Chromium feature status is not a release guarantee.
- `[Unresolved]` `@oqs/liboqs-js` package metadata does not by itself prove the
  exact LibOQS backend source used by a build that clones `main`.
- `[Verified]` The noble package's source and NPM tarball are pinned, but it has
  no independent security audit and makes no constant-time guarantee. Standard
  container interop remains a future evaluator-derived operation.
- `[Verified]` The BC Kotlin adapter has no released artifact and an old
  toolchain. RustCrypto is credible but unaudited in this project. Native
  LibOQS wrappers are prototype-oriented. These are selection risks, not
  correctness results.
- `[Verified]` CryptoKit has no verified ML-DSA-44 API in the selected Apple
  baseline and has no native SPKI/PKCS#8 interface. Never infer one.

## 18. Ready For Implementation

The plan is ready for implementation when the following decisions remain true:

- All three finalized FIPS 204 parameter sets and normative sizes are fixed.
- Candidate identities, versions, licenses, engine lineages, and exclusions are
  recorded with claim labels and source URLs.
- Raw public, seed, expanded private, standard containers, JWK, opaque native
  objects, WASM byte arrays, and provider-specific representations remain
  distinct.
- Unsupported, missing, not-applicable, pass, fail, skipped, and error states
  cannot be confused with zero-valued success.
- The result remains private-material-free and correctness-only.
- Schema `1.0` and `1.1` compatibility is preserved, and the minimal `1.2`
  change is implemented only after the JS opaque-object requirement is verified.
- Future benchmark results remain a separate result type and schema.

Implementation order:

1. Freeze the schema `1.2` decision, canonical representation kinds, status
   vocabulary, fixtures, and compatibility tests. Do not add benchmark fields.
2. Implement the independent `runners/javascript/noble` TypeScript runner using
   `@noble/post-quantum@0.7.0`, targeting Node `>=20.19.0` and verifying it on
   Node 22 LTS and Node 24. It must emit a private-material-free result for all
   three parameter sets, raw key evidence, contexts, HashML-DSA, deterministic
   signing, source call sites, and unsupported native container capabilities.
3. Add the Node native and WebCrypto runners and compare their standard export
   behavior with the noble raw API while retaining separate engine lineages.
4. Add the pinned LibOQS WASM Node runner, then the browser WASM harness. Keep
   the generic wrapper's empty-context and raw-byte limitations explicit.
5. Add producer-by-consumer interop fixtures across standard containers, raw
   public keys, signatures, and matching context modes.
6. Extend backend import, runner catalog, comparison projection, frontend types,
   and table/detail rendering in separate changes. Keep browser execution
   outside the backend process boundary.
7. Add persistence and multi-platform CI after the in-memory vertical slice is
   stable.

The first bounded runner must be accepted only when it produces valid result
JSON, imports through the existing API boundary, preserves private-material
rules, and clearly reports unsupported or not-applicable operations instead of
fabricating evidence.
