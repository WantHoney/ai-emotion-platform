<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import {
  getReportTrend,
  getReportTrendInsight,
  type ReportTrendInsight,
  type ReportTrendItem,
  type ReportTrendThresholds,
} from '@/api/report'
import { buildNarrativeTechNote } from '@/utils/analysisNarrative'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const rows = ref<ReportTrendItem[]>([])
const errorState = ref<ErrorStatePayload | null>(null)
const days = ref(30)
const riskThresholds = ref<ReportTrendThresholds>({
  mediumMin: 40,
  highMin: 70,
})

const trendInsight = ref<ReportTrendInsight | null>(null)
const trendInsightLoading = ref(false)

let latestTrendRequestId = 0
let latestInsightRequestId = 0

const chartWidth = 920
const chartHeight = 340
const chartPaddingLeft = 54
const chartPaddingRight = 56
const chartPaddingTop = 26
const chartPaddingBottom = 50
const plotWidth = chartWidth - chartPaddingLeft - chartPaddingRight
const plotHeight = chartHeight - chartPaddingTop - chartPaddingBottom

const mediumThreshold = computed(() => Number(riskThresholds.value.mediumMin || 40))
const highThreshold = computed(() => Number(riskThresholds.value.highMin || 70))

const formatThreshold = (value: number) => {
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

const getMediumRangeEnd = computed(() => {
  if (Number.isInteger(highThreshold.value)) {
    return String(highThreshold.value - 1)
  }
  return (highThreshold.value - 0.01).toFixed(2)
})

const lowRiskLabel = computed(() => `低风险 < ${formatThreshold(mediumThreshold.value)}`)
const mediumRiskLabel = computed(
  () => `中风险 ${formatThreshold(mediumThreshold.value)}-${getMediumRangeEnd.value}`,
)
const highRiskLabel = computed(() => `高风险 >= ${formatThreshold(highThreshold.value)}`)

const maxCount = computed(() => {
  const maxValue = Math.max(0, ...rows.value.map((item) => item.reportCount))
  return maxValue <= 0 ? 1 : maxValue
})

const totalReports = computed(() => rows.value.reduce((acc, item) => acc + item.reportCount, 0))

const avgRisk = computed(() => {
  if (!rows.value.length || totalReports.value === 0) return 0
  const weightedSum = rows.value.reduce((acc, item) => acc + item.avgRiskScore * item.reportCount, 0)
  return Math.round((weightedSum / totalReports.value) * 100) / 100
})

const highRiskDays = computed(() => rows.value.filter((item) => item.highCount > 0).length)

const formulaText = computed(() => '风险分 = 0.6 × 语音风险 + 0.4 × 文本负向值')
const explanationText = computed(
  () =>
    '语音风险不是只看“悲伤/愤怒”标签，而是综合悲伤占比、愤怒占比和置信度波动一起计算，因此主情绪偏悲伤时，也可能仍然处于低风险区间。',
)

const trendInsightSourceNote = computed(() => {
  if (!trendInsight.value) return ''
  if (trendInsight.value.status === 'ready') {
    return '以下趋势解读由本地部署的 Gemma 4 模型辅助生成，数据不会上传至外部服务。'
  }
  if (trendInsight.value.status === 'disabled') {
    return '当前未启用本地模型趋势解读，以下内容以统计结果为主。'
  }
  return '当前趋势解读以统计结果为主，本地模型结果暂未成功生成。'
})
const trendInsightTechNote = computed(() =>
  trendInsight.value ? buildNarrativeTechNote(trendInsight.value) : null,
)
const trendInsightHighlights = computed(() =>
  Array.isArray(trendInsight.value?.highlights)
    ? trendInsight.value!.highlights.filter((item) => typeof item === 'string' && item.trim() !== '')
    : [],
)

const formatDateLabel = (value: string) => {
  if (!value) return ''
  return value.length >= 10 ? value.slice(5) : value
}

const getX = (index: number, total: number) => {
  if (total <= 1) return chartPaddingLeft + plotWidth / 2
  return chartPaddingLeft + (plotWidth * index) / (total - 1)
}

const riskToY = (score: number) => {
  const clamped = Math.max(0, Math.min(100, score))
  return chartPaddingTop + plotHeight * (1 - clamped / 100)
}

const countToY = (count: number) => {
  return chartPaddingTop + plotHeight * (1 - Math.max(0, count) / maxCount.value)
}

const riskLinePoints = computed(() => {
  if (!rows.value.length) return ''
  return rows.value
    .map((item, index) => `${getX(index, rows.value.length)},${riskToY(item.avgRiskScore)}`)
    .join(' ')
})

const countBars = computed(() => {
  const barWidth = rows.value.length > 0 ? Math.max(16, plotWidth / Math.max(rows.value.length * 1.9, 1)) : 14
  return rows.value.map((item, index) => {
    const x = getX(index, rows.value.length) - barWidth / 2
    const y = countToY(item.reportCount)
    return {
      x,
      y,
      width: barWidth,
      height: chartPaddingTop + plotHeight - y,
    }
  })
})

const xAxisLabels = computed(() => {
  const total = rows.value.length
  const step = total <= 6 ? 1 : Math.ceil(total / 6)

  return rows.value
    .map((item, index) => ({
      x: getX(index, total),
      label: formatDateLabel(item.date),
      show: index === 0 || index === total - 1 || index % step === 0,
    }))
    .filter((item) => item.show)
})

const riskAxisTicks = computed(() => {
  const values = Array.from(new Set([0, mediumThreshold.value, highThreshold.value, 100]))
  return values.map((value) => ({
    value,
    y: riskToY(value),
  }))
})

const countAxisTicks = computed(() => {
  const values = Array.from(new Set([0, Math.ceil(maxCount.value / 2), maxCount.value]))
  return values.map((value) => ({
    value,
    y: countToY(value),
  }))
})

const thresholdLines = computed(() => [
  { key: 'medium', y: riskToY(mediumThreshold.value), tone: 'medium' },
  { key: 'high', y: riskToY(highThreshold.value), tone: 'high' },
])

const riskBands = computed(() => {
  const top = chartPaddingTop
  const medium = riskToY(mediumThreshold.value)
  const high = riskToY(highThreshold.value)
  const bottom = chartPaddingTop + plotHeight

  return [
    { key: 'high', y: top, height: Math.max(0, high - top), tone: 'high' },
    { key: 'medium', y: high, height: Math.max(0, medium - high), tone: 'medium' },
    { key: 'low', y: medium, height: Math.max(0, bottom - medium), tone: 'low' },
  ]
})

const loadTrendInsight = async () => {
  const requestId = ++latestInsightRequestId
  trendInsightLoading.value = true
  trendInsight.value = null
  try {
    const data = await getReportTrendInsight(days.value)
    if (requestId !== latestInsightRequestId) return
    trendInsight.value = data
  } catch {
    if (requestId !== latestInsightRequestId) return
    trendInsight.value = null
  } finally {
    if (requestId === latestInsightRequestId) {
      trendInsightLoading.value = false
    }
  }
}

const loadTrend = async () => {
  const requestId = ++latestTrendRequestId
  loading.value = true
  errorState.value = null
  try {
    const data = await getReportTrend(days.value)
    if (requestId !== latestTrendRequestId) return
    rows.value = Array.isArray(data.items) ? data.items : []
    if (data.riskThresholds) {
      riskThresholds.value = data.riskThresholds
    }
    if (rows.value.length) {
      void loadTrendInsight()
    } else {
      latestInsightRequestId += 1
      trendInsight.value = null
      trendInsightLoading.value = false
    }
  } catch (error) {
    if (requestId !== latestTrendRequestId) return
    errorState.value = parseError(error, '趋势数据加载失败')
  } finally {
    if (requestId === latestTrendRequestId) {
      loading.value = false
    }
  }
}

onMounted(() => {
  void loadTrend()
})
</script>

<template>
  <div class="trend-page user-layout">
    <SectionBlock
      eyebrow="时间趋势"
      title="个人情绪趋势"
      description="按时间查看报告数量和风险变化，图表口径也一起说明清楚。"
    >
      <div class="toolbar">
        <el-select v-model="days" style="width: 146px">
          <el-option :value="7" label="近 7 天" />
          <el-option :value="30" label="近 30 天" />
          <el-option :value="90" label="近 90 天" />
        </el-select>
        <el-button type="primary" @click="loadTrend">刷新</el-button>
      </div>

      <LoadingState v-if="loading" />
      <ErrorState
        v-else-if="errorState"
        :title="errorState.title"
        :detail="errorState.detail"
        :trace-id="errorState.traceId"
        @retry="loadTrend"
      />
      <EmptyState
        v-else-if="rows.length === 0"
        title="暂无趋势数据"
        description="请先上传并完成语音分析，随后即可查看趋势。"
        action-text="重新加载"
        @action="loadTrend"
      />
      <template v-else>
        <div class="summary-grid">
          <LoreCard title="报告总数" :subtitle="`统计周期：近 ${days} 天`">{{ totalReports }}</LoreCard>
          <LoreCard title="平均风险分" subtitle="0-100 分，按报告数量加权">{{ avgRisk }}</LoreCard>
          <LoreCard
            title="高风险天数"
            :subtitle="`当天至少出现 1 份 ${highRiskLabel} 的报告`"
          >
            {{ highRiskDays }}
          </LoreCard>
        </div>

        <LoreCard
          title="趋势解读"
          subtitle="结合近期报告数量、风险均值和分布变化生成补充说明。"
        >
          <div v-if="trendInsightLoading && !trendInsight" class="trend-insight trend-insight-loading">
            <div class="trend-insight-main">
              <p class="trend-insight-kicker">本地趋势解读</p>
              <h4 class="trend-insight-headline">正在生成趋势解读...</h4>
              <p class="trend-insight-summary">
                本地模型正在根据近期时间序列和风险分布生成更自然的趋势说明，图表和统计结果已可先行查看。
              </p>
            </div>
          </div>
          <div v-else-if="trendInsight" class="trend-insight">
            <div class="trend-insight-layout">
              <div class="trend-insight-main">
                <p class="trend-insight-kicker">本地趋势解读</p>
                <h4 class="trend-insight-headline">{{ trendInsight.headline ?? '近期趋势解读' }}</h4>
                <p class="trend-insight-summary">{{ trendInsight.summary }}</p>
                <p v-if="trendInsight.note" class="trend-insight-note">{{ trendInsight.note }}</p>
              </div>
              <div v-if="trendInsightHighlights.length" class="trend-insight-side">
                <p class="trend-insight-side-title">观察点</p>
                <ul class="trend-insight-list">
                  <li v-for="item in trendInsightHighlights" :key="item">{{ item }}</li>
                </ul>
              </div>
            </div>
            <div class="trend-insight-meta">
              <span class="trend-insight-source">{{ trendInsightSourceNote }}</span>
              <span v-if="trendInsightTechNote" class="trend-insight-tech">{{ trendInsightTechNote }}</span>
            </div>
          </div>
        </LoreCard>

        <LoreCard title="风险趋势图" subtitle="柱状表示当天报告数，折线表示当天平均风险分">
          <div class="chart-caption">
            X 轴：日期，左轴：平均风险分，右轴：报告数。{{ lowRiskLabel }}，{{ mediumRiskLabel }}，{{ highRiskLabel }}。
          </div>

          <div class="chart-head">
            <div class="legend-row">
              <span class="legend-item">
                <span class="legend-swatch legend-bar" />
                <span>当天报告数</span>
              </span>
              <span class="legend-item">
                <span class="legend-swatch legend-line" />
                <span>当天平均风险分</span>
              </span>
            </div>

            <div class="legend-row">
              <span class="legend-item">
                <span class="legend-swatch legend-band-low" />
                <span>{{ lowRiskLabel }}</span>
              </span>
              <span class="legend-item">
                <span class="legend-swatch legend-band-medium" />
                <span>{{ mediumRiskLabel }}</span>
              </span>
              <span class="legend-item">
                <span class="legend-swatch legend-band-high" />
                <span>{{ highRiskLabel }}</span>
              </span>
            </div>
          </div>

          <div class="chart-wrap">
            <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" preserveAspectRatio="none" class="chart">
              <defs>
                <linearGradient id="trend-bar-fill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#67d3dc" stop-opacity="0.88" />
                  <stop offset="100%" stop-color="#2f6076" stop-opacity="0.58" />
                </linearGradient>
                <linearGradient id="trend-plot-bg" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stop-color="#12253f" />
                  <stop offset="100%" stop-color="#0b1628" />
                </linearGradient>
                <filter id="trend-line-glow">
                  <feGaussianBlur stdDeviation="2" result="blur" />
                  <feMerge>
                    <feMergeNode in="blur" />
                    <feMergeNode in="SourceGraphic" />
                  </feMerge>
                </filter>
              </defs>

              <rect
                :x="chartPaddingLeft"
                :y="chartPaddingTop"
                :width="plotWidth"
                :height="plotHeight"
                class="plot-frame"
              />
              <rect
                :x="chartPaddingLeft"
                :y="chartPaddingTop"
                :width="plotWidth"
                :height="plotHeight"
                fill="url(#trend-plot-bg)"
              />

              <rect
                v-for="band in riskBands"
                :key="band.key"
                :x="chartPaddingLeft"
                :y="band.y"
                :width="plotWidth"
                :height="band.height"
                :class="['risk-band', `risk-band-${band.tone}`]"
              />

              <line
                v-for="tick in riskAxisTicks"
                :key="`risk-grid-${tick.value}`"
                :x1="chartPaddingLeft"
                :y1="tick.y"
                :x2="chartWidth - chartPaddingRight"
                :y2="tick.y"
                class="grid-line"
              />

              <line
                v-for="line in thresholdLines"
                :key="`threshold-${line.key}`"
                :x1="chartPaddingLeft"
                :y1="line.y"
                :x2="chartWidth - chartPaddingRight"
                :y2="line.y"
                :class="['threshold-line', `threshold-line-${line.tone}`]"
              />

              <line
                :x1="chartPaddingLeft"
                :y1="chartHeight - chartPaddingBottom"
                :x2="chartWidth - chartPaddingRight"
                :y2="chartHeight - chartPaddingBottom"
                class="axis"
              />
              <line
                :x1="chartPaddingLeft"
                :y1="chartPaddingTop"
                :x2="chartPaddingLeft"
                :y2="chartHeight - chartPaddingBottom"
                class="axis"
              />
              <line
                :x1="chartWidth - chartPaddingRight"
                :y1="chartPaddingTop"
                :x2="chartWidth - chartPaddingRight"
                :y2="chartHeight - chartPaddingBottom"
                class="axis axis-fade"
              />

              <rect
                v-for="(bar, index) in countBars"
                :key="`bar-${index}`"
                :x="bar.x"
                :y="bar.y"
                :width="bar.width"
                :height="bar.height"
                rx="3"
                fill="url(#trend-bar-fill)"
                class="bar"
              />

              <polyline
                v-if="riskLinePoints"
                :points="riskLinePoints"
                class="risk-line"
                filter="url(#trend-line-glow)"
              />

              <circle
                v-for="(item, index) in rows"
                :key="`point-${item.date}-${index}`"
                :cx="getX(index, rows.length)"
                :cy="riskToY(item.avgRiskScore)"
                r="3.2"
                class="risk-point"
              />

              <text
                v-for="tick in riskAxisTicks"
                :key="`risk-axis-${tick.value}`"
                :x="chartPaddingLeft - 8"
                :y="tick.y + 4"
                class="axis-label axis-label-left"
              >
                {{ formatThreshold(tick.value) }}
              </text>

              <text
                v-for="tick in countAxisTicks"
                :key="`count-axis-${tick.value}`"
                :x="chartWidth - chartPaddingRight + 8"
                :y="tick.y + 4"
                class="axis-label axis-label-right"
              >
                {{ tick.value }}
              </text>

              <text
                v-for="label in xAxisLabels"
                :key="`date-${label.label}`"
                :x="label.x"
                :y="chartHeight - 14"
                class="axis-label axis-label-bottom"
              >
                {{ label.label }}
              </text>

              <text :x="chartPaddingLeft" :y="14" class="axis-title">平均风险分</text>
              <text :x="chartWidth - chartPaddingRight" :y="14" class="axis-title axis-title-right">报告数</text>
            </svg>
          </div>

          <div class="chart-note">{{ formulaText }}。{{ explanationText }}</div>
        </LoreCard>

        <el-table :data="rows" border size="small">
          <el-table-column prop="date" label="日期" min-width="140" />
          <el-table-column prop="reportCount" label="报告数" width="110" />
          <el-table-column prop="avgRiskScore" label="平均风险分" width="140" />
          <el-table-column prop="lowCount" :label="lowRiskLabel" min-width="120" />
          <el-table-column prop="mediumCount" :label="mediumRiskLabel" min-width="140" />
          <el-table-column prop="highCount" :label="highRiskLabel" min-width="120" />
        </el-table>
      </template>
    </SectionBlock>
  </div>
</template>

<style scoped>
.trend-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.trend-insight {
  display: grid;
  gap: 14px;
}

.trend-insight-loading {
  min-height: 140px;
  align-items: center;
}

.trend-insight-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(240px, 0.95fr);
  gap: 14px;
}

