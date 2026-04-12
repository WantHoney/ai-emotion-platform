<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  getAnalyticsQuality,
  getDailyAnalytics,
  getGovernanceSummary,
  triggerGovernanceDriftScan,
  type AnalyticsQualityResponse,
  type DailyAnalyticsItem,
  type GovernanceSummary,
} from '@/api/governance'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { SLA_LABEL, formatEmotion } from '@/utils/uiText'

const router = useRouter()

const loading = ref(false)
const driftScanning = ref(false)
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

const activeDayCount = computed(() =>
  dailyRows.value.filter((row) => {
    return (
      Number(row.dau || 0) > 0 ||
      Number(row.upload_count || 0) > 0 ||
      Number(row.report_count || 0) > 0 ||
      Number(row.warning_count || 0) > 0
    )
  }).length,
)

const maxErrorCategoryCount = computed(() => {
  if (!quality.value?.errorCategoryStats.length) return 1
  return Math.max(...quality.value.errorCategoryStats.map((item) => Number(item.count || 0)), 1)
})

const emotionDriftRows = computed(() => quality.value?.emotionDrift.slice(0, 8) ?? [])
const errorCategoryRows = computed(() => quality.value?.errorCategoryStats ?? [])
const errorSamples = computed(() => quality.value?.errorSamples ?? [])
const slaTrendRows = computed(() => quality.value?.slaTrend ?? [])

const dashboardCards = computed(() => [
  {
    title: '预警总量',
    value: summary.value.warningCount,
    note: '跳转预警处置台查看当前闭环进度',
    routeName: 'adminWarnings',
  },
  {
    title: '启用规则数',
    value: summary.value.enabledRuleCount,
    note: '跳转预警规则查看当前生效阈值',
    routeName: 'adminRules',
  },
  {
    title: '有数据日期数',
    value: activeDayCount.value,
    note: '当前统计窗口内实际有数据的日期数',
    routeName: 'adminDashboard',
  },
  {
    title: '漂移样本数',
    value: emotionDriftRows.value.length,
    note: '跳转模型治理核对当前运行态与登记态',
    routeName: 'adminModels',
  },
])

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

const runDriftScan = async () => {
  driftScanning.value = true
  try {
    const result = await triggerGovernanceDriftScan({
      windowDays: Math.min(days.value, 30),
      baselineDays: 7,
    })
    ElMessage.success(`漂移扫描已执行，本次新增 ${result.created ?? 0} 条漂移预警`)
    await loadData()
  } catch (error) {
    const parsed = parseError(error, '执行漂移扫描失败')
    ElMessage.error(parsed.detail)
  } finally {
    driftScanning.value = false
  }
}

