import { Dialog } from "@base-ui/react/dialog"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { createFileRoute } from "@tanstack/react-router"
import {
	createColumnHelper,
	createSortedRowModel,
	rowSortingFeature,
	type SortingState,
	tableFeatures,
	useTable,
} from "@tanstack/react-table"
import {
	AlertCircle,
	CheckCircle2,
	ChevronRight,
	ChevronsUpDown,
	CircleDashed,
	Info,
	LoaderCircle,
	PanelRightClose,
	Play,
	RefreshCw,
	Search,
	ShieldCheck,
	XCircle,
} from "lucide-react"
import type * as React from "react"
import { useEffect, useRef, useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select } from "@/components/ui/select"
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table"
import {
	type CallSiteDetail,
	type ComparisonRow,
	type EvaluationRun,
	fetchComparisonRows,
	fetchEvaluationRun,
	fetchExecutions,
	fetchRunners,
	queryKeys,
	type RunnerDescriptor,
	type RunnerExecution,
	startRunner,
} from "@/lib/comparison"

export const Route = createFileRoute("/")({ component: Dashboard })

const tableFeaturesConfig = tableFeatures({
	rowSortingFeature,
	sortedRowModel: createSortedRowModel(),
})
const columnHelper = createColumnHelper<
	typeof tableFeaturesConfig,
	ComparisonRow
