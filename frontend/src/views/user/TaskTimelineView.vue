<script setup lang="ts">
import { DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { useTaskPolling } from '@/composables/useTaskPolling'
import { useTaskRealtimeStream } from '@/composables/useTaskRealtimeStream'

type TimelineNode = {
  key: string
  title: string
  timestamp?: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  detail?: string
}

const STATUS_LABEL_MAP: Record<string, string> = {
  PENDING: '待处理',
  RUNNING: '处理中',
  RETRY_WAIT: '等待重试',
  SUCCESS: '处理成功',
  FAILED: '处理失败',
  CANCELED: '已取消',
}

const STREAM_LABEL_MAP: Record<string, string> = {
  idle: '未连接',
  connecting: '连接中',
  open: '已连接',
  closed: '已关闭',
  error: '异常',
}

const route = useRoute()
const router = useRouter()

const taskId = computed(() => Number(route.params.id))
const { pollingState, task, errorMessage, statusText: pollStatusText, start } = useTaskPolling(taskId, {
  baseIntervalMs: 3000,
  maxIntervalMs: 15000,
  timeoutMs: 1800000,
  maxRetry: 8,
})

const realtime = useTaskRealtimeStream(taskId)
const streamSnapshot = computed(() => realtime.snapshot.value)
const streamState = computed(() => realtime.state.value)
const streamError = computed(() => realtime.errorMessage.value)

const displayTaskNo = computed(
  () => streamSnapshot.value?.taskNo || task.value?.taskNo || `TASK-${taskId.value}`,
)

const latestStatus = computed(() => streamSnapshot.value?.status ?? task.value?.status ?? 'PENDING')
const latestStatusText = computed(() => {
  const code = latestStatus.value
  return `${STATUS_LABEL_MAP[code] ?? '处理中'} (${code})`
})

const flowStep = computed(() => {
  const status = latestStatus.value
  if (status === 'PENDING') return 0
  if (status === 'RUNNING') return 1
  if (status === 'RETRY_WAIT') return 2
  if (status === 'SUCCESS') return 3
  if (status === 'FAILED' || status === 'CANCELED') return 4
  return 0
})

const streamLabel = computed(() => STREAM_LABEL_MAP[streamState.value] ?? streamState.value)
const streamTagType = computed(() => {
  if (streamState.value === 'open') return 'success'
  if (streamState.value === 'connecting') return 'warning'
  if (streamState.value === 'error') return 'danger'
  return 'info'
})

const riskSummary = computed(() => streamSnapshot.value?.risk ?? null)
const progressSummary = computed(() => streamSnapshot.value?.progress ?? null)
const curveRows = computed(() => streamSnapshot.value?.curve ?? [])

const chartWidth = 960
const chartHeight = 280
const chartPaddingX = 56
const chartPaddingY = 24

const curvePolyline = computed(() => {
  if (!curveRows.value.length) return ''
  const drawWidth = chartWidth - chartPaddingX * 2
  const drawHeight = chartHeight - chartPaddingY * 2
  return curveRows.value
    .map((point, index) => {
      const clampedRisk = Math.max(0, Math.min(100, point.riskIndex))
      const x = chartPaddingX + (drawWidth * index) / Math.max(curveRows.value.length - 1, 1)
      const y = chartPaddingY + drawHeight * (1 - clampedRisk / 100)
      return `${x},${y}`
    })
    .join(' ')
})

const latestCurvePoint = computed(() => {
  if (!curveRows.value.length) return null
  const drawWidth = chartWidth - chartPaddingX * 2
  const drawHeight = chartHeight - chartPaddingY * 2
  const index = curveRows.value.length - 1
  const point = curveRows.value[index]
  if (!point) return null
  const clampedRisk = Math.max(0, Math.min(100, point.riskIndex))
  const x = chartPaddingX + (drawWidth * index) / Math.max(curveRows.value.length - 1, 1)
  const y = chartPaddingY + drawHeight * (1 - clampedRisk / 100)
  return { x, y, point }
})

const averageCurveRisk = computed(() => {
  if (!curveRows.value.length) return null
  const total = curveRows.value.reduce((sum, point) => sum + point.riskIndex, 0)
  return total / curveRows.value.length
})

const timelineNodes = computed<TimelineNode[]>(() => {
  const rows: TimelineNode[] = []
  if (task.value) {
    rows.push({
      key: `created-${task.value.id}`,
      title: '任务创建',
      timestamp: task.value.createdAt,
      type: 'primary',
      detail: `任务已入队，当前状态：${latestStatus.value}`,
    })
  }

  const progress = progressSummary.value
  if (progress) {
    rows.push({
      key: `progress-${progress.sequence}`,
      title: `阶段：${progress.phase}`,
      timestamp: new Date(progress.emittedAtMs).toLocaleString('zh-CN', { hour12: false }),
      type: latestStatus.value === 'FAILED' ? 'danger' : latestStatus.value === 'SUCCESS' ? 'success' : 'warning',
      detail: progress.message,
    })
  }

  if (latestStatus.value === 'RETRY_WAIT') {
    rows.push({
      key: `retry-wait-${taskId.value}`,
      title: '重试等待',
      timestamp: streamSnapshot.value?.nextRunAt || task.value?.nextRunAt || undefined,
      type: 'warning',
      detail: '系统将在下一次调度窗口自动重试。',
    })
  }

  if (latestStatus.value === 'SUCCESS') {
    rows.push({
      key: `success-${taskId.value}`,
      title: '任务完成',
      timestamp: task.value?.finishedAt ?? streamSnapshot.value?.updatedAt ?? task.value?.updatedAt,
      type: 'success',
      detail: '可查看融合结果与风险曲线。',
    })
  }

  if (latestStatus.value === 'FAILED' || latestStatus.value === 'CANCELED') {
    rows.push({
      key: `failed-${taskId.value}`,
      title: latestStatus.value === 'FAILED' ? '任务失败' : '任务取消',
      timestamp: task.value?.finishedAt ?? streamSnapshot.value?.updatedAt ?? task.value?.updatedAt,
      type: 'danger',
      detail: streamSnapshot.value?.errorMessage || task.value?.errorMessage || '任务未成功完成。',
    })
  }
  return rows
})

const copyTaskNo = async () => {
  try {
    await navigator.clipboard.writeText(displayTaskNo.value)
    ElMessage.success('任务编号已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

onMounted(() => {
  void start()
})
</script>

<template>
  <div class="timeline-page">
    <el-card shadow="never">
      <div class="header-row">
        <div>
          <p class="subtitle">任务实时轨迹</p>
          <h2>任务编号 {{ displayTaskNo }}</h2>
          <p class="tip">任务ID: {{ taskId }}</p>
        </div>
        <div class="header-actions">
          <el-tag effect="dark" type="primary">{{ latestStatusText }}</el-tag>
          <el-tag :type="streamTagType">实时通道：{{ streamLabel }}</el-tag>
          <el-button :icon="DocumentCopy" @click="copyTaskNo">复制编号</el-button>
          <el-button @click="router.push(`/app/tasks/${taskId}`)">返回详情</el-button>
        </div>
      </div>

      <LoadingState v-if="pollingState === 'loading' && !task && !streamSnapshot" />
      <ErrorState
        v-else-if="pollingState === 'error' && !task && !streamSnapshot"
        title="任务时间线加载失败"
        :detail="errorMessage"
        @retry="start"
      />
      <EmptyState
        v-else-if="!task && !streamSnapshot"
        title="暂无任务数据"
        description="任务可能尚未创建完成，请稍后重试。"
        action-text="重新加载"
        @action="start"
      />
      <template v-else>
        <el-alert
          v-if="streamState !== 'open'"
          class="channel-alert"
          type="warning"
          show-icon
          :closable="false"
          :title="`实时通道未连接，当前使用轮询兜底（${pollStatusText}）`"
          :description="streamError || '连接恢复后将自动切换到实时推送。'"
        />

        <el-steps :active="flowStep" finish-status="success" simple class="step-row">
          <el-step title="创建" />
          <el-step title="执行" />
          <el-step title="重试等待" />
          <el-step title="完成" />
          <el-step title="结束" />
        </el-steps>

        <el-row :gutter="12" class="metrics-row">
          <el-col :xs="24" :md="8">
            <el-card shadow="hover" class="metric-card">
              <p>风险分数(PSI)</p>
              <h3>{{ riskSummary ? (riskSummary.riskScore * 100).toFixed(2) : '-' }}</h3>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-card shadow="hover" class="metric-card">
              <p>风险等级</p>
              <h3>{{ riskSummary?.riskLevel || '-' }}</h3>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-card shadow="hover" class="metric-card">
              <p>曲线均值</p>
              <h3>{{ averageCurveRisk != null ? averageCurveRisk.toFixed(2) : '-' }}</h3>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="hover" class="curve-card">
          <template #header>风险时间轴曲线</template>
          <div v-if="curveRows.length" class="chart-wrap">
            <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" preserveAspectRatio="none" class="chart">
              <line
                :x1="chartPaddingX"
                :y1="chartHeight - chartPaddingY"
                :x2="chartWidth - chartPaddingX"
                :y2="chartHeight - chartPaddingY"
                stroke="#4a607f"
                stroke-width="1.2"
              />
              <line
                :x1="chartPaddingX"
                :y1="chartPaddingY"
                :x2="chartPaddingX"
                :y2="chartHeight - chartPaddingY"
                stroke="#4a607f"
                stroke-width="1.2"
              />
              <line
                :x1="chartPaddingX"
                :y1="chartPaddingY + (chartHeight - chartPaddingY * 2) / 2"
                :x2="chartWidth - chartPaddingX"
                :y2="chartPaddingY + (chartHeight - chartPaddingY * 2) / 2"
                stroke="#364a68"
                stroke-dasharray="6 6"
              />
              <polyline
                :points="curvePolyline"
                fill="none"
                stroke="url(#riskLineGradient)"
                stroke-width="3"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <defs>
                <linearGradient id="riskLineGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stop-color="#69d4ff" />
                  <stop offset="100%" stop-color="#ff7c7c" />
                </linearGradient>
              </defs>
              <circle
                v-if="latestCurvePoint"
                :cx="latestCurvePoint.x"
                :cy="latestCurvePoint.y"
                r="5"
                fill="#ffd06a"
                stroke="#ffffff"
                stroke-width="1.5"
              />
            </svg>
          </div>
          <p v-else class="muted">
            当前尚无可绘制曲线。任务成功后会自动推送分段风险点。
          </p>
        </el-card>

        <el-descriptions border :column="2" class="meta-card">
          <el-descriptions-item label="任务状态">{{ latestStatusText }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">
            {{ streamSnapshot?.attemptCount ?? task?.attemptCount ?? 0 }}
            /
            {{ streamSnapshot?.maxAttempts ?? task?.maxAttempts ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="下一次执行">{{ streamSnapshot?.nextRunAt ?? task?.nextRunAt ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="Trace ID">{{ streamSnapshot?.traceId ?? task?.traceId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近阶段" :span="2">
            {{ progressSummary ? `${progressSummary.phase} - ${progressSummary.message}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">
            {{ streamSnapshot?.errorMessage ?? task?.errorMessage ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-timeline>
          <el-timeline-item
            v-for="node in timelineNodes"
            :key="node.key"
            :timestamp="node.timestamp"
            :type="node.type"
            placement="top"
          >
            <el-card shadow="never">
              <p class="node-title">{{ node.title }}</p>
              <p v-if="node.detail" class="node-detail">{{ node.detail }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.timeline-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.subtitle {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

h2 {
  margin: 6px 0 0;
  color: #0f172a;
}

.tip {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.channel-alert {
  margin-bottom: 12px;
}

.step-row {
  margin: 12px 0;
}

.metrics-row {
  margin-bottom: 12px;
}

.metric-card :deep(.el-card__body) {
  padding: 12px;
}

.metric-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.metric-card h3 {
  margin: 8px 0 0;
  color: #0f172a;
  font-size: 20px;
}

.curve-card {
  margin-bottom: 12px;
}

.chart-wrap {
  width: 100%;
  min-height: 280px;
}

.chart {
  width: 100%;
  height: 280px;
  display: block;
}

.meta-card {
  margin: 12px 0;
}

.node-title {
  margin: 0;
  font-weight: 600;
}

.node-detail {
  margin: 8px 0 0;
  color: #334155;
}

.muted {
  margin: 0;
  color: #64748b;
}
</style>