.trend-insight-main {
  display: grid;
  gap: 10px;
}

.trend-insight-kicker {
  margin: 0;
  color: rgba(141, 192, 234, 0.82);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.trend-insight-headline {
  margin: 0;
  color: #f7fbff;
  font-size: 24px;
  line-height: 1.2;
}

.trend-insight-summary {
  margin: 0;
  color: rgba(223, 234, 248, 0.92);
  line-height: 1.8;
}

.trend-insight-note {
  margin: 0;
  color: rgba(160, 182, 214, 0.78);
  font-size: 12px;
  line-height: 1.7;
}

.trend-insight-side {
  border: 1px solid rgba(147, 170, 211, 0.18);
  border-radius: 14px;
  padding: 14px;
  background:
    linear-gradient(180deg, rgba(17, 29, 50, 0.72), rgba(10, 17, 31, 0.76)),
    radial-gradient(circle at top right, rgba(93, 140, 205, 0.12), transparent 42%);
}

.trend-insight-side-title {
  margin: 0 0 10px;
  color: #eaf2ff;
  font-size: 13px;
  font-weight: 600;
}

.trend-insight-list {
  margin: 0;
  padding-left: 18px;
  color: rgba(220, 231, 247, 0.9);
  line-height: 1.8;
}

.trend-insight-meta {
  display: grid;
  gap: 4px;
}

.trend-insight-source {
  color: rgba(184, 204, 236, 0.8);
  font-size: 12px;
  line-height: 1.6;
}

.trend-insight-tech {
  color: rgba(146, 170, 209, 0.76);
  font-size: 12px;
  line-height: 1.5;
}

.chart-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.chart-caption {
  margin-bottom: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: rgba(188, 199, 220, 0.76);
}

.legend-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: rgba(231, 237, 247, 0.86);
  font-size: 13px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.legend-swatch {
  display: inline-block;
  width: 18px;
  height: 10px;
  border-radius: 999px;
}

.legend-bar {
  background: linear-gradient(180deg, rgba(103, 211, 220, 0.95), rgba(47, 96, 118, 0.72));
}

.legend-line {
  height: 2px;
  background: #e2b980;
}

.legend-band-low {
  background: rgba(83, 169, 113, 0.32);
}

.legend-band-medium {
  background: rgba(213, 162, 74, 0.32);
}

.legend-band-high {
  background: rgba(219, 96, 96, 0.28);
}

.chart-wrap {
  width: 100%;
  border: 1px solid rgba(145, 166, 206, 0.28);
  border-radius: 16px;
  padding: 14px;
  background:
    radial-gradient(circle at top left, rgba(76, 133, 188, 0.12), transparent 42%),
    rgba(8, 17, 32, 0.92);
}

.chart {
  width: 100%;
  height: 340px;
}

.plot-frame {
  fill: none;
  stroke: rgba(151, 174, 214, 0.12);
  stroke-width: 1;
}

.risk-band-low {
  fill: rgba(83, 169, 113, 0.08);
}

.risk-band-medium {
  fill: rgba(213, 162, 74, 0.08);
}

.risk-band-high {
  fill: rgba(219, 96, 96, 0.08);
}

.grid-line {
  stroke: rgba(164, 183, 219, 0.16);
  stroke-dasharray: 4 5;
}

.threshold-line {
  stroke-width: 1.15;
  stroke-dasharray: 5 5;
}

.threshold-line-medium {
  stroke: rgba(232, 180, 92, 0.68);
}

.threshold-line-high {
  stroke: rgba(236, 128, 128, 0.74);
}

.axis {
  stroke: rgba(148, 170, 208, 0.82);
  stroke-width: 1.15;
}

.axis-fade {
  opacity: 0.55;
}

.bar {
  opacity: 0.94;
}

.risk-line {
  fill: none;
  stroke: #e2b980;
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.risk-point {
  fill: #f2d4a8;
  stroke: rgba(10, 18, 32, 0.96);
  stroke-width: 1.4;
}

.axis-title,
.axis-label {
  fill: rgba(221, 229, 243, 0.88);
}

.axis-title {
  font-size: 12px;
  font-weight: 600;
}

.axis-title-right {
  text-anchor: end;
}

.axis-label {
  font-size: 11px;
}

.axis-label-left {
  text-anchor: end;
}

.axis-label-bottom {
  text-anchor: middle;
}

.chart-note {
  margin-top: 12px;
  font-size: 12px;
  color: rgba(188, 199, 220, 0.74);
  line-height: 1.7;
}

@media (max-width: 980px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .trend-insight-layout {
    grid-template-columns: 1fr;
  }
}
</style>
