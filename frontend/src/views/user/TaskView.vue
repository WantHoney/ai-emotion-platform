<script setup lang="ts">
import { DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getResult,
  type AnalysisTaskResultDetail,
  type RiskAssessmentPayload,
  type TaskStatus,
} from '@/api/task'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { useTaskPolling } from '@/composables/useTaskPolling'
import { useTaskRealtimeStream } from '@/composables/useTaskRealtimeStream'
import {
  buildNarrativeSourceNote,
  buildNarrativeTechNote,
  parseNarrativeFromRawJson,
  splitAdviceText,
} from '@/utils/analysisNarrative'
import {
  PSI_LABEL,
  SER_LABEL,
  TEXT_NEG_LABEL,
  TRACE_ID_LABEL,
  formatEmotion,
  formatRiskLevel,
  formatTaskStatus,
} from '@/utils/uiText'

type ProgressPhasePresentation = {
  label: string
  defaultMessage: string
  floor: number
  ceiling: number
}

const route = useRoute()
const router = useRouter()
const taskId = computed(() => Number(route.params.id))

const {
  pollingState,
  task,
  errorMessage,
  statusText: pollStatusText,
  start,
} = useTaskPolling(taskId, {
  baseIntervalMs: 3000,
  maxIntervalMs: 15000,
  timeoutMs: 1800000,
  maxRetry: 8,
})

const realtime = useTaskRealtimeStream(taskId)
const streamSnapshot = computed(() => realtime.snapshot.value)
const streamState = computed(() => realtime.state.value)
const streamError = computed(() => realtime.errorMessage.value)

const taskResult = ref<AnalysisTaskResultDetail | null>(null)
const resultLoading = ref(false)
const nowMs = ref(Date.now())

let nowTimer: number | null = null

const WEIGHT_SAD = 0.45
const WEIGHT_ANGRY = 0.22
const WEIGHT_HAPPY_OFFSET = 0.28
const WEIGHT_NEUTRAL_OFFSET = 0.08
const WEIGHT_VAR_CONF = 0.08
const WEIGHT_VOICE_IN_PSI = 0.65
const WEIGHT_TEXT_IN_PSI = 0.35
const TEXT_NEG_CONFLICT_DISCOUNT = 0.75
const TEXT_NEG_CONFLICT_MIN_HAPPY = 0.35
const TEXT_NEG_CONFLICT_MIN_TEXT_NEG = 0.5
const TEXT_NEG_CONFLICT_MAX_SAD = 0.35

const STREAM_STATE_LABELS: Record<string, string> = {
  idle: '实时通道未连接',
  connecting: '实时通道连接中',
  open: '实时通道已连接',
  terminal: '实时同步已完成',
  closed: '当前使用轮询兜底',
  error: '实时通道异常',
}

const PHASE_PRESENTATIONS = {
  CLAIMED: {
    label: '任务已接入处理队列',
    defaultMessage: '服务端已开始处理当前音频，正在准备执行分析。',
    floor: 8,
    ceiling: 18,
  },
  ASR_RUNNING: {
    label: '语音转写中',
    defaultMessage: '正在将语音内容转成文本，便于后续情绪和风险分析。',
    floor: 20,
    ceiling: 36,
  },
  ASR_DONE: {
    label: '语音转写完成',
    defaultMessage: '语音文本已经就绪，准备进入文本情感分析。',
    floor: 38,
    ceiling: 48,
  },
  ASR_FAILED: {
    label: '语音转写降级中',
    defaultMessage: '语音转写失败，系统已切换为语音单模态分析继续执行。',
    floor: 38,
    ceiling: 48,
  },
  TEXT_RUNNING: {
    label: '文本情感分析中',
    defaultMessage: '正在分析文本的负向程度与情绪倾向。',
    floor: 50,
    ceiling: 64,
  },
  TEXT_DONE: {
    label: '文本情感分析完成',
    defaultMessage: '文本特征已经提取完成，准备进入语音情绪识别。',
    floor: 66,
    ceiling: 74,
  },
  TEXT_FALLBACK: {
    label: '文本分析回退中',
    defaultMessage: '文本模型暂不可用，系统已自动回退到词典结果。',
    floor: 66,
    ceiling: 74,
  },
  SER_RUNNING: {
    label: '语音情绪识别中',
    defaultMessage: '正在执行语音情绪识别、融合计算和风险评分。',
    floor: 76,
    ceiling: 88,
  },
  NARRATIVE_RUNNING: {
    label: '解释与建议生成中',
    defaultMessage: '正在整理个性化解释与建议文本。',
    floor: 88,
    ceiling: 94,
  },
  NARRATIVE_DONE: {
    label: '解释与建议已生成',
    defaultMessage: '个性化解释已准备完成，正在等待写入最终结果。',
    floor: 94,
    ceiling: 97,
  },
  PERSISTING: {
    label: '正在写入最终结果',
    defaultMessage: '模型推理已完成，正在保存结果并生成可查看页面。',
    floor: 97,
    ceiling: 99,
  },
  DONE: {
    label: '任务处理完成',
    defaultMessage: '分析已经完成，正在同步详情结果。',
    floor: 100,
    ceiling: 100,
  },
  FAILED: {
    label: '任务执行失败',
    defaultMessage: '任务处理未成功完成，系统将根据配置决定重试或终止。',
    floor: 100,
    ceiling: 100,
  },
} satisfies Record<string, ProgressPhasePresentation>

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value))