>()
const EMPTY_ROWS: ComparisonRow[] = []
const EMPTY_RUNNERS: RunnerDescriptor[] = []
const EMPTY_EXECUTIONS: RunnerExecution[] = []
const parameterSets = ["all", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87"] as const

const comparisonColumns = columnHelper.columns([
	columnHelper.accessor("implementationName", {
		id: "implementation",
		header: "Implementation",
		cell: ({ row }) => (
			<div className="min-w-52">
				<div className="font-medium text-foreground">
					{row.original.implementationName}
				</div>
				<div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
					<span>{row.original.implementationVersion}</span>
					<span className="text-border">|</span>
					<span>{row.original.engineLineageId}</span>
				</div>
				<div className="mt-2">
					<AssuranceBadge status={row.original.assuranceStatus} />
				</div>
			</div>
		),
	}),
	columnHelper.accessor("parameterSet", {
		header: "Parameter set",
		cell: ({ row }) => (
			<div>
				<div className="font-mono text-xs font-semibold">
					{row.original.parameterSet}
				</div>
				<div className="mt-1 text-xs text-muted-foreground">
					NIST level {row.original.securityLevel}
				</div>
			</div>
		),
	}),
	columnHelper.accessor("rawPublicKeyBytes", {
		id: "sizes",
		header: "FIPS expected sizes",
		sortFn: (a, b) =>
			a.original.rawPublicKeyBytes - b.original.rawPublicKeyBytes,
		cell: ({ row }) => (
			<div className="grid min-w-40 grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs">
				<span className="text-muted-foreground">public</span>
				<span className="font-mono text-right">
					{formatBytes(row.original.rawPublicKeyBytes)}
				</span>
				<span className="text-muted-foreground">expanded private</span>
				<span className="font-mono text-right">
					{formatBytes(row.original.rawPrivateExpandedBytes)}
				</span>
				<span className="text-muted-foreground">signature</span>
				<span className="font-mono text-right">
					{formatBytes(row.original.rawSignatureBytes)}
				</span>
			</div>
		),
	}),
	columnHelper.accessor("availableRepresentations", {
		id: "representations",
		header: "Formats",
		cell: ({ row }) => (
			<div className="representation-summary">
				<div>
					{row.original.availableRepresentations.map((kind) => (
						<Badge key={kind} variant="outline">
							{formatRepresentationKind(kind)}
						</Badge>
					))}
				</div>
				{row.original.unavailableRepresentations.length > 0 && (
					<span className="text-xs text-amber-700">
						{row.original.unavailableRepresentations.length} unavailable
					</span>
				)}
			</div>
		),
	}),
	columnHelper.accessor("checksFailed", {
		id: "checks",
		header: "Checks",
		sortFn: (a, b) => a.original.checksFailed - b.original.checksFailed,
		cell: ({ row }) => (
			<ResultSummary
				passed={row.original.checksPassed}
				failed={row.original.checksFailed}
				unsupported={row.original.checksUnsupported}
			/>
		),
	}),
	columnHelper.accessor("interopFailed", {
		id: "interop",
		header: "Interop",
		sortFn: (a, b) => a.original.interopFailed - b.original.interopFailed,
		cell: ({ row }) => (
			<ResultSummary
				passed={row.original.interopPassed}
				failed={row.original.interopFailed}
				unsupported={row.original.interopUnsupported}
				emptyLabel="not evaluated"
			/>
		),
	}),
	columnHelper.accessor("generatedAt", {
		id: "generated",
		header: "Generated",
		cell: ({ row }) => (
			<div className="min-w-32 text-xs">
				<div>{formatDate(row.original.generatedAt)}</div>
				<div className="mt-1 font-mono text-[10px] text-muted-foreground">
					{shortId(row.original.runId)}
				</div>
			</div>
		),
	}),
])

function Dashboard() {
	const queryClient = useQueryClient()
	const [search, setSearch] = useState("")
	const [parameterSet, setParameterSet] =
		useState<(typeof parameterSets)[number]>("all")
	const [outcome, setOutcome] = useState("all")
	const [selectedRow, setSelectedRow] = useState<ComparisonRow | null>(null)
	const [sorting, setSorting] = useState<SortingState>([
		{ id: "implementation", desc: false },
		{ id: "parameterSet", desc: false },
	])
	const seenSuccessfulExecutions = useRef(new Set<string>())

	const comparisons = useQuery({
		queryKey: queryKeys.comparisons,
		queryFn: ({ signal }) => fetchComparisonRows(signal),
		retry: false,
	})
	const runners = useQuery({
		queryKey: queryKeys.runners,
		queryFn: ({ signal }) => fetchRunners(signal),
		retry: false,
	})
	const executions = useQuery({
		queryKey: queryKeys.executions,
		queryFn: ({ signal }) => fetchExecutions(signal),
		retry: false,
		refetchInterval: 1500,
	})
	const selectedRun = useQuery({
		queryKey: queryKeys.evaluationRun(selectedRow?.runId ?? "none"),
		queryFn: ({ signal }) =>
			fetchEvaluationRun(selectedRow?.runId ?? "", signal),
		enabled: selectedRow !== null,
		retry: false,
	})
	const runMutation = useMutation({
		mutationFn: startRunner,
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: queryKeys.executions })
		},
	})

	useEffect(() => {
		for (const execution of executions.data ?? EMPTY_EXECUTIONS) {
			if (
				execution.status === "SUCCEEDED" &&
				!seenSuccessfulExecutions.current.has(execution.executionId)
			) {
				seenSuccessfulExecutions.current.add(execution.executionId)
				void queryClient.invalidateQueries({ queryKey: queryKeys.comparisons })
			}
		}
	}, [executions.data, queryClient])

	const rows = latestRows(comparisons.data ?? EMPTY_ROWS)
	const filteredRows = rows.filter((row) => {
		const searchText = search.trim().toLowerCase()
		const matchesSearch =
			searchText.length === 0 ||
			[
				row.implementationName,
				row.implementationId,
				row.engineLineageId,
				row.parameterSet,
			].some((value) => value.toLowerCase().includes(searchText))
		const matchesParameter =
			parameterSet === "all" || row.parameterSet === parameterSet
		const hasFailedChecks = row.checksFailed > 0 || row.interopFailed > 0
		const hasUnsupportedChecks =
			row.checksUnsupported > 0 || row.interopUnsupported > 0
		const matchesOutcome =
			outcome === "all" ||
			(outcome === "attention" && hasFailedChecks) ||
			(outcome === "unsupported" && hasUnsupportedChecks) ||
			(outcome === "passing" && !hasFailedChecks && !hasUnsupportedChecks)
		return matchesSearch && matchesParameter && matchesOutcome
	})
	const table = useTable({
		features: tableFeaturesConfig,
		columns: comparisonColumns,
		data: filteredRows,
		state: { sorting },
		onSortingChange: setSorting,
	})
	const readyRunners = (runners.data ?? EMPTY_RUNNERS).filter(
		(runner) => runner.executionSupported,
	)
	const activeExecutions = (executions.data ?? EMPTY_EXECUTIONS).filter(
		isActive,
	)
	const latestExecutions = latestExecutionsByRunner(
		executions.data ?? EMPTY_EXECUTIONS,
	)
	const passingChecks = rows.reduce((total, row) => total + row.checksPassed, 0)
	const unsupportedChecks = rows.reduce(
		(total, row) => total + row.checksUnsupported,
		0,
	)
	const failedChecks = rows.reduce((total, row) => total + row.checksFailed, 0)
	const totalExpectedRows = readyRunners.length * 3

	async function runAll() {
		await Promise.allSettled(
			readyRunners.map((runner) => runMutation.mutateAsync(runner.id)),
		)
	}

	return (
		<div className="dashboard-shell">
			<header className="dashboard-header">
				<div className="brand-lockup">
					<div className="brand-mark">PQC</div>
					<div>
						<div className="brand-name">PQC Evaluation</div>
						<div className="brand-context">ML-DSA implementation console</div>
					</div>
				</div>
				<div className="header-actions">
					<Badge variant="success">
						<ShieldCheck className="mr-1 size-3" />
						loopback only
					</Badge>
					<Button
						variant="outline"
						size="sm"
						onClick={() => {
							void queryClient.invalidateQueries()
						}}
					>
						<RefreshCw className="size-3.5" />
						Refresh
					</Button>
					<Button
						size="sm"
						disabled={readyRunners.length === 0 || runMutation.isPending}
						onClick={() => void runAll()}
					>
						{runMutation.isPending ? (
							<LoaderCircle className="size-3.5 animate-spin" />
						) : (
							<Play className="size-3.5" />
						)}
						Run all available
					</Button>
				</div>
			</header>

			<main className="dashboard-main">
				<div className="dashboard-title-row">
					<div>
						<div className="section-kicker">Operational dashboard</div>
						<h1>Provider comparison</h1>
						<p>
							Run isolated provider processes, inspect the latest normalized
							evidence, and compare the same three FIPS 204 parameter sets.
						</p>
					</div>
					<div className="scope-summary">
						<span>Scope</span>
						<strong>FIPS 204</strong>
						<small>ML-DSA-44 / 65 / 87</small>
					</div>
				</div>

				{comparisons.isError && (
					<div className="dashboard-alert">
						<AlertCircle className="size-4" />
						<div>
							<strong>Backend unavailable</strong>
							<span>
								Start the local Spring Boot API on port 8080 to load runner
								status and results.
							</span>
						</div>
					</div>
				)}
				{runMutation.isError && (
					<div className="dashboard-alert dashboard-alert-error">
						<AlertCircle className="size-4" />
						<span>{runMutation.error.message}</span>
					</div>
				)}

				<section className="kpi-grid" aria-label="Evaluation summary">
					<Kpi
						label="Provider surfaces"
						value={new Set(rows.map(providerSurfaceKey)).size}
						detail="latest normalized results"
					/>
					<Kpi
						label="Coverage"
						value={`${rows.length}/${totalExpectedRows || 9}`}
						detail="provider / parameter rows"
					/>
					<Kpi
						label="Checks passed"
						value={passingChecks}
						detail="latest normalized checks"
						tone="success"
					/>
					<Kpi
						label="Unsupported"
						value={unsupportedChecks}
						detail={`${failedChecks} failed checks`}
						tone={failedChecks > 0 ? "danger" : "muted"}
					/>
				</section>

				<section
					className="dashboard-section"
					aria-labelledby="runners-heading"
				>
					<div className="section-heading">
						<div>
							<div className="section-kicker">Execution control</div>
							<h2 id="runners-heading">Runner catalog</h2>
						</div>
						<span className="section-meta">
							{activeExecutions.length} active / {readyRunners.length} available
						</span>
					</div>
					<div className="runner-grid">
						{(runners.data ?? EMPTY_RUNNERS).map((runner) => (
							<RunnerCard
								key={runner.id}
								runner={runner}
								execution={latestExecutions.get(runner.id)}
								isPending={runMutation.isPending}
								onRun={() => runMutation.mutate(runner.id)}
							/>
						))}
						{runners.isLoading && <RunnerCardSkeleton />}
					</div>
				</section>

				<section
					className="dashboard-section"
					aria-labelledby="comparison-heading"
				>
					<div className="section-heading section-heading-table">
						<div>
							<div className="section-kicker">Normalized result set</div>
							<h2 id="comparison-heading">Latest comparison</h2>
							<p className="section-description">
								One row per provider surface and parameter set. Repeated runs
								collapse to the most recent result. Select a row for check and
								representation details.
							</p>
						</div>
						<Badge variant="outline">schema 1.0</Badge>
					</div>
					<div className="filter-toolbar">
						<label className="search-field">
							<Search className="size-4 text-muted-foreground" />
							<span className="sr-only">Search implementations</span>
							<Input
								value={search}
								onChange={(event) => setSearch(event.target.value)}
								placeholder="Search provider, lineage, parameter set"
							/>
						</label>
						<Select
							value={parameterSet}
							onChange={(event) =>
								setParameterSet(
									event.target.value as (typeof parameterSets)[number],
								)
							}
							aria-label="Filter parameter set"
						>
							{parameterSets.map((value) => (
								<option key={value} value={value}>
									{value === "all" ? "All parameter sets" : value}
								</option>
							))}
						</Select>
						<Select
							value={outcome}
							onChange={(event) => setOutcome(event.target.value)}
							aria-label="Filter outcome"
						>
							<option value="all">All outcomes</option>
							<option value="passing">Passing only</option>
							<option value="unsupported">Has unsupported</option>
							<option value="attention">Needs attention</option>
						</Select>
						<span className="filter-count">
							{filteredRows.length} of {rows.length} rows
						</span>
					</div>
					<div className="comparison-workspace">
						<div className="comparison-table-area">
							{comparisons.isLoading ? (
								<div className="table-empty">
									<LoaderCircle className="size-5 animate-spin" /> Reading
									normalized results
								</div>
							) : table.getRowModel().rows.length === 0 ? (
								<div className="table-empty">
									<CircleDashed className="size-5" />
									<strong>No matching results</strong>
									<span>Run an available provider or reset the filters.</span>
								</div>
							) : (
								<div className="table-scroll">
									<Table>
										<TableHeader>
											{table.getHeaderGroups().map((headerGroup) => (
												<TableRow key={headerGroup.id}>
													{headerGroup.headers.map((header) => (
														<TableHead
															key={header.id}
															className="whitespace-nowrap"
														>
															{header.isPlaceholder ? null : (
																<button
																	className="table-sort-button"
																	onClick={header.column.getToggleSortingHandler()}
																	type="button"
																>
																	<table.FlexRender header={header} />
																	<ChevronsUpDown className="size-3.5" />
																</button>
															)}
														</TableHead>
													))}
												</TableRow>
											))}
										</TableHeader>
										<TableBody>
											{table.getRowModel().rows.map((row) => (
												<TableRow
													key={row.id}
													className={
														selectedRow?.runId === row.original.runId &&
														selectedRow?.parameterSet ===
															row.original.parameterSet
															? "table-row-selected"
															: "table-row-clickable"
													}
													onClick={() => setSelectedRow(row.original)}
													onKeyDown={(event) => {
														if (event.key === "Enter" || event.key === " ") {
															event.preventDefault()
															setSelectedRow(row.original)
														}
													}}
													role="button"
													tabIndex={0}
												>
													{row.getAllCells().map((cell) => (
														<TableCell key={cell.id}>
															<table.FlexRender cell={cell} />
														</TableCell>
													))}
												</TableRow>
											))}
										</TableBody>
									</Table>
								</div>
							)}
						</div>
						{selectedRow && (
							<RunDetailPanel
								row={selectedRow}
								run={selectedRun.data}
								isLoading={selectedRun.isLoading}
								error={selectedRun.error}
								onClose={() => setSelectedRow(null)}
							/>
						)}
					</div>
				</section>
			</main>
		</div>
	)
}

