import axios, { AxiosError, AxiosInstance } from 'axios'
import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * API 基础错误结构（与后端 GlobalExceptionHandler 对齐）
 */
export interface ApiErrorBody {
  code: string
  message: string
  traceId?: string
  timestamp?: string
  path?: string
  details?: Record<string, unknown>
}

/**
 * ---------- Audio ----------
 */
export interface AudioUploadResponse {
  audioId: number
  originalName: string
  fileName: string
  downloadUrl: string
}

export interface AudioListItem {
  id: number
  userId: number | null
  originalName: string
  storedName: string
  url: string
  sizeBytes: number | null
  durationMs: number | null
  status: string
  createdAt: string | null
}

export interface AudioListResponse {
  total: number
  page: number
  size: number
  items: AudioListItem[]
}

export interface AudioDeleteResponse {
  audioId: number
  status: string
}

/**
 * ---------- Task ----------
 */
export interface AnalysisTaskStartResponse {
  taskId: number
  status: string
}

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

export interface AnalysisTaskStatusResponse {
  taskId: number
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'RETRY_WAIT'
  attempt_count: number
  next_run_at: string | null
  error_message: string | null
  created_at: string
  updated_at: string
  overall: AnalysisTaskOverallSummary | null
}

export interface AnalysisSegmentPayload {
  id: number
  task_id: number
  start_ms: number
  end_ms: number
  emotion_code: string
  confidence: number
  created_at: string
}

export interface AnalysisResultPayload {
  id: number
  task_id: number
  model_name: string
  overall_emotion_code: string
  overall_confidence: number
  duration_ms: number
  sample_rate: number
  raw_json: string
  transcript: string | null
  risk_assessment: RiskAssessmentPayload
  created_at: string
}

export interface AnalysisTaskResultResponse {
  analysis_result: AnalysisResultPayload
  analysis_segment: AnalysisSegmentPayload[]
  segments_total: number
  segments_truncated: boolean
}

export interface AnalysisSegmentsResponse {
  taskId: number
  fromMs: number
  toMs: number
  limit: number
  offset: number
  total: number
  items: AnalysisSegmentPayload[]
}

/**
 * ---------- Analysis / Report ----------
 */
export interface AudioAnalysisDetailResponse {
  id: number
  audioId: number
  modelName: string
  modelVersion: string
  status: string
  summary: unknown
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AudioAnalysisRunResponse {
  analysisId: number
  status: string
}

export interface AudioAnalysisReportEmotion {
  emotionId: number
  code: string
  nameZh: string
  scheme: string
  score: number
}

export interface AudioAnalysisReportSegment {
  segmentId: number
  startMs: number
  endMs: number
  transcript: string | null
  emotions: AudioAnalysisReportEmotion[]
}

export interface AudioAnalysisReportOverall {
  emotionCode: string
  emotionNameZh: string
  confidence: number
}

export interface AudioAnalysisReportResponse {
  analysisId: number
  audioId: number
  modelName: string
  modelVersion: string
  status: string
  summary: unknown
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
  overall: AudioAnalysisReportOverall | null
  segments: AudioAnalysisReportSegment[]
}

/**
 * ---------- Axios 基础封装 ----------
 */
export class ApiClientError extends Error {
  status?: number
  traceId?: string
  code?: string
  details?: Record<string, unknown>

