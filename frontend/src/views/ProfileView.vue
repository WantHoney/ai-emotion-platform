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
    <SectionBlock
      eyebrow="Account"
      title="Personal Profile"
      description="Account identity and 30-day emotional activity snapshot."
    >
      <LoadingState v-if="loading" />
      <ErrorState
        v-else-if="errorState"
        :title="errorState.title"
        :detail="errorState.detail"
        :trace-id="errorState.traceId"
        @retry="loadProfileSummary"
      />
      <template v-else>
        <div class="identity-card">
          <div>
            <p class="label">Username</p>
            <strong>{{ authStore.currentUser?.username || '-' }}</strong>
          </div>
          <div>
            <p class="label">Role</p>
            <strong>{{ authStore.userRole || '-' }}</strong>
          </div>
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
        <el-table v-else :data="trendRows.slice(-7).reverse()" border>
          <el-table-column prop="date" label="Date" min-width="140" />
          <el-table-column prop="reportCount" label="Reports" width="100" />
          <el-table-column prop="avgRiskScore" label="Avg Score" width="120" />
          <el-table-column prop="highCount" label="High Risk" width="110" />
        </el-table>
      </template>
    </SectionBlock>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
}

.identity-card {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
  border: 1px solid rgba(145, 167, 207, 0.32);
  border-radius: 14px;
  padding: 12px 14px;
  background: rgba(13, 22, 39, 0.8);
}

.identity-card strong {
  color: #eff5ff;
  font-size: 15px;
}

.label {
  color: #9fb6dc;
  margin: 0 0 6px;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
</style>
