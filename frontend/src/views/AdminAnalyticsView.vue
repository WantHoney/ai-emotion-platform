<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  getDailyAnalytics,
  getGovernanceSummary,
  type DailyAnalyticsItem,
  type GovernanceSummary,
} from '@/api/governance'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const rows = ref<DailyAnalyticsItem[]>([])
const days = ref(14)
const summary = ref<GovernanceSummary>({
  ruleCount: 0,
  enabledRuleCount: 0,
  warningCount: 0,
})

const totals = computed(() => {
  return rows.value.reduce(
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

const loadData = async () => {
  loading.value = true
  errorState.value = null
  try {
    const [dailyData, summaryData] = await Promise.all([
      getDailyAnalytics(days.value),
      getGovernanceSummary(),
    ])
    rows.value = dailyData.items ?? []
    summary.value = {
      ruleCount: Number(summaryData.ruleCount ?? 0),
      enabledRuleCount: Number(summaryData.enabledRuleCount ?? 0),
      warningCount: Number(summaryData.warningCount ?? 0),
    }
  } catch (error) {
    errorState.value = parseError(error, 'Failed to load analytics data')
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
        <span>Admin Analytics</span>
        <div class="header-actions">
          <el-input-number v-model="days" :min="1" :max="30" size="small" />
          <el-button @click="loadData">Refresh</el-button>
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
        <el-col :xs="24" :md="6"><el-statistic title="Rule Count" :value="summary.ruleCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="Enabled Rules" :value="summary.enabledRuleCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="Warnings (All)" :value="summary.warningCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="Daily Rows" :value="rows.length" /></el-col>
      </el-row>

      <el-row :gutter="16" class="mt-16">
        <el-col :xs="24" :md="6"><el-statistic title="DAU Sum" :value="totals.dau" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="Uploads Sum" :value="totals.uploadCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="Reports Sum" :value="totals.reportCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="Warnings Sum" :value="totals.warningCount" /></el-col>
      </el-row>

      <EmptyState
        v-if="rows.length === 0"
        class="mt-16"
        title="No daily analytics"
        description="No daily summary data found in the selected date range."
        action-text="Reload"
        @action="loadData"
      />
      <el-table v-else :data="rows" border class="mt-16">
        <el-table-column prop="stat_date" label="Date" min-width="160" />
        <el-table-column prop="dau" label="DAU" width="120" />
        <el-table-column prop="upload_count" label="Uploads" width="120" />
        <el-table-column prop="report_count" label="Reports" width="120" />
        <el-table-column prop="warning_count" label="Warnings" width="120" />
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
</style>
