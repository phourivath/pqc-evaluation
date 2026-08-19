import { mkdir, writeFile } from "node:fs/promises"
import { dirname, resolve } from "node:path"

import { evaluateAll } from "./evaluator.js"

const outputPath = resolve(process.argv[2] ?? "build/evaluation-result.json")

try {
	await mkdir(dirname(outputPath), { recursive: true })
	const result = evaluateAll()
	await writeFile(outputPath, `${JSON.stringify(result, null, 2)}\n`, "utf8")
	console.log(outputPath)
} catch (error) {
	const message = error instanceof Error ? error.stack ?? error.message : String(error)
	console.error(message)
	process.exitCode = 1
}
