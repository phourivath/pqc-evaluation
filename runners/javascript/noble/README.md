# Noble ML-DSA Runner

This is an independent TypeScript runner for
`@noble/post-quantum@0.7.0`. It targets Node `>=20.19.0`, including Node 22
LTS, and evaluates ML-DSA-44, ML-DSA-65, and ML-DSA-87.

The runner uses the package's raw `Uint8Array` API for seeded key generation,
expanded private keys, public keys, pure signing, context signing, and
HashML-DSA pre-hashing. The package does not provide native SPKI, PKCS#8, or
JWK APIs; those representations are reported as unsupported rather than being
wrapped and mislabeled as provider-native support.

Private seeds, expanded private keys, and private hashes remain in memory only.
The normalized result contains their normative lengths and capability evidence,
but never the private values.

## Build

Run from the repository root:

```bash
npm --prefix runners/javascript/noble ci
npm --prefix runners/javascript/noble run check
npm --prefix runners/javascript/noble test
```

## Run

The default output is `build/evaluation-result.json` within the runner root. A
custom output path may be supplied as the only argument:

```bash
node runners/javascript/noble/dist/main.js \
  runners/javascript/noble/build/evaluation-result.json
```

One invocation emits one schema `1.1` result containing all three parameter-set
observations. Supported key-generation, signing, verification, and HashML-DSA
capabilities include source snippets and safe argument descriptions through the
contract's `callSite` field.
