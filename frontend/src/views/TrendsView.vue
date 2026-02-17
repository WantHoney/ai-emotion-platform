<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import LoadingState from '@/components/states/LoadingState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import { getReportTrend, type ReportTrendItem } from '@/api/report'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const rows = ref<ReportTrendItem[]>([])
const errorState = ref<ErrorStatePayload | null>(null)
const days = ref(30)

const chartWidth = 900
const chartHeight = 280
const chartPaddingX = 48
const chartPaddingY = 24

const maxCount = computed(() => {
  const maxValue = Math.max(0, ...rows.value.map((item) => item.reportCount))
  return maxValue <= 0 ? 1 : maxValue
})

const riskLinePoints = computed(() => {
  if (rows.value.length <= 1) {
    return ''
  }
  const drawWidth = chartWidth - chartPaddingX * 2
  const drawHeight = chartHeight - chartPaddingY * 2

  return rows.value
    .map((item, index) => {
      const x = chartPaddingX + (drawWidth * index) / (rows.value.length - 1)
      const clampedRisk = Math.max(0, Math.min(100, item.avgRiskScore))
      const y = chartPaddingY + drawHeight * (1 - clampedRisk / 100)
      return `${x},${y}`
    })
    .join(' ')
})

const countBars = computed(() => {
  const drawWidth = chartWidth - chartPaddingX * 2
  const drawHeight = chartHeight - chartPaddingY * 2
  const barWidth = rows.value.length > 0 ? Math.max(10, drawWidth / Math.max(rows.value.length * 1.6, 1)) : 12

  return rows.value.map((item, index) => {
    const x = chartPaddingX + (drawWidth * index) / Math.max(rows.value.length - 1, 1) - barWidth / 2
    const h = (item.reportCount / maxCount.value) * drawHeight
    const y = chartPaddingY + drawHeight - h
    return {
      x,
      y,
      width: barWidth,
      height: h,
    }
  })
})

const avgRisk = computed(() => {
  if (!rows.value.length) return 0
  const sum = rows.value.reduce((acc, item) => acc + item.avgRiskScore, 0)
  return Math.round((sum / rows.value.length) * 100) / 100
})

const totalReports = computed(() => rows.value.reduce((acc, item) => acc + item.reportCount, 0))

const highRiskDays = computed(() => rows.value.filter((item) => item.highCount > 0).length)

const loadTrend = async () => {
  loading.value = true
  errorState.value = null
  try {
    const data = await getReportTrend(days.value)
    rows.value = Array.isArray(data.items) ? data.items : []
  } catch (error) {
    errorState.value = parseError(error, 'Failed to load report trends')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadTrend()
})
</script>

<template>
  <div class="trend-page user-layout">
    <SectionBlock
      eyebrow="Timeline"
      title="Personal Mood Trend"
      description="View report frequency and risk score trajectory over selected time windows."
    >
      <div class="toolbar">
        <el-select v-model="days" style="width: 146px">
          <el-option :value="7" label="Last 7 days" />
          <el-option :value="30" label="Last 30 days" />
          <el-option :value="90" label="Last 90 days" />
        </el-select>
        <el-button type="primary" @click="loadTrend">Refresh</el-button>
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
        title="No trend data"
        description="Upload and analyze audio first, then your trend will be shown here."
        action-text="Reload"
        @action="loadTrend"
      />
      <template v-else>
        <div class="summary-grid">
          <LoreCard title="Total Reports" :subtitle="`Within ${days} days`">{{ totalReports }}</LoreCard>
          <LoreCard title="Average Risk Score" subtitle="0-100 normalized">{{ avgRisk }}</LoreCard>
          <LoreCard title="Days with High Risk" subtitle="At least one HIGH report">{{ highRiskDays }}</LoreCard>
        </div>

        <LoreCard title="Risk Line and Report Volume" subtitle="Line = avg risk score, bars = report count">
          <div class="chart-wrap">
            <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" preserveAspectRatio="none" class="chart">
              <line
                :x1="chartPaddingX"
                :y1="chartHeight - chartPaddingY"
                :x2="chartWidth - chartPaddingX"
                :y2="chartHeight - chartPaddingY"
                class="axis"
              />
              <line
                :x1="chartPaddingX"
                :y1="chartPaddingY"
                :x2="chartPaddingX"
                :y2="chartHeight - chartPaddingY"
                class="axis"
              />
              <line
                :x1="chartPaddingX"
                :y1="chartPaddingY + (chartHeight - chartPaddingY * 2) / 2"
                :x2="chartWidth - chartPaddingX"
                :y2="chartPaddingY + (chartHeight - chartPaddingY * 2) / 2"
                class="grid-line"
              />
              <rect
                v-for="(bar, index) in countBars"
                :key="`bar-${index}`"
                :x="bar.x"
                :y="bar.y"
                :width="bar.width"
                :height="bar.height"
                rx="2"
                class="bar"
              />
              <polyline v-if="riskLinePoints" :points="riskLinePoints" class="risk-line" />
            </svg>
          </div>
        </LoreCard>

        <el-table :data="rows" border size="small">
          <el-table-column prop="date" label="Date" min-width="140" />
          <el-table-column prop="reportCount" label="Reports" width="110" />
          <el-table-column prop="avgRiskScore" label="Avg Risk Score" width="140" />
          <el-table-column prop="lowCount" label="Low" width="90" />
          <el-table-column prop="mediumCount" label="Medium" width="90" />
          <el-table-column prop="highCount" label="High" width="90" />
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

.chart-wrap {
  width: 100%;
  border: 1px solid rgba(145, 166, 206, 0.38);
  border-radius: 12px;
  padding: 10px;
  background: rgba(11, 19, 36, 0.8);
}

.chart {
  width: 100%;
  height: 280px;
}

.axis {
  stroke: #8ca6d1;
  stroke-width: 1.2;
}

.grid-line {
  stroke: rgba(163, 183, 219, 0.5);
  stroke-dasharray: 4 4;
}

.bar {
  fill: rgba(76, 168, 188, 0.5);
}

.risk-line {
  fill: none;
  stroke: #d5b17f;
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