function Kpi({
	label,
	value,
	detail,
	tone = "default",
}: {
	label: string
	value: number | string
	detail: string
	tone?: "default" | "success" | "danger" | "muted"
}) {
	return (
		<Card className={`kpi-card kpi-${tone}`}>
			<CardContent>
				<div className="kpi-label">{label}</div>
				<div className="kpi-value">{value}</div>
				<div className="kpi-detail">{detail}</div>
			</CardContent>
		</Card>
	)
}

function RunDetailPanel({
	row,
	run,
	isLoading,
	error,
	onClose,
}: {
	row: ComparisonRow
	run: EvaluationRun | undefined
	isLoading: boolean
	error: Error | null
	onClose: () => void
}) {
	const parameterSet = run?.parameterSets.find(
		(value) => value.parameterSet === row.parameterSet,
	)
	const checks =
		run?.checks.filter((check) => check.parameterSet === row.parameterSet) ?? []
	const interoperability =
		run?.interoperability.filter(
			(item) => item.parameterSet === row.parameterSet,
		) ?? []

	return (
		<Dialog.Root open onOpenChange={(open) => !open && onClose()}>
			<Dialog.Portal>
				<Dialog.Backdrop className="dialog-backdrop" />
				<Dialog.Popup className="detail-panel" aria-label="Evaluation details">
					<Dialog.Title className="sr-only">
						Selected evidence for {row.implementationName} {row.parameterSet}
					</Dialog.Title>
					<div className="detail-panel-header">
						<div>
							<div className="section-kicker">Selected evidence</div>
							<h3>{row.parameterSet}</h3>
							<p>{row.implementationName}</p>
						</div>
						<Dialog.Close
							render={
								<Button
									variant="ghost"
									size="icon-sm"
									aria-label="Close details"
								>
									<PanelRightClose className="size-4" />
								</Button>
							}
						/>
					</div>

					{isLoading && (
						<div className="detail-loading">
							<LoaderCircle className="size-4 animate-spin" /> Loading full
							result
						</div>
					)}
					{error && (
						<div className="detail-error">
							Could not load the full result: {error.message}
						</div>
					)}
					{parameterSet && (
						<div className="detail-panel-body">
							<div className="detail-note">
								<Info className="size-4 shrink-0" />
								<span>
									FIPS sizes are normative expectations. An expected private
									size does not mean this provider exposes private bytes.
								</span>
							</div>

							<DetailSection title="Checks">
								<div className="detail-list">
									{checks.map((check) => (
										<div className="detail-list-row" key={check.id}>
											<div>
												<strong>{formatLabel(check.id)}</strong>
												<span>{check.message}</span>
											</div>
											<StatusBadge status={check.status} />
										</div>
									))}
								</div>
							</DetailSection>

							<DetailSection title="Capabilities">
								<div className="detail-list">
									{parameterSet.capabilities.map((capability) => (
										<div
											className="capability-detail"
											key={capability.operation}
										>
											<div className="detail-list-row">
												<div>
													<strong>{formatLabel(capability.operation)}</strong>
													<span>{capability.evidence}</span>
													{capability.reason && <em>{capability.reason}</em>}
												</div>
												<StatusBadge status={capability.status} />
											</div>
											{capability.callSite && (
												<CodeEvidence callSite={capability.callSite} />
											)}
										</div>
									))}
								</div>
							</DetailSection>

							<DetailSection title="Key representations">
								<div className="detail-list">
									{parameterSet.representations.map((representation) => (
										<div
											className="representation-detail"
											key={representation.kind}
										>
											<div className="detail-list-row">
												<strong>
													{formatRepresentationKind(representation.kind)}
												</strong>
												<StatusBadge status={representation.status} />
											</div>
											<div className="representation-facts">
												<span>
													Observed size{" "}
													<strong>
														{representation.byteLength == null
															? "not exposed"
															: formatBytes(representation.byteLength)}
													</strong>
												</span>
												<span>
													Origin <strong>{representation.origin}</strong>
												</span>
												{representation.privateChoice && (
													<span>
														Private choice{" "}
														<strong>{representation.privateChoice}</strong>
													</span>
												)}
												{representation.algorithmOid && (
													<span>
														OID <strong>{representation.algorithmOid}</strong>
													</span>
												)}
												{representation.parametersAbsent !== null && (
													<span>
														Parameters{" "}
														<strong>
															{representation.parametersAbsent
																? "absent"
																: "present"}
														</strong>
													</span>
												)}
											</div>
											{representation.reason && <p>{representation.reason}</p>}
										</div>
									))}
								</div>
							</DetailSection>

							<DetailSection title="Interoperability">
								{interoperability.length === 0 ? (
									<div className="detail-not-evaluated">
										<CircleDashed className="size-4" />
										<span>
											Not evaluated yet. No producer/consumer interoperability
											records exist for this run.
										</span>
									</div>
								) : (
									<div className="detail-list">
										{interoperability.map((item) => (
											<div
												className="detail-list-row"
												key={`${item.producer}-${item.consumer}-${item.mode}`}
											>
												<div>
													<strong>
														{item.producer} to {item.consumer}
													</strong>
													<span>{item.message}</span>
												</div>
												<StatusBadge status={item.status} />
											</div>
										))}
									</div>
								)}
							</DetailSection>

							{run?.warnings.length ? (
								<DetailSection title="Warnings">
									<ul className="detail-warnings">
										{run.warnings.map((warning) => (
											<li key={warning}>{warning}</li>
										))}
									</ul>
								</DetailSection>
							) : null}
						</div>
					)}
				</Dialog.Popup>
			</Dialog.Portal>
		</Dialog.Root>
	)
}

