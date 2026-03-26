import type { TaskStatus } from '@/api/task'

export const TRACE_ID_LABEL = '链路 ID（Trace ID）'
export const SLA_LABEL = '服务等级协议（SLA）'
export const PSI_LABEL = '心理风险指数（PSI）'
export const SER_LABEL = '语音情绪识别（SER）'
export const JSON_LABEL = 'JSON'
export const TEXT_NEG_LABEL = '文本负向值（text_neg）'

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  PENDING: '待处理',
  RUNNING: '处理中',
  RETRY_WAIT: '等待重试',
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELED: '已取消',
}

const RISK_LEVEL_LABELS = {
  low: '低风险',
  medium: '中风险',
  high: '高风险',
  normal: '低风险',
  attention: '中风险',
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  NORMAL: '低风险',
  ATTENTION: '中风险',
} as const

const WARNING_STATUS_LABELS = {
  NEW: '新建',
  ACKED: '已确认',
  FOLLOWING: '跟进中',
  RESOLVED: '已结案',
  CLOSED: '已关闭',
} as const

const MODEL_STATUS_LABELS = {
  ONLINE: '在线',
  OFFLINE: '离线',
  ARCHIVED: '已归档',
} as const

const MODEL_TYPE_LABELS = {
  ASR: '语音转写',
  AUDIO_EMOTION: '音频情绪',
  TEXT_SENTIMENT: '文本情绪',
  FUSION: '融合分析',
  SCORING: '综合评分',
} as const

const ENV_LABELS = {
  dev: '开发环境',
  staging: '预发环境',
  prod: '生产环境',
} as const

const SERVICE_STATUS_LABELS = {
  UP: '正常',
  DOWN: '离线',
  DEGRADED: '降级',
} as const

const WARNING_ACTION_LABELS = {
  MARK_FOLLOWED: '确认',
  ADD_NOTE: '跟进备注',
  RESOLVE: '结案',
  ACTION: '处置动作',
} as const

const EMOTION_LABELS = {
  HAPPY: '高兴',
  SAD: '悲伤',
  ANGRY: '愤怒',
  NEUTRAL: '平静',
  FEAR: '恐惧',
  DISGUST: '厌恶',
  SURPRISE: '惊讶',
} as const

export const formatTaskStatus = (value?: string) => {
  if (!value) return '-'
  return TASK_STATUS_LABELS[value as TaskStatus] ?? value
}

export const formatRiskLevel = (value?: string) => {
  if (!value) return '-'
  return RISK_LEVEL_LABELS[value as keyof typeof RISK_LEVEL_LABELS] ?? value
}

export const formatWarningStatus = (value?: string) => {
  if (!value) return '-'
  return WARNING_STATUS_LABELS[value as keyof typeof WARNING_STATUS_LABELS] ?? value
}

export const formatModelStatus = (value?: string) => {
  if (!value) return '-'
  return MODEL_STATUS_LABELS[value as keyof typeof MODEL_STATUS_LABELS] ?? value
}

export const formatModelType = (value?: string) => {
  if (!value) return '-'
  return MODEL_TYPE_LABELS[value as keyof typeof MODEL_TYPE_LABELS] ?? value
}

export const formatEnv = (value?: string) => {
  if (!value) return '-'
  return ENV_LABELS[value as keyof typeof ENV_LABELS] ?? value
}

export const formatServiceStatus = (value?: string) => {
  if (!value) return '-'
  return SERVICE_STATUS_LABELS[value as keyof typeof SERVICE_STATUS_LABELS] ?? value
}

export const formatWarningActionType = (value?: string) => {
  if (!value) return WARNING_ACTION_LABELS.ACTION
  return WARNING_ACTION_LABELS[value as keyof typeof WARNING_ACTION_LABELS] ?? value
}

export const formatEmotion = (value?: string) => {
  if (!value) return '-'
  const trimmed = value.trim()
  if (!trimmed) return '-'
  return EMOTION_LABELS[trimmed.toUpperCase() as keyof typeof EMOTION_LABELS] ?? trimmed
}

export const formatBooleanText = (value: boolean) => (value ? '是' : '否')

export const formatTaskSortBy = (value: 'createdAt' | 'updatedAt' | 'status') => {
  const labels = {
    createdAt: '创建时间',
    updatedAt: '更新时间',
    status: '状态',
  } as const

  return labels[value]
}

export const formatSortOrder = (value: 'asc' | 'desc') => {
  return value === 'asc' ? '升序' : '降序'
}
