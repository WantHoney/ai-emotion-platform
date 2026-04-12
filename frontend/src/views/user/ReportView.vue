<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getHomeContent, type HomePayload } from '@/api/home'
import { getReportDetail, type ReportDetail } from '@/api/report'
import { getResult, type AnalysisTaskResultDetail, type RiskAssessmentPayload } from '@/api/task'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import BadgeTag from '@/components/ui/BadgeTag.vue'
import KpiGrid, { type KpiItem } from '@/components/ui/KpiGrid.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import MediaFeatureCard from '@/components/ui/MediaFeatureCard.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import { ARTICLE_CATEGORY_LABELS, ARTICLE_DIFFICULTY_LABELS } from '@/constants/contentMeta'
import {
  buildNarrativeSourceNote,
  buildNarrativeTechNote,
  parseNarrativeFromRawJson,
  splitAdviceText,
} from '@/utils/analysisNarrative'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { PSI_LABEL, SER_LABEL, TEXT_NEG_LABEL, formatEmotion, formatRiskLevel } from '@/utils/uiText'

const route = useRoute()
const router = useRouter()

const reportId = computed(() => Number(route.params.id))
const loading = ref(false)
const report = ref<ReportDetail | null>(null)
const taskResult = ref<AnalysisTaskResultDetail | null>(null)
const homeContent = ref<HomePayload | null>(null)
const errorState = ref<ErrorStatePayload | null>(null)

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

const toNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

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

const EMOTION_ACCENT_COLORS: Record<string, string> = {
  HAP: '#e6a23c',
  SAD: '#4f8cff',
  ANG: '#f56c6c',
  NEU: '#67c23a',
}

const formatProbabilitySummary = (
  entries: Array<{ code: string; value?: number }>,
  limit = 4,
) => {
  const ranked = entries
    .filter((entry): entry is { code: string; value: number } => entry.value != null && Number.isFinite(entry.value))
    .sort((left, right) => right.value - left.value)
    .slice(0, limit)
  if (!ranked.length) return '--'
  return ranked.map((entry) => `${entry.code} ${Math.round(clamp(entry.value, 0, 1) * 100)}%`).join(' / ')
}

const buildProbabilitySummaryEntries = (
  entries: Array<{ code: string; value?: number }>,
  limit = 4,
) =>
  entries
    .filter((entry): entry is { code: string; value: number } => entry.value != null && Number.isFinite(entry.value))
    .sort((left, right) => right.value - left.value)
    .slice(0, limit)
    .map((entry) => ({
      code: entry.code,
      percentText: `${Math.round(clamp(entry.value, 0, 1) * 100)}%`,
      color: EMOTION_ACCENT_COLORS[entry.code] ?? '#f4f8ff',
    }))

const riskAssessment = computed<RiskAssessmentPayload | null>(() => taskResult.value?.riskAssessment ?? null)
const narrative = computed(() => parseNarrativeFromRawJson(taskResult.value?.rawJson))

const normalizedRiskScore = computed(() => {
  const score = riskAssessment.value?.risk_score ?? report.value?.riskScore
  if (score == null || Number.isNaN(score)) return 0
  return clamp(score <= 1 ? score * 100 : score, 0, 100)
})

const confidencePercent = computed(() => {
  const confidence = report.value?.confidence ?? taskResult.value?.overallConfidence
  if (confidence == null || Number.isNaN(confidence)) return '--'
  const normalized = confidence <= 1 ? confidence * 100 : confidence
  return `${normalized.toFixed(2)}%`
})

const rawRiskLevel = computed(() => riskAssessment.value?.risk_level ?? report.value?.riskLevel ?? '')
const riskLevel = computed(() => formatRiskLevel(rawRiskLevel.value))
const narrativeSourceNote = computed(() => buildNarrativeSourceNote(narrative.value))
const narrativeTechNote = computed(() => buildNarrativeTechNote(narrative.value))

const riskTone = computed<'low' | 'medium' | 'high' | 'neutral'>(() => {
  const level = rawRiskLevel.value.toLowerCase()
  if (level.includes('high')) return 'high'
  if (level.includes('attention') || level.includes('medium')) return 'medium'
  if (level.includes('low') || level.includes('normal')) return 'low'
  return 'neutral'
})

const formattedCreatedAt = computed(() => {
  const value = report.value?.createdAt
  if (!value) return '未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
})

const parsedRawJson = computed<Record<string, unknown> | null>(() => {
  const raw = taskResult.value?.rawJson
  if (!raw) return null
  try {
    return JSON.parse(raw) as Record<string, unknown>
  } catch {
    return null
  }
})

const textSemanticInfo = computed(() => {
  const root = parsedRawJson.value
  const textNegNode = (root?.textNeg ?? null) as Record<string, unknown> | null
  const textSentimentNode = (root?.textSentiment ?? null) as Record<string, unknown> | null
  const textNegFusionNode = (root?.textNegFusion ?? null) as Record<string, unknown> | null
  const emotion4Node = (textSentimentNode?.emotion4Scores ?? null) as Record<string, unknown> | null

  return {
    lexiconScore: toNumber(textNegNode?.diagnosticScore ?? textNegNode?.textNeg),
    lexiconHits: Array.isArray(textNegNode?.hits)
      ? (textNegNode.hits as unknown[]).filter((item): item is string => typeof item === 'string')
      : [],
    modelLabel:
      typeof textSentimentNode?.emotion4Label === 'string' ? textSentimentNode.emotion4Label : undefined,
    modelConfidence: toNumber(textSentimentNode?.emotion4Confidence),
    modelNegative: toNumber(textSentimentNode?.negativeScore),
    modelAngry: toNumber(emotion4Node?.ANG),
    modelHappy: toNumber(emotion4Node?.HAP),
    modelNeutral: toNumber(emotion4Node?.NEU),
    modelSad: toNumber(emotion4Node?.SAD),
    fusedNegative: toNumber(textNegFusionNode?.fusedTextNeg) ?? riskAssessment.value?.text_neg,
    lexiconWeight: toNumber(textNegFusionNode?.lexiconWeight),
    modelWeight: toNumber(textNegFusionNode?.modelWeight),
  }
})