  constructor(message: string, init?: Partial<ApiClientError>) {
    super(message)
    this.name = 'ApiClientError'
    Object.assign(this, init)
  }
}

export interface CreateApiClientOptions {
  baseURL: string
  getAccessToken?: () => string | null
  onUnauthorized?: () => void
}

export function createApiClient(options: CreateApiClientOptions): AxiosInstance {
  const client = axios.create({
    baseURL: options.baseURL,
    timeout: 30000,
  })

  client.interceptors.request.use((config) => {
    const token = options.getAccessToken?.()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  client.interceptors.response.use(
    (response) => response,
    (error: AxiosError<ApiErrorBody>) => {
      const status = error.response?.status
      const body = error.response?.data

      if (status === 401) {
        options.onUnauthorized?.()
      }

      throw new ApiClientError(body?.message || error.message, {
        status,
        traceId: body?.traceId,
        code: body?.code,
        details: body?.details,
      })
    },
  )

  return client
}

/**
 * ---------- 按模块组织 API ----------
 */
export function createBackendApi(client: AxiosInstance) {
  return {
    audio: {
      upload: async (file: File) => {
        const formData = new FormData()
        formData.append('file', file)
        const { data } = await client.post<AudioUploadResponse>('/api/audio/upload', formData)
        return data
      },
      list: async (params?: {
        page?: number
        size?: number
        userId?: number
        onlyUploaded?: boolean
      }) => {
        const { data } = await client.get<AudioListResponse>('/api/audio/list', { params })
        return data
      },
      delete: async (audioId: number) => {
        const { data } = await client.delete<AudioDeleteResponse>(`/api/audio/${audioId}`)
        return data
      },
    },

    task: {
      start: async (audioId: number) => {
        const { data } = await client.post<AnalysisTaskStartResponse>(
          `/api/audio/${audioId}/analysis/start`,
        )
        return data
      },
      status: async (taskId: number) => {
        const { data } = await client.get<AnalysisTaskStatusResponse>(
          `/api/analysis/task/${taskId}`,
        )
        return data
      },
      result: async (taskId: number) => {
        const { data } = await client.get<AnalysisTaskResultResponse>(
          `/api/analysis/task/${taskId}/result`,
        )
        return data
      },
      segments: async (
        taskId: number,
        params?: { fromMs?: number; toMs?: number; limit?: number; offset?: number },
      ) => {
        const { data } = await client.get<AnalysisSegmentsResponse>(
          `/api/analysis/task/${taskId}/segments`,
          { params },
        )
        return data
      },
    },

    analysis: {
      detail: async (analysisId: number) => {
        const { data } = await client.get<AudioAnalysisDetailResponse>(
          `/api/analysis/${analysisId}`,
        )
        return data
      },
      report: async (analysisId: number) => {
        const { data } = await client.get<AudioAnalysisReportResponse>(
          `/api/analysis/${analysisId}/report`,
        )
        return data
      },
      run: async (analysisId: number) => {
        const { data } = await client.post<AudioAnalysisReportResponse>(
          `/api/analysis/${analysisId}/run`,
        )
        return data
      },
      runAsync: async (analysisId: number) => {
        const { data } = await client.post<AudioAnalysisRunResponse>(
          `/api/analysis/${analysisId}/run-async`,
        )
        return data
      },
    },
  }
}

/**
 * ---------- React 轮询 Hook ----------
 * 用法：
 * const { data, loading, error, stop } = useTaskPolling({ api, taskId, onSuccess: ... });
 */
export interface UseTaskPollingOptions {
  api: ReturnType<typeof createBackendApi>
  taskId: number | null
  intervalMs?: number
  autoStart?: boolean
  onSuccess?: (task: AnalysisTaskStatusResponse) => void
  onFailed?: (task: AnalysisTaskStatusResponse) => void
}

export function useTaskPolling(options: UseTaskPollingOptions) {
  const { api, taskId, intervalMs = 2000, autoStart = true, onSuccess, onFailed } = options

  const timerRef = useRef<number | null>(null)
  const [data, setData] = useState<AnalysisTaskStatusResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ApiClientError | null>(null)
  const [running, setRunning] = useState(autoStart)

  const stop = useCallback(() => {
    setRunning(false)
    if (timerRef.current) {
      window.clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const tick = useCallback(async () => {
    if (!taskId || !running) return

    setLoading(true)
    setError(null)
    try {
      const status = await api.task.status(taskId)
      setData(status)

      if (status.status === 'SUCCESS') {
        onSuccess?.(status)
        stop()
        return
      }

      if (status.status === 'FAILED') {
        onFailed?.(status)
        stop()
        return
      }

      timerRef.current = window.setTimeout(tick, intervalMs)
    } catch (e) {
      setError(e as ApiClientError)
      timerRef.current = window.setTimeout(tick, intervalMs)
    } finally {
      setLoading(false)
    }
  }, [api.task, taskId, running, intervalMs, onSuccess, onFailed, stop])

  useEffect(() => {
    if (!running || !taskId) return
    void tick()
    return stop
  }, [running, taskId, tick, stop])

  return {
    data,
    loading,
    error,
    running,
    start: () => setRunning(true),
    stop,
  }
}
