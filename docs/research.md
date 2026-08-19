# Companies Using Libraries in the PQC Runners

Date: 2026-08-19

## Scope

This document names the actual companies or organizations for that use 
which public source code or official documentation shows usage.

The runner inventory comes from [`runners/README.md`](../runners/README.md) and
the runner manifests. Jackson, Kotlin, and the Swift ASN.1 helpers are also
covered where they are relevant as direct or supporting dependencies. Test-only
libraries and this repository's internal `evaluation-contract` are excluded.

## Evidence Rules

- **Direct library use** means a company-owned or organization-owned source file
  imports the library or declares it as a dependency.
- **PQC-specific use** means the source calls an ML-DSA, ML-KEM, Kyber, or other
  post-quantum API. A dependency alone does not prove PQC use.
- **Production status** is reported only when the source or product documentation
  supports it. Public source code normally proves repository use, not deployment
  or customer adoption.
- Open-source foundations and projects are included when they are the public
  steward of the relevant code. They are labeled as organizations rather than
  being presented as commercial customers.

## Library Inventory

| Library or framework | Runner | Version in this repository | Role |
| --- | --- | --- | --- |
| JDK 25 `SUN` provider | `runners/java/jdk25` | Java 25 | Built-in Java ML-DSA implementation |
| Bouncy Castle Java Base | `runners/java/bc-base`, `runners/kotlin/bc-kotlin` | `bcprov-jdk18on` 1.85.2 | Java PQC provider |
| Bouncy Castle Java LTS | `runners/java/bc-lts` | `bcprov-lts8on` 2.73.12.1 | Long-term-support Java PQC provider |
| Kotlin/JVM | `runners/kotlin/bc-kotlin` | Kotlin 2.4.0 | Language/runtime adapter; the PQC implementation remains Bouncy Castle Java |
| Apple CryptoKit | `runners/swift/cryptokit` | macOS 26 / Xcode 26 | Apple system cryptography framework |
| SwiftDilithium | `runners/swift/swift-dilithium` | 3.6.0 | Pure-Swift ML-DSA implementation |
| `@noble/post-quantum` | `runners/javascript/noble` | 0.7.0 | Pure JavaScript/TypeScript ML-DSA implementation |
| `@noble/hashes` | `runners/javascript/noble` | 2.3.0 | SHA-2 hashing used by the runner's HashML-DSA path |

The Kotlin runner does **not** use the separate upstream `bc-kotlin` adapter. It
is Kotlin source calling the Bouncy Castle Java provider directly.

## JDK 25 and SUN

The runner uses the standard Java security APIs supplied by JDK 25. The
following companies publicly use the Java ML-DSA API, but neither source
explicitly selects the `SUN` provider. These entries therefore demonstrate JDK
ML-DSA use, not a confirmed independent adoption of the exact SUN provider.

