<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { useAuthStore } from '@/stores/auth'
import { getReportTrend } from '@/api/report'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import KpiGrid from '@/components/ui/KpiGrid.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const authStore = useAuthStore()
const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const trendRows = ref<
  Array<{
    date: string
    reportCount: number
    avgRiskScore: number
    lowCount: number
    mediumCount: number
    highCount: number
  }>
>([])

const summary = computed(() => {
  const totalReports = trendRows.value.reduce((acc, item) => acc + Number(item.reportCount || 0), 0)
  const highDays = trendRows.value.filter((item) => Number(item.highCount || 0) > 0).length
  const avgScore =
    trendRows.value.length === 0
      ? 0
      : trendRows.value.reduce((acc, item) => acc + Number(item.avgRiskScore || 0), 0) /
        trendRows.value.length

  return {
    totalReports,
    highDays,
    avgScore: avgScore.toFixed(1),
  }
})

const loadProfileSummary = async () => {
  loading.value = true
  errorState.value = null
  try {
    const trend = await getReportTrend(30)
    trendRows.value = trend.items ?? []
  } catch (error) {
    errorState.value = parseError(error, 'Failed to load profile summary')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadProfileSummary()
})
</script>

<template>
  <div class="profile-page user-layout">
    <SectionBlock eyebrow="Account" title="Personal Profile" description="Simple account panel and last 30-day summary.">
      <LoadingState v-if="loading" />
      <ErrorState
        v-else-if="errorState"
        :title="errorState.title"
        :detail="errorState.detail"
        :trace-id="errorState.traceId"
        @retry="loadProfileSummary"
      />
      <template v-else>
        <div class="user-line">
          <span class="label">Username:</span>
          <strong>{{ authStore.currentUser?.username || '-' }}</strong>
          <span class="label">Role:</span>
          <strong>{{ authStore.userRole || '-' }}</strong>
        </div>

        <KpiGrid
          :items="[
            { label: 'Reports (30D)', value: summary.totalReports },
            { label: 'Avg Risk Score', value: summary.avgScore },
            { label: 'High Risk Days', value: summary.highDays },
            { label: 'Trend Rows', value: trendRows.length },
          ]"
        />

        <EmptyState
          v-if="trendRows.length === 0"
          title="No trend yet"
          description="Upload audio and generate reports to build your timeline."
          action-text="Refresh"
          @action="loadProfileSummary"
        />
      </template>
    </SectionBlock>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
}

.user-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: #d4e3fb;
}

.label {
  color: #9fb6dc;
}
</style>
