import { deepStrictEqual, equal, match, ok } from "node:assert/strict"
import { readFileSync } from "node:fs"
import { test } from "node:test"

import { evaluateAll } from "../evaluator.js"

test("evaluates all finalized ML-DSA parameter sets", () => {
	const result = evaluateAll()

	equal(result.schemaVersion, "1.1")
	equal(result.parameterSets.length, 3)
	deepStrictEqual(
		result.parameterSets.map((value) => value.parameterSet),
		["ML-DSA-44", "ML-DSA-65", "ML-DSA-87"],
	)
	equal(result.runtime.javaVersion, null)
	equal(result.runtime.javaVendor, null)

	for (const parameterSet of result.parameterSets) {
		const checks = result.checks.filter(
			(check) => check.parameterSet === parameterSet.parameterSet,
		)
		ok(checks.length > 0)
		ok(checks.every((check) => check.status === "pass"))
		ok(parameterSet.capabilities.some((capability) => capability.operation === "hash-ml-dsa" && capability.status === "supported"))
		ok(parameterSet.capabilities.some((capability) => capability.operation === "spki" && capability.status === "unsupported"))
		ok(parameterSet.capabilities.some((capability) => capability.callSite?.snippet.includes("keygen")))

		const publicRepresentation = parameterSet.representations.find(
			(representation) => representation.kind === "raw-public",
		)
		ok(publicRepresentation)
		ok(publicRepresentation.sha256)

		for (const kind of ["raw-private-seed", "raw-private-expanded"]) {
			const representation = parameterSet.representations.find(
				(value) => value.kind === kind,
			)
			ok(representation)
			equal(representation.sha256, null)
		}
	}
})

test("records source snippets without private key material", () => {
	const result = evaluateAll()
	const capabilities = result.parameterSets.flatMap((value) => value.capabilities)
	const callSites = capabilities.flatMap((capability) =>
		capability.callSite ? [capability.callSite] : [],
	)

	equal(callSites.length, 12)
	for (const callSite of callSites) {
		equal(callSite.sourceFile, "runners/javascript/noble/src/evaluator.ts")
		ok(callSite.lineNumber > 0)
		ok(callSite.highlightLine > 0)
		match(callSite.snippet, /\[evidence:/)
		ok(callSite.arguments.every((argument) => !argument.value.includes("secretKey:")))
		ok(callSite.arguments.every((argument) => !argument.value.match(/^[0-9a-f]{64,}$/i)))
	}
})

test("call-site snippets match the source lines they point at", () => {
	const sourceUrl = new URL("../../src/evaluator.ts", import.meta.url)
	const sourceLines = readFileSync(sourceUrl, "utf8").split("\n")
	const callSites = evaluateAll().parameterSets.flatMap((parameterSet) =>
		parameterSet.capabilities.flatMap((capability) =>
			capability.callSite ? [capability.callSite] : [],
		),
	)

	equal(callSites.length, 12)
	for (const callSite of callSites) {
		const firstLine = callSite.lineNumber - callSite.highlightLine + 1
		const snippetLines = callSite.snippet.replace(/\n$/, "").split("\n")
		const sourceSlice = sourceLines.slice(
			firstLine - 1,
			firstLine - 1 + snippetLines.length,
		)
		deepStrictEqual(
			sourceSlice.map((line) => line.trim()),
			snippetLines.map((line) => line.trim()),
			`${callSite.sourceFile}:${callSite.lineNumber} snippet desynced from source`,
		)
	}
})
