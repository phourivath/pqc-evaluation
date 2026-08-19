import { createHash, randomUUID } from "node:crypto"
import { release } from "node:os"

import type {
	Argument,
	CallSite,
	Capability,
	CheckResult,
	EvaluationResult,
	ParameterSetResult,
	Representation,
} from "./contract.js"
import {
	fixtureSeed,
	PARAMETER_SETS,
	type ParameterSet,
} from "./parameters.js"

const MESSAGE = new TextEncoder().encode("PQC evaluation message")
const CONTEXT = new TextEncoder().encode("pqc-evaluation")
const LONG_CONTEXT = new Uint8Array(255).fill(0x41)
const OVERSIZED_CONTEXT = new Uint8Array(256).fill(0x42)
const WRONG_MESSAGE = new TextEncoder().encode("PQC evaluation message altered")
const SOURCE_FILE = "runners/javascript/noble/src/evaluator.ts"
const CLASS_NAME = "NobleMlDsaEvaluator"

const KEY_GENERATION_SNIPPET = `// [evidence:key-generation] @noble/post-quantum seeded keygen
const { secretKey, publicKey } = spec.algorithm.keygen(seed);`
const SIGN_SNIPPET = `// [evidence:sign] @noble/post-quantum deterministic sign
const signature = spec.algorithm.sign(MESSAGE, secretKey, {
  extraEntropy: false,
});`
const VERIFY_SNIPPET = `// [evidence:verify] @noble/post-quantum verify
const verified = spec.algorithm.verify(signature, MESSAGE, publicKey);`
const PREHASH_SNIPPET = `// [evidence:hash-ml-dsa] @noble/post-quantum HashML-DSA prehash
const prehashSigner = spec.algorithm.prehash(spec.prehash);`

export function evaluateAll(): EvaluationResult {
	const checks: CheckResult[] = []
	const parameterSets = PARAMETER_SETS.map((parameterSet) =>
		evaluateParameterSet(parameterSet, checks),
	)

	return {
		schemaVersion: "1.1",
		runId: `noble-ml-dsa-${cryptoRandomUuid()}`,
		generatedAt: new Date().toISOString(),
		implementation: {
			id: "noble-ml-dsa",
			displayName: "Noble ML-DSA",
			version: "0.7.0",
			engineLineageId: "noble-js",
			distribution: "@noble/post-quantum",
			license: "MIT",
			assuranceStatus: "third-party pure JavaScript; not independently audited",
		},
		runtime: {
			javaVersion: null,
			javaVendor: null,
			osName: process.platform,
			osVersion: release(),
			architecture: process.arch,
			buildProperties: {
				language: "typescript",
				api: "@noble/post-quantum/ml-dsa.js",
				package: "@noble/post-quantum",
				packageVersion: "0.7.0",
				packageIntegrity:
					"sha512-IH2tpuGV4vBMdpCCua2BN7EuUICtmGp6DlBMNBYAYcL6QQ7eHt85GjLyD7ZT6Qx/xgIPIMqsSLDGvYqOm8Vqag==",
				nodeVersion: process.version,
				v8Version: process.versions.v8,
				engine: "pure-javascript",
				algorithm: "ML-DSA",
			},
		},
		parameterSets,
		checks,
		interoperability: [],
		warnings: [
			"The package exposes raw Uint8Array keys only; SPKI, PKCS#8, and JWK are unsupported native representations.",
			"The implementation is pure JavaScript, has no independent security audit, and makes no constant-time guarantee.",
		],
	}
}

