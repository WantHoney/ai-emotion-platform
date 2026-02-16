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

// 兼容旧页面：后端已切换为 analysis task 接口，任务列表接口可能未提供。
// 若服务端未暴露 /api/tasks，可在页面侧仅通过 taskId 查看详情。
export const getTaskList = (params: TaskListQuery) => {
  return http.get<PaginatedResponse<AnalysisTask>>('/api/tasks', { params })
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
