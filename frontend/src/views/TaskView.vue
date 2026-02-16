<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { useTaskPolling } from '@/composables/useTaskPolling'

const route = useRoute()
const router = useRouter()

const taskId = computed(() => Number(route.params.id))

const { pollingState, task, errorMessage, statusText, start } = useTaskPolling(taskId, {
  baseIntervalMs: 3000,
  maxIntervalMs: 15000,
  timeoutMs: 300000,
  maxRetry: 8,
})

const durationSeconds = computed(() => ((task.value?.durationMs ?? 0) / 1000).toFixed(2))
const confidencePercent = computed(() => Math.round((task.value?.result?.confidence ?? 0) * 100))
const riskScorePercent = computed(() => Math.round((task.value?.result?.risk_score ?? 0) * 100))

const confidenceColor = computed(() => {
  if (confidencePercent.value >= 80) return '#67c23a'
  if (confidencePercent.value >= 60) return '#e6a23c'
  return '#f56c6c'
})

const riskColor = computed(() => {
  if (riskScorePercent.value >= 70) return '#f56c6c'
  if (riskScorePercent.value >= 40) return '#e6a23c'
  return '#67c23a'
})

const statusTagType = computed(() => {
  if (pollingState.value === 'success') return 'success'
  if (pollingState.value === 'error') return 'danger'
  return 'warning'
})

onMounted(() => {
  void start()
})
</script>

<template>
  <div class="task-detail-page">
    <el-card class="hero-card" shadow="never">
      <div class="hero-header">
        <div>
          <p class="hero-subtitle">情绪分析任务</p>
          <h2>任务 #{{ taskId }}</h2>
        </div>
        <el-tag effect="dark" :type="statusTagType">{{ statusText }}</el-tag>
      </div>

      <LoadingState v-if="pollingState === 'loading' && !task" />
      <ErrorState
        v-else-if="pollingState === 'error'"
        title="任务加载失败"
        :detail="errorMessage"
        :trace-id="task?.traceId"
        @retry="start"
      />
      <EmptyState
        v-else-if="!task"
        title="任务不存在"
        description="该任务暂不可用，可能仍在初始化。"
        action-text="重新拉取"
        @action="start"
      />
      <div class="metric-grid" v-else>
        <el-card class="metric-item" shadow="hover">
          <p>综合情绪</p>
          <h3>{{ task.result?.overall ?? '-' }}</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>风险等级</p>
          <h3>{{ task.result?.risk_level ?? '-' }}</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>处理耗时</p>
          <h3>{{ durationSeconds }}s</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>重试次数</p>
          <h3>{{ task.attemptCount ?? '-' }}</h3>
        </el-card>
      </div>
    </el-card>

    <template v-if="task?.result">
      <el-row :gutter="16" class="chart-row">
        <el-col :xs="24" :md="12">
          <el-card shadow="hover">
            <template #header>置信度</template>
            <el-progress type="dashboard" :percentage="confidencePercent" :color="confidenceColor" :stroke-width="12" />
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="hover">
            <template #header>风险分数</template>
            <el-progress type="dashboard" :percentage="riskScorePercent" :color="riskColor" :stroke-width="12" />
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover">
        <template #header>任务详情</template>
        <el-descriptions border :column="2">
          <el-descriptions-item label="status">{{ task.status ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="errorMessage">{{ task.errorMessage ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="traceId">{{ task.traceId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="serLatency">{{ task.serLatencyMs ?? '-' }}ms</el-descriptions-item>
          <el-descriptions-item label="建议" :span="2">{{ task.result?.advice_text ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-button @click="router.push('/tasks')">返回列表</el-button>
          <el-button type="primary" @click="router.push('/reports')">前往报告中心</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.task-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card {
  border: 0;
  background: linear-gradient(135deg, #eff6ff 0%, #faf5ff 100%);
}

.hero-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.hero-subtitle {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.hero-header h2 {
  margin: 6px 0 0;
  font-size: 24px;
  color: #1e293b;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-item :deep(.el-card__body) {
  padding: 14px;
}

.metric-item p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.metric-item h3 {
  margin: 8px 0 0;
  font-size: 20px;
  color: #0f172a;
}

.chart-row {
  margin: 0;
}

.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}

@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
