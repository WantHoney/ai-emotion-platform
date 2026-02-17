<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  getAnalyticsQuality,
  getDailyAnalytics,
  getGovernanceSummary,
  type AnalyticsQualityResponse,
  type DailyAnalyticsItem,
  type GovernanceSummary,
} from '@/api/governance'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const dailyRows = ref<DailyAnalyticsItem[]>([])
const days = ref(14)
const summary = ref<GovernanceSummary>({
  ruleCount: 0,
  enabledRuleCount: 0,
  warningCount: 0,
})
const quality = ref<AnalyticsQualityResponse | null>(null)

const totals = computed(() => {
  return dailyRows.value.reduce(
    (acc, row) => {
      acc.dau += Number(row.dau || 0)
      acc.uploadCount += Number(row.upload_count || 0)
      acc.reportCount += Number(row.report_count || 0)
      acc.warningCount += Number(row.warning_count || 0)
      return acc
    },
    { dau: 0, uploadCount: 0, reportCount: 0, warningCount: 0 },
  )
})

const maxErrorCategoryCount = computed(() => {
  if (!quality.value?.errorCategoryStats.length) return 1
  return Math.max(...quality.value.errorCategoryStats.map((item) => Number(item.count || 0)), 1)
})

const loadData = async () => {
  loading.value = true
  errorState.value = null
  try {
    const [dailyData, summaryData, qualityData] = await Promise.all([
      getDailyAnalytics(days.value),
      getGovernanceSummary(),
      getAnalyticsQuality({ windowDays: Math.min(days.value, 30), baselineDays: 7 }),
    ])
    dailyRows.value = dailyData.items ?? []
    summary.value = {
      ruleCount: Number(summaryData.ruleCount ?? 0),
      enabledRuleCount: Number(summaryData.enabledRuleCount ?? 0),
      warningCount: Number(summaryData.warningCount ?? 0),
    }
    quality.value = qualityData
  } catch (error) {
    errorState.value = parseError(error, '统计分析数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadData()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>管理端统计分析</span>
        <div class="header-actions">
          <el-input-number v-model="days" :min="1" :max="30" size="small" />
          <el-button @click="loadData">刷新</el-button>
        </div>
      </div>
    </template>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadData"
    />
    <template v-else>
      <el-row :gutter="16">
        <el-col :xs="24" :md="6"><el-statistic title="规则总数" :value="summary.ruleCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="启用规则数" :value="summary.enabledRuleCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="预警总量" :value="summary.warningCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="日统计条数" :value="dailyRows.length" /></el-col>
      </el-row>

      <el-row :gutter="16" class="mt-16">
        <el-col :xs="24" :md="6"><el-statistic title="DAU 累计" :value="totals.dau" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="上传累计" :value="totals.uploadCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="报告累计" :value="totals.reportCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="预警累计" :value="totals.warningCount" /></el-col>
      </el-row>

      <el-row v-if="quality" :gutter="16" class="mt-16">
        <el-col :xs="24" :md="8">
          <el-card shadow="never" class="metric-card">
            <template #header>SLA 概览</template>
            <p>总数：{{ quality.slaOverview.total ?? 0 }}</p>
            <p>已解决：{{ quality.slaOverview.resolved ?? 0 }}</p>
            <p>已超时：{{ quality.slaOverview.breached ?? 0 }}</p>
            <p>已确认：{{ quality.slaOverview.acked ?? 0 }}</p>
            <p>平均确认时长（分钟）：{{ Number(quality.slaOverview.avg_ack_minutes ?? 0).toFixed(1) }}</p>
            <p>平均解决时长（分钟）：{{ Number(quality.slaOverview.avg_resolve_minutes ?? 0).toFixed(1) }}</p>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="16">
          <el-card shadow="never">
            <template #header>情绪分布漂移（当前 vs 基线）</template>
            <el-table :data="quality.emotionDrift.slice(0, 8)" size="small" border>
              <el-table-column prop="emotion" label="情绪" width="120" />
              <el-table-column label="当前占比">
                <template #default="scope">{{ (scope.row.currentRatio * 100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column label="基线占比">
                <template #default="scope">{{ (scope.row.baselineRatio * 100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column label="漂移值">
                <template #default="scope">
                  <el-tag :type="scope.row.drift >= 0 ? 'danger' : 'success'">
                    {{ scope.row.drift >= 0 ? '+' : '' }}{{ (scope.row.drift * 100).toFixed(1) }}%
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-row v-if="quality" :gutter="16" class="mt-16">
        <el-col :xs="24" :md="10">
          <el-card shadow="never">
            <template #header>错误类别样本</template>
            <div
              v-for="item in quality.errorCategoryStats"
              :key="item.category"
              class="bar-row"
            >
              <span class="bar-label">{{ item.category }}</span>
              <div class="bar-track">
                <div
                  class="bar-fill"
                  :style="{ width: `${(Number(item.count || 0) / maxErrorCategoryCount) * 100}%` }"
                />
              </div>
              <span class="bar-value">{{ item.count }}</span>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14">
          <el-card shadow="never">
            <template #header>近期错误样本</template>
            <el-table :data="quality.errorSamples" size="small" border>
              <el-table-column prop="id" label="任务" width="90" />
              <el-table-column prop="audio_file_id" label="音频" width="90" />
              <el-table-column prop="error_message" label="错误信息" min-width="260" show-overflow-tooltip />
              <el-table-column prop="updated_at" label="更新时间" min-width="160" />
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-card v-if="quality" shadow="never" class="mt-16">
        <template #header>SLA 趋势</template>
        <el-table :data="quality.slaTrend" size="small" border>
          <el-table-column prop="stat_date" label="日期" min-width="140" />
          <el-table-column prop="total" label="总数" width="110" />
          <el-table-column prop="resolved" label="已解决" width="110" />
          <el-table-column prop="breached" label="已超时" width="110" />
        </el-table>
      </el-card>

      <EmptyState
        v-if="dailyRows.length === 0"
        class="mt-16"
        title="暂无日统计数据"
        description="所选时间范围内未找到日汇总数据。"
        action-text="重新加载"
        @action="loadData"
      />
      <el-table v-else :data="dailyRows" border class="mt-16">
        <el-table-column prop="stat_date" label="日期" min-width="160" />
        <el-table-column prop="dau" label="DAU" width="120" />
        <el-table-column prop="upload_count" label="上传数" width="120" />
        <el-table-column prop="report_count" label="报告数" width="120" />
        <el-table-column prop="warning_count" label="预警数" width="120" />
      </el-table>
    </template>
  </el-card>
</template>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mt-16 {
  margin-top: 16px;
}

.metric-card p {
  margin: 6px 0;
}

.bar-row {
  display: grid;
  grid-template-columns: 90px 1fr 60px;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.bar-label {
  color: #334155;
  font-size: 13px;
}

.bar-track {
  height: 10px;
  background: #e2e8f0;
  border-radius: 999px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #f97316, #ef4444);
}

.bar-value {
  text-align: right;
  color: #0f172a;
  font-size: 12px;
}
</style>