const textFusionItems = computed<KpiItem[]>(() => [
  {
    label: 'Gemma文本标签',
    value: formatEmotion(textSemanticInfo.value.modelLabel),
    helper: '文本语义主判断',
  },
  {
    label: 'Gemma文本置信度',
    value:
      textSemanticInfo.value.modelConfidence != null
        ? `${(textSemanticInfo.value.modelConfidence * 100).toFixed(2)}%`
        : '--',
    helper: 'Gemma 四类情绪置信度',
  },
  {
    label: 'Gemma文本概率摘要',
    value: formatProbabilitySummary([
      { code: 'SAD', value: textSemanticInfo.value.modelSad },
      { code: 'NEU', value: textSemanticInfo.value.modelNeutral },
      { code: 'HAP', value: textSemanticInfo.value.modelHappy },
      { code: 'ANG', value: textSemanticInfo.value.modelAngry },
    ]),
    helper: '按概率从高到低展示 Gemma 文本四类结果',
  },
  {
    label: 'Gemma负向分',
    value: textSemanticInfo.value.modelNegative != null ? textSemanticInfo.value.modelNegative.toFixed(4) : '--',
    helper: '文本主模型负向得分',
  },
  {
    label: '文本融合负向分',
    value:
      textSemanticInfo.value.fusedNegative != null ? textSemanticInfo.value.fusedNegative.toFixed(4) : '--',
    helper: `融合后${TEXT_NEG_LABEL}`,
  },
  {
    label: '词典诊断分',
    value: textSemanticInfo.value.lexiconScore != null ? textSemanticInfo.value.lexiconScore.toFixed(4) : '--',
    helper: '词典情绪诊断得分',
  },
  {
    label: '词典命中词',
    value: textSemanticInfo.value.lexiconHits.length ? textSemanticInfo.value.lexiconHits.join(' / ') : '未命中',
    helper: '词典命中结果仅供诊断参考',
  },
  {
    label: '词典 / 模型权重',
    value:
      textSemanticInfo.value.lexiconWeight != null && textSemanticInfo.value.modelWeight != null
        ? `${textSemanticInfo.value.lexiconWeight.toFixed(2)} : ${textSemanticInfo.value.modelWeight.toFixed(2)}`
        : '--',
    helper: '词典权重 : Gemma 权重',
  },
])