function DetailSection({
	title,
	children,
}: {
	title: string
	children: React.ReactNode
}) {
	return (
		<section className="detail-section">
			<h4>{title}</h4>
			{children}
		</section>
	)
}

function CodeEvidence({ callSite }: { callSite: CallSiteDetail }) {
	const firstLine = callSite.lineNumber - callSite.highlightLine + 1
	const lines = callSite.snippet
		.replace(/\n$/, "")
		.split("\n")
		.map((line, index) => ({ line, number: firstLine + index }))
	const shortClassName =
		callSite.className.split(".").pop() ?? callSite.className
	return (
		<details className="code-evidence">
			<summary className="code-evidence-summary">
				<ChevronRight className="code-evidence-chevron size-3.5" />
				<span>
					Code evidence — {callSite.sourceFile}:{callSite.lineNumber} ·{" "}
					{shortClassName}.{callSite.methodName}()
				</span>
			</summary>
			<div className="code-evidence-body">
				{callSite.usageExample && (
					<section
						className="usage-example"
						aria-label="Copyable usage example"
					>
						<div className="usage-example-title">Copyable usage example</div>
						<pre className="usage-example-code">
							<code>{callSite.usageExample.trim()}</code>
						</pre>
					</section>
				)}
				<section
					className="code-block"
					aria-label={`Source around ${callSite.sourceFile}:${callSite.lineNumber}`}
				>
					{lines.map((entry) => (
						<div
							className={
								entry.number === callSite.lineNumber
									? "code-line code-line-highlight"
									: "code-line"
							}
							key={entry.number}
						>
							<span className="code-line-number">{entry.number}</span>
							<code>{entry.line || " "}</code>
						</div>
					))}
				</section>
				{callSite.arguments.length > 0 && (
					<div className="code-args">
						<div className="code-args-title">Arguments passed</div>
						{callSite.arguments.map((argument) => (
							<div className="code-arg" key={argument.name}>
								<span className="code-arg-name">{argument.name}</span>
								<span className="code-arg-type">{argument.type}</span>
								<span className="code-arg-value">{argument.value}</span>
							</div>
						))}
					</div>
				)}
			</div>
		</details>
	)
}

