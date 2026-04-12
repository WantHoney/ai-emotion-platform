<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getReportList, getReportTrend } from '@/api/report'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import KpiGrid from '@/components/ui/KpiGrid.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import { useUserAuthStore } from '@/stores/userAuth'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const TREND_DAYS = 30

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
        title: `近 ${TREND_DAYS} 天暂无趋势记录`,
        description: '之前的报告还在，你可以先去报告中心看看。',
        actionText: '查看报告中心',
      }
    : {
        title: '暂无报告数据',
        description: '先上传一段语音，后面就能慢慢看到自己的变化。',
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

onMounted(() => {
  void loadProfileSummary()
})
</script>

<template>
  <div class="profile-page user-layout">
    <SectionBlock
      eyebrow="我的信息"
      title="个人中心"
      description="账号信息、最近的报告情况和使用说明都放在这里。"
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
            { label: '近30天平均风险分', value: summary.avgScore, helper: '按近 30 天每日结果计算' },
            { label: '近30天高风险天数', value: summary.highDays, helper: '当天出现高风险报告就会计入' },
            { label: '近30天趋势记录', value: trendRows.length, helper: '按天整理出来的记录数' },
          ]"
        />

        <div class="trend-panel">
          <div class="subsection-head">
            <div>
              <p class="subsection-eyebrow">最近变化</p>
              <h3 class="subsection-title">最近 7 个有记录的日期</h3>
            </div>
            <p class="subsection-description">按天看看最近有没有明显变化。</p>
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

    <SectionBlock
      eyebrow="使用说明"
      title="这个系统能帮你做什么"
      description="如果你是第一次使用，可以先从这里快速了解。"
    >
      <div class="about-grid">
        <article class="about-card">
          <p class="subsection-eyebrow">你可以做什么</p>
          <h3>从语音到报告，再到后续支持</h3>
          <p>上传或录制语音后，系统会生成分析结果、报告，并给出内容与支持资源。</p>
        </article>
        <article class="about-card">
          <p class="subsection-eyebrow">怎么开始</p>
          <h3>先完成一次上传，再继续往下看</h3>
          <p>如果你刚开始用，建议先去语音上传页完成一次分析，再回来查看报告、趋势和内容。</p>
        </article>
      </div>
    </SectionBlock>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
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
  color: #9fb6dc;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.subsection-title {
  margin: 0;
  color: #eff5ff;
  font-size: 18px;
}

.subsection-description {
  margin: 0;
  max-width: 460px;
  color: #a9bedf;
  line-height: 1.6;
}

.about-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.about-card {
  border: 1px solid rgba(145, 167, 207, 0.32);
  border-radius: 16px;
  padding: 18px;
  background: linear-gradient(180deg, rgba(20, 31, 52, 0.82), rgba(13, 21, 36, 0.9));
}

.about-card h3 {
  margin: 0 0 10px;
  color: #eff5ff;
  font-size: 20px;
}

.about-card p:last-child {
  margin: 0;
  color: #bfd1ee;
  line-height: 1.75;
}

@media (max-width: 880px) {
  .about-grid {
    grid-template-columns: 1fr;
  }

  .subsection-head {
    align-items: flex-start;
  }
}
</style>
