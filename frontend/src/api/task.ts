import http from './http'

export type TaskStatus = 'PENDING' | 'RUNNING' | 'RETRY_WAIT' | 'SUCCESS' | 'FAILED' | 'CANCELED'

export interface RiskAssessmentPayload {
  risk_score: number
  risk_level: string
  advice_text: string
  p_sad: number
  p_angry: number
  var_conf: number
  text_neg: number
}

export interface AnalysisTaskOverallSummary {
  overall_emotion_code: string
  overall_confidence: number
  duration_ms: number
  sample_rate: number
  model_name: string
  risk_assessment: RiskAssessmentPayload
}

export interface SegmentEmotion {
  start: number
  end: number
  emotion: string
  confidence: number
}

export interface AnalysisTaskResult {
  overall?: string
  confidence?: number
  risk_score?: number
  risk_level?: string
  advice_text?: string
  segments?: SegmentEmotion[]
}

export interface AnalysisTaskResultDetail {
  taskId: number
  overallEmotionCode?: string
  overallConfidence?: number
  riskAssessment?: RiskAssessmentPayload
  rawJson?: string
  transcript?: string
  segments: SegmentEmotion[]
}

export interface TaskRealtimeRiskSummary {
  riskScore: number
  riskLevel: string
  pSad: number
  pAngry: number
  varConf: number
  textNeg: number
}

export interface TaskRealtimeProgressSummary {
  phase: string
  message: string
  sequence: number
  emittedAtMs: number
  details?: Record<string, unknown>
}

export interface TaskRealtimeCurvePoint {
  index: number
  startMs: number
  endMs: number
  emotion: string
  confidence: number
  riskIndex: number
}

export interface TaskRealtimeSnapshot {
  event: 'snapshot'
  taskId: number
  taskNo?: string
  status: TaskStatus
  attemptCount: number
  maxAttempts?: number
  traceId?: string
  nextRunAt?: string
  updatedAt?: string
  errorMessage?: string
  terminal: boolean
  risk?: TaskRealtimeRiskSummary | null
  progress?: TaskRealtimeProgressSummary | null
  curve?: TaskRealtimeCurvePoint[] | null
}

interface AnalysisTaskStatusRaw {
  taskId: number
  taskNo?: string
  task_no?: string
  status: Exclude<TaskStatus, 'CANCELED'>
  attempt_count: number
  max_attempts?: number | null
  trace_id?: string | null
  next_run_at: string | null
  error_message: string | null
  started_at?: string | null
  finished_at?: string | null
  created_at: string
  updated_at: string
  overall: AnalysisTaskOverallSummary | null
}

interface AnalysisTaskListRaw {
  id?: number
  taskNo?: string
  task_no?: string
  status?: TaskStatus
  attemptCount?: number
  attempt_count?: number
  errorMessage?: string | null
  error_message?: string | null
  traceId?: string
  trace_id?: string
  nextRunAt?: string | null
  next_run_at?: string | null
  durationMs?: number | null
  duration_ms?: number | null
  serLatencyMs?: number | null
  ser_latency_ms?: number | null
  createdAt?: string
  created_at?: string
  updatedAt?: string
  updated_at?: string
}

interface AnalysisTaskResultRaw {
  analysis_result?: {
    task_id?: number
    overall_emotion_code?: string
    overall_confidence?: number
    risk_assessment?: RiskAssessmentPayload
    raw_json?: string
    transcript?: string
  }
  analysis_segment?: Array<{
    start_ms?: number
    end_ms?: number
    emotion_code?: string
    confidence?: number
  }>
}

export interface AnalysisTask {
  id: number
  taskNo?: string
  status: TaskStatus
  attemptCount?: number
  maxAttempts?: number
  errorMessage?: string
  traceId?: string
  nextRunAt?: string
  startedAt?: string
  finishedAt?: string
  durationMs?: number
  serLatencyMs?: number
  result?: AnalysisTaskResult
  createdAt?: string
  updatedAt?: string
}

export interface TaskListQuery {
  page?: number
  pageSize?: number
  status?: TaskStatus | ''
  keyword?: string
  sortBy?: 'createdAt' | 'updatedAt' | 'status'
  sortOrder?: 'asc' | 'desc'
}

export interface PaginatedResponse<T> {
  items?: T[]
  list?: T[]
  total: number
  page: number
  size?: number
  pageSize?: number
}

