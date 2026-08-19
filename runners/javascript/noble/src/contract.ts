export type EvaluationResult = {
	schemaVersion: "1.1"
	runId: string
	generatedAt: string
	implementation: Implementation
	runtime: RuntimeInfo
	parameterSets: ParameterSetResult[]
	checks: CheckResult[]
	interoperability: InteropResult[]
	warnings: string[]
}

export type Implementation = {
	id: string
	displayName: string
	version: string
	engineLineageId: string
	distribution: string
	license: string
	assuranceStatus: string
}

export type RuntimeInfo = {
	javaVersion: null
	javaVendor: null
	osName: string
	osVersion: string
	architecture: string
	buildProperties: Record<string, string>
}

export type ParameterSetResult = {
	parameterSet: string
	securityLevel: number
	rawPublicKeyBytes: number
	rawPrivateSeedBytes: number
	rawPrivateExpandedBytes: number
	rawSignatureBytes: number
	capabilities: Capability[]
	representations: Representation[]
}

export type Capability = {
	operation: string
	status: "supported" | "unsupported"
	origin: string
	evidence: string
	reason: string | null
	callSite?: CallSite
}

export type CallSite = {
	sourceFile: string
	className: string
	methodName: string
	lineNumber: number
	snippet: string
	highlightLine: number
	arguments: Argument[]
}

export type Argument = {
	name: string
	type: string
	value: string
}

export type Representation = {
	kind: string
	status: "pass" | "fail" | "unsupported"
	byteLength?: number | null
	sha256?: string | null
	algorithmOid?: string | null
	parametersAbsent?: boolean | null
	privateChoice?: string | null
	origin: string
	reason?: string | null
}

export type CheckResult = {
	id: string
	parameterSet: string
	category: string
	status: "pass" | "fail" | "error" | "skipped" | "unsupported"
	message: string
}

export type InteropResult = {
	producer: string
	consumer: string
	parameterSet: string
	mode: string
	status: "pass" | "fail" | "unsupported"
	message: string
}
