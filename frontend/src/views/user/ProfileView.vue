<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getReportList, getReportTrend } from '@/api/report'
import EmotionArchiveView from '@/views/user/EmotionArchiveView.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import KpiGrid from '@/components/ui/KpiGrid.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import { useUserAuthStore } from '@/stores/userAuth'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const TREND_DAYS = 30

const route = useRoute()
const router = useRouter()
const authStore = useUserAuthStore()

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const totalReports = ref(0)
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
  const highDays = trendRows.value.filter((item) => Number(item.highCount || 0) > 0).length
  const avgScore =
    trendRows.value.length === 0
      ? 0
      : trendRows.value.reduce((acc, item) => acc + Number(item.avgRiskScore || 0), 0) /
        trendRows.value.length

  return {
    totalReports: totalReports.value,
    highDays,
    avgScore: avgScore.toFixed(1),
  }
})

const hasHistoricalReports = computed(() => totalReports.value > 0)

const emptyState = computed(() =>
  hasHistoricalReports.value
    ? {
        title: `近 ${TREND_DAYS} 天暂时没有新的趋势记录`,
        description: '之前的报告还在，你可以先去报告中心回看，再继续往下补记录。',
        actionText: '查看报告中心',
      }
    : {
        title: '还没有报告数据',
        description: '先留下一次语音记录，后面这里就会慢慢长出属于你的情绪档案。',
        actionText: '去上传语音',
      },
)

const displayRole = computed(() => {
  if (!authStore.userRole) return '-'
  if (authStore.userRole === 'USER') return '用户'
  if (authStore.userRole === 'ADMIN') return '管理员'
  return authStore.userRole
})

const loadProfileSummary = async () => {
  loading.value = true
  errorState.value = null
  try {
    const [trend, reportList] = await Promise.all([
      getReportTrend(TREND_DAYS),
      getReportList({ page: 1, pageSize: 1 }),
    ])
    trendRows.value = trend.items ?? []
    totalReports.value = Number(reportList.data.total ?? 0)
  } catch (error) {
    errorState.value = parseError(error, '个人概览加载失败')
  } finally {
    loading.value = false
  }
}

const handleEmptyAction = () => {
  void router.push(hasHistoricalReports.value ? '/app/reports' : '/app/upload')
}

const scrollToArchive = async () => {
  await nextTick()
  const target = document.getElementById('profile-emotion-archive')
  if (!target) return
  target.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

onMounted(() => {
  void loadProfileSummary()
})

watch(
  () => route.query.section,
  (section) => {
    if (section === 'archive') {
      void scrollToArchive()
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="profile-page user-layout">
    <SectionBlock
      eyebrow="我的信息"
      title="个人中心"
      description="账号信息、最近的报告概览和长期情绪档案都放在这里。"
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
            <p class="label">用户名</p>
            <strong>{{ authStore.currentUser?.username || '-' }}</strong>
          </div>
          <div>
            <p class="label">角色</p>
            <strong>{{ displayRole }}</strong>
          </div>
        </div>

        <KpiGrid
          :items="[
            { label: '报告总数', value: summary.totalReports, helper: '你一共生成过多少份报告' },
            { label: '近 30 天平均风险分', value: summary.avgScore, helper: '按近 30 天每日结果计算' },
            { label: '近 30 天高风险天数', value: summary.highDays, helper: '当天出现高风险报告就会计入' },
            { label: '近 30 天趋势记录', value: trendRows.length, helper: '按天整理出来的记录数' },
          ]"
        />

        <div class="trend-panel">
          <div class="subsection-head">
            <div>
              <p class="subsection-eyebrow">最近变化</p>
              <h3 class="subsection-title">最近 7 个有记录的日子</h3>
            </div>
            <p class="subsection-description">先从近几天回看一下，再往下看完整的情绪档案时间轴。</p>
          </div>

          <EmptyState
            v-if="trendRows.length === 0"
            :title="emptyState.title"
            :description="emptyState.description"
            :action-text="emptyState.actionText"
            @action="handleEmptyAction"
          />
          <el-table v-else :data="trendRows.slice(-7).reverse()" border>
            <el-table-column prop="date" label="日期" min-width="140" />
            <el-table-column prop="reportCount" label="报告数" width="100" />
            <el-table-column prop="avgRiskScore" label="平均分" width="120" />
            <el-table-column prop="highCount" label="高风险" width="110" />
          </el-table>
        </div>
      </template>
    </SectionBlock>

    <div id="profile-emotion-archive" class="archive-section-shell">
      <EmotionArchiveView />
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.archive-section-shell {
  display: flex;
  flex-direction: column;
}

.identity-card {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
  border: 1px solid var(--user-border);
  border-radius: 14px;
  padding: 12px 14px;
  background: var(--user-section-bg);
}

.identity-card strong {
  color: var(--user-text-primary);
  font-size: 15px;
}

.label {
  color: var(--user-text-secondary);
  margin: 0 0 6px;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.trend-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.subsection-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 18px;
  flex-wrap: wrap;
}

.subsection-eyebrow {
  margin: 0 0 6px;
  color: var(--user-text-secondary);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.subsection-title {
  margin: 0;
  color: var(--user-text-primary);
  font-size: 18px;
}

.subsection-description {
  margin: 0;
  max-width: 460px;
  color: var(--user-text-secondary);
  line-height: 1.6;
}

@media (max-width: 880px) {
  .subsection-head {
    align-items: flex-start;
  }
}
</style>