function StatusBadge({ status }: { status: string }) {
	const normalized = status.toLowerCase()
	const variant =
		normalized === "pass" || normalized === "supported"
			? "success"
			: normalized === "unsupported" || normalized === "skipped"
				? "warning"
				: normalized === "fail" || normalized === "error"
					? "danger"
					: "muted"
	return <Badge variant={variant}>{status}</Badge>
}

function formatRepresentationKind(kind: string) {
	return (
		{
			"raw-public": "Raw public",
			"raw-private-seed": "Raw seed",
			"raw-private-expanded": "Expanded private",
			spki: "SPKI",
			pkcs8: "PKCS#8",
		}[kind] ?? kind
	)
}

function formatLabel(value: string) {
	return value
		.replaceAll("-", " ")
		.replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function RunnerCard({
	runner,
	execution,
	isPending,
	onRun,
}: {
	runner: RunnerDescriptor
	execution?: RunnerExecution
	isPending: boolean
	onRun: () => void
}) {
	const ready = runner.executionSupported
	const executionActive = execution ? isActive(execution) : false
	return (
		<Card className="runner-card">
			<CardHeader>
				<div className="runner-card-title">
					<div>
						<CardTitle>{runner.displayName}</CardTitle>
						<CardDescription>{runner.implementationId}</CardDescription>
					</div>
					<RunnerAvailability runner={runner} />
				</div>
			</CardHeader>
			<CardContent>
				<div className="runner-facts">
					<span>
						Version <strong>{runner.version}</strong>
					</span>
					<span>
						Lineage <strong>{runner.engineLineageId}</strong>
					</span>
				</div>
				<div className="runner-parameters">
					{runner.parameterSets.map((value) => (
						<span key={value}>{value.replace("ML-DSA-", "")}</span>
					))}
				</div>
				{runner.reason && <p className="runner-reason">{runner.reason}</p>}
				<div className="runner-action-row">
					{execution && <ExecutionBadge execution={execution} />}
					<Button
						size="sm"
						className="ml-auto"
						disabled={!ready || isPending || executionActive}
						onClick={onRun}
					>
						{executionActive ? (
							<LoaderCircle className="size-3.5 animate-spin" />
						) : (
							<Play className="size-3.5" />
						)}
						{executionActive ? execution?.status.toLowerCase() : "Run"}
					</Button>
				</div>
			</CardContent>
		</Card>
	)
}

function RunnerCardSkeleton() {
	return (
		<Card className="runner-card runner-card-skeleton">
			<CardContent>
				<LoaderCircle className="size-5 animate-spin text-muted-foreground" />
			</CardContent>
		</Card>
	)
}

function RunnerAvailability({ runner }: { runner: RunnerDescriptor }) {
	if (runner.availability === "READY")
		return <Badge variant="success">Ready</Badge>
	if (runner.availability === "GATED")
		return <Badge variant="warning">Gated</Badge>
	return <Badge variant="muted">Build required</Badge>
}

function ExecutionBadge({ execution }: { execution: RunnerExecution }) {
	if (execution.status === "SUCCEEDED")
		return (
			<Badge variant="success">
				<CheckCircle2 className="mr-1 size-3" />
				Completed
			</Badge>
		)
	if (execution.status === "FAILED" || execution.status === "TIMED_OUT")
		return (
			<Badge variant="danger">
				<XCircle className="mr-1 size-3" />
				{execution.status.toLowerCase()}
			</Badge>
		)
	if (isActive(execution))
		return (
			<Badge variant="warning">
				<LoaderCircle className="mr-1 size-3 animate-spin" />
				{execution.status.toLowerCase()}
			</Badge>
		)
	return <Badge variant="muted">{execution.status.toLowerCase()}</Badge>
}

function AssuranceBadge({ status }: { status: string }) {
	const isPlatform = status.toLowerCase().includes("platform")
	return (
		<Badge variant={isPlatform ? "success" : "warning"}>
			{isPlatform ? "platform" : "third-party"}
		</Badge>
	)
}

function ResultSummary({
	passed,
	failed,
	unsupported,
	emptyLabel = "",
}: {
	passed: number
	failed: number
	unsupported: number
	emptyLabel?: string
}) {
	if (passed === 0 && failed === 0 && unsupported === 0 && emptyLabel)
		return <span className="text-xs text-muted-foreground">{emptyLabel}</span>
	return (
		<div className="result-summary">
			<span className="result-pass">{passed} pass</span>
			{failed > 0 && <span className="result-fail">{failed} fail</span>}
			{unsupported > 0 && (
				<span className="result-unsupported">{unsupported} unsupported</span>
			)}
		</div>
	)
}

function latestRows(rows: ComparisonRow[]) {
	const latest = new Map<string, ComparisonRow>()
	for (const row of rows) {
		const key = `${providerSurfaceKey(row)}:${row.parameterSet}`
		const current = latest.get(key)
		if (!current || row.generatedAt > current.generatedAt) latest.set(key, row)
	}
	return [...latest.values()]
}

function providerSurfaceKey(row: ComparisonRow) {
	return `${row.implementationId}:${row.implementationName}:${row.implementationVersion}`
}

function latestExecutionsByRunner(executions: RunnerExecution[]) {
	const latest = new Map<string, RunnerExecution>()
	for (const execution of executions) {
		const current = latest.get(execution.runnerId)
		if (!current || execution.submittedAt > current.submittedAt)
			latest.set(execution.runnerId, execution)
	}
	return latest
}

function isActive(execution: RunnerExecution) {
	return ["QUEUED", "STARTING", "RUNNING", "CANCELLING"].includes(
		execution.status,
	)
}

function formatBytes(value: number) {
	return `${value.toLocaleString()} B`
}

function formatDate(value: string) {
	return new Intl.DateTimeFormat(undefined, {
		dateStyle: "medium",
		timeStyle: "short",
	}).format(new Date(value))
}

function shortId(value: string) {
	return value.length > 20 ? `${value.slice(0, 8)}...${value.slice(-6)}` : value
}
