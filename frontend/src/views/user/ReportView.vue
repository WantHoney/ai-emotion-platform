<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getReportDetail, type ReportDetail } from '@/api/report'
import { getHomeContent, type HomePayload } from '@/api/home'
import { getResult, type AnalysisTaskResultDetail, type RiskAssessmentPayload } from '@/api/task'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import KpiGrid, { type KpiItem } from '@/components/ui/KpiGrid.vue'
import BadgeTag from '@/components/ui/BadgeTag.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'

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

const riskAssessment = computed<RiskAssessmentPayload | null>(() => {
  return taskResult.value?.riskAssessment ?? null
})

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

const riskLevel = computed(() => {
  return riskAssessment.value?.risk_level ?? report.value?.riskLevel ?? ''
})

const riskTone = computed<'low' | 'medium' | 'high' | 'neutral'>(() => {
  const level = riskLevel.value.toLowerCase()
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

const voiceRiskScore = computed(() => {
  const source = riskAssessment.value
  if (!source) return 0
  return (
    100 *
    (WEIGHT_SAD * source.p_sad + WEIGHT_ANGRY * source.p_angry + WEIGHT_VAR_CONF * source.var_conf)
  )
})

const textRiskScore = computed(() => {
  const source = riskAssessment.value
  if (!source) return 0
  return 100 * source.text_neg
})

const fusionKpiItems = computed<KpiItem[]>(() => [
  {
    label: '语音分',
    value: `${voiceRiskScore.value.toFixed(2)}`,
    helper: '由 p_sad / p_angry / var_conf 计算',
  },
  {
    label: '文本分',
    value: `${textRiskScore.value.toFixed(2)}`,
    helper: '由 text_neg 计算',
  },
  {
    label: '融合分(PSI)',
    value: `${normalizedRiskScore.value.toFixed(2)}`,
    helper: 'PSI = 0.6*语音分 + 0.4*文本分',
  },
  {
    label: '风险等级',
    value: riskLevel.value || '未知',
    helper: '低风险 / 关注 / 高风险',
  },
])

const parsedRawJson = computed<Record<string, unknown> | null>(() => {
  const raw = taskResult.value?.rawJson
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>
    return parsed
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
      label: '词典负向分',
      value: lexiconScore != null ? lexiconScore.toFixed(4) : '--',
      helper: '词典情绪负向得分',
    },
    {
      label: '模型负向分',
      value: modelScore != null ? modelScore.toFixed(4) : '--',
      helper: '文本模型负向得分',
    },
    {
      label: '融合负向分',
      value: fusedScore != null ? fusedScore.toFixed(4) : '--',
      helper: '融合后 text_neg',
    },
    {
      label: '融合权重',
      value:
        lexiconWeight != null && modelWeight != null
          ? `${lexiconWeight.toFixed(2)}:${modelWeight.toFixed(2)}`
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

const serFusionItems = computed<KpiItem[]>(() => [
  {
    label: 'SER融合就绪',
    value: serFusionInfo.value.ready ? '是' : serFusionInfo.value.enabled ? '否' : '已禁用',
    helper: '在线融合运行状态',
  },
  {
    label: 'SER融合标签',
    value: serFusionInfo.value.label ?? '--',
    helper: '融合后预测类别',
  },
  {
    label: 'SER融合置信度',
    value:
      serFusionInfo.value.confidence != null ? `${(serFusionInfo.value.confidence * 100).toFixed(2)}%` : '--',
    helper: '校准后的置信度',
  },
  {
    label: 'SER融合错误',
    value: serFusionInfo.value.error ? '有' : '无',
    helper: serFusionInfo.value.error ?? '无',
  },
])

const serFusionScoreItems = computed<KpiItem[]>(() => [
  {
    label: '怒概率',
    value: serFusionInfo.value.scoreAngry != null ? serFusionInfo.value.scoreAngry.toFixed(4) : '--',
  },
  {
    label: '喜概率',
    value: serFusionInfo.value.scoreHappy != null ? serFusionInfo.value.scoreHappy.toFixed(4) : '--',
  },
  {
    label: '中性概率',
    value: serFusionInfo.value.scoreNeutral != null ? serFusionInfo.value.scoreNeutral.toFixed(4) : '--',
  },
  {
    label: '悲伤概率',
    value: serFusionInfo.value.scoreSad != null ? serFusionInfo.value.scoreSad.toFixed(4) : '--',
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
    { key: 'sad', label: '悲伤贡献', value: sadPart, helper: '0.6*100*0.45*p_sad' },
    { key: 'angry', label: '愤怒贡献', value: angryPart, helper: '0.6*100*0.25*p_angry' },
    { key: 'var', label: '波动贡献', value: varPart, helper: '0.6*100*0.10*var_conf' },
    { key: 'text', label: '文本贡献', value: textPart, helper: '0.4*100*text_neg' },
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
    { label: '综合情绪', value: report.value.overall || '-' },
    { label: '置信度', value: confidencePercent.value },
    { label: '风险分数', value: `${normalizedRiskScore.value.toFixed(0)}/100` },
    { label: '任务编号', value: report.value.taskNo || `TASK-${report.value.taskId}` },
  ]
})

const displaySegments = computed(() => {
  if (taskResult.value?.segments?.length) return taskResult.value.segments
  return report.value?.segments ?? []
})

const adviceBuckets = computed(() => {
  const fallbackByRisk = {
    instant: ['先暂停 2-3 分钟，进行深呼吸与地面化练习。'],
    longTerm: ['保持规律作息，并每周进行一次情绪自检。'],
    resource: ['如有持续困扰，联系学校或本地心理支持资源。'],
  }

  const source = riskAssessment.value?.advice_text?.trim() || report.value?.adviceText?.trim()
  if (!source) return fallbackByRisk

  const items = source
    .split(/[\n;；。]+/)
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

  factors.push(`综合情绪：${report.value.overall ?? '未知'}`)
  factors.push(`PSI：${normalizedRiskScore.value.toFixed(2)}`)
  if (riskAssessment.value) {
    factors.push(
      `风险因子：p_sad=${riskAssessment.value.p_sad}, p_angry=${riskAssessment.value.p_angry}, var_conf=${riskAssessment.value.var_conf}, text_neg=${riskAssessment.value.text_neg}`,
    )
  }

  const topSegments = [...displaySegments.value].sort((a, b) => b.confidence - a.confidence).slice(0, 3)
  topSegments.forEach((segment, index) => {
    factors.push(
      `第${index + 1}条：${segment.emotion}，置信度 ${(segment.confidence * 100).toFixed(1)}%，窗口 ${segment.start}ms-${segment.end}ms`,
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
      description="当前暂时无法显示该报告。"
      action-text="重新加载"
      @action="loadReport"
    />
    <template v-else>
      <SectionBlock
        eyebrow="报告总览"
        :title="report.reportNo || `REPORT-${report.id}`"
        description="多模态情绪分析结果汇总。"
      >
        <div class="top-meta">
          <p>报告ID: {{ report.id }} | 任务ID: {{ report.taskId }} | 生成时间: {{ formattedCreatedAt }}</p>
          <BadgeTag :tone="riskTone" :text="riskLevel || '风险未知'" />
        </div>
        <KpiGrid :items="kpiItems" />
      </SectionBlock>

      <div class="report-grid">
        <SectionBlock title="融合看板" description="语音/文本/融合(PSI)与贡献细项。">
          <KpiGrid :items="fusionKpiItems" />

          <LoreCard title="文本融合细项" subtitle="词典 + 文本模型">
            <KpiGrid :items="textFusionItems" />
          </LoreCard>
          <LoreCard title="SER 融合输出" subtitle="在线融合预测">
            <KpiGrid :items="serFusionItems" />
            <KpiGrid :items="serFusionScoreItems" />
          </LoreCard>

          <LoreCard title="PSI 贡献项">
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
            <p v-else class="muted">暂无 PSI 贡献数据。</p>
          </LoreCard>
        </SectionBlock>

        <SectionBlock title="建议方案" description="按干预阶段给出可执行建议。">
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
        <SectionBlock title="可解释性" description="本次评分的关键依据。">
          <LoreCard title="评分因素">
            <ul>
              <li v-for="item in contributionFactors" :key="item">{{ item }}</li>
            </ul>
          </LoreCard>

          <LoreCard title="情绪片段" subtitle="本次记录中置信度最高的窗口">
            <div v-if="displaySegments.length" class="segment-grid">
              <article
                v-for="(segment, index) in displaySegments"
                :key="`${segment.start}-${segment.end}-${index}`"
                class="segment-item"
              >
                <strong>{{ segment.emotion }}</strong>
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
        <el-button type="primary" plain @click="router.push(`/app/tasks/${report.taskId}`)">查看任务</el-button>
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
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #bcd0ef;
}

.top-meta p {
  margin: 0;
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
}

@media (max-width: 1024px) {
  .recommend-grid {
    grid-template-columns: 1fr;
  }
}
</style>