const serAudioInfo = computed(() => {
  const root = parsedRawJson.value
  const serNode = (root?.ser ?? null) as Record<string, unknown> | null
  const audioSummaryNode = (serNode?.audioSummary ?? null) as Record<string, unknown> | null

  return {
    label:
      typeof audioSummaryNode?.dominantEmotion === 'string'
        ? audioSummaryNode.dominantEmotion
        : taskResult.value?.overallEmotionCode,
    confidence: toNumber(audioSummaryNode?.audio_confidence) ?? taskResult.value?.overallConfidence,
    scoreAngry: toNumber(audioSummaryNode?.audio_prob_ang),
    scoreHappy: toNumber(audioSummaryNode?.audio_prob_hap),
    scoreNeutral: toNumber(audioSummaryNode?.audio_prob_neu),
    scoreSad: toNumber(audioSummaryNode?.audio_prob_sad),
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

const serProbabilityRings = computed(() => {
  const toPercent = (value?: number) => (value != null ? clamp(value * 100, 0, 100) : 0)
  const toDisplay = (value?: number) => (value != null ? `${(clamp(value, 0, 1) * 100).toFixed(2)}%` : '--')

  return [
    {
      key: 'happy',
      label: '高兴概率',
      percentage: Number(toPercent(serFusionInfo.value.scoreHappy).toFixed(1)),
      valueText: toDisplay(serFusionInfo.value.scoreHappy),
      color: EMOTION_ACCENT_COLORS.HAP,
    },
    {
      key: 'sad',
      label: '悲伤概率',
      percentage: Number(toPercent(serFusionInfo.value.scoreSad).toFixed(1)),
      valueText: toDisplay(serFusionInfo.value.scoreSad),
      color: EMOTION_ACCENT_COLORS.SAD,
    },
    {
      key: 'angry',
      label: '愤怒概率',
      percentage: Number(toPercent(serFusionInfo.value.scoreAngry).toFixed(1)),
      valueText: toDisplay(serFusionInfo.value.scoreAngry),
      color: EMOTION_ACCENT_COLORS.ANG,
    },
    {
      key: 'neutral',
      label: '平静概率',
      percentage: Number(toPercent(serFusionInfo.value.scoreNeutral).toFixed(1)),
      valueText: toDisplay(serFusionInfo.value.scoreNeutral),
      color: EMOTION_ACCENT_COLORS.NEU,
    },
  ]
})

const voiceProbabilitySummaryEntries = computed(() =>
  buildProbabilitySummaryEntries([
    { code: 'HAP', value: serAudioInfo.value.scoreHappy },
    { code: 'SAD', value: serAudioInfo.value.scoreSad },
    { code: 'NEU', value: serAudioInfo.value.scoreNeutral },
    { code: 'ANG', value: serAudioInfo.value.scoreAngry },
  ]),
)

const textProbabilitySummaryEntries = computed(() =>
  buildProbabilitySummaryEntries([
    { code: 'SAD', value: textSemanticInfo.value.modelSad },
    { code: 'NEU', value: textSemanticInfo.value.modelNeutral },
    { code: 'HAP', value: textSemanticInfo.value.modelHappy },
    { code: 'ANG', value: textSemanticInfo.value.modelAngry },
  ]),
)

const serAudioItems = computed<KpiItem[]>(() => [
  {
    label: '纯语音标签',
    value: formatEmotion(serAudioInfo.value.label),
    helper: '纯语音声学分支主结果',
  },
  {
    label: '纯语音置信度',
    value: serAudioInfo.value.confidence != null ? `${(serAudioInfo.value.confidence * 100).toFixed(2)}%` : '--',
    helper: '纯语音声学分支置信度',
  },
  {
    label: '纯语音概率摘要',
    value: formatProbabilitySummary([
      { code: 'HAP', value: serAudioInfo.value.scoreHappy },
      { code: 'SAD', value: serAudioInfo.value.scoreSad },
      { code: 'NEU', value: serAudioInfo.value.scoreNeutral },
      { code: 'ANG', value: serAudioInfo.value.scoreAngry },
    ]),
    helper: '按概率从高到低展示纯语音四类结果',
  },
  {
    label: '纯语音状态',
    value: serAudioInfo.value.label ? '已生成' : '--',
    helper: '纯语音分支结果状态',
  },
])

const fusionItems = computed<KpiItem[]>(() => [
  {
    label: '原始融合标签',
    value: formatEmotion(serFusionInfo.value.label),
    helper: '模型原始音频 + 文本融合主结果',
  },
  {
    label: '原始融合置信度',
    value: serFusionInfo.value.confidence != null ? `${(serFusionInfo.value.confidence * 100).toFixed(2)}%` : '--',
    helper: '模型原始融合结果置信度',
  },
  {
    label: '原始融合状态',
    value: serFusionInfo.value.ready ? '已就绪' : serFusionInfo.value.enabled ? '处理中' : '未启用',
    helper: '在线融合运行状态',
  },
  {
    label: '原始融合异常',
    value: serFusionInfo.value.error ? '有异常' : '正常',
    helper: serFusionInfo.value.error ?? '暂无异常',
  },
])

const shouldPreferVoiceEmotion = computed(() => {
  const voiceLabel = serAudioInfo.value.label?.trim().toUpperCase()
  const fusionLabel = serFusionInfo.value.label?.trim().toUpperCase()
  const textLabel = textSemanticInfo.value.modelLabel?.trim().toUpperCase()
  return (
    voiceLabel === 'HAPPY' &&
    fusionLabel === 'SAD' &&
    (serAudioInfo.value.confidence ?? 0) >= 0.4 &&
    (serAudioInfo.value.scoreHappy ?? 0) >= 0.42 &&
    (serAudioInfo.value.scoreHappy ?? 0) > (serAudioInfo.value.scoreSad ?? 0) &&
    (textLabel === 'SAD' || (textSemanticInfo.value.modelNegative ?? 0) >= 0.55) &&
    textSemanticInfo.value.lexiconHits.length === 0 &&
    ((serFusionInfo.value.confidence ?? 0) <= 0.55 || serFusionInfo.value.confidence == null)
  )
})

const resultDifferenceHint = computed(() => {
  if (!report.value && !taskResult.value) return ''
  if (shouldPreferVoiceEmotion.value) {
    return '已触发语音优先修正：检测到歌词/演唱场景下文本语义可能拉偏融合结果，最终综合情绪优先参考语音情绪表达；原始融合评分仍保留展示，便于复核。'
  }
  const labels = [serAudioInfo.value.label, textSemanticInfo.value.modelLabel, serFusionInfo.value.label].filter(
    (value): value is string => typeof value === 'string' && value.trim().length > 0,
  )
  const unique = Array.from(new Set(labels))
  if (unique.length <= 1) return ''
  return '顶部显示最终综合情绪；下方环图显示原始融合评分，并分别展示语音情绪分支、文本语义分支与融合摘要，便于复核。'
})

const psiContributionRows = computed(() => {
  const source = riskAssessment.value
  if (!source) return []

  const pNeutral = inferNeutralProbability(source)
  const adjustedTextNeg = adjustPsiTextNeg(source)
  const sadPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_SAD * source.p_sad
  const angryPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_ANGRY * source.p_angry
  const happyOffsetPart = -100 * WEIGHT_VOICE_IN_PSI * WEIGHT_HAPPY_OFFSET * source.p_happy
  const neutralOffsetPart = -100 * WEIGHT_VOICE_IN_PSI * WEIGHT_NEUTRAL_OFFSET * pNeutral
  const varPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_VAR_CONF * source.var_conf
  const textPart = 100 * WEIGHT_TEXT_IN_PSI * adjustedTextNeg

  const rows = [
    { key: 'sad', label: '悲伤拉升', value: sadPart, helper: '语音中的悲伤表达会抬高风险分' },
    { key: 'angry', label: '愤怒拉升', value: angryPart, helper: '语音中的愤怒表达会抬高风险分' },
    { key: 'happy', label: '积极缓冲', value: happyOffsetPart, helper: '高兴表达会对风险分起到缓冲作用' },
    { key: 'neutral', label: '平静缓冲', value: neutralOffsetPart, helper: '表达越平稳，中性缓冲越明显' },
    { key: 'var', label: '波动拉升', value: varPart, helper: '情绪波动越明显，风险分越容易升高' },
    {
      key: 'text',
      label: '文本负向拉升',
      value: textPart,
      helper:
        adjustedTextNeg === source.text_neg
          ? '文本越偏负向，风险分越容易升高'
          : '当前样本存在语音积极与文本负向冲突，文本拉升已做轻量折减',
    },
  ]

  const total = Math.max(
    rows.reduce((sum, row) => sum + Math.abs(row.value), 0),
    0.0001,
  )
  return rows.map((row) => ({
    ...row,
    percent: clamp((Math.abs(row.value) / total) * 100, 0, 100),
  }))
})

const kpiItems = computed<KpiItem[]>(() => {
  if (!report.value) return []
  return [
    { label: '最终综合情绪', value: formatEmotion(report.value.overall) },
    { label: '置信度', value: confidencePercent.value },
    { label: PSI_LABEL, value: `${normalizedRiskScore.value.toFixed(0)}/100` },
    { label: '关联任务编号', value: report.value.taskNo || `任务-${report.value.taskId}` },
  ]
})

const displaySegments = computed(() => {
  if (taskResult.value?.segments?.length) return taskResult.value.segments
  return report.value?.segments ?? []
})

const adviceBuckets = computed(() => {
  const fallbackByRisk = {
    instant: ['先暂停 2 到 3 分钟，做几次缓慢深呼吸，让情绪先稳定下来。'],
    longTerm: ['保持规律作息，并每周记录一次情绪变化，观察波动趋势。'],
    resource: ['如果困扰持续存在，建议联系学校或本地心理支持资源。'],
  }

  const narrativeBuckets = narrative.value?.adviceBuckets
  if (narrativeBuckets) {
    const hasNarrativeAdvice =
      narrativeBuckets.instant.length > 0 ||
      narrativeBuckets.longTerm.length > 0 ||
      narrativeBuckets.resource.length > 0
    if (hasNarrativeAdvice) {
      return {
        instant: narrativeBuckets.instant.length ? narrativeBuckets.instant : fallbackByRisk.instant,
        longTerm: narrativeBuckets.longTerm.length ? narrativeBuckets.longTerm : fallbackByRisk.longTerm,
        resource: narrativeBuckets.resource.length ? narrativeBuckets.resource : fallbackByRisk.resource,
      }
    }
  }

  const source = riskAssessment.value?.advice_text?.trim() || report.value?.adviceText?.trim()
  if (!source) return fallbackByRisk

  const items = splitAdviceText(source)

  return {
    instant: items.slice(0, 2).length ? items.slice(0, 2) : fallbackByRisk.instant,
    longTerm: items.slice(2, 4).length ? items.slice(2, 4) : fallbackByRisk.longTerm,
    resource: items.slice(4, 7).length ? items.slice(4, 7) : fallbackByRisk.resource,
  }
})

const adviceFocus = computed(() => {
  if (shouldPreferVoiceEmotion.value) {
    return {
      title: '先结合场景理解结果',
      description:
        '当前样本的语音表达与文本语义存在差异，更适合先结合场景理解结果，再参考原始融合评分与分支细节做判断。',
    }
  }
  if (normalizedRiskScore.value >= 70) {
    return {
      title: '先降风险，再求解释',
      description: '当前风险分偏高，建议优先减少持续刺激、避免独自承受，并尽快联系可信赖的人或专业支持资源。',
    }
  }
  if (normalizedRiskScore.value >= 40) {
    return {
      title: '先稳定状态，再观察波动',
      description: '当前需要重点关注负向情绪是否持续、是否反复波动，以及是否开始影响到睡眠、学习或日常节律。',
    }
  }

  const overall = report.value?.overall?.trim().toUpperCase()
  if (overall === 'HAPPY') {
    return {
      title: '保持积极状态，继续观察变化',
      description: '当前整体情绪偏积极，建议保持自然表达、规律作息和适度活动，同时留意后续是否出现明显回落或波动。',
    }
  }
  if (overall === 'SAD') {
    return {
      title: '先减轻低落感，再恢复节律',
      description: '当前整体偏低落，建议先降低情绪负担，再逐步恢复休息、进食和学习等日常节律。',
    }
  }
  if (overall === 'ANGRY') {
    return {
      title: '先降低激惹，再处理冲突',
      description: '当前整体偏激惹，建议先拉开情绪距离，避免立即进入争执，再回头处理具体问题。',
    }
  }
  return {
    title: '保持平稳节律，持续轻量观察',
    description: '当前整体状态较平稳，建议保持规律作息和正常表达，继续观察后续情绪是否出现持续性偏移。',
  }
})

const adviceSummaryText = computed(
  () =>
    narrative.value?.summary?.trim() ||
    `${adviceFocus.value.description} 当前系统建议以低负担、可执行、便于坚持的调整方式为主。`,
)

const adviceExplanationText = computed(() => {
  if (narrative.value?.explanation?.trim()) {
    return narrative.value.explanation.trim()
  }

  const evidenceParts: string[] = []
  if (report.value?.overall) {
    evidenceParts.push(`最终综合情绪为 ${formatEmotion(report.value.overall)}`)
  }
  evidenceParts.push(`${PSI_LABEL} 为 ${normalizedRiskScore.value.toFixed(0)}/100`)
  if (serAudioInfo.value.label) {
    evidenceParts.push(`语音分支偏 ${formatEmotion(serAudioInfo.value.label)}`)
  }
  if (textSemanticInfo.value.modelLabel) {
    evidenceParts.push(`文本语义偏 ${formatEmotion(textSemanticInfo.value.modelLabel)}`)
  }
  return `${evidenceParts.join('，')}，因此当前建议重点是“${adviceFocus.value.title}”。`
})

const adviceSignalItems = computed(() => {
  const items: string[] = []
  if (rawRiskLevel.value) {
    items.push(`风险等级：${riskLevel.value}`)
  }
  if (report.value?.overall) {
    items.push(`综合情绪：${formatEmotion(report.value.overall)}`)
  }
  if (serAudioInfo.value.label && textSemanticInfo.value.modelLabel) {
    items.push(`语音 / 文本：${formatEmotion(serAudioInfo.value.label)} / ${formatEmotion(textSemanticInfo.value.modelLabel)}`)
  }
  items.push(`当前重点：${adviceFocus.value.title}`)
  return items
})

const contributionFactors = computed(() => {
  const factors: string[] = []
  if (!report.value) return factors

  factors.push(`综合情绪：${formatEmotion(report.value.overall)}`)
  factors.push(`${PSI_LABEL}：${normalizedRiskScore.value.toFixed(2)}`)
  if (riskAssessment.value) {
    factors.push(
      `风险因子：悲伤概率 ${riskAssessment.value.p_sad}，愤怒概率 ${riskAssessment.value.p_angry}，波动系数 ${riskAssessment.value.var_conf}，文本负向值 ${riskAssessment.value.text_neg}`,
    )
  }

  const topSegments = [...displaySegments.value].sort((a, b) => b.confidence - a.confidence).slice(0, 3)
  topSegments.forEach((segment, index) => {
    factors.push(
      `片段 ${index + 1}：${formatEmotion(segment.emotion)}，置信度 ${(segment.confidence * 100).toFixed(1)}%，时间窗 ${segment.start}ms - ${segment.end}ms`,
    )
  })

  return factors
})

const openArticle = (url?: string) => {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

const openBook = (url?: string) => {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

const loadReport = async () => {
  loading.value = true
  errorState.value = null

  try {
    const detailResp = await getReportDetail(reportId.value)
    report.value = detailResp.data

    const [homeResp, resultResp] = await Promise.all([
      getHomeContent().catch(() => null),
      detailResp.data.taskId ? getResult(detailResp.data.taskId).catch(() => null) : Promise.resolve(null),
    ])

    homeContent.value = homeResp
    taskResult.value = resultResp?.data ?? null
  } catch (error) {
    errorState.value = parseError(error, '报告详情加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadReport()
})
</script>

<template>
  <div class="report-page user-layout">
    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadReport"
    />
    <EmptyState
      v-else-if="!report"
      title="报告不可用"
      description="当前暂时无法显示这份报告。"
      action-text="重新加载"
      @action="loadReport"
    />
    <template v-else>
      <SectionBlock
        eyebrow="报告总览"
        :title="report.reportNo || `报告-${report.id}`"
        description="把这次分析结果整理成更容易阅读的内容。"
      >
        <div class="top-meta">
          <p>
            报告 ID：{{ report.id }} | 任务 ID：{{ report.taskId }} | 关联任务编号：{{
              report.taskNo || `任务-${report.taskId}`
            }} | 生成时间：{{ formattedCreatedAt }}
          </p>
          <BadgeTag :tone="riskTone" :text="rawRiskLevel ? riskLevel : '风险待评估'" />
        </div>
        <KpiGrid :items="kpiItems" />
      </SectionBlock>

      <div class="report-grid report-grid-primary">
        <SectionBlock title="这次结果怎么看" description="先看整体结果，再往下看语音、文本和综合判断。">
          <el-alert
            v-if="resultDifferenceHint"
            class="difference-alert"
            type="info"
            show-icon
            :closable="false"
            :title="resultDifferenceHint"
          />
          <LoreCard title="综合评分" subtitle="模型对这次状态的整体判断">
            <div class="viz-ring-grid">
              <article v-for="ring in serProbabilityRings" :key="ring.key" class="viz-ring-item">
                <el-progress
                  type="dashboard"
                  :percentage="ring.percentage"
                  :color="ring.color"
                  :stroke-width="11"
                />
                <p>{{ ring.label }}</p>
                <strong>{{ ring.valueText }}</strong>
              </article>
            </div>
          </LoreCard>

          <LoreCard title="语音结果" subtitle="结合声音特征得到的结果">
            <div class="branch-grid branch-grid-ser">
              <article class="branch-card">
                <p class="branch-label">纯语音标签</p>
                <h3 class="branch-value">{{ formatEmotion(serAudioInfo.label) }}</h3>
                <p class="branch-helper">纯语音声学分支主结果</p>
              </article>
              <article class="branch-card">
                <p class="branch-label">纯语音置信度</p>
                <h3 class="branch-value">
                  {{ serAudioInfo.confidence != null ? `${(serAudioInfo.confidence * 100).toFixed(2)}%` : '--' }}
                </h3>
                <p class="branch-helper">纯语音声学分支置信度</p>
              </article>
              <article class="branch-card branch-card-summary">
                <p class="branch-label">纯语音概率摘要</p>
                <div class="branch-value branch-value-summary branch-value-summary-colored">
                  <template v-if="voiceProbabilitySummaryEntries.length">
                    <template v-for="(entry, index) in voiceProbabilitySummaryEntries" :key="`voice-${entry.code}`">
                      <span class="summary-entry">
                        <span class="summary-code" :style="{ color: entry.color }">{{ entry.code }}</span>
                        <span class="summary-percent">{{ entry.percentText }}</span>
                      </span>
                      <span v-if="index < voiceProbabilitySummaryEntries.length - 1" class="summary-separator"> / </span>
                    </template>
                  </template>
                  <template v-else>--</template>
                </div>
                <p class="branch-helper">按概率从高到低展示纯语音四类结果</p>
              </article>
            </div>
          </LoreCard>
          <LoreCard title="文本结果" subtitle="根据转写文本给出的语义判断">
            <div class="branch-header-meta">
              <span class="weight-pill">
                词典 / 模型权重
                {{
                  textSemanticInfo.lexiconWeight != null && textSemanticInfo.modelWeight != null
                    ? ` ${textSemanticInfo.lexiconWeight.toFixed(2)} : ${textSemanticInfo.modelWeight.toFixed(2)}`
                    : ' -'
                }}
              </span>
            </div>
            <div class="branch-grid branch-grid-text">
              <article class="branch-card">
                <p class="branch-label">Gemma文本标签</p>
                <h3 class="branch-value">{{ formatEmotion(textSemanticInfo.modelLabel) }}</h3>
                <p class="branch-helper">文本语义主判断</p>
              </article>
              <article class="branch-card">
                <p class="branch-label">Gemma文本置信度</p>
                <h3 class="branch-value">
                  {{
                    textSemanticInfo.modelConfidence != null
                      ? `${(textSemanticInfo.modelConfidence * 100).toFixed(2)}%`
                      : '--'
                  }}
                </h3>
                <p class="branch-helper">Gemma 四类情绪置信度</p>
              </article>
              <article class="branch-card branch-card-summary">
                <p class="branch-label">Gemma文本概率摘要</p>
                <div class="branch-value branch-value-summary branch-value-summary-colored">
                  <template v-if="textProbabilitySummaryEntries.length">
                    <template v-for="(entry, index) in textProbabilitySummaryEntries" :key="`text-${entry.code}`">
                      <span class="summary-entry">
                        <span class="summary-code" :style="{ color: entry.color }">{{ entry.code }}</span>
                        <span class="summary-percent">{{ entry.percentText }}</span>
                      </span>
                      <span v-if="index < textProbabilitySummaryEntries.length - 1" class="summary-separator"> / </span>
                    </template>
                  </template>
                  <template v-else>--</template>
                </div>
                <p class="branch-helper">按概率从高到低展示 Gemma 文本四类结果</p>
              </article>
              <article class="branch-card">
                <p class="branch-label">Gemma负向分</p>
                <h3 class="branch-value">
                  {{ textSemanticInfo.modelNegative != null ? textSemanticInfo.modelNegative.toFixed(4) : '--' }}
                </h3>
                <p class="branch-helper">文本主模型负向得分</p>
              </article>
              <article class="branch-card">
                <p class="branch-label">文本融合负向分</p>
                <h3 class="branch-value">
                  {{ textSemanticInfo.fusedNegative != null ? textSemanticInfo.fusedNegative.toFixed(4) : '--' }}
                </h3>
                <p class="branch-helper">融合后文本负向值（text_neg）</p>
              </article>
              <article class="branch-card">
                <p class="branch-label">词典命中词</p>
                <h3 class="branch-value">
                  {{ textSemanticInfo.lexiconHits.length ? textSemanticInfo.lexiconHits.join(' / ') : '未命中' }}
                </h3>
                <p class="branch-helper">词典命中结果仅作诊断参考</p>
              </article>
              <article class="branch-card">
                <p class="branch-label">词典诊断分</p>
                <h3 class="branch-value">
                  {{ textSemanticInfo.lexiconScore != null ? textSemanticInfo.lexiconScore.toFixed(4) : '--' }}
                </h3>
                <p class="branch-helper">词典情绪诊断得分</p>
              </article>
            </div>
          </LoreCard>
          <LoreCard title="综合结果" subtitle="把语音和文本一起看后的结果">
            <KpiGrid :items="fusionItems" />
          </LoreCard>

          <LoreCard :title="`${PSI_LABEL}贡献项`">
            <div v-if="psiContributionRows.length" class="psi-list">
              <article v-for="row in psiContributionRows" :key="row.key" class="psi-item">
                <div class="psi-row-head">
                  <strong>{{ row.label }}</strong>
                  <span>{{ row.value.toFixed(2) }} ({{ row.percent.toFixed(1) }}%)</span>
                </div>
                <div class="psi-bar">
                  <span :style="{ width: `${row.percent}%` }"></span>
                </div>
                <small>{{ row.helper }}</small>
              </article>
            </div>
            <p v-else class="muted">暂无心理风险指数贡献数据。</p>
          </LoreCard>
        </SectionBlock>

        <div class="right-column-stack">
          <SectionBlock title="接下来可以怎么做" description="把这次结果整理成更容易开始的建议。">
            <LoreCard title="建议总览" subtitle="优先看这一块，先把握当前建议重点。">
              <p class="advice-hero-copy">{{ adviceSummaryText }}</p>
            </LoreCard>

          <div class="advice-overview-grid">
            <LoreCard title="为什么这样建议" subtitle="结合结果证据解释当前建议方向。">
              <p class="narrative-copy">{{ adviceExplanationText }}</p>
              <p v-if="narrative?.safetyNotice" class="narrative-note">{{ narrative.safetyNotice }}</p>
            </LoreCard>

            <LoreCard title="当前重点" subtitle="把最值得先关注的点提炼出来。">
              <p class="advice-focus-title">{{ adviceFocus.title }}</p>
              <p class="narrative-copy">{{ adviceFocus.description }}</p>
              <ul class="advice-signal-list">
                <li v-for="item in adviceSignalItems" :key="`signal-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
          </div>

          <div class="advice-action-grid">
            <LoreCard title="即时行动" subtitle="先做成本低、可以马上开始的调整。">
              <ul>
                <li v-for="item in adviceBuckets.instant" :key="`instant-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
            <LoreCard title="中长期行动" subtitle="把状态稳定下来后，再持续推进。">
              <ul>
                <li v-for="item in adviceBuckets.longTerm" :key="`long-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
            <LoreCard class="advice-resource-card" title="资源建议" subtitle="如果这种状态持续，可以从这些方向继续找支持。">
              <ul>
                <li v-for="item in adviceBuckets.resource" :key="`res-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
          </div>

          <div class="advice-footnotes">
            <p class="narrative-source-note">{{ narrativeSourceNote }}</p>
            <p v-if="narrativeTechNote" class="narrative-tech-note">{{ narrativeTechNote }}</p>
          </div>
        </SectionBlock>

        <SectionBlock eyebrow="相关内容" title="延伸阅读与练习" description="如果你想继续了解，可以从这里往下看。">
          <div class="recommend-grid">
            <MediaFeatureCard
              v-for="item in homeContent?.recommendedArticles ?? []"
              :key="`article-${item.id}`"
              :image-url="item.coverImageUrl"
              :image-alt="item.title"
              image-kind="article"
              :title="item.title"
              :subtitle="item.sourceName || '推荐文章'"
              :description="item.summary"
              interactive
              @click="openArticle(item.sourceUrl || item.contentUrl)"
            >
              <template #meta>
                <span class="recommend-pill recommend-pill-muted">
                  {{ ARTICLE_CATEGORY_LABELS[item.category || ''] || '内容推荐' }}
                </span>
                <span class="recommend-pill recommend-pill-accent">
                  {{ ARTICLE_DIFFICULTY_LABELS[item.difficultyTag || ''] || '精选' }}
                </span>
              </template>
            </MediaFeatureCard>
            <MediaFeatureCard
              v-for="item in homeContent?.recommendedBooks ?? []"
              :key="`book-${item.id}`"
              :image-url="item.coverImageUrl"
              :image-alt="item.title"
              image-kind="book"
              :title="item.title"
              :subtitle="item.author || '推荐阅读'"
              :description="item.description"
              interactive
              @click="openBook(item.purchaseUrl)"
            >
              <template #meta>
                <span class="recommend-pill recommend-pill-muted">书单推荐</span>
                <span class="recommend-pill recommend-pill-book">本地书封</span>
              </template>
            </MediaFeatureCard>
          </div>
        </SectionBlock>
        </div>
      </div>

      <div class="report-grid report-grid-secondary">
        <SectionBlock title="这次结果主要参考了什么" description="这里会说明这次结果主要参考了哪些信息。">
          <div class="evidence-grid">
            <LoreCard title="评分因素">
              <ul>
                <li v-for="item in contributionFactors" :key="item">{{ item }}</li>
              </ul>
            </LoreCard>

            <LoreCard title="情绪片段" subtitle="本次记录中置信度较高的片段">
              <div v-if="displaySegments.length" class="segment-grid">
                <article
                  v-for="(segment, index) in displaySegments"
                  :key="`${segment.start}-${segment.end}-${index}`"
                  class="segment-item"
                >
                  <strong>{{ formatEmotion(segment.emotion) }}</strong>
                  <span>{{ (segment.confidence * 100).toFixed(1) }}%</span>
                  <span>{{ segment.start }}ms - {{ segment.end }}ms</span>
                </article>
              </div>
              <p v-else class="muted">暂无片段详情。</p>
            </LoreCard>
          </div>
        </SectionBlock>
      </div>

      <div class="footer-actions">
        <el-button @click="router.push('/app/reports')">返回报告中心</el-button>
        <el-button type="primary" plain @click="report && router.push(`/app/tasks/${report.taskId}`)">
          查看关联任务
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.top-meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  color: #bcd0ef;
}

.top-meta p {
  margin: 0;
  flex: 1 1 460px;
  min-width: 0;
  line-height: 1.5;
  word-break: break-word;
}

.top-meta :deep(.badge) {
  flex: 0 0 auto;
  max-width: 100%;
  white-space: nowrap;
}

.difference-alert {
  margin-bottom: 12px;
}

.report-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
}

.report-grid-primary {
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
}

.report-grid-secondary {
  grid-template-columns: 1fr;
}

.evidence-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.right-column-stack {
  display: grid;
  gap: 10px;
  align-content: start;
}

.advice-overview-grid,
.advice-action-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.advice-resource-card {
  grid-column: 1 / -1;
}

.advice-hero-copy {
  margin: 0;
  color: #d9e7fb;
  line-height: 1.72;
}

.advice-focus-title {
  margin: 0 0 6px;
  color: #f8fafc;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}

.advice-signal-list {
  margin-top: 8px;
}

.advice-footnotes {
  display: grid;
  gap: 4px;
}

.narrative-source-note {
  margin: 0;
  color: #9eb7de;
  font-size: 12px;
  line-height: 1.6;
}

.narrative-tech-note {
  margin: 0;
  color: #87a1ca;
  font-size: 12px;
  line-height: 1.5;
}

.viz-ring-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.viz-ring-item {
  border: 1px solid rgba(141, 163, 205, 0.35);
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  background: linear-gradient(180deg, rgba(20, 31, 52, 0.45), rgba(13, 21, 36, 0.65));
}

.viz-ring-item p {
  margin: 0;
  font-size: 13px;
  color: #9eb7de;
}

.viz-ring-item strong {
  color: #f4f8ff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.branch-header-meta {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.weight-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  padding: 5px 10px;
  color: #d8e8f7;
  background: rgba(70, 100, 141, 0.28);
  font-size: 12px;
  white-space: nowrap;
}

.branch-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.branch-grid-ser .branch-card-summary,
.branch-grid-text .branch-card-summary {
  grid-column: 1 / -1;
}

.branch-card {
  border-radius: 14px;
  border: 1px solid rgba(170, 185, 216, 0.28);
  background: linear-gradient(180deg, rgba(20, 31, 52, 0.84), rgba(13, 21, 36, 0.9));
  padding: 14px;
  min-width: 0;
  overflow: hidden;
}

.branch-label {
  margin: 0;
  color: #9eb3d7;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.branch-value {
  margin: 8px 0 0;
  color: #f8fafc;
  font-size: clamp(24px, 2vw, 36px);
  line-height: 1.15;
  font-family: var(--font-display);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.branch-value-summary {
  font-size: clamp(14px, 0.95vw, 22px);
  line-height: 1.15;
  letter-spacing: 0;
  white-space: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  text-overflow: clip;
  scrollbar-width: none;
}

.branch-value-summary::-webkit-scrollbar {
  display: none;
}

.branch-value-summary-colored {
  display: block;
}

.summary-entry {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}

.summary-code {
  font-weight: 800;
}

.summary-percent {
  color: #f8fafc;
}

.summary-separator {
  color: #9eb7de;
}

.branch-helper {
  margin: 10px 0 0;
  color: #bfd1ee;
  font-size: 12px;
  overflow-wrap: break-word;
  word-break: break-word;
}

ul {
  margin: 0;
  padding-left: 18px;
  color: #d9e7fb;
  line-height: 1.7;
}

.psi-list {
  display: grid;
  gap: 10px;
}

.psi-item {
  border: 1px solid rgba(141, 163, 205, 0.35);
  border-radius: 10px;
  padding: 10px;
  display: grid;
  gap: 6px;
}

.psi-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #d3e3fb;
}

.psi-row-head strong {
  color: #f5f9ff;
}

.psi-row-head span {
  color: #9eb7de;
  font-size: 13px;
}

.psi-bar {
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: rgba(84, 109, 153, 0.26);
  overflow: hidden;
}

.psi-bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #67bdf6, #8e92ff);
}

.psi-item small {
  color: #89a4d0;
  font-size: 12px;
}

.segment-grid {
  display: grid;
  gap: 8px;
}

.segment-item {
  border: 1px solid rgba(141, 163, 205, 0.35);
  border-radius: 10px;
  padding: 10px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 10px;
  color: #cfddf5;
}

.segment-item strong {
  color: #f5f9ff;
}

.muted {
  margin: 0;
  color: #aac0e2;
}

.recommend-grid {
  display: grid;
  gap: 8px;
  align-items: stretch;
}

.recommend-grid :deep(.media-card) {
  height: 100%;
}

.recommend-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
}

.recommend-pill-muted {
  color: #d8e8f7;
  background: rgba(70, 100, 141, 0.3);
}

.recommend-pill-accent {
  color: #f5fff7;
  background: rgba(108, 182, 140, 0.26);
}

.recommend-pill-book {
  color: #fff5df;
  background: rgba(191, 154, 90, 0.24);
}

.narrative-copy {
  margin: 0;
  color: #d9e7fb;
  line-height: 1.72;
}

.narrative-note {
  margin: 8px 0 0;
  color: #9eb7de;
  font-size: 12px;
  line-height: 1.6;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 1200px) {
  .report-grid {
    grid-template-columns: 1fr;
  }

  .report-grid-primary {
    grid-template-columns: 1fr;
  }

  .viz-ring-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .recommend-grid {
    grid-template-columns: 1fr;
  }

  .advice-overview-grid,
  .advice-action-grid {
    grid-template-columns: 1fr;
  }

  .evidence-grid {
    grid-template-columns: 1fr;
  }

  .advice-resource-card {
    grid-column: auto;
  }
}

@media (max-width: 768px) {
  .top-meta {
    align-items: stretch;
  }

  .top-meta :deep(.badge) {
    margin-left: auto;
  }

  .branch-header-meta {
    justify-content: flex-start;
  }

  .branch-grid {
    grid-template-columns: 1fr;
  }

  .branch-grid-ser .branch-card-summary,
  .branch-grid-text .branch-card-summary {
    grid-column: auto;
  }

  .viz-ring-grid {
    grid-template-columns: 1fr;
  }
}
</style>