export function evaluateParameterSet(
	spec: ParameterSet,
	checks: CheckResult[],
): ParameterSetResult {
	const seed = fixtureSeed(spec)
	const keyGenerationCallSite = makeCallSite(
		"evaluateParameterSet",
		104,
		KEY_GENERATION_SNIPPET,
		2,
		[
			argument("algorithm", "ParameterSet", spec.name),
			argument("seed", "Uint8Array", `${seed.length}-byte deterministic fixture`),
			argument("keyPair", "CryptoKeys", "secretKey + publicKey"),
		],
	)

	// [evidence:key-generation] @noble/post-quantum seeded keygen
	const { secretKey, publicKey } = spec.algorithm.keygen(seed)
	const derivedPublicKey = spec.algorithm.getPublicKey(secretKey)

	const signCallSite = makeCallSite(
		"evaluateParameterSet",
		125,
		SIGN_SNIPPET,
		2,
		[
			argument("algorithm", "ParameterSet", spec.name),
			argument(
				"key",
				"Uint8Array",
				`expanded private key (${secretKey.length} bytes)`,
			),
			argument("message", "Uint8Array", messageDescription()),
			argument("extraEntropy", "false", "deterministic signature"),
		],
	)

	// [evidence:sign] @noble/post-quantum deterministic sign
	const signature = spec.algorithm.sign(MESSAGE, secretKey, {
		extraEntropy: false,
	})

	// [evidence:verify] @noble/post-quantum verify
	const verified = spec.algorithm.verify(signature, MESSAGE, publicKey)
	const verifyCallSite = makeCallSite(
		"evaluateParameterSet",
		130,
		VERIFY_SNIPPET,
		2,
		[
			argument("algorithm", "ParameterSet", spec.name),
			argument("publicKey", "Uint8Array", `raw public key (${publicKey.length} bytes)`),
			argument("signature", "Uint8Array", `${signature.length} bytes`),
			argument("message", "Uint8Array", messageDescription()),
			argument("verified", "boolean", String(verified)),
		],
	)
	const repeatedSignature = spec.algorithm.sign(MESSAGE, secretKey, {
		extraEntropy: false,
	})
	const contextSignature = spec.algorithm.sign(MESSAGE, secretKey, {
		context: CONTEXT,
		extraEntropy: false,
	})
	const contextVerified = spec.algorithm.verify(contextSignature, MESSAGE, publicKey, {
		context: CONTEXT,
	})
	const longContextSignature = spec.algorithm.sign(MESSAGE, secretKey, {
		context: LONG_CONTEXT,
		extraEntropy: false,
	})
	const longContextVerified = spec.algorithm.verify(
		longContextSignature,
		MESSAGE,
		publicKey,
		{ context: LONG_CONTEXT },
	)
	const oversizedContextRejected = throws(() =>
		spec.algorithm.sign(MESSAGE, secretKey, {
			context: OVERSIZED_CONTEXT,
			extraEntropy: false,
		}),
	)
	const wrongMessageRejected = !spec.algorithm.verify(signature, WRONG_MESSAGE, publicKey)
	const modifiedSignature = new Uint8Array(signature)
	const firstSignatureByte = modifiedSignature[0]
	if (firstSignatureByte === undefined) {
		throw new Error("ML-DSA signature must not be empty")
	}
	modifiedSignature[0] = firstSignatureByte ^ 0x01
	const modifiedSignatureRejected = !spec.algorithm.verify(
		modifiedSignature,
		MESSAGE,
		publicKey,
	)

	const reconstructed = spec.algorithm.keygen(seed)
	// [evidence:hash-ml-dsa] @noble/post-quantum HashML-DSA prehash
	const prehashSigner = spec.algorithm.prehash(spec.prehash)
	const prehashCallSite = makeCallSite(
		"evaluateParameterSet",
		185,
		PREHASH_SNIPPET,
		2,
		[
			argument("algorithm", "ParameterSet", spec.name),
			argument("hash", "CHash", spec.prehash.name ?? "approved hash"),
		],
	)

	const prehashSignature = prehashSigner.sign(MESSAGE, secretKey, {
		context: CONTEXT,
		extraEntropy: false,
	})
	const prehashVerified = prehashSigner.verify(
		prehashSignature,
		MESSAGE,
		publicKey,
		{ context: CONTEXT },
	)

	addCheck(
		checks,
		spec,
		"key-generation",
		"correctness",
		"pass",
		"Generated a deterministic key pair from a 32-byte seed",
	)
	addCheck(
		checks,
		spec,
		"raw-public-length",
		"encoding",
		status(publicKey.length === spec.publicBytes),
		`Raw public key is ${publicKey.length} bytes`,
	)
	addCheck(
		checks,
		spec,
		"raw-private-seed-length",
		"encoding",
		status(seed.length === spec.seedBytes),
		`Raw seed is ${seed.length} bytes`,
	)
	addCheck(
		checks,
		spec,
		"raw-private-expanded-length",
		"encoding",
		status(secretKey.length === spec.expandedPrivateBytes),
		`Expanded private key is ${secretKey.length} bytes`,
	)
	addCheck(
		checks,
		spec,
		"signature-length",
		"correctness",
		status(signature.length === spec.signatureBytes),
		`Signature is ${signature.length} bytes`,
	)
	addCheck(
		checks,
		spec,
		"self-sign-verify",
		"correctness",
		status(verified),
		"Generated signature verifies",
	)
	addCheck(
		checks,
		spec,
		"public-key-derivation",
		"correctness",
		status(equalBytes(publicKey, derivedPublicKey)),
		"getPublicKey(secretKey) matches the generated public key",
	)
	addCheck(
		checks,
		spec,
		"seed-reconstruction",
		"correctness",
		status(equalBytes(publicKey, reconstructed.publicKey) && equalBytes(secretKey, reconstructed.secretKey)),
		"Repeating keygen with the same seed reproduces both raw keys",
	)
	addCheck(
		checks,
		spec,
		"deterministic-signature",
		"correctness",
		status(equalBytes(signature, repeatedSignature)),
		"extraEntropy=false reproduces the signature",
	)
	addCheck(
		checks,
		spec,
		"context-sign-verify",
		"correctness",
		status(contextVerified),
		"Non-empty context signs and verifies",
	)
	addCheck(
		checks,
		spec,
		"context-255-byte",
		"correctness",
		status(longContextVerified),
		"255-byte context signs and verifies",
	)
	addCheck(
		checks,
		spec,
		"context-256-byte",
		"correctness",
		status(oversizedContextRejected),
		"256-byte context is rejected",
	)
	addCheck(
		checks,
		spec,
		"wrong-message",
		"correctness",
		status(wrongMessageRejected),
		"Verification rejects a modified message",
	)
	addCheck(
		checks,
		spec,
		"modified-signature",
		"correctness",
		status(modifiedSignatureRejected),
		"Verification rejects a modified signature",
	)
	addCheck(
		checks,
		spec,
		"hash-ml-dsa",
		"correctness",
		status(prehashVerified),
		"HashML-DSA pre-hash signature verifies with the selected approved hash",
	)

	const capabilities: Capability[] = [
		capability("key-generation", "supported", "native-api", "@noble/post-quantum keygen(seed)", null, keyGenerationCallSite),
		capability("sign", "supported", "native-api", "@noble/post-quantum sign(message, secretKey)", null, signCallSite),
		capability("verify", "supported", "native-api", "@noble/post-quantum verify(signature, message, publicKey)", null, verifyCallSite),
		capability("raw-public", "supported", "native-api", "keygen(seed).publicKey", null),
		capability(
			"raw-private-seed",
			"supported",
			"native-api",
			"keygen(seed) accepts a 32-byte seed",
			null,
		),
		capability(
			"raw-private-expanded",
			"supported",
			"native-api",
			"keygen(seed).secretKey",
			null,
		),
		capability("context", "supported", "native-api", "sign/verify options.context", null),
		capability("hash-ml-dsa", "supported", "native-api", "prehash(approvedHash)", null, prehashCallSite),
		capability(
			"spki",
			"unsupported",
			"native-api",
			"No SPKI or PKIX API",
			"The package exposes raw Uint8Array keys only",
		),
		capability(
			"pkcs8",
			"unsupported",
			"native-api",
			"No PKCS#8 API",
			"The package exposes raw Uint8Array keys only",
		),
		capability(
			"jwk",
			"unsupported",
			"native-api",
			"No JWK API",
			"The package exposes raw Uint8Array keys only",
		),
	]

	const representations: Representation[] = [
		representation(
			"raw-public",
			"pass",
			publicKey.length,
			sha256(publicKey),
			"native-api",
		),
		representation(
			"raw-private-seed",
			"pass",
			seed.length,
			null,
			"native-api",
			"seed",
			"Seed is used in memory only; private bytes are not retained in the result",
		),
		representation(
			"raw-private-expanded",
			"pass",
			secretKey.length,
			null,
			"native-api",
			"expandedKey",
			"Expanded private bytes are used in memory only; private bytes are not retained in the result",
		),
		representation(
			"raw-signature",
			"pass",
			signature.length,
			sha256(signature),
			"native-api",
		),
		unsupportedRepresentation("spki", "No SPKI or PKIX API"),
		unsupportedRepresentation("pkcs8", "No PKCS#8 API"),
		unsupportedRepresentation("jwk-public", "No JWK API"),
		unsupportedRepresentation("jwk-private", "No JWK API"),
	]

	return {
		parameterSet: spec.name,
		securityLevel: spec.securityLevel,
		rawPublicKeyBytes: spec.publicBytes,
		rawPrivateSeedBytes: spec.seedBytes,
		rawPrivateExpandedBytes: spec.expandedPrivateBytes,
		rawSignatureBytes: spec.signatureBytes,
		capabilities,
		representations,
	}
}

