<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { getReportDetail, type ReportDetail } from '@/api/report'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const route = useRoute()
const router = useRouter()

const reportId = computed(() => Number(route.params.id))
const result = ref<ReportDetail | null>(null)
const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)

const normalizedRiskScore = computed(() => {
  const score = result.value?.riskScore
  if (score == null || Number.isNaN(score)) return 0
  return Math.max(0, Math.min(100, score <= 1 ? score * 100 : score))
})

const riskTagType = computed(() => {
  const level = (result.value?.riskLevel ?? '').toLowerCase()
  if (level.includes('high') || level.includes('高')) return 'danger'
  if (level.includes('medium') || level.includes('中')) return 'warning'
  if (level.includes('low') || level.includes('低')) return 'success'
  return 'info'
})

const confidencePercent = computed(() => {
  const confidence = result.value?.confidence
  if (confidence == null || Number.isNaN(confidence)) return '-'
  const normalized = confidence <= 1 ? confidence * 100 : confidence
  return `${normalized.toFixed(2)}%`
})

const formattedCreatedAt = computed(() => {
  const createdAt = result.value?.createdAt
  if (!createdAt) return '未知时间'
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return createdAt
  return date.toLocaleString('zh-CN', { hour12: false })
})

const summaryText = computed(() => {
  if (!result.value) return ''
  return `报告 #${result.value.id} 综合情绪为 ${result.value.overall ?? '未知'}，风险等级 ${result.value.riskLevel ?? '未知'}，建议结合任务上下文持续跟踪。`
})

const exportJson = () => {
  if (!result.value) return
  const content = JSON.stringify(result.value, null, 2)
  const blob = new Blob([content], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `report-${reportId.value}.json`
  a.click()
  URL.revokeObjectURL(url)
}

const exportAsImageOrPdf = () => {
  window.print()
}

const loadReport = async () => {
  loading.value = true
  errorState.value = null
  try {
    const { data } = await getReportDetail(reportId.value)
    result.value = data
  } catch (error) {
    errorState.value = parseError(error, '报告详情加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadReport()
})
</script>

<template>
  <div class="report-page">
    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadReport"
    />
    <EmptyState
      v-else-if="!result"
      title="报告未生成"
      description="此报告尚不可用，请稍后刷新。"
      action-text="刷新详情"
      @action="loadReport"
    />

    <template v-else>
      <section class="page-header card-shell">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item @click="router.push('/reports')">报告中心</el-breadcrumb-item>
            <el-breadcrumb-item>报告详情</el-breadcrumb-item>
          </el-breadcrumb>
          <h1>报告详情 #{{ result.id }}</h1>
          <p>
            生成时间：{{ formattedCreatedAt }} ｜ 来源任务：#{{ result.taskId }} ｜
            <el-tag size="small" effect="light" :type="riskTagType">{{ result.riskLevel ?? '未知等级' }}</el-tag>
          </p>
        </div>
        <div class="header-actions">
          <el-button plain @click="router.push('/reports')">返回列表</el-button>
          <el-button type="info" plain @click="router.push(`/tasks/${result.taskId}`)">关联任务</el-button>
          <el-button type="info" plain @click="exportJson">导出 JSON</el-button>
          <el-button type="primary" @click="exportAsImageOrPdf">导出图片/PDF</el-button>
        </div>
      </section>

      <section class="kpi-grid">
        <el-card class="kpi-card" shadow="hover">
          <p>综合情绪</p>
          <el-tag size="large" effect="dark">{{ result.overall ?? '-' }}</el-tag>
        </el-card>
        <el-card class="kpi-card" shadow="hover">
          <p>置信度</p>
          <h3>{{ confidencePercent }}</h3>
        </el-card>
        <el-card class="kpi-card" shadow="hover">
          <p>风险等级</p>
          <el-tag effect="dark" :type="riskTagType">{{ result.riskLevel ?? '-' }}</el-tag>
        </el-card>
        <el-card class="kpi-card" shadow="hover">
          <p>风险评分</p>
          <h3>{{ normalizedRiskScore.toFixed(0) }}/100</h3>
          <el-progress :percentage="normalizedRiskScore" :stroke-width="8" :show-text="false" :status="riskTagType === 'danger' ? 'exception' : undefined" />
        </el-card>
        <el-card class="kpi-card" shadow="hover">
          <p>关联任务</p>
          <el-link type="primary" @click="router.push(`/tasks/${result.taskId}`)">任务 #{{ result.taskId }}</el-link>
        </el-card>
        <el-card class="kpi-card" shadow="hover">
          <p>报告 ID</p>
          <h3>#{{ result.id }}</h3>
        </el-card>
      </section>

      <section class="card-shell detail-card">
        <h2>详情信息</h2>
        <div class="field-grid">
          <div class="field-item">
            <span class="label">报告 ID</span>
            <span class="value">{{ result.id }}</span>
          </div>
          <div class="field-item">
            <span class="label">来源任务</span>
            <span class="value">{{ result.taskId }}</span>
          </div>
          <div class="field-item">
            <span class="label">综合情绪</span>
            <span class="value">{{ result.overall ?? '-' }}</span>
          </div>
          <div class="field-item">
            <span class="label">风险等级</span>
            <span class="value"><el-tag :type="riskTagType">{{ result.riskLevel ?? '-' }}</el-tag></span>
          </div>
          <div class="field-item">
            <span class="label">风险评分</span>
            <span class="value">{{ normalizedRiskScore.toFixed(0) }}</span>
          </div>
          <div class="field-item">
            <span class="label">置信度</span>
            <span class="value">{{ confidencePercent }}</span>
          </div>
        </div>
      </section>

      <section class="content-grid">
        <el-card shadow="never" class="content-card">
          <template #header>建议</template>
          <p v-if="result.adviceText">{{ result.adviceText }}</p>
          <EmptyState v-else title="暂无建议" description="当前报告没有生成建议内容。" action-text="已了解" @action="() => {}" />
        </el-card>

        <el-card shadow="never" class="content-card">
          <template #header>结论</template>
          <p v-if="result.riskLevel || result.overall">当前报告判定为 {{ result.riskLevel ?? '未知' }} 风险，建议重点关注 {{ result.overall ?? '情绪波动' }} 相关片段。</p>
          <EmptyState v-else title="暂无结论" description="当前报告没有可展示的结论。" action-text="已了解" @action="() => {}" />
        </el-card>

        <el-card shadow="never" class="content-card">
          <template #header>摘要</template>
          <p v-if="summaryText">{{ summaryText }}</p>
          <EmptyState v-else title="暂无摘要" description="当前报告没有摘要信息。" action-text="已了解" @action="() => {}" />
        </el-card>
      </section>
    </template>
  </div>
</template>

<style scoped>
.report-page {
  max-width: 1160px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-shell {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 18px 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.header-left h1 {
  margin: 10px 0 6px;
  color: #111827;
}

.header-left p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.kpi-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.kpi-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
}

.kpi-card p {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}

.kpi-card h3 {
  margin: 0;
  font-size: 24px;
  color: #111827;
}

.detail-card h2 {
  margin: 0 0 14px;
  font-size: 18px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 20px;
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.label {
  color: #9ca3af;
  font-size: 12px;
}

.value {
  color: #111827;
  font-size: 15px;
  font-weight: 600;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.content-card {
  min-height: 180px;
}

.content-card p {
  margin: 0;
  color: #374151;
  line-height: 1.7;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .field-grid,
  .content-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }

  .kpi-grid,
  .field-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
