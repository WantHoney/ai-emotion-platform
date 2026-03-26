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
import SectionBlock from '@/components/ui/SectionBlock.vue'
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
const WEIGHT_ANGRY = 0.25
const WEIGHT_VAR_CONF = 0.1
const WEIGHT_VOICE_IN_PSI = 0.6
const WEIGHT_TEXT_IN_PSI = 0.4

const toNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value))

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

const riskAssessment = computed<RiskAssessmentPayload | null>(() => taskResult.value?.riskAssessment ?? null)

const normalizedRiskScore = computed(() => {
  const score = riskAssessment.value?.risk_score ?? report.value?.riskScore
  if (score == null || Number.isNaN(score)) return 0
  return clamp(score <= 1 ? score * 100 : score, 0, 100)
})

const confidencePercent = computed(() => {
  const fusionConfidence = extractFusionConfidence(taskResult.value?.rawJson)
  const confidence = fusionConfidence ?? report.value?.confidence ?? taskResult.value?.overallConfidence
  if (confidence == null || Number.isNaN(confidence)) return '--'
  const normalized = confidence <= 1 ? confidence * 100 : confidence
  return `${normalized.toFixed(2)}%`
})

const rawRiskLevel = computed(() => riskAssessment.value?.risk_level ?? report.value?.riskLevel ?? '')
const riskLevel = computed(() => formatRiskLevel(rawRiskLevel.value))

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

const textFusionItems = computed<KpiItem[]>(() => {
  const root = parsedRawJson.value
  const textNegNode = (root?.textNeg ?? null) as Record<string, unknown> | null
  const textSentimentNode = (root?.textSentiment ?? null) as Record<string, unknown> | null
  const textNegFusionNode = (root?.textNegFusion ?? null) as Record<string, unknown> | null

  const lexiconScore = toNumber(textNegNode?.textNeg)
  const modelScore = toNumber(textSentimentNode?.negativeScore)
  const fusedScore = toNumber(textNegFusionNode?.fusedTextNeg) ?? riskAssessment.value?.text_neg
  const lexiconWeight = toNumber(textNegFusionNode?.lexiconWeight)
  const modelWeight = toNumber(textNegFusionNode?.modelWeight)

  return [
    {
      label: '词典负向值',
      value: lexiconScore != null ? lexiconScore.toFixed(4) : '--',
      helper: '词典情绪负向得分',
    },
    {
      label: '模型负向值',
      value: modelScore != null ? modelScore.toFixed(4) : '--',
      helper: '文本模型负向得分',
    },
    {
      label: '融合后负向值',
      value: fusedScore != null ? fusedScore.toFixed(4) : '--',
      helper: `融合后${TEXT_NEG_LABEL}`,
    },
    {
      label: '融合权重',
      value:
        lexiconWeight != null && modelWeight != null
          ? `${lexiconWeight.toFixed(2)} : ${modelWeight.toFixed(2)}`
          : '--',
      helper: '词典权重 : 模型权重',
    },
  ]
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
      color: '#67c23a',
    },
    {
      key: 'sad',
      label: '悲伤概率',
      percentage: Number(toPercent(serFusionInfo.value.scoreSad).toFixed(1)),
      valueText: toDisplay(serFusionInfo.value.scoreSad),
      color: '#4f8cff',
    },
    {
      key: 'angry',
      label: '愤怒概率',
      percentage: Number(toPercent(serFusionInfo.value.scoreAngry).toFixed(1)),
      valueText: toDisplay(serFusionInfo.value.scoreAngry),
      color: '#f56c6c',
    },
    {
      key: 'neutral',
      label: '平静概率',
      percentage: Number(toPercent(serFusionInfo.value.scoreNeutral).toFixed(1)),
      valueText: toDisplay(serFusionInfo.value.scoreNeutral),
      color: '#e6a23c',
    },
  ]
})

const serFusionItems = computed<KpiItem[]>(() => [
  {
    label: '语音融合状态',
    value: serFusionInfo.value.ready ? '已就绪' : serFusionInfo.value.enabled ? '处理中' : '未启用',
    helper: '在线语音融合运行状态',
  },
  {
    label: '语音融合标签',
    value: formatEmotion(serFusionInfo.value.label),
    helper: '融合后的语音情绪结果',
  },
  {
    label: '语音融合置信度',
    value: serFusionInfo.value.confidence != null ? `${(serFusionInfo.value.confidence * 100).toFixed(2)}%` : '--',
    helper: '融合后的语音置信度',
  },
  {
    label: '语音融合异常',
    value: serFusionInfo.value.error ? '有异常' : '正常',
    helper: serFusionInfo.value.error ?? '暂无异常',
  },
])