const inferNeutralProbability = (source: RiskAssessmentPayload) =>
  clamp(1 - source.p_sad - source.p_angry - source.p_happy, 0, 1)

const adjustPsiTextNeg = (source: RiskAssessmentPayload) => {
  if (
    source.p_happy >= TEXT_NEG_CONFLICT_MIN_HAPPY &&
    source.text_neg >= TEXT_NEG_CONFLICT_MIN_TEXT_NEG &&
    source.p_sad <= TEXT_NEG_CONFLICT_MAX_SAD
  ) {
    return clamp(source.text_neg * TEXT_NEG_CONFLICT_DISCOUNT, 0, 1)
  }
  return clamp(source.text_neg, 0, 1)
}

const toNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

const parseTimestamp = (value?: string | null) => {
  if (!value) return undefined
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : undefined
}

const formatDurationMetric = (ms: number) => {
  const safeMs = Math.max(0, ms)
  const seconds = safeMs / 1000
  if (seconds < 10) return `${seconds.toFixed(1)} 秒`
  const roundedSeconds = Math.round(seconds)
  if (roundedSeconds < 60) return `${roundedSeconds} 秒`
  const minutes = Math.floor(roundedSeconds / 60)
  const remainSeconds = roundedSeconds % 60
  return `${minutes} 分 ${remainSeconds.toString().padStart(2, '0')} 秒`
}

const formatPhaseTime = (ms?: number) => {
  if (!ms || Number.isNaN(ms)) return '-'
  return new Date(ms).toLocaleTimeString('zh-CN', { hour12: false })
}