const normalizeTask = (raw: AnalysisTaskStatusRaw): AnalysisTask => {
  const riskAssessment = raw.overall?.risk_assessment

  return {
    id: raw.taskId,
    taskNo: raw.taskNo ?? raw.task_no ?? undefined,
    status: raw.status,
    attemptCount: raw.attempt_count,
    maxAttempts: raw.max_attempts ?? undefined,
    errorMessage: raw.error_message ?? undefined,
    traceId: raw.trace_id ?? undefined,
    nextRunAt: raw.next_run_at ?? undefined,
    startedAt: raw.started_at ?? undefined,
    finishedAt: raw.finished_at ?? undefined,
    createdAt: raw.created_at,
    updatedAt: raw.updated_at,
    durationMs: raw.overall?.duration_ms,
    result: raw.overall
      ? {
          overall: raw.overall.overall_emotion_code,
          confidence: raw.overall.overall_confidence,
          risk_score: riskAssessment?.risk_score,
          risk_level: riskAssessment?.risk_level,
          advice_text: riskAssessment?.advice_text,
        }
      : undefined,
  }
}

const normalizeTaskListItem = (raw: AnalysisTaskListRaw): AnalysisTask => {
  return {
    id: Number(raw.id ?? 0),
    taskNo: raw.taskNo ?? raw.task_no ?? undefined,
    status: (raw.status ?? 'PENDING') as TaskStatus,
    attemptCount: raw.attemptCount ?? raw.attempt_count ?? undefined,
    errorMessage: raw.errorMessage ?? raw.error_message ?? undefined,
    traceId: raw.traceId ?? raw.trace_id ?? undefined,
    nextRunAt: raw.nextRunAt ?? raw.next_run_at ?? undefined,
    durationMs: raw.durationMs ?? raw.duration_ms ?? undefined,
    serLatencyMs: raw.serLatencyMs ?? raw.ser_latency_ms ?? undefined,
    createdAt: raw.createdAt ?? raw.created_at ?? undefined,
    updatedAt: raw.updatedAt ?? raw.updated_at ?? undefined,
  }
}

const normalizeTaskListResponse = (payload: unknown): PaginatedResponse<AnalysisTask> => {
  const source = (payload ?? {}) as Record<string, unknown>
  const list = source.items ?? source.list ?? source.records
  const rows = Array.isArray(list)
    ? list.map((item) => normalizeTaskListItem(item as AnalysisTaskListRaw))
    : []

  const page = typeof source.page === 'number' ? source.page : 1
  const pageSize =
    typeof source.pageSize === 'number'
      ? source.pageSize
      : typeof source.size === 'number'
        ? source.size
        : rows.length

  return {
    items: rows,
    total: typeof source.total === 'number' ? source.total : rows.length,
    page,
    pageSize,
    size: pageSize,
  }
}

const buildTaskListParams = (params: TaskListQuery) => {
  const nextParams: Record<string, string | number> = {}
  if (params.page != null) nextParams.page = params.page
  if (params.pageSize != null) nextParams.pageSize = params.pageSize
  if (params.status) nextParams.status = params.status
  if (params.keyword?.trim()) nextParams.keyword = params.keyword.trim()
  if (params.sortBy) nextParams.sortBy = params.sortBy
  if (params.sortOrder) nextParams.sortOrder = params.sortOrder
  return nextParams
}

const normalizeTaskResult = (payload: AnalysisTaskResultRaw): AnalysisTaskResultDetail => {
  const result = payload.analysis_result
  const segments: SegmentEmotion[] = Array.isArray(payload.analysis_segment)
    ? payload.analysis_segment.map((it) => ({
        start: Number(it.start_ms ?? 0),
        end: Number(it.end_ms ?? 0),
        emotion: String(it.emotion_code ?? ''),
        confidence: Number(it.confidence ?? 0),
      }))
    : []

  return {
    taskId: Number(result?.task_id ?? 0),
    overallEmotionCode: result?.overall_emotion_code,
    overallConfidence: result?.overall_confidence,
    riskAssessment: result?.risk_assessment,
    rawJson: result?.raw_json,
    transcript: result?.transcript,
    segments,
  }
}

export const getTaskList = async (params: TaskListQuery) => {
  const response = await http.get<PaginatedResponse<AnalysisTaskListRaw>>('/api/tasks', {
    params: buildTaskListParams(params),
  })

  return {
    ...response,
    data: normalizeTaskListResponse(response.data),
  }
}

export const getTask = async (taskId: number) => {
  const response = await http.get<AnalysisTaskStatusRaw>(`/api/analysis/task/${taskId}`)

  return {
    ...response,
    data: normalizeTask(response.data),
  }
}

export const getResult = (taskId: number) => {
  return http
    .get<AnalysisTaskResultRaw>(`/api/analysis/task/${taskId}/result`)
    .then((response) => ({
      ...response,
      data: normalizeTaskResult(response.data),
    }))
}