const psiContributionRows = computed(() => {
  const source = riskAssessment.value
  if (!source) return []

  const sadPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_SAD * source.p_sad
  const angryPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_ANGRY * source.p_angry
  const varPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_VAR_CONF * source.var_conf
  const textPart = 100 * WEIGHT_TEXT_IN_PSI * source.text_neg

  const rows = [
    { key: 'sad', label: '悲伤贡献', value: sadPart, helper: '语音权重 × 悲伤概率' },
    { key: 'angry', label: '愤怒贡献', value: angryPart, helper: '语音权重 × 愤怒概率' },
    { key: 'var', label: '波动贡献', value: varPart, helper: '语音权重 × 波动系数' },
    { key: 'text', label: '文本贡献', value: textPart, helper: '文本权重 × 文本负向值' },
  ]

  const total = Math.max(normalizedRiskScore.value, 0.0001)
  return rows.map((row) => ({
    ...row,
    percent: clamp((row.value / total) * 100, 0, 100),
  }))
})

const kpiItems = computed<KpiItem[]>(() => {
  if (!report.value) return []
  return [
    { label: '综合情绪', value: formatEmotion(report.value.overall) },
    { label: '置信度', value: confidencePercent.value },
    { label: PSI_LABEL, value: `${normalizedRiskScore.value.toFixed(0)}/100` },
    { label: '任务编号', value: report.value.taskNo || `任务-${report.value.taskId}` },
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

  const source = riskAssessment.value?.advice_text?.trim() || report.value?.adviceText?.trim()
  if (!source) return fallbackByRisk

  const items = source
    .split(/[\n;；]+/)
    .map((item) => item.trim())
    .filter(Boolean)

  return {
    instant: items.slice(0, 2).length ? items.slice(0, 2) : fallbackByRisk.instant,
    longTerm: items.slice(2, 4).length ? items.slice(2, 4) : fallbackByRisk.longTerm,
    resource: items.slice(4, 7).length ? items.slice(4, 7) : fallbackByRisk.resource,
  }
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
        description="多模态情绪分析结果汇总。"
      >
        <div class="top-meta">
          <p>报告 ID：{{ report.id }} | 任务 ID：{{ report.taskId }} | 生成时间：{{ formattedCreatedAt }}</p>
          <BadgeTag :tone="riskTone" :text="rawRiskLevel ? riskLevel : '风险待评估'" />
        </div>
        <KpiGrid :items="kpiItems" />
      </SectionBlock>

      <div class="report-grid">
        <SectionBlock title="融合看板" description="汇总语音、文本与心理风险指数的核心结果。">
          <LoreCard title="可视化总览" :subtitle="`${SER_LABEL}概率环图`">
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

          <LoreCard title="文本融合详情" subtitle="词典与文本模型">
            <KpiGrid :items="textFusionItems" />
          </LoreCard>
          <LoreCard :title="`${SER_LABEL}输出`" subtitle="在线语音融合预测">
            <KpiGrid :items="serFusionItems" />
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

        <SectionBlock title="建议方案" description="按干预阶段给出可直接执行的建议。">
          <div class="bucket-grid">
            <LoreCard title="即时行动">
              <ul>
                <li v-for="item in adviceBuckets.instant" :key="`instant-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
            <LoreCard title="中长期行动">
              <ul>
                <li v-for="item in adviceBuckets.longTerm" :key="`long-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
            <LoreCard title="资源建议">
              <ul>
                <li v-for="item in adviceBuckets.resource" :key="`res-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
          </div>
        </SectionBlock>
      </div>

      <div class="report-grid">
        <SectionBlock title="可解释依据" description="展示本次评分的关键依据。">
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
        </SectionBlock>

        <SectionBlock eyebrow="相关内容" title="延伸阅读与练习" description="辅助提升情绪韧性的内容推荐。">
          <div class="recommend-grid">
            <LoreCard
              v-for="item in homeContent?.recommendedArticles ?? []"
              :key="`article-${item.id}`"
              :title="item.title"
              :subtitle="item.summary || '点击查看文章'"
              interactive
              @click="openArticle(item.contentUrl)"
            />
            <LoreCard
              v-for="item in homeContent?.recommendedBooks ?? []"
              :key="`book-${item.id}`"
              :title="item.title"
              :subtitle="item.author || '点击查看书籍详情'"
              interactive
              @click="openBook(item.purchaseUrl)"
            />
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
  gap: 16px;
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

.report-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.bucket-grid {
  display: grid;
  gap: 10px;
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
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

  .viz-ring-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .recommend-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .top-meta {
    align-items: stretch;
  }

  .top-meta :deep(.badge) {
    margin-left: auto;
  }

  .viz-ring-grid {
    grid-template-columns: 1fr;
  }
}
</style>
