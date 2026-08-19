export type ComparisonRow = {
	runId: string
	generatedAt: string
	implementationId: string
	implementationName: string
	implementationVersion: string
	engineLineageId: string
	assuranceStatus: string
	parameterSet: "ML-DSA-44" | "ML-DSA-65" | "ML-DSA-87"
	securityLevel: number
	rawPublicKeyBytes: number
	rawPrivateSeedBytes: number
	rawPrivateExpandedBytes: number
	rawSignatureBytes: number
	checksPassed: number
	checksFailed: number
	checksUnsupported: number
	interopPassed: number
	interopFailed: number
	interopUnsupported: number
	supportedCapabilities: string[]
	unsupportedCapabilities: string[]
	availableRepresentations: string[]
	unavailableRepresentations: string[]
}

export type EvaluationRun = {
	runId: string
	generatedAt: string
	implementation: {
		id: string
		displayName: string
		version: string
		engineLineageId: string
		distribution: string
		license: string
		assuranceStatus: string
	}
	parameterSets: ParameterSetDetail[]
	checks: CheckDetail[]
	interoperability: InteroperabilityDetail[]
	warnings: string[]
}

export type ParameterSetDetail = {
	parameterSet: ComparisonRow["parameterSet"]
	securityLevel: number
	rawPublicKeyBytes: number
	rawPrivateSeedBytes: number
	rawPrivateExpandedBytes: number
	rawSignatureBytes: number
	capabilities: CapabilityDetail[]
	representations: RepresentationDetail[]
}

export type CapabilityDetail = {
	operation: string
	status: string
	origin: string
	evidence: string
	reason: string | null
	callSite: CallSiteDetail | null
}

export type CallSiteDetail = {
	sourceFile: string
	className: string
	methodName: string
	lineNumber: number
	snippet: string
	highlightLine: number
	arguments: ArgumentDetail[]
}

export type ArgumentDetail = {
	name: string
	type: string
	value: string
}

export type RepresentationDetail = {
	kind: string
	status: string
	byteLength: number | null
	sha256: string | null
	algorithmOid: string | null
	parametersAbsent: boolean | null
	privateChoice: string | null
	origin: string
	reason: string | null
}

export type CheckDetail = {
	id: string
	parameterSet: string
	category: string
	status: string
	message: string
}

export type InteroperabilityDetail = {
	producer: string
	consumer: string
	parameterSet: string
	mode: string
	status: string
	message: string
}

export type RunnerDescriptor = {
	id: string
	displayName: string
	implementationId: string
	version: string
	engineLineageId: string
	lifecycle: "IMPLEMENTED" | "GATED"
	availability: "READY" | "MISSING_ARTIFACT" | "GATED"
	executionSupported: boolean
	reason: string | null
	parameterSets: string[]
}

export type RunnerExecution = {
	executionId: string
	runnerId: string
	status:
		| "QUEUED"
		| "STARTING"
		| "RUNNING"
		| "CANCELLING"
		| "SUCCEEDED"
		| "FAILED"
		| "TIMED_OUT"
		| "CANCELLED"
	submittedAt: string
	startedAt: string | null
	finishedAt: string | null
	exitCode: number | null
	resultRunId: string | null
	resultUrl: string | null
	failure: string | null
}

export const queryKeys = {
	comparisons: ["comparisons"] as const,
	runners: ["runners"] as const,
	executions: ["runner-executions"] as const,
	evaluationRun: (runId: string) => ["evaluation-run", runId] as const,
}

export async function fetchComparisonRows(
	signal?: AbortSignal,
): Promise<ComparisonRow[]> {
	return fetchJson<ComparisonRow[]>("/api/v1/comparisons", signal)
}

export async function fetchEvaluationRun(
	runId: string,
	signal?: AbortSignal,
): Promise<EvaluationRun> {
	return fetchJson<EvaluationRun>(`/api/v1/evaluation-runs/${runId}`, signal)
}

export async function fetchRunners(
	signal?: AbortSignal,
): Promise<RunnerDescriptor[]> {
	return fetchJson<RunnerDescriptor[]>("/api/v1/runners", signal)
}

export async function fetchExecutions(
	signal?: AbortSignal,
): Promise<RunnerExecution[]> {
	return fetchJson<RunnerExecution[]>("/api/v1/runner-executions", signal)
}

export async function startRunner(runnerId: string): Promise<RunnerExecution> {
	return fetchJson<RunnerExecution>(
		`/api/v1/runners/${runnerId}/executions`,
		undefined,
		{
			method: "POST",
		},
	)
}

export async function fetchExecution(
	executionId: string,
	signal?: AbortSignal,
): Promise<RunnerExecution> {
	return fetchJson<RunnerExecution>(
		`/api/v1/runner-executions/${executionId}`,
		signal,
	)
}

async function fetchJson<T>(
	url: string,
	signal?: AbortSignal,
	init?: RequestInit,
): Promise<T> {
	const response = await fetch(url, { ...init, signal })
	if (!response.ok) {
		const problem = (await response.json().catch(() => null)) as {
			detail?: string
		} | null
		throw new Error(problem?.detail ?? `API returned ${response.status}`)
	}
	return (await response.json()) as T
}
