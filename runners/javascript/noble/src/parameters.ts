import { ml_dsa44, ml_dsa65, ml_dsa87 } from "@noble/post-quantum/ml-dsa.js"
import { sha256, sha512 } from "@noble/hashes/sha2.js"
import type { CHash } from "@noble/hashes/utils.js"

export type ParameterSetName = "ML-DSA-44" | "ML-DSA-65" | "ML-DSA-87"

type NobleMlDsa = typeof ml_dsa44

export type ParameterSet = {
	name: ParameterSetName
	securityLevel: number
	publicBytes: number
	seedBytes: number
	expandedPrivateBytes: number
	signatureBytes: number
	algorithmOid: string
	algorithm: NobleMlDsa
	prehash: CHash
}

export const PARAMETER_SETS = [
	{
		name: "ML-DSA-44",
		securityLevel: 2,
		publicBytes: 1312,
		seedBytes: 32,
		expandedPrivateBytes: 2560,
		signatureBytes: 2420,
		algorithmOid: "2.16.840.1.101.3.4.3.17",
		algorithm: ml_dsa44,
		prehash: sha256,
	},
	{
		name: "ML-DSA-65",
		securityLevel: 3,
		publicBytes: 1952,
		seedBytes: 32,
		expandedPrivateBytes: 4032,
		signatureBytes: 3309,
		algorithmOid: "2.16.840.1.101.3.4.3.18",
		algorithm: ml_dsa65,
		prehash: sha512,
	},
	{
		name: "ML-DSA-87",
		securityLevel: 5,
		publicBytes: 2592,
		seedBytes: 32,
		expandedPrivateBytes: 4896,
		signatureBytes: 4627,
		algorithmOid: "2.16.840.1.101.3.4.3.19",
		algorithm: ml_dsa87,
		prehash: sha512,
	},
] satisfies readonly ParameterSet[]

export function fixtureSeed(parameterSet: ParameterSet): Uint8Array {
	const seed = new Uint8Array(parameterSet.seedBytes)
	for (let index = 0; index < seed.length; index += 1) {
		seed[index] = (parameterSet.securityLevel * 17 + index) & 0xff
	}
	return seed
}