const formatRetryCountdown = (ms: number) => {
  const safeMs = Math.max(0, ms)
  const totalSeconds = Math.ceil(safeMs / 1000)
  if (totalSeconds <= 1) return '即将重试'
  if (totalSeconds < 60) return `${totalSeconds} 秒后重试`
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes} 分 ${seconds.toString().padStart(2, '0')} 秒后重试`
}

const localizeProgressMessage = (message?: string) => {
  if (!message) return ''
  const normalized = message.trim()
  if (!normalized) return ''
  const localizedMap: Record<string, string> = {
    'Generating local narrative': '正在生成本地解释与建议文本。',
    'Local narrative ready': '解释与建议文本已生成完成。',
    'Using fallback narrative': '解释与建议已切换为兜底文案。',
  }
  return localizedMap[normalized] ?? normalized
}

const extractFusionConfidence = (rawJson?: string | null): number | undefined => {
  if (!rawJson) return undefined
  try {
    const root = JSON.parse(rawJson) as Record<string, unknown>
    const serNode = (root.ser ?? null) as Record<string, unknown> | null
    const fusionNode = (serNode?.fusion ?? null) as Record<string, unknown> | null
    const enabled = Boolean(fusionNode?.enabled)
    const ready = Boolean(fusionNode?.ready)
    const confidence = toNumber(fusionNode?.confidence)
    if (enabled && ready && confidence != null) return confidence
  } catch {
    return undefined
  }
  return undefined
}

const latestStatus = computed<TaskStatus>(
  () => (streamSnapshot.value?.status ?? task.value?.status ?? 'PENDING') as TaskStatus,
)
const latestStatusText = computed(() => formatTaskStatus(latestStatus.value))
const displayTaskNo = computed(
  () => streamSnapshot.value?.taskNo || task.value?.taskNo || `任务-${taskId.value}`,
)
const latestAttemptCount = computed(() =>
  Number(streamSnapshot.value?.attemptCount ?? task.value?.attemptCount ?? 0),
)
const latestTraceId = computed(() => streamSnapshot.value?.traceId ?? task.value?.traceId)
const latestNextRunAt = computed(() => streamSnapshot.value?.nextRunAt ?? task.value?.nextRunAt)
const latestErrorDetail = computed(
  () => streamSnapshot.value?.errorMessage ?? task.value?.errorMessage ?? errorMessage.value,
)
const progressSummary = computed(() => streamSnapshot.value?.progress ?? null)
const streamLabel = computed(() => STREAM_STATE_LABELS[streamState.value] ?? streamState.value)
const usesRealtimeChannel = computed(
  () => streamState.value === 'open' || streamState.value === 'terminal',
)

const fetchTaskResult = async () => {
  if (!Number.isFinite(taskId.value) || taskId.value <= 0) return
  resultLoading.value = true
  try {
    const response = await getResult(taskId.value)
    taskResult.value = response.data
  } catch {
    taskResult.value = null
  } finally {
    resultLoading.value = false
  }
}

watch(
  taskId,
  () => {
    taskResult.value = null
  },
  { immediate: true },
)

watch(
  () => [taskId.value, latestStatus.value] as const,
  async ([id, status]) => {
    if (!Number.isFinite(id) || id <= 0) {
      taskResult.value = null
      return
    }
    if (status === 'SUCCESS') {
      await fetchTaskResult()
      return
    }
    if (status === 'FAILED' || status === 'CANCELED') {
      taskResult.value = null
    }
  },
)

const riskAssessment = computed<RiskAssessmentPayload | null>(
  () => taskResult.value?.riskAssessment ?? null,
)
const narrative = computed(() => parseNarrativeFromRawJson(taskResult.value?.rawJson))

const overallEmotionDisplay = computed(() => {
  const value = task.value?.result?.overall
  if (value) return formatEmotion(value)
  if (latestStatus.value === 'SUCCESS' && resultLoading.value) return '同步中'
  if (latestStatus.value === 'FAILED' || latestStatus.value === 'CANCELED') return '-'
  return '待生成'
})

const riskLevelDisplay = computed(() => {
  const value =
    riskAssessment.value?.risk_level ??
    task.value?.result?.risk_level ??
    streamSnapshot.value?.risk?.riskLevel
  if (value) return formatRiskLevel(value)
  if (latestStatus.value === 'SUCCESS' && resultLoading.value) return '同步中'
  if (latestStatus.value === 'FAILED' || latestStatus.value === 'CANCELED') return '-'
  return '待生成'
})

const confidencePercent = computed(() => {
  const fusionConfidence = extractFusionConfidence(taskResult.value?.rawJson)
  const confidence =
    fusionConfidence ?? taskResult.value?.overallConfidence ?? task.value?.result?.confidence
  if (confidence == null || Number.isNaN(confidence)) return 0
  const normalized = confidence <= 1 ? confidence * 100 : confidence
  return Math.round(clamp(normalized, 0, 100))
})

const riskScorePercent = computed(() => {
  const score = riskAssessment.value?.risk_score ?? task.value?.result?.risk_score ?? 0
  const normalized = score <= 1 ? score * 100 : score
  return Math.round(clamp(normalized, 0, 100))
})

const voiceRiskScore = computed(() => {
  const source = riskAssessment.value
  if (!source) return 0
  const pNeutral = inferNeutralProbability(source)
  return (
    100 *
    Math.max(
      0,
      WEIGHT_SAD * source.p_sad +
        WEIGHT_ANGRY * source.p_angry +
        WEIGHT_VAR_CONF * source.var_conf -
        WEIGHT_HAPPY_OFFSET * source.p_happy -
        WEIGHT_NEUTRAL_OFFSET * pNeutral,
    )
  )
})

const textRiskScore = computed(() => {
  const source = riskAssessment.value
  if (!source) return 0
  return 100 * adjustPsiTextNeg(source)
})

const riskLevel = computed(() =>
  formatRiskLevel(riskAssessment.value?.risk_level ?? task.value?.result?.risk_level),
)
const narrativeSourceNote = computed(() => buildNarrativeSourceNote(narrative.value))
const narrativeTechNote = computed(() => buildNarrativeTechNote(narrative.value))
const displayAdviceItems = computed(() =>
  narrative.value?.personalizedAdvice?.length
    ? narrative.value.personalizedAdvice
    : splitAdviceText(task.value?.result?.advice_text),
)
const displayAdviceText = computed(() =>
  displayAdviceItems.value.length
    ? displayAdviceItems.value.join('；')
    : (task.value?.result?.advice_text ?? '-'),
)

const confidenceColor = computed(() =>
  confidencePercent.value >= 80 ? '#67c23a' : confidencePercent.value >= 60 ? '#e6a23c' : '#f56c6c',
)
const riskColor = computed(() =>
  riskScorePercent.value >= 70 ? '#f56c6c' : riskScorePercent.value >= 40 ? '#e6a23c' : '#67c23a',
)

const currentPhasePresentation = computed<ProgressPhasePresentation>(() => {
  const phase = progressSummary.value?.phase?.trim()
  const normalizedPhase = phase ? phase.toUpperCase() : ''
  if (normalizedPhase && normalizedPhase in PHASE_PRESENTATIONS) {
    return PHASE_PRESENTATIONS[normalizedPhase as keyof typeof PHASE_PRESENTATIONS]
  }
  switch (latestStatus.value) {
    case 'SUCCESS':
      return PHASE_PRESENTATIONS.DONE
    case 'FAILED':
    case 'CANCELED':
      return PHASE_PRESENTATIONS.FAILED
    case 'RETRY_WAIT':
      return {
        label: '等待自动重试',
        defaultMessage: '系统正在等待下一次调度窗口，无需重复上传音频。',
        floor: 58,
        ceiling: 68,
      }
    case 'RUNNING':
      return {
        label: '分析处理中',
        defaultMessage: '系统正在依次执行转写、文本分析、语音识别和结果整理。',
        floor: 28,
        ceiling: 82,
      }
    default:
      return {
        label: '等待处理',
        defaultMessage: '音频已上传成功，系统正在排队分配处理资源。',
        floor: 4,
        ceiling: 14,
      }
  }
})

const currentPhaseLabel = computed(() => currentPhasePresentation.value.label)
const currentPhaseMessage = computed(
  () =>
    localizeProgressMessage(progressSummary.value?.message) ||
    currentPhasePresentation.value.defaultMessage,
)

const processingStartedAtMs = computed(() => {
  if (latestStatus.value === 'PENDING')
    return parseTimestamp(task.value?.createdAt) ?? parseTimestamp(task.value?.startedAt)
  return parseTimestamp(task.value?.startedAt) ?? parseTimestamp(task.value?.createdAt)
})

const elapsedDurationMs = computed(() => {
  if (latestStatus.value === 'SUCCESS' && task.value?.durationMs != null)
    return task.value.durationMs
  const startMs = processingStartedAtMs.value
  if (!startMs) return 0
  const endMs =
    latestStatus.value === 'FAILED' || latestStatus.value === 'CANCELED'
      ? (parseTimestamp(task.value?.finishedAt) ??
        parseTimestamp(task.value?.updatedAt) ??
        nowMs.value)
      : nowMs.value
  return Math.max(0, endMs - startMs)
})

const durationMetricLabel = computed(() =>
  latestStatus.value === 'PENDING'
    ? '等待时长'
    : latestStatus.value === 'RUNNING'
      ? '已运行时长'
      : latestStatus.value === 'RETRY_WAIT'
        ? '累计耗时'
        : latestStatus.value === 'SUCCESS'
          ? '总耗时'
          : '处理耗时',
)
const durationMetricValue = computed(() =>
  latestStatus.value === 'SUCCESS' && task.value?.durationMs != null
    ? `${(task.value.durationMs / 1000).toFixed(2)} 秒`
    : formatDurationMetric(elapsedDurationMs.value),
)
const progressPercent = computed(() => {
  if (latestStatus.value === 'SUCCESS') return 100
  const { floor, ceiling } = currentPhasePresentation.value
  if (floor >= ceiling) return floor
  const anchorMs = progressSummary.value?.emittedAtMs ?? processingStartedAtMs.value ?? nowMs.value
  const phaseElapsedMs = Math.max(0, nowMs.value - anchorMs)
  const easedRatio = 1 - Math.exp(-phaseElapsedMs / 10000)
  return Number(clamp(floor + (ceiling - floor) * clamp(easedRatio, 0, 1), 0, 99).toFixed(1))
})
const progressPercentLabel = computed(() => `${Math.round(progressPercent.value)}%`)
const progressToneClass = computed(() =>
  latestStatus.value === 'SUCCESS'
    ? 'is-success'
    : latestStatus.value === 'FAILED' || latestStatus.value === 'CANCELED'
      ? 'is-danger'
      : latestStatus.value === 'RETRY_WAIT'
        ? 'is-warning'
        : 'is-active',
)
const syncTagType = computed(() =>
  streamState.value === 'open' || streamState.value === 'terminal'
    ? 'success'
    : streamState.value === 'connecting' || streamState.value === 'closed'
      ? 'warning'
      : streamState.value === 'error'
        ? 'danger'
        : 'info',
)
const statusTagType = computed(() =>
  latestStatus.value === 'SUCCESS'
    ? 'success'
    : latestStatus.value === 'FAILED' || latestStatus.value === 'CANCELED'
      ? 'danger'
      : latestStatus.value === 'RUNNING' || latestStatus.value === 'RETRY_WAIT'
        ? 'warning'
        : 'info',
)
const latestPhaseAtLabel = computed(() => formatPhaseTime(progressSummary.value?.emittedAtMs))
const retryCountdownText = computed(() => {
  if (latestStatus.value !== 'RETRY_WAIT') return '-'
  const nextRunAtMs = parseTimestamp(latestNextRunAt.value)
  return nextRunAtMs ? formatRetryCountdown(nextRunAtMs - nowMs.value) : '等待系统自动重试'
})
const syncModeText = computed(() => (usesRealtimeChannel.value ? '实时推送' : '自动轮询兜底'))
const progressMetaText = computed(() =>
  latestStatus.value === 'SUCCESS'
    ? resultLoading.value
      ? '结果已生成，正在同步详情…'
      : '结果已完成并可查看'
    : latestStatus.value === 'FAILED'
      ? '任务已结束，可查看错误信息'
      : latestStatus.value === 'CANCELED'
        ? '任务已取消'
        : latestStatus.value === 'RETRY_WAIT'
          ? retryCountdownText.value
          : usesRealtimeChannel.value
            ? '阶段状态会实时刷新'
            : '实时通道异常时自动切换轮询',
)
const processingNote = computed(() =>
  latestStatus.value === 'PENDING'
    ? '音频上传已经完成，系统正在等待可用处理资源。'
    : latestStatus.value === 'RUNNING'
      ? '你可以留在当前页面等待，系统会依次完成转写、文本分析、语音识别和结果写入。'
      : latestStatus.value === 'RETRY_WAIT'
        ? '本次任务会在下一个调度窗口自动继续，无需重新上传语音。'
        : latestStatus.value === 'FAILED'
          ? latestErrorDetail.value || '任务处理失败，请稍后重试或查看时间线了解详情。'
          : latestStatus.value === 'CANCELED'
            ? '任务已被取消。'
            : resultLoading.value
              ? '结果已经生成，正在同步详细内容。'
              : '分析已经完成。',
)
const showProcessingPanel = computed(
  () => Boolean(task.value || streamSnapshot.value) && !task.value?.result,
)
const showChannelFallbackAlert = computed(
  () =>
    showProcessingPanel.value && !usesRealtimeChannel.value && streamState.value !== 'connecting',
)

const parsedRawJson = computed<Record<string, unknown> | null>(() => {
  const raw = taskResult.value?.rawJson
  if (!raw) return null
  try {
    return JSON.parse(raw) as Record<string, unknown>
  } catch {
    return null
  }
})

const textFusionInfo = computed(() => {
  const root = parsedRawJson.value
  const textNegNode = (root?.textNeg ?? null) as Record<string, unknown> | null
  const textSentimentNode = (root?.textSentiment ?? null) as Record<string, unknown> | null
  const textNegFusionNode = (root?.textNegFusion ?? null) as Record<string, unknown> | null
  return {
    lexiconNeg: toNumber(textNegNode?.textNeg),
    modelNeg: toNumber(textSentimentNode?.negativeScore),
    fusedNeg: toNumber(textNegFusionNode?.fusedTextNeg) ?? riskAssessment.value?.text_neg,
    lexiconWeight: toNumber(textNegFusionNode?.lexiconWeight),
    modelWeight: toNumber(textNegFusionNode?.modelWeight),
  }
})

const serFusionInfo = computed(() => {
  const root = parsedRawJson.value
  const serNode = (root?.ser ?? null) as Record<string, unknown> | null
  const fusionNode = (serNode?.fusion ?? null) as Record<string, unknown> | null
  const scoresNode = (fusionNode?.scores ?? null) as Record<string, unknown> | null
  return {
    enabled: Boolean(fusionNode?.enabled),
    ready: Boolean(fusionNode?.ready),
    label: typeof fusionNode?.label === 'string' ? fusionNode.label : undefined,
    confidence: toNumber(fusionNode?.confidence),
    error: typeof fusionNode?.error === 'string' ? fusionNode.error : undefined,
    scoreAngry: toNumber(scoresNode?.ANGRY),
    scoreHappy: toNumber(scoresNode?.HAPPY),
    scoreNeutral: toNumber(scoresNode?.NEUTRAL),
    scoreSad: toNumber(scoresNode?.SAD),
  }
})

const psiContributionRows = computed(() => {
  const source = riskAssessment.value
  if (!source) return []
  const pNeutral = inferNeutralProbability(source)
  const adjustedTextNeg = adjustPsiTextNeg(source)
  const sadPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_SAD * source.p_sad
  const angryPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_ANGRY * source.p_angry
  const happyPart = -100 * WEIGHT_VOICE_IN_PSI * WEIGHT_HAPPY_OFFSET * source.p_happy
  const neutralPart = -100 * WEIGHT_VOICE_IN_PSI * WEIGHT_NEUTRAL_OFFSET * pNeutral
  const varPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_VAR_CONF * source.var_conf
  const textPart = 100 * WEIGHT_TEXT_IN_PSI * adjustedTextNeg
  const rows = [
    { key: 'sad', label: '悲伤拉升', value: sadPart, formula: '语音权重 × 悲伤概率' },
    { key: 'angry', label: '愤怒拉升', value: angryPart, formula: '语音权重 × 愤怒概率' },
    { key: 'happy', label: '积极缓冲', value: happyPart, formula: '语音权重 × 高兴缓冲' },
    { key: 'neutral', label: '平静缓冲', value: neutralPart, formula: '语音权重 × 平静缓冲' },
    { key: 'var', label: '波动拉升', value: varPart, formula: '语音权重 × 波动系数' },
    {
      key: 'text',
      label: '文本负向拉升',
      value: textPart,
      formula:
        adjustedTextNeg === source.text_neg
          ? '文本权重 × 文本负向值'
          : '文本权重 × 轻量折减后的文本负向值',
    },
  ]
  const total = Math.max(
    rows.reduce((sum, row) => sum + Math.abs(row.value), 0),
    0.0001,
  )
  return rows.map((row) => ({ ...row, percent: clamp((Math.abs(row.value) / total) * 100, 0, 100) }))
})

const contributionStatus = (key: string): 'success' | 'warning' | 'exception' => {
  if (key === 'happy' || key === 'neutral') return 'success'
  if (key === 'text') return 'success'
  if (key === 'sad' || key === 'angry') return 'warning'
  return 'exception'
}

const copyTaskNo = async () => {
  try {
    await navigator.clipboard.writeText(displayTaskNo.value)
    ElMessage.success('任务编号已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

onMounted(() => {
  nowTimer = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
  void start()
})

onUnmounted(() => {
  if (nowTimer != null) {
    window.clearInterval(nowTimer)
    nowTimer = null
  }
})
</script>

<template>
  <div class="task-detail-page">
    <el-card class="hero-card" shadow="never">
      <div class="hero-header">
        <div>
          <p class="hero-subtitle">情绪分析任务</p>
          <h2>任务编号 {{ displayTaskNo }}</h2>
          <p class="task-id-tip">任务 ID：{{ taskId }}</p>
        </div>
        <div class="header-actions">
          <el-tag effect="dark" :type="statusTagType">{{ latestStatusText }}</el-tag>
          <el-tag effect="plain" :type="syncTagType">{{ streamLabel }}</el-tag>
          <el-button size="small" :icon="DocumentCopy" @click="copyTaskNo">复制编号</el-button>
        </div>
      </div>

      <LoadingState v-if="pollingState === 'loading' && !task && !streamSnapshot" />
      <ErrorState
        v-else-if="pollingState === 'error' && !task && !streamSnapshot"
        title="任务加载失败"
        :detail="errorMessage"
        :trace-id="latestTraceId"
        @retry="start"
      />
      <EmptyState
        v-else-if="!task && !streamSnapshot"
        title="任务不可用"
          description="这条任务现在还没准备好，请稍后再来看看。"
        action-text="重新加载"
        @action="start"
      />
      <div v-else class="metric-grid">
        <el-card class="metric-item" shadow="hover">
          <p>综合情绪</p>
          <h3>{{ overallEmotionDisplay }}</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>风险等级</p>
          <h3>{{ riskLevelDisplay }}</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>{{ durationMetricLabel }}</p>
          <h3>{{ durationMetricValue }}</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>重试次数</p>
          <h3>{{ latestAttemptCount }}</h3>
        </el-card>
      </div>
    </el-card>

    <el-card v-if="showProcessingPanel" class="processing-card" shadow="hover">
      <template #header>
        <div class="processing-card-header">
          <div>
            <span class="processing-card-title">当前处理进度</span>
            <p class="processing-card-subtitle">
              状态会优先使用实时流更新，通道异常时自动切回轮询。
            </p>
          </div>
          <div class="processing-card-tags">
            <el-tag effect="dark" :type="statusTagType">{{ latestStatusText }}</el-tag>
            <el-tag effect="plain" :type="syncTagType">{{ streamLabel }}</el-tag>
          </div>
        </div>
      </template>

      <el-alert
        v-if="showChannelFallbackAlert"
        class="channel-alert"
        type="warning"
        show-icon
        :closable="false"
        title="实时通道暂不可用，当前使用轮询保持状态更新。"
        :description="streamError || `轮询状态：${pollStatusText}`"
      />

      <div class="processing-panel">
        <div class="processing-summary">
          <p class="processing-eyebrow">当前阶段</p>
          <h3>{{ currentPhaseLabel }}</h3>
          <p class="processing-message">{{ currentPhaseMessage }}</p>
        </div>

        <div class="stage-progress">
          <div class="stage-progress__track">
            <div
              class="stage-progress__fill"
              :class="progressToneClass"
              :style="{ width: `${progressPercent}%` }"
            />
          </div>
          <div class="stage-progress__meta">
            <span>{{ progressPercentLabel }}</span>
            <span>{{ progressMetaText }}</span>
          </div>
        </div>

        <div class="processing-meta-grid">
          <article class="processing-meta-item">
            <span>已运行时长</span>
            <strong>{{ durationMetricValue }}</strong>
          </article>
          <article class="processing-meta-item">
            <span>最近阶段时间</span>
            <strong>{{ latestPhaseAtLabel }}</strong>
          </article>
          <article class="processing-meta-item">
            <span>同步方式</span>
            <strong>{{ syncModeText }}</strong>
          </article>
          <article class="processing-meta-item">
            <span>下一次重试</span>
            <strong>{{ retryCountdownText }}</strong>
          </article>
        </div>

        <p class="processing-note">{{ processingNote }}</p>

        <div class="actions">
          <el-button @click="router.push('/app/tasks')">返回列表</el-button>
          <el-button @click="router.push(`/app/tasks/${taskId}/timeline`)">查看时间线</el-button>
        </div>
      </div>
    </el-card>

    <template v-if="task?.result">
      <el-row :gutter="16" class="chart-row">
        <el-col :xs="24" :md="12">
          <el-card shadow="hover">
            <template #header>置信度</template>
            <el-progress
              type="dashboard"
              :percentage="confidencePercent"
              :color="confidenceColor"
              :stroke-width="12"
            />
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="hover">
            <template #header>{{ PSI_LABEL }}</template>
            <el-progress
              type="dashboard"
              :percentage="riskScorePercent"
              :color="riskColor"
              :stroke-width="12"
            />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="fusion-row">
        <el-col :xs="24" :lg="12">
          <el-card shadow="hover">
            <template #header>融合结果（语音分 / 文本分 / 心理风险指数）</template>
            <el-descriptions border :column="1">
              <el-descriptions-item label="语音分">{{
                voiceRiskScore.toFixed(2)
              }}</el-descriptions-item>
              <el-descriptions-item label="文本分">{{
                textRiskScore.toFixed(2)
              }}</el-descriptions-item>
              <el-descriptions-item label="融合分">{{
                riskScorePercent.toFixed(2)
              }}</el-descriptions-item>
              <el-descriptions-item label="风险等级">{{ riskLevel }}</el-descriptions-item>
              <el-descriptions-item :label="TEXT_NEG_LABEL">
                {{ riskAssessment?.text_neg != null ? riskAssessment.text_neg.toFixed(4) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="语音融合已就绪">
                {{ serFusionInfo.ready ? '是' : serFusionInfo.enabled ? '否' : '未启用' }}
              </el-descriptions-item>
              <el-descriptions-item label="语音融合标签">
                {{ formatEmotion(serFusionInfo.label) }}
              </el-descriptions-item>
              <el-descriptions-item label="语音融合置信度">
                {{
                  serFusionInfo.confidence != null
                    ? `${(serFusionInfo.confidence * 100).toFixed(2)}%`
                    : '-'
                }}
              </el-descriptions-item>
              <el-descriptions-item label="语音融合概率（怒/喜/中/悲）">
                {{
                  serFusionInfo.scoreAngry != null &&
                  serFusionInfo.scoreHappy != null &&
                  serFusionInfo.scoreNeutral != null &&
                  serFusionInfo.scoreSad != null
                    ? `${serFusionInfo.scoreAngry.toFixed(4)} / ${serFusionInfo.scoreHappy.toFixed(4)} / ${serFusionInfo.scoreNeutral.toFixed(4)} / ${serFusionInfo.scoreSad.toFixed(4)}`
                    : '-'
                }}
              </el-descriptions-item>
              <el-descriptions-item v-if="serFusionInfo.error" label="语音融合错误">
                {{ serFusionInfo.error }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="hover">
            <template #header>文本融合细项（词典 + 模型）</template>
            <el-descriptions border :column="1">
              <el-descriptions-item label="词典负向分">
                {{ textFusionInfo.lexiconNeg != null ? textFusionInfo.lexiconNeg.toFixed(4) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="模型负向分">
                {{ textFusionInfo.modelNeg != null ? textFusionInfo.modelNeg.toFixed(4) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="融合负向分">
                {{ textFusionInfo.fusedNeg != null ? textFusionInfo.fusedNeg.toFixed(4) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="融合权重">
                {{
                  textFusionInfo.lexiconWeight != null && textFusionInfo.modelWeight != null
                    ? `${textFusionInfo.lexiconWeight.toFixed(2)} : ${textFusionInfo.modelWeight.toFixed(2)}`
                    : '-'
                }}
              </el-descriptions-item>
            </el-descriptions>
            <p v-if="resultLoading" class="loading-tip">正在同步任务结果详情...</p>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover">
        <template #header>{{ `${PSI_LABEL}贡献项` }}</template>
        <div v-if="psiContributionRows.length" class="psi-list">
          <article v-for="row in psiContributionRows" :key="row.key" class="psi-item">
            <div class="psi-header">
              <strong>{{ row.label }}</strong>
              <span>{{ row.value.toFixed(2) }} ({{ row.percent.toFixed(1) }}%)</span>
            </div>
            <el-progress
              :percentage="Number(row.percent.toFixed(1))"
              :status="contributionStatus(row.key)"
              :stroke-width="10"
            />
            <p class="psi-formula">{{ row.formula }}</p>
          </article>
        </div>
        <EmptyState
          v-else
          title="暂无贡献项数据"
          description="风险贡献细项暂未生成。"
          action-text="刷新任务"
          @action="start"
        />
      </el-card>

      <el-card shadow="hover">
        <template #header>个性化解释与建议</template>
        <div class="narrative-panel">
          <p class="narrative-source-note">{{ narrativeSourceNote }}</p>
          <p v-if="narrativeTechNote" class="narrative-tech-note">{{ narrativeTechNote }}</p>
          <p class="narrative-text">
            {{ narrative?.summary ?? '当前还没有生成额外摘要，下面展示的是安全边界内的基础建议。' }}
          </p>
          <p v-if="narrative?.explanation" class="narrative-text">{{ narrative.explanation }}</p>
          <ul v-if="displayAdviceItems.length" class="narrative-list">
            <li v-for="item in displayAdviceItems" :key="item">{{ item }}</li>
          </ul>
          <p class="narrative-note">
            {{
              narrative?.safetyNotice ??
              '以上内容仅作辅助参考，不构成医疗诊断。若持续感到痛苦或风险升高，请尽快联系值得信任的人或专业支持资源。'
            }}
          </p>
        </div>
      </el-card>

      <el-card shadow="hover">
        <template #header>任务详情</template>
        <el-descriptions border :column="2">
          <el-descriptions-item label="任务状态">{{ latestStatusText }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{
            latestErrorDetail || '-'
          }}</el-descriptions-item>
          <el-descriptions-item :label="TRACE_ID_LABEL">{{
            latestTraceId ?? '-'
          }}</el-descriptions-item>
          <el-descriptions-item :label="`${SER_LABEL}延迟`"
            >{{ task.serLatencyMs ?? '-' }} 毫秒</el-descriptions-item
          >
          <el-descriptions-item label="建议" :span="2">{{
            displayAdviceText
          }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          title="这次分析还在继续进行，你离开页面也不会影响服务端处理。"
          type="info"
          :closable="false"
          class="polling-hint"
          show-icon
        />

        <div class="actions">
          <el-button @click="router.push('/app/tasks')">返回列表</el-button>
          <el-button @click="router.push(`/app/tasks/${taskId}/timeline`)">查看时间线</el-button>
          <el-button type="primary" @click="router.push('/app/reports')">前往报告中心</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.task-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-detail-page :deep(.el-card__header) {
  color: var(--user-text-primary);
  border-bottom: 1px solid rgba(130, 154, 196, 0.18);
}

.task-detail-page :deep(.el-card__body) {
  color: var(--user-text-primary);
}

.task-detail-page :deep(.el-descriptions__table),
.task-detail-page :deep(.el-descriptions__cell) {
  border-color: rgba(130, 154, 196, 0.24);
}

.task-detail-page :deep(.el-descriptions__label.el-descriptions__cell.is-bordered-label) {
  background: rgba(20, 31, 51, 0.92);
  color: rgba(190, 203, 227, 0.8);
}

.task-detail-page :deep(.el-descriptions__content.el-descriptions__cell.is-bordered-content) {
  background: rgba(10, 16, 29, 0.88);
  color: var(--user-text-primary);
  word-break: break-word;
}

.hero-card {
  border: 1px solid rgba(161, 182, 216, 0.26);
  background:
    radial-gradient(circle at top left, rgba(76, 133, 188, 0.16), transparent 34%),
    linear-gradient(180deg, rgba(14, 23, 39, 0.96), rgba(9, 15, 28, 0.96));
}

.hero-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.hero-subtitle {
  margin: 0;
  color: var(--user-text-secondary);
  font-size: 13px;
}

.hero-header h2 {
  margin: 6px 0 0;
  font-size: 26px;
  color: var(--user-text-primary);
}

.task-id-tip {
  margin: 6px 0 0;
  color: rgba(190, 203, 227, 0.76);
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-item :deep(.el-card__body) {
  padding: 14px;
  min-height: 96px;
}

.metric-item p {
  margin: 0;
  color: rgba(192, 206, 230, 0.78);
  font-size: 13px;
}

.metric-item h3 {
  margin: 8px 0 0;
  font-size: 20px;
  color: var(--user-text-primary);
}

.processing-card {
  border: 1px solid rgba(141, 163, 205, 0.24);
  background:
    radial-gradient(circle at top right, rgba(92, 164, 255, 0.08), transparent 32%),
    linear-gradient(180deg, rgba(14, 21, 36, 0.96), rgba(9, 15, 27, 0.96));
}

.processing-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.processing-card-title {
  display: block;
  color: var(--user-text-primary);
  font-size: 16px;
  font-weight: 600;
}

.processing-card-subtitle {
  margin: 6px 0 0;
  color: rgba(185, 202, 228, 0.74);
  font-size: 12px;
}

.processing-card-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.channel-alert {
  margin-bottom: 16px;
}

.processing-panel {
  display: grid;
  gap: 16px;
}

.processing-summary {
  display: grid;
  gap: 6px;
}

.processing-eyebrow {
  margin: 0;
  color: rgba(152, 178, 217, 0.78);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.processing-summary h3 {
  margin: 0;
  font-size: 28px;
  line-height: 1.15;
  color: var(--user-text-primary);
}

.processing-message {
  margin: 0;
  color: rgba(219, 229, 245, 0.88);
  line-height: 1.7;
}

.stage-progress {
  display: grid;
  gap: 8px;
}

.stage-progress__track {
  position: relative;
  overflow: hidden;
  height: 12px;
  border-radius: 999px;
  background: rgba(73, 96, 132, 0.28);
  box-shadow: inset 0 0 0 1px rgba(151, 176, 216, 0.08);
}

.stage-progress__fill {
  position: relative;
  height: 100%;
  border-radius: inherit;
  transition: width 0.6s ease;
}

.stage-progress__fill::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.22), transparent);
  transform: translateX(-100%);
  animation: progress-sheen 1.6s linear infinite;
}

.stage-progress__fill.is-active {
  background: linear-gradient(90deg, #4fa0ff 0%, #67d8d6 100%);
}

.stage-progress__fill.is-warning {
  background: linear-gradient(90deg, #d69b47 0%, #efc66b 100%);
}

.stage-progress__fill.is-success {
  background: linear-gradient(90deg, #4ea662 0%, #81d897 100%);
}

.stage-progress__fill.is-danger {
  background: linear-gradient(90deg, #d56060 0%, #f08c8c 100%);
}

.stage-progress__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: rgba(191, 207, 231, 0.78);
  font-size: 12px;
  line-height: 1.5;
}

.processing-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.processing-meta-item {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid rgba(134, 157, 197, 0.22);
  border-radius: 12px;
  background: rgba(14, 22, 38, 0.72);
}

.processing-meta-item span {
  color: rgba(176, 194, 222, 0.72);
  font-size: 12px;
}

.processing-meta-item strong {
  color: var(--user-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.processing-note {
  margin: 0;
  color: rgba(188, 205, 232, 0.82);
  line-height: 1.7;
}

.chart-row {
  margin: 0;
}

.fusion-row {
  margin: 0;
}

.loading-tip {
  margin: 12px 0 0;
  color: rgba(184, 204, 236, 0.78);
  font-size: 12px;
}

.psi-list {
  display: grid;
  gap: 12px;
}

.psi-item {
  border: 1px solid rgba(141, 163, 205, 0.28);
  border-radius: 10px;
  padding: 12px;
  background: linear-gradient(180deg, rgba(18, 28, 46, 0.9), rgba(11, 18, 31, 0.9));
}

.psi-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  gap: 8px;
}

.psi-formula {
  margin: 8px 0 0;
  color: rgba(168, 187, 217, 0.78);
  font-size: 12px;
}

.polling-hint {
  margin-top: 12px;
}

.narrative-panel {
  display: grid;
  gap: 10px;
}

.narrative-text {
  margin: 0;
  color: rgba(228, 237, 251, 0.92);
  line-height: 1.7;
}

.narrative-source-note {
  margin: 0;
  color: rgba(184, 204, 236, 0.76);
  font-size: 12px;
  line-height: 1.6;
}

.narrative-tech-note {
  margin: -4px 0 0;
  color: rgba(159, 181, 216, 0.72);
  font-size: 12px;
  line-height: 1.5;
}

.narrative-list {
  margin: 0;
  padding-left: 18px;
  color: rgba(228, 237, 251, 0.92);
  line-height: 1.7;
}

.narrative-note {
  margin: 0;
  color: rgba(174, 193, 222, 0.78);
  font-size: 12px;
  line-height: 1.6;
}

.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .processing-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .processing-meta-grid {
    grid-template-columns: 1fr;
  }

  .stage-progress__meta {
    flex-direction: column;
    align-items: flex-start;
  }
}

@keyframes progress-sheen {
  from {
    transform: translateX(-100%);
  }

  to {
    transform: translateX(100%);
  }
}
</style>
