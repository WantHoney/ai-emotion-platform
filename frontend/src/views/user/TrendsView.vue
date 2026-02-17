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
    errorState.value = parseError(error, '趋势数据加载失败')
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
      eyebrow="时间趋势"
      title="个人情绪趋势"
      description="按时间窗口查看报告数量与风险分走势。"
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
          <LoreCard title="平均风险分" subtitle="0-100 标准化">{{ avgRisk }}</LoreCard>
          <LoreCard title="高风险天数" subtitle="当天至少有 1 条高风险（HIGH）报告">{{ highRiskDays }}</LoreCard>
        </div>

        <LoreCard title="风险曲线与报告数量" subtitle="折线=平均风险分，柱状=报告数">
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
          <el-table-column prop="date" label="日期" min-width="140" />
          <el-table-column prop="reportCount" label="报告数" width="110" />
          <el-table-column prop="avgRiskScore" label="平均风险分" width="140" />
          <el-table-column prop="lowCount" label="低风险" width="90" />
          <el-table-column prop="mediumCount" label="中风险" width="90" />
          <el-table-column prop="highCount" label="高风险" width="90" />
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
