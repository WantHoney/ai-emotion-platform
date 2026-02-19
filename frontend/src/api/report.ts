import http from './http'

import type { PaginatedResponse } from './task'

export interface ReportSummary {
  id: number
  reportNo?: string
  taskId: number
  taskNo?: string
  overall?: string
  riskLevel?: string
  confidence?: number
  createdAt?: string
}

export interface ReportDetail extends ReportSummary {
  riskScore?: number
  adviceText?: string
  segments?: Array<{
    start: number
    end: number
    emotion: string
    confidence: number
  }>
}

export interface ReportListQuery {
  page?: number
  pageSize?: number
  keyword?: string
  riskLevel?: '' | 'low' | 'medium' | 'high'
  emotion?: string
  sortBy?: 'createdAt' | 'riskLevel' | 'overall'
  sortOrder?: 'asc' | 'desc'
}

export interface ReportTrendItem {
  date: string
  reportCount: number
  avgRiskScore: number
  lowCount: number
  mediumCount: number
  highCount: number
}

export interface ReportTrendResponse {
  items: ReportTrendItem[]
  total: number
  page: number
  pageSize: number
  days: number
}

interface ReportSummaryRaw {
  id?: number | string
  report_id?: number | string
  reportNo?: string
  report_no?: string
  taskId?: number | string
  task_id?: number | string
  taskNo?: string
  task_no?: string
  overall?: string
  overall_emotion_code?: string
  riskLevel?: string
  risk_level?: string
  risk?: {
    score?: number | string
    level?: string
  }
  confidence?: number | string
  overall_confidence?: number | string
  createdAt?: string
  created_at?: string
}

interface ReportDetailRaw extends ReportSummaryRaw {
  riskScore?: number | string
  risk_score?: number | string
  adviceText?: string
  advice_text?: string
  segments?: Array<{
    start?: number
    end?: number
    startMs?: number
    endMs?: number
    emotion?: string
    emotionCode?: string
    confidence?: number
  }>
}

const toNumber = (value: unknown) => {
  if (typeof value === 'number') return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isNaN(parsed) ? undefined : parsed
  }
  return undefined
}

const normalizeReportSummary = (raw: ReportSummaryRaw): ReportSummary => {
  const id = toNumber(raw.id ?? raw.report_id) ?? 0
  const taskId = toNumber(raw.taskId ?? raw.task_id) ?? 0

  return {
    id,
    reportNo: raw.reportNo ?? raw.report_no,
    taskId,
    taskNo: raw.taskNo ?? raw.task_no,
    overall: raw.overall ?? raw.overall_emotion_code,
    riskLevel: raw.riskLevel ?? raw.risk_level ?? raw.risk?.level,
    confidence: toNumber(raw.confidence ?? raw.overall_confidence),
    createdAt: raw.createdAt ?? raw.created_at,
  }
}

const normalizeReportDetail = (raw: ReportDetailRaw): ReportDetail => {
  const summary = normalizeReportSummary(raw)

  const segments = Array.isArray(raw.segments)
    ? raw.segments.map((segment) => ({
        start: segment.start ?? segment.startMs ?? 0,
        end: segment.end ?? segment.endMs ?? 0,
        emotion: segment.emotion ?? segment.emotionCode ?? '',
        confidence: segment.confidence ?? 0,
      }))
    : undefined

  return {
    ...summary,
    riskScore: toNumber(raw.riskScore ?? raw.risk_score ?? raw.risk?.score),
    adviceText: raw.adviceText ?? raw.advice_text,
    segments,
  }
}

const buildQueryParams = (params: ReportListQuery) => {
  const nextParams: Record<string, string | number> = {}

  if (params.page != null) nextParams.page = params.page
  if (params.pageSize != null) nextParams.pageSize = params.pageSize
  if (params.keyword?.trim()) nextParams.keyword = params.keyword.trim()
  if (params.riskLevel) nextParams.riskLevel = params.riskLevel
  if (params.emotion?.trim()) nextParams.emotion = params.emotion.trim()
  if (params.sortBy) nextParams.sortBy = params.sortBy
  if (params.sortOrder) nextParams.sortOrder = params.sortOrder

  return nextParams
}

const normalizeListResponse = (payload: unknown): PaginatedResponse<ReportSummary> => {
  const source = (payload ?? {}) as Record<string, unknown>
  const list = source.items ?? source.list ?? source.records

  const rows = Array.isArray(list) ? list.map((item) => normalizeReportSummary(item as ReportSummaryRaw)) : []

  const pageSize = toNumber(source.pageSize ?? source.size) ?? rows.length

  return {
    items: rows,
    total: toNumber(source.total ?? source.totalCount ?? source.count) ?? rows.length,
    page: toNumber(source.page ?? source.current) ?? 1,
    pageSize,
    size: pageSize,
  }
}

export const getReportList = async (params: ReportListQuery) => {
  const response = await http.get<PaginatedResponse<ReportSummaryRaw>>('/api/reports', {
    params: buildQueryParams(params),
  })

  return {
    ...response,
    data: normalizeListResponse(response.data),
  }
}

export const getReportDetail = async (reportId: number) => {
  const response = await http.get<ReportDetailRaw>(`/api/reports/${reportId}`)

  return {
    ...response,
    data: normalizeReportDetail(response.data),
  }
}

export const getReportTrend = async (days = 30) => {
  const response = await http.get<ReportTrendResponse>('/api/reports/trend', {
    params: { days },
  })
  return response.data
}