const goTo = async (name: string, params?: Record<string, string | number>) => {
  await router.push({ name, params })
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>管理看板</span>
        <div class="header-actions">
          <span class="days-label">统计天数</span>
          <el-input-number v-model="days" :min="1" :max="30" size="small" />
          <el-button :loading="driftScanning" @click="runDriftScan">执行漂移扫描</el-button>
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
      <div class="overview-grid">
        <button
          v-for="card in dashboardCards"
          :key="card.title"
          class="overview-card"
          type="button"
          @click="goTo(card.routeName)"
        >
          <span>{{ card.title }}</span>
          <strong>{{ card.value }}</strong>
          <p>{{ card.note }}</p>
        </button>
      </div>

      <el-row :gutter="16" class="mt-16">
        <el-col :xs="24" :md="6"><el-statistic title="规则总数" :value="summary.ruleCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="日活用户累计" :value="totals.dau" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="上传累计" :value="totals.uploadCount" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="报告累计" :value="totals.reportCount" /></el-col>
      </el-row>

      <el-row v-if="quality" :gutter="16" class="mt-16">
        <el-col :xs="24" :md="8">
          <el-card shadow="never" class="metric-card">
            <template #header>{{ `${SLA_LABEL} 概览` }}</template>
            <p>总数：{{ quality.slaOverview.total ?? 0 }}</p>
            <p>已解决：{{ quality.slaOverview.resolved ?? 0 }}</p>
            <p>已超时：{{ quality.slaOverview.breached ?? 0 }}</p>
            <p>已确认：{{ quality.slaOverview.acked ?? 0 }}</p>
            <p>平均确认时长（分钟）：{{ Number(quality.slaOverview.avg_ack_minutes ?? 0).toFixed(1) }}</p>
            <p>平均解决时长（分钟）：{{ Number(quality.slaOverview.avg_resolve_minutes ?? 0).toFixed(1) }}</p>
            <div class="metric-actions">
              <el-button link type="primary" @click="goTo('adminWarnings')">前往预警处置台</el-button>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="16">
          <el-card shadow="never">
            <template #header>情绪分布变化（当前与基线）</template>
            <el-table v-if="emotionDriftRows.length" :data="emotionDriftRows" size="small" border>
              <el-table-column label="情绪" width="120">
                <template #default="scope">{{ formatEmotion(scope.row.emotion) }}</template>
              </el-table-column>
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
              <el-table-column label="操作" width="140">
                <template #default>
                  <el-button link type="primary" @click="goTo('adminModels')">查看模型治理</el-button>
                </template>
              </el-table-column>
            </el-table>
            <p v-else class="section-empty">当前窗口或基线窗口样本不足，暂不展示情绪漂移。</p>
          </el-card>
        </el-col>
      </el-row>

      <el-row v-if="quality" :gutter="16" class="mt-16">
        <el-col :xs="24" :md="10">
          <el-card shadow="never">
            <template #header>错误类别样本</template>
            <template v-if="errorCategoryRows.length">
              <div
                v-for="item in errorCategoryRows"
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
            </template>
            <p v-else class="section-empty">当前时间范围内暂无错误类别样本。</p>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14">
          <el-card shadow="never">
            <template #header>近期错误样本</template>
            <el-table v-if="errorSamples.length" :data="errorSamples" size="small" border>
              <el-table-column prop="id" label="任务" width="90" />
              <el-table-column prop="audio_file_id" label="音频" width="90" />
              <el-table-column prop="error_message" label="错误信息" min-width="260" show-overflow-tooltip />
              <el-table-column prop="updated_at" label="更新时间" min-width="160" />
              <el-table-column label="操作" width="110">
                <template #default="scope">
                  <el-button link type="primary" @click="goTo('adminTaskInspect', { id: scope.row.id })">查看任务</el-button>
                </template>
              </el-table-column>
            </el-table>
            <p v-else class="section-empty">当前时间范围内暂无错误样本。</p>
          </el-card>
        </el-col>
      </el-row>

      <el-card v-if="quality" shadow="never" class="mt-16">
        <template #header>{{ `${SLA_LABEL} 趋势` }}</template>
        <el-table v-if="slaTrendRows.length" :data="slaTrendRows" size="small" border>
          <el-table-column prop="stat_date" label="日期" min-width="140" />
          <el-table-column prop="total" label="总数" width="110" />
          <el-table-column prop="resolved" label="已解决" width="110" />
          <el-table-column prop="breached" label="已超时" width="110" />
        </el-table>
        <p v-else class="section-empty">当前时间范围内暂无 SLA 趋势数据。</p>
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
        <el-table-column prop="dau" label="日活用户数" width="120" />
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

.days-label {
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.mt-16 {
  margin-top: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.overview-card {
  padding: 18px;
  border-radius: 16px;
  border: 1px solid rgba(96, 119, 158, 0.16);
  background: rgba(12, 21, 38, 0.76);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease;
}

.overview-card:hover {
  transform: translateY(-2px);
  border-color: rgba(96, 165, 250, 0.4);
}

.overview-card span,
.bar-label {
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.overview-card strong {
  display: block;
  margin-top: 10px;
  color: var(--admin-text-primary);
  font-size: 30px;
}

.overview-card p,
.section-empty {
  margin: 10px 0 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.metric-card p {
  margin: 6px 0;
  color: var(--admin-text-primary);
}

.metric-actions {
  margin-top: 12px;
}

.bar-row {
  display: grid;
  grid-template-columns: 90px 1fr 60px;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.bar-track {
  height: 10px;
  background: rgba(96, 119, 158, 0.18);
  border-radius: 999px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #f97316, #ef4444);
}

.bar-value {
  text-align: right;
  color: var(--admin-text-primary);
  font-size: 12px;
}

:deep(.el-statistic__head) {
  color: var(--admin-text-secondary);
}

:deep(.el-statistic__content),
:deep(.el-statistic__number) {
  color: var(--admin-text-primary);
}
</style>
