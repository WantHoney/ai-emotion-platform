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

interface AnalysisTaskStatusRaw {
  taskId: number
  status: Exclude<TaskStatus, 'CANCELED'>
  attempt_count: number
  next_run_at: string | null
  error_message: string | null
  created_at: string
  updated_at: string
  overall: AnalysisTaskOverallSummary | null
}

interface AnalysisTaskListRaw {
  id?: number
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

export interface AnalysisTask {
  id: number
  status: TaskStatus
  attemptCount?: number
  errorMessage?: string
  traceId?: string
  nextRunAt?: string
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
    status: raw.status,
    attemptCount: raw.attempt_count,
    errorMessage: raw.error_message ?? undefined,
    nextRunAt: raw.next_run_at ?? undefined,
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
  return http.get<AnalysisTaskResult>(`/api/analysis/task/${taskId}/result`)
}
