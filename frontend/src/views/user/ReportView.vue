<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getReportDetail, type ReportDetail } from '@/api/report'
import { getHomeContent, type HomePayload } from '@/api/home'
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
const homeContent = ref<HomePayload | null>(null)
const errorState = ref<ErrorStatePayload | null>(null)

const normalizedRiskScore = computed(() => {
  const score = report.value?.riskScore
  if (score == null || Number.isNaN(score)) return 0
  return Math.max(0, Math.min(100, score <= 1 ? score * 100 : score))
})

const confidencePercent = computed(() => {
  const confidence = report.value?.confidence
  if (confidence == null || Number.isNaN(confidence)) return '--'
  const normalized = confidence <= 1 ? confidence * 100 : confidence
  return `${normalized.toFixed(2)}%`
})

const riskTone = computed<'low' | 'medium' | 'high' | 'neutral'>(() => {
  const level = (report.value?.riskLevel ?? '').toLowerCase()
  if (level.includes('high')) return 'high'
  if (level.includes('medium')) return 'medium'
  if (level.includes('low')) return 'low'
  return 'neutral'
})

const formattedCreatedAt = computed(() => {
  const value = report.value?.createdAt
  if (!value) return '未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
})

const kpiItems = computed<KpiItem[]>(() => {
  if (!report.value) return []
  return [
    { label: '综合情绪', value: report.value.overall || '-' },
    { label: '置信度', value: confidencePercent.value },
    { label: '风险分数', value: `${normalizedRiskScore.value.toFixed(0)}/100` },
    { label: '任务 ID', value: `#${report.value.taskId}` },
  ]
})

const adviceBuckets = computed(() => {
  const fallbackByRisk = {
    instant: ['先做 2-3 分钟呼吸放松，避免持续高负荷。'],
    longTerm: ['保持规律作息，并进行每周情绪自检。'],
    resource: ['如有需要，请联系本地心理支持资源。'],
  }

  const source = report.value?.adviceText?.trim()
  if (!source) {
    return fallbackByRisk
  }

  const items = source
    .split(/\n|；|;|。|\./)
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

  factors.push(`综合情绪判定为 ${report.value.overall ?? '未知'}。`)
  factors.push(`风险分标准化后为 ${normalizedRiskScore.value.toFixed(1)}。`)

  const topSegments = [...(report.value.segments ?? [])]
    .sort((a, b) => b.confidence - a.confidence)
    .slice(0, 3)

  topSegments.forEach((segment, index) => {
    factors.push(
      `因子 ${index + 1}：${segment.emotion}，置信度 ${(segment.confidence * 100).toFixed(1)}%，` +
        `时间窗 ${segment.start}ms-${segment.end}ms。`,
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
    const [detailResp, homeResp] = await Promise.all([
      getReportDetail(reportId.value),
      getHomeContent().catch(() => null),
    ])
    report.value = detailResp.data
    homeContent.value = homeResp
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
      title="报告暂不可用"
      description="当前无法展示该报告内容。"
      action-text="重新加载"
      @action="loadReport"
    />
    <template v-else>
      <SectionBlock
        eyebrow="报告卷宗"
        :title="`报告 #${report.id}`"
        description="多模态情绪分析的结构化输出。"
      >
        <div class="top-meta">
          <p>生成时间：{{ formattedCreatedAt }}</p>
          <BadgeTag :tone="riskTone" :text="report.riskLevel || '风险未知'" />
        </div>

        <KpiGrid :items="kpiItems" />
      </SectionBlock>

      <div class="report-grid">
        <SectionBlock title="建议方案" description="按干预时序组织的可执行建议。">
          <div class="bucket-grid">
            <LoreCard title="即时建议">
              <ul>
                <li v-for="item in adviceBuckets.instant" :key="`instant-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
            <LoreCard title="长期建议">
              <ul>
                <li v-for="item in adviceBuckets.longTerm" :key="`long-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
            <LoreCard title="资源引导">
              <ul>
                <li v-for="item in adviceBuckets.resource" :key="`res-${item}`">{{ item }}</li>
              </ul>
            </LoreCard>
          </div>
        </SectionBlock>

        <SectionBlock title="可解释性" description="展示本次评分的关键贡献因子。">
          <LoreCard title="评分贡献因子">
            <ul>
              <li v-for="item in contributionFactors" :key="item">{{ item }}</li>
            </ul>
          </LoreCard>

          <LoreCard title="情绪片段" subtitle="本次记录中的高置信窗口">
            <div v-if="report.segments?.length" class="segment-grid">
              <article
                v-for="(segment, index) in report.segments"
                :key="`${segment.start}-${segment.end}-${index}`"
                class="segment-item"
              >
                <strong>{{ segment.emotion }}</strong>
                <span>{{ (segment.confidence * 100).toFixed(1) }}%</span>
                <span>{{ segment.start }}ms - {{ segment.end }}ms</span>
              </article>
            </div>
            <p v-else class="muted">暂无片段明细。</p>
          </LoreCard>
        </SectionBlock>
      </div>

      <SectionBlock
        eyebrow="相关推荐"
        title="延伸阅读与练习"
        description="结合内容资源，帮助持续提升情绪韧性。"
      >
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
  grid-template-columns: 1.05fr 1fr;
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

@media (max-width: 1024px) {
  .report-grid,
  .recommend-grid {
    grid-template-columns: 1fr;
  }
}
</style>