| Company or organization | Public evidence | What can be verified |
| --- | --- | --- |
| Yubico AB | [Yubico ML-DSA test source](https://github.com/Yubico/java-webauthn-server/blob/main/webauthn-server-core/src/test/scala/com/yubico/webauthn/TestAuthenticator.scala) | The Yubico WebAuthn test code calls `KeyPairGenerator.getInstance("ML-DSA")` and initializes `NamedParameterSpec`. It is test code and does not name `SUN`. |
| wolfSSL Inc. | [wolfSSL JCE ML-DSA test](https://github.com/wolfSSL/wolfssljni/blob/master/src/test/com/wolfssl/provider/jsse/test/WolfSSLPQCAuthKeyStoreTest.java) | wolfSSL tests JDK ML-DSA key factories and JKS/PKCS12 keystores with ML-DSA certificates. The TLS provider selected in the test is wolfJSSE, so this is not proof of SUN-provider use. |

The JDK implementation itself is documented by Oracle's [JDK 25 security API
documentation](https://docs.oracle.com/en/java/javase/25/docs/api/) and the
OpenJDK [ML-DSA provider source](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/sun/security/provider/ML_DSA_Impls.java).
Those sources establish ownership and implementation, not third-party company
adoption.

**Result:** no independently verifiable company was found that explicitly
selects `SUN` for ML-DSA in public source.

## Bouncy Castle Java Base

The Base runner uses `org.bouncycastle:bcprov-jdk18on:1.85.2`. The Kotlin runner
uses the same library and the same Bouncy Castle Java lineage.

| Company or organization | Public evidence | What can be verified |
| --- | --- | --- |
| Apache Software Foundation / Apache Camel | [Camel PQC Maven manifest](https://github.com/apache/camel/blob/main/components/camel-pqc/pom.xml), [ML-DSA lifecycle test](https://github.com/apache/camel/blob/main/components/camel-pqc/src/test/java/org/apache/camel/component/pqc/PQCKeyLifecycleTest.java) | Apache Camel directly declares `bcprov-jdk18on` and tests `BouncyCastleProvider`, `BouncyCastlePQCProvider`, and ML-DSA key generation. This is strong PQC integration evidence, not proof of production deployment. |
| Apache Software Foundation / Apache JMeter | [JMeter component build](https://github.com/apache/jmeter/blob/master/src/components/build.gradle.kts) | JMeter declares Bouncy Castle provider and PKIX artifacts for its component and test runtime. The cited use is general cryptography/test support, not ML-DSA-specific. |

**Result:** public PQC-specific use is verified in Apache Camel; public source also shows broader Bouncy Castle use by Apache JMeter.

## Bouncy Castle Java LTS

The LTS runner uses `org.bouncycastle:bcprov-lts8on:2.73.12.1`. LTS is a separate
distribution, but it shares the Bouncy Castle Java implementation lineage with
the Base distribution.

| Company or organization | Public evidence | What can be verified |
| --- | --- | --- |
| R3 / Corda | [Corda core test build](https://github.com/corda/corda/blob/release/os/4.15/core-tests/build.gradle), [Corda node API build](https://github.com/corda/corda/blob/release/os/4.15/node-api/build.gradle) | Corda declares `bcprov-lts8on` and `bcpkix-lts8on`; the source comments identify Bouncy Castle as needed for X.509 certificate manipulation. The cited use is not ML-DSA-specific. |
| Open Source Geospatial Foundation / GeoServer | [GeoServer web-core Maven manifest](https://github.com/geoserver/geoserver/blob/main/src/web/core/pom.xml) | GeoServer directly declares `bcprov-lts8on` as a compile dependency. The cited source does not prove a PQC algorithm call. |
| OpenSearch project | [OpenSearch Java client build](https://github.com/opensearch-project/opensearch-java/blob/main/java-client/build.gradle.kts) | The OpenSearch Java client declares Bouncy Castle LTS for tests. This is test dependency evidence, not production or ML-DSA evidence. |

**Result:** direct LTS adoption is publicly verified in Corda, GeoServer, and
the OpenSearch project. No cited LTS adopter was verified as calling ML-DSA.

## Apple CryptoKit

CryptoKit is an Apple system framework. The current runner uses Apple's native
ML-DSA APIs, which Apple documents for ML-DSA-65 and ML-DSA-87.

| Company or organization | Public evidence | What can be verified |
| --- | --- | --- |
| Apple Inc. | [Apple quantum-secure workflows](https://developer.apple.com/documentation/cryptokit/enhancing-your-app-s-privacy-and-security-with-quantum-secure-workflows.md), [Apple MLDSA65 API](https://developer.apple.com/documentation/cryptokit/mldsa65.md), [Apple MLDSA87 API](https://developer.apple.com/documentation/cryptokit/mldsa87.md) | Apple officially documents and demonstrates CryptoKit ML-DSA-65 and ML-DSA-87 signing, verification, and Secure Enclave workflows. This is first-party framework documentation, not evidence of a separate customer. |
| Auth0 / Okta | [Auth0.swift DPoP key store](https://github.com/auth0/Auth0.swift/blob/master/Auth0/DPoP/DPoPKeyStore.swift) | Auth0's Swift SDK imports CryptoKit and uses P-256 and Secure Enclave signing keys. This proves general CryptoKit use, not native ML-DSA use. |
| Microsoft | [Entra Verified ID wallet ES256 implementation](https://github.com/microsoft/entra-verifiedid-wallet-library-ios/blob/dev/WalletLibrary/Submodules/VerifiableCredential-SDK-iOS/VCCrypto/VCCrypto/Algorithms/ES256.swift) | Microsoft's iOS wallet library imports CryptoKit and uses P-256 signing and verification. This proves general CryptoKit use, not native ML-DSA use. |
| Expo | [Expo Crypto encryption key](https://github.com/expo/expo/blob/main/packages/expo-crypto/ios/AES/EncryptionKey.swift) | Expo's iOS crypto module imports CryptoKit and uses `CryptoKit.SymmetricKey`. This proves general CryptoKit use, not native ML-DSA use. |

**Result:** general CryptoKit use is publicly verified in Auth0, Microsoft, and
Expo. No non-Apple company-owned project was found that publicly calls native
`CryptoKit.MLDSA65` or `CryptoKit.MLDSA87`.

## SwiftDilithium

The runner uses `SwiftDilithium` 3.6.0 from [Leif Ibsen's upstream repository](https://github.com/leif-ibsen/SwiftDilithium).
The repository describes the library as a Swift implementation of FIPS 204 and
contains its own tests and documentation.

No publicly verifiable company or corporate product using SwiftDilithium was
found. [Swift Package Index's package list](https://github.com/SwiftPackageIndex/PackageList/blob/main/packages.json#L5579)
shows that the package is indexed, but package indexing is not adoption
evidence. The upstream repository is maintained under an individual account and
does not identify a corporate adopter.

The runner's resolved supporting packages are [ASN1](https://github.com/leif-ibsen/ASN1),
[BigInt](https://github.com/leif-ibsen/BigInt), and
[Digest](https://github.com/leif-ibsen/Digest). No corporate adopters of these
specific SwiftDilithium support packages were verified either.

## `@noble/post-quantum`

The JavaScript runner uses `@noble/post-quantum` 0.7.0. The following public
repositories directly declare or import the library.

| Company or organization | Public evidence | What can be verified |
| --- | --- | --- |
| NEAR Inc. | [NEAR package manifest](https://github.com/near/near-api-js/blob/master/package.json), [ML-DSA-65 key pair](https://github.com/near/near-api-js/blob/master/src/crypto/key_pair_ml_dsa_65.ts), [release changelog](https://github.com/near/near-api-js/blob/master/CHANGELOG.md) | The NEAR SDK author is NEAR Inc., declares `@noble/post-quantum`, and implements ML-DSA-65 transaction signing. This is strong SDK integration evidence; independent production deployment is not proved. |
| IOTA Foundation | [IOTA package manifest](https://github.com/iotaledger/identity/blob/main/bindings/wasm/identity_wasm/package.json), [PQC verifier](https://github.com/iotaledger/identity/blob/main/bindings/wasm/identity_wasm/lib/pq_verifier.ts) | An IOTA Foundation package declares `@noble/post-quantum` and verifies ML-DSA-44, ML-DSA-65, and ML-DSA-87 signatures. The package is marked beta in its manifest. |
| Microsoft | [WebAuthn Test manifest](https://github.com/microsoft/webauthntest/blob/main/package.json), [WebAuthn ML-DSA verifier](https://github.com/microsoft/webauthntest/blob/main/src/lib/webauthn.js) | A Microsoft-owned repository declares the package and verifies ML-DSA signatures in a Passkey Playground. This is an experiment/playground, not evidence of a Microsoft production service. |
| Remnawave | [Frontend manifest](https://github.com/remnawave/frontend/blob/main/package.json), [ML-DSA/ML-KEM key generation](https://github.com/remnawave/frontend/blob/main/src/widgets/dashboard/config-profiles/keypair-generator/keypair-utils.ts) | The Remnawave project declares the package and uses ML-DSA-65 and ML-KEM-768 in its panel code. The project's legal corporate identity and deployment status were not independently verified. |
| Ethereum / Kohaku | [PQ account manifest](https://github.com/ethereum/kohaku/blob/master/packages/pq-account/js/package.json), [ML-DSA signer](https://github.com/ethereum/kohaku/blob/master/packages/pq-account/js/software-signer/mldsaSigner.js), [Kohaku README](https://github.com/ethereum/kohaku/blob/master/README.md) | The Ethereum organization uses the package in a post-quantum account prototype. The README identifies the project as work in progress and not production-ready. |

**Result:** multiple organizations publicly integrate `@noble/post-quantum`.
NEAR Inc. and Pantheon Security provide the clearest company-identified source
evidence. Most projects do not provide independent proof that the integration
is deployed at scale.

## `@noble/hashes`

The runner directly uses `@noble/hashes` 2.3.0 for SHA-256 and SHA-512 in its
HashML-DSA evaluation path. It is a hashing library, not the ML-DSA
implementation.

| Company or organization | Public evidence | What can be verified |
| --- | --- | --- |
| NEAR Inc. | [NEAR package manifest](https://github.com/near/near-api-js/blob/master/package.json) | NEAR Inc. directly declares `@noble/hashes` alongside `@noble/post-quantum`. The manifest alone does not identify each call site. |
| IOTA Foundation | [IOTA package manifest](https://github.com/iotaledger/identity/blob/main/bindings/wasm/identity_wasm/package.json) | An IOTA Foundation package directly declares `@noble/hashes` and `@noble/post-quantum`. This shows package use in the identity WASM distribution, not necessarily the same HashML-DSA operation as this runner. |
| BlueWallet | [BlueWallet dependency-resolution note](https://github.com/BlueWallet/BlueWallet/blob/master/metro.config.js) | BlueWallet documents resolving `@noble/hashes` for its Bitcoin-related dependencies. This is general hashing-library use, not PQC use. |



These references establish general library use only. They should not be used to
claim that those organizations use the PQC runner or its selected algorithms.