function capability(
	operation: string,
	status: Capability["status"],
	origin: string,
	evidence: string,
	reason: string | null,
	callSite?: CallSite,
): Capability {
	return callSite
		? { operation, status, origin, evidence, reason, callSite }
		: { operation, status, origin, evidence, reason }
}

function representation(
	kind: string,
	status: Representation["status"],
	byteLength: number,
	sha256Value: string | null,
	origin: string,
	privateChoice?: string,
	reason?: string,
): Representation {
	return {
		kind,
		status,
		byteLength,
		sha256: sha256Value,
		privateChoice: privateChoice ?? null,
		origin,
		reason: reason ?? null,
	}
}

function unsupportedRepresentation(kind: string, reason: string): Representation {
	return {
		kind,
		status: "unsupported",
		byteLength: null,
		sha256: null,
		origin: "native-api",
		reason,
	}
}

function makeCallSite(
	methodName: string,
	lineNumber: number,
	snippet: string,
	highlightLine: number,
	argumentsList: Argument[],
): CallSite {
	return {
		sourceFile: SOURCE_FILE,
		className: CLASS_NAME,
		methodName,
		lineNumber,
		snippet,
		highlightLine,
		arguments: argumentsList,
	}
}

function argument(name: string, type: string, value: string): Argument {
	return { name, type, value }
}

function addCheck(
	checks: CheckResult[],
	spec: ParameterSet,
	id: string,
	category: string,
	checkStatus: CheckResult["status"],
	message: string,
): void {
	checks.push({
		id,
		parameterSet: spec.name,
		category,
		status: checkStatus,
		message,
	})
}

function status(value: boolean): "pass" | "fail" {
	return value ? "pass" : "fail"
}

function equalBytes(left: Uint8Array, right: Uint8Array): boolean {
	if (left.length !== right.length) return false
	for (let index = 0; index < left.length; index += 1) {
		if (left[index] !== right[index]) return false
	}
	return true
}

function throws(action: () => unknown): boolean {
	try {
		action()
		return false
	} catch {
		return true
	}
}

function sha256(bytes: Uint8Array): string {
	return createHash("sha256").update(bytes).digest("hex")
}

function messageDescription(): string {
	return `PQC evaluation message (${MESSAGE.length} bytes UTF-8)`
}

function cryptoRandomUuid(): string {
	return randomUUID()
}
