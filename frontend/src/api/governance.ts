import http from './http'

export interface ModelRegistryItem {
  id: number
  model_code: string
  model_name: string
  model_type: string
  provider?: string
  version: string
  env: string
  status: string
  metrics_json?: unknown
  config_json?: unknown
  published_at?: string
  created_at?: string
  updated_at?: string
}

export interface ModelSwitchLogItem {
  id: number
  model_type: string
  env: string
  from_model_id?: number
  to_model_id: number
  switch_reason?: string
  switched_by?: number
  switched_at?: string
}

export interface WarningRuleItem {
  id: number
  rule_code: string
  rule_name: string
  description?: string
  enabled: number | boolean
  priority: number
  low_threshold: number
  medium_threshold: number
  high_threshold: number
  emotion_combo_json?: unknown
  trend_window_days: number
  trigger_count: number
  suggest_template_code?: string
  sla_low_minutes?: number
  sla_medium_minutes?: number
  sla_high_minutes?: number
  created_at?: string
  updated_at?: string
}

export interface WarningEventItem {
  id: number
  report_id?: number
  task_id?: number
  user_id?: number
  user_mask?: string
  risk_score: number
  risk_level: string
  top_emotion?: string
  status: string
  assigned_to?: number
  sla_deadline_at?: string
  first_acked_at?: string
  first_followed_at?: string
  breached?: number | boolean
  resolved_at?: string
  created_at?: string
}

export interface WarningActionItem {
  id: number
  warning_event_id: number
  action_type: string
  action_note?: string
  template_code?: string
  operator_id?: number
  created_at?: string
}

export interface PaginatedResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface DailyAnalyticsItem {
  stat_date: string
  dau: number
  upload_count: number
  report_count: number
  warning_count: number
}

export interface GovernanceSummary {
  ruleCount: number
  enabledRuleCount: number
  warningCount: number
}

export interface EmotionDriftItem {
  emotion: string
  currentCount: number
  baselineCount: number
  currentRatio: number
  baselineRatio: number
  drift: number
}

export interface ErrorCategoryItem {
  category: string
  count: number
}

export interface ErrorSampleItem {
  id: number
  audio_file_id?: number
  error_message?: string
  updated_at?: string
}

export interface SlaTrendItem {
  stat_date: string
  total: number
  resolved: number
  breached: number
}

export interface AnalyticsQualityResponse {
  windowDays: number
  baselineDays: number
  emotionDrift: EmotionDriftItem[]
  errorCategoryStats: ErrorCategoryItem[]
  errorSamples: ErrorSampleItem[]
  slaOverview: {
    total?: number
    resolved?: number
    breached?: number
    acked?: number
    avg_ack_minutes?: number
    avg_resolve_minutes?: number
  }
  slaTrend: SlaTrendItem[]
}

export const getAdminModels = async (params?: { modelType?: string; env?: string; status?: string }) => {
  const response = await http.get<ModelRegistryItem[]>('/api/admin/models', { params })
  return response.data
}

export const createAdminModel = async (payload: {
  modelCode: string
  modelName: string
  modelType: string
  provider?: string
  version: string
  env?: string
  status?: string
  metrics?: Record<string, unknown>
  config?: Record<string, unknown>
}) => {
  const response = await http.post<{ id: number }>('/api/admin/models', payload)
  return response.data
}

export const switchAdminModel = async (modelId: number, reason?: string) => {
  const response = await http.post(`/api/admin/models/${modelId}/switch`, { reason })
  return response.data
}

export const getAdminModelSwitchLogs = async (params?: {
  modelType?: string
  env?: string
  limit?: number
}) => {
  const response = await http.get<ModelSwitchLogItem[]>('/api/admin/models/switch-logs', { params })
  return response.data
}

export const getWarningRules = async () => {
  const response = await http.get<WarningRuleItem[]>('/api/admin/warning-rules')
  return response.data
}

export const createWarningRule = async (payload: {
  ruleCode: string
  ruleName: string
  description?: string
  enabled: boolean
  priority: number
  lowThreshold: number
  mediumThreshold: number
  highThreshold: number
  emotionCombo?: Record<string, unknown>
  trendWindowDays: number
  triggerCount: number
  suggestTemplateCode?: string
  slaLowMinutes?: number
  slaMediumMinutes?: number
  slaHighMinutes?: number
}) => {
  const response = await http.post<{ id: number }>('/api/admin/warning-rules', payload)
  return response.data
}

export const updateWarningRule = async (
  ruleId: number,
  payload: {
    ruleName: string
    description?: string
    enabled: boolean
    priority: number
    lowThreshold: number
    mediumThreshold: number
    highThreshold: number
    emotionCombo?: Record<string, unknown>
    trendWindowDays: number
    triggerCount: number
    suggestTemplateCode?: string
    slaLowMinutes?: number
    slaMediumMinutes?: number
    slaHighMinutes?: number
  },
) => {
  const response = await http.put(`/api/admin/warning-rules/${ruleId}`, payload)
  return response.data
}

export const toggleWarningRule = async (ruleId: number, enabled: boolean) => {
  const response = await http.post(`/api/admin/warning-rules/${ruleId}/toggle`, null, {
    params: { enabled },
  })
  return response.data
}

export const getWarnings = async (params: {
  page: number
  pageSize: number
  status?: string
  riskLevel?: string
}) => {
  const response = await http.get<PaginatedResponse<WarningEventItem>>('/api/admin/warnings', {
    params,
  })
  return response.data
}

export const postWarningAction = async (
  warningId: number,
  payload: {
    actionType: string
    actionNote?: string
    templateCode?: string
    nextStatus?: string
  },
) => {
  const response = await http.post(`/api/admin/warnings/${warningId}/actions`, payload)
  return response.data
}

export const getWarningActions = async (warningId: number) => {
  const response = await http.get<WarningActionItem[]>(`/api/admin/warnings/${warningId}/actions`)
  return response.data
}

export const getDailyAnalytics = async (days = 14) => {
  const response = await http.get<{ items: DailyAnalyticsItem[]; days: number }>('/api/admin/analytics/daily', {
    params: { days },
  })
  return response.data
}

export const getAnalyticsQuality = async (params?: { windowDays?: number; baselineDays?: number }) => {
  const response = await http.get<AnalyticsQualityResponse>('/api/admin/analytics/quality', { params })
  return response.data
}

export const getGovernanceSummary = async () => {
  const response = await http.get<GovernanceSummary>('/api/admin/governance/summary')
  return response.data
}
