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
import { PSI_LABEL, TRACE_ID_LABEL, formatEmotion, formatRiskLevel, formatTaskStatus } from '@/utils/uiText'

type TimelineNode = {
  key: string
  title: string
  timestamp?: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  detail?: string
}

type FlowStepVisualStatus = 'wait' | 'process' | 'finish' | 'success' | 'error'

type FlowStepItem = {
  title: string
  status: FlowStepVisualStatus
}

const STREAM_LABEL_MAP: Record<string, string> = {
  idle: '未连接',
  connecting: '连接中',
  open: '已连接',
  terminal: '已完成',
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

const displayTaskNo = computed(() => streamSnapshot.value?.taskNo || task.value?.taskNo || `任务-${taskId.value}`)
const latestStatus = computed(() => streamSnapshot.value?.status ?? task.value?.status ?? 'PENDING')
const latestStatusText = computed(() => formatTaskStatus(latestStatus.value))
const latestAttemptCount = computed(() => Number(streamSnapshot.value?.attemptCount ?? task.value?.attemptCount ?? 0))

const hasRetryHistory = computed(() => {
  if (latestStatus.value === 'RETRY_WAIT') return true
  if (latestAttemptCount.value > 0) return true
  return Boolean(streamSnapshot.value?.nextRunAt ?? task.value?.nextRunAt)
})

const flowSteps = computed<FlowStepItem[]>(() => {
  const status = latestStatus.value
  const isSuccess = status === 'SUCCESS'
  const isFailed = status === 'FAILED'
  const isCanceled = status === 'CANCELED'
  const isTerminalError = isFailed || isCanceled

  const steps: FlowStepItem[] = [
    {
      title: '创建',
      status: status === 'PENDING' ? 'process' : 'finish',
    },
    {
      title: '执行',
      status:
        status === 'PENDING'
          ? 'wait'
          : status === 'RUNNING'
            ? 'process'
            : status === 'RETRY_WAIT' || isSuccess || isTerminalError
              ? 'finish'
              : 'wait',
    },
    {
      title: '完成',
      status: isSuccess ? 'success' : 'wait',
    },
  ]

  if (hasRetryHistory.value || status === 'RETRY_WAIT') {
    steps.splice(2, 0, {
      title: '重试等待',
      status:
        status === 'RETRY_WAIT'
          ? 'process'
          : hasRetryHistory.value && (isSuccess || isTerminalError)
            ? 'finish'
            : 'wait',
    })
  }

  if (isTerminalError) {
    steps.push({
      title: isCanceled ? '已取消' : '异常结束',
      status: 'error',
    })
  }

  return steps
})

const streamLabel = computed(() => STREAM_LABEL_MAP[streamState.value] ?? streamState.value)
const streamTagType = computed(() => {
  if (streamState.value === 'open' || streamState.value === 'terminal') return 'success'
  if (streamState.value === 'connecting') return 'warning'
  if (streamState.value === 'error') return 'danger'
  return 'info'
})

const riskSummary = computed(() => streamSnapshot.value?.risk ?? null)
const progressSummary = computed(() => streamSnapshot.value?.progress ?? null)
const curveRows = computed(() => streamSnapshot.value?.curve ?? [])

const chartWidth = 960
const chartHeight = 300
const chartPaddingLeft = 58
const chartPaddingRight = 28
const chartPaddingTop = 24
const chartPaddingBottom = 40
const plotWidth = chartWidth - chartPaddingLeft - chartPaddingRight
const plotHeight = chartHeight - chartPaddingTop - chartPaddingBottom

const formatDurationShort = (ms?: number | null) => {
  if (ms == null || Number.isNaN(ms)) return '-'
  const safeMs = Math.max(0, ms)
  const totalSeconds = safeMs / 1000
  const roundedTenths = Math.round(totalSeconds * 10) / 10
  if (roundedTenths < 10) {
    return `${roundedTenths.toFixed(1)}s`
  }
  const roundedWholeSeconds = Math.round(totalSeconds)
  if (roundedWholeSeconds < 60) {
    return `${roundedWholeSeconds}s`
  }
  const minutes = Math.floor(roundedWholeSeconds / 60)
  const seconds = roundedWholeSeconds % 60
  return `${minutes}m${seconds.toString().padStart(2, '0')}s`
}

const curvePoints = computed(() =>
  curveRows.value.map((point, index) => {
    const clampedRisk = Math.max(0, Math.min(100, point.riskIndex))
    const x = chartPaddingLeft + (plotWidth * index) / Math.max(curveRows.value.length - 1, 1)
    const y = chartPaddingTop + plotHeight * (1 - clampedRisk / 100)
    return {
      x,
      y,
      point,
      isLatest: index === curveRows.value.length - 1,
    }
  }),
)

const curvePolyline = computed(() => {
  if (!curvePoints.value.length) return ''
  return curvePoints.value.map((point) => `${point.x},${point.y}`).join(' ')
})

const curveAreaPath = computed(() => {
  if (!curvePoints.value.length) return ''
  const firstPoint = curvePoints.value[0]
  const lastPoint = curvePoints.value[curvePoints.value.length - 1]
  if (!firstPoint || !lastPoint) return ''
  const baselineY = chartHeight - chartPaddingBottom
  const linePath = curvePoints.value.map((point) => `L ${point.x} ${point.y}`).join(' ')
  return `M ${firstPoint.x} ${baselineY} ${linePath} L ${lastPoint.x} ${baselineY} Z`
})

const latestCurvePoint = computed(() => {
  if (!curvePoints.value.length) return null
  return curvePoints.value[curvePoints.value.length - 1] ?? null
})

const curveSegmentCount = computed(() => curveRows.value.length)

const curveDurationDisplay = computed(() => {
  if (!curveRows.value.length) return '-'
  return formatDurationShort(curveRows.value[curveRows.value.length - 1]?.endMs ?? 0)
})

const yAxisTicks = computed(() =>
  [0, 50, 100].map((value) => ({
    value,
    y: chartPaddingTop + plotHeight * (1 - value / 100),
  })),
)

const xAxisTicks = computed(() => {
  if (!curveRows.value.length) return []
  if (curveRows.value.length === 1) {
    const onlyPoint = curveRows.value[0]
    const onlyMs = onlyPoint?.endMs ?? onlyPoint?.startMs ?? 0
    return [{ key: 'single', x: chartPaddingLeft + plotWidth / 2, label: formatDurationShort(onlyMs) }]
  }
  const firstPoint = curveRows.value[0]
  const lastPoint = curveRows.value[curveRows.value.length - 1]
  const startMs = firstPoint?.startMs ?? 0
  const endMs = lastPoint?.endMs ?? startMs
  const middleMs = Math.round((startMs + endMs) / 2)
  return [
    { key: 'start', x: chartPaddingLeft, label: formatDurationShort(startMs) },
    { key: 'middle', x: chartPaddingLeft + plotWidth / 2, label: formatDurationShort(middleMs) },
    { key: 'end', x: chartWidth - chartPaddingRight, label: formatDurationShort(endMs) },
  ]
})

const latestCurveSummary = computed(() => {
  const latestPoint = latestCurvePoint.value
  if (!latestPoint) return ''
  return `最新片段 ${formatDurationShort(latestPoint.point.startMs)}-${formatDurationShort(latestPoint.point.endMs)}，情绪 ${formatEmotion(latestPoint.point.emotion)}，分段指数 ${latestPoint.point.riskIndex.toFixed(2)}。`
})

const riskScoreDisplay = computed(() => {
  const score = riskSummary.value?.riskScore
  if (score == null || Number.isNaN(score)) return '-'
  const normalized = score <= 1 ? score * 100 : score
  return normalized.toFixed(2)
})

const timelineNodes = computed<TimelineNode[]>(() => {
  const rows: TimelineNode[] = []
  if (task.value) {
    rows.push({
      key: `created-${task.value.id}`,
      title: '任务创建',
      timestamp: task.value.createdAt,
      type: 'primary',
      detail: `任务已进入队列，当前状态：${latestStatusText.value}`,
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
      detail: '可查看融合结果、风险趋势与报告结论。',
    })
  }

  if (latestStatus.value === 'FAILED' || latestStatus.value === 'CANCELED') {
    rows.push({
      key: `failed-${taskId.value}`,
      title: latestStatus.value === 'FAILED' ? '任务失败' : '任务取消',
      timestamp: task.value?.finishedAt ?? streamSnapshot.value?.updatedAt ?? task.value?.updatedAt,
      type: 'danger',
      detail: streamSnapshot.value?.errorMessage || task.value?.errorMessage || '任务未能成功完成。',
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
    <el-card shadow="never" class="page-card">
      <div class="header-row">
        <div>
          <p class="subtitle">查看任务从创建到完成的处理节点</p>
          <h2>任务时间线</h2>
          <p class="tip">任务编号：{{ displayTaskNo }} ｜ 任务 ID：{{ taskId }}</p>
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
        description="这条任务现在还没准备好，请稍后刷新。"
        action-text="重新加载"
        @action="start"
      />
      <template v-else>
        <el-alert
          v-if="streamState !== 'open' && streamState !== 'terminal'"
          class="channel-alert"
          type="warning"
          show-icon
          :closable="false"
          :title="`实时连接暂时不可用，先用刷新方式继续跟踪进度（${pollStatusText}）`"
          :description="streamError || '连接恢复后会自动切回实时更新。'"
        />

        <el-steps simple class="step-row">
          <el-step
            v-for="step in flowSteps"
            :key="step.title"
            :title="step.title"
            :status="step.status"
          />
        </el-steps>

        <el-row :gutter="12" class="metrics-row">
          <el-col :xs="24" :md="8">
            <el-card shadow="hover" class="metric-card">
              <div class="metric-head">
                <p>{{ PSI_LABEL }}</p>
                <span class="metric-badge">最终分</span>
              </div>
              <h3>{{ riskScoreDisplay }}</h3>
              <span class="metric-note">这是本次任务的最终综合评分</span>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-card shadow="hover" class="metric-card">
              <div class="metric-head">
                <p>风险等级</p>
                <span class="metric-badge">结论</span>
              </div>
              <h3>{{ formatRiskLevel(riskSummary?.riskLevel) }}</h3>
              <span class="metric-note">由综合评分与模型结果共同得出</span>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-card shadow="hover" class="metric-card">
              <div class="metric-head">
                <p>分析时长</p>
                <span class="metric-badge">过程</span>
              </div>
              <h3>{{ curveDurationDisplay }}</h3>
              <span class="metric-note">
                {{ curveSegmentCount ? `共 ${curveSegmentCount} 段音频片段` : '任务完成后展示分段结果' }}
              </span>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="hover" class="curve-card">
          <template #header>
            <div class="curve-card-header">
              <div>
                <span class="curve-title">风险趋势</span>
                <p class="curve-subtitle">
                  横轴是音频时间，纵轴是分段风险指数。每个点代表一段音频，用来看任务处理过程中的起伏，不等于上方最终
                  {{ PSI_LABEL }}。
                </p>
              </div>
              <span v-if="curveSegmentCount" class="curve-badge">{{ curveSegmentCount }} 段</span>
            </div>
          </template>
          <div v-if="curveRows.length" class="chart-wrap">
            <svg :viewBox="`0 0 ${chartWidth} ${chartHeight}`" preserveAspectRatio="none" class="chart">
              <defs>
                <linearGradient id="taskRiskLineGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stop-color="#67d3dc" />
                  <stop offset="100%" stop-color="#efb17c" />
                </linearGradient>
              </defs>
              <rect
                :x="chartPaddingLeft"
                :y="chartPaddingTop"
                :width="plotWidth"
                :height="plotHeight"
                rx="16"
                class="plot-frame"
              />
              <line
                v-for="tick in yAxisTicks"
                :key="`grid-${tick.value}`"
                :x1="chartPaddingLeft"
                :y1="tick.y"
                :x2="chartWidth - chartPaddingRight"
                :y2="tick.y"
                class="grid-line"
              />
              <line
                :x1="chartPaddingLeft"
                :y1="chartHeight - chartPaddingBottom"
                :x2="chartWidth - chartPaddingRight"
                :y2="chartHeight - chartPaddingBottom"
                class="axis"
              />
              <line
                :x1="chartPaddingLeft"
                :y1="chartPaddingTop"
                :x2="chartPaddingLeft"
                :y2="chartHeight - chartPaddingBottom"
                class="axis"
              />
              <path v-if="curveAreaPath" :d="curveAreaPath" class="curve-area" />
              <polyline
                :points="curvePolyline"
                fill="none"
                class="curve-line"
              />
              <circle
                v-for="point in curvePoints"
                :key="`dot-${point.point.index}`"
                :cx="point.x"
                :cy="point.y"
                :r="point.isLatest ? 5 : 3.5"
                class="curve-dot"
                :class="{ 'is-latest': point.isLatest }"
              />
              <circle
                v-if="latestCurvePoint"
                :cx="latestCurvePoint.x"
                :cy="latestCurvePoint.y"
                r="7"
                class="curve-dot-halo"
              />
              <text
                :x="chartPaddingLeft"
                :y="chartPaddingTop - 8"
                class="axis-title"
              >
                分段风险指数
              </text>
              <text
                :x="chartWidth - chartPaddingRight"
                :y="chartHeight - 10"
                class="axis-title axis-title-right"
              >
                音频时间
              </text>
              <text
                v-for="tick in yAxisTicks"
                :key="`y-${tick.value}`"
                :x="chartPaddingLeft - 10"
                :y="tick.y + 4"
                class="axis-label axis-label-left"
              >
                {{ tick.value }}
              </text>
              <text
                v-for="tick in xAxisTicks"
                :key="tick.key"
                :x="tick.x"
                :y="chartHeight - 14"
                class="axis-label axis-label-bottom"
              >
                {{ tick.label }}
              </text>
            </svg>
            <p class="curve-note">
              {{ latestCurveSummary }}
              最终风险等级仍以上方综合评分和报告结论为准。
            </p>
          </div>
          <p v-else class="muted">
            当前还没有可绘制的风险趋势。任务完成后，系统会按音频分段自动生成这条曲线。
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
          <el-descriptions-item :label="TRACE_ID_LABEL">{{ streamSnapshot?.traceId ?? task?.traceId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近阶段" :span="2">
            {{ progressSummary ? `${progressSummary.phase} - ${progressSummary.message}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">
            {{ streamSnapshot?.errorMessage ?? task?.errorMessage ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-timeline class="event-timeline">
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

.page-card {
  overflow: hidden;
}

.timeline-page :deep(.el-card__header) {
  color: var(--user-text-primary);
  border-bottom: 1px solid rgba(130, 154, 196, 0.18);
}

.timeline-page :deep(.el-card__body) {
  color: var(--user-text-primary);
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
  color: var(--user-text-secondary);
  font-size: 13px;
}

h2 {
  margin: 6px 0 0;
  color: var(--user-text-primary);
  font-size: 30px;
  letter-spacing: 0.01em;
}

.tip {
  margin: 8px 0 0;
  color: rgba(190, 203, 227, 0.78);
  font-size: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.channel-alert {
  margin: 14px 0 12px;
}

.step-row {
  margin: 14px 0;
  padding: 8px 10px;
  border: 1px solid rgba(131, 153, 194, 0.24);
  border-radius: 16px;
  background: rgba(10, 16, 29, 0.72);
}

.step-row :deep(.el-step.is-simple) {
  background: transparent;
}

.step-row :deep(.el-step__title) {
  color: rgba(201, 215, 239, 0.74);
  font-size: 13px;
}

.step-row :deep(.el-step__title.is-process),
.step-row :deep(.el-step__head.is-process) {
  color: #7ab8ff;
}

.step-row :deep(.el-step__title.is-success),
.step-row :deep(.el-step__head.is-success),
.step-row :deep(.el-step__title.is-finish),
.step-row :deep(.el-step__head.is-finish) {
  color: #88d49a;
}

.step-row :deep(.el-step__title.is-error),
.step-row :deep(.el-step__head.is-error) {
  color: #ff8d8d;
}

.metrics-row {
  margin-bottom: 12px;
}

.metric-card {
  height: 100%;
  position: relative;
  overflow: hidden;
}

.metric-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: linear-gradient(90deg, rgba(103, 211, 220, 0.86), rgba(239, 177, 124, 0.78));
  opacity: 0.72;
}

.metric-card :deep(.el-card__body) {
  padding: 14px 16px;
  min-height: 108px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.metric-card p {
  margin: 0;
  color: rgba(192, 206, 230, 0.78);
  font-size: 13px;
}

.metric-badge {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(131, 153, 194, 0.24);
  background: rgba(20, 31, 51, 0.66);
  color: rgba(215, 225, 241, 0.72);
  font-size: 11px;
  line-height: 1;
}

.metric-card h3 {
  margin: 8px 0 0;
  color: var(--user-text-primary);
  font-size: 24px;
  letter-spacing: 0.01em;
}

.metric-note {
  margin-top: auto;
  padding-top: 10px;
  color: rgba(168, 187, 217, 0.76);
  font-size: 12px;
  line-height: 1.5;
}

.curve-card {
  margin-bottom: 12px;
}

.curve-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.curve-title {
  color: var(--user-text-primary);
  font-size: 15px;
  font-weight: 700;
}

.curve-subtitle {
  margin: 6px 0 0;
  max-width: 720px;
  color: rgba(194, 207, 231, 0.78);
  font-size: 12px;
  line-height: 1.7;
}

.curve-badge {
  flex-shrink: 0;
  padding: 6px 10px;
  border: 1px solid rgba(195, 162, 110, 0.32);
  border-radius: 999px;
  background: rgba(195, 162, 110, 0.12);
  color: #e6c89c;
  font-size: 12px;
  line-height: 1;
}

.chart-wrap {
  width: 100%;
  border: 1px solid rgba(145, 166, 206, 0.28);
  border-radius: 16px;
  padding: 14px;
  background:
    radial-gradient(circle at top left, rgba(76, 133, 188, 0.12), transparent 42%),
    rgba(8, 17, 32, 0.92);
}

.chart {
  width: 100%;
  height: 300px;
  display: block;
}

.plot-frame {
  fill: rgba(8, 15, 29, 0.88);
  stroke: rgba(151, 174, 214, 0.14);
  stroke-width: 1;
}

.grid-line {
  stroke: rgba(164, 183, 219, 0.16);
  stroke-dasharray: 4 5;
}

.axis {
  stroke: rgba(148, 170, 208, 0.82);
  stroke-width: 1.1;
}

.curve-area {
  fill: rgba(103, 211, 220, 0.12);
}

.curve-line {
  fill: none;
  stroke: url(#taskRiskLineGradient);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.curve-dot {
  fill: #f0d09a;
  stroke: rgba(8, 16, 29, 0.96);
  stroke-width: 1.4;
}

.curve-dot.is-latest {
  fill: #ffd996;
}

.curve-dot-halo {
  fill: rgba(255, 217, 150, 0.18);
  stroke: rgba(255, 217, 150, 0.48);
  stroke-width: 1.2;
}

.axis-title,
.axis-label {
  fill: rgba(224, 232, 244, 0.9);
}

.axis-title {
  font-size: 12px;
  font-weight: 600;
}

.axis-title-right {
  text-anchor: end;
}

.axis-label {
  font-size: 11px;
}

.axis-label-left {
  text-anchor: end;
}

.axis-label-bottom {
  text-anchor: middle;
}

.curve-note {
  margin: 12px 0 0;
  color: rgba(194, 207, 231, 0.76);
  font-size: 12px;
  line-height: 1.7;
}

.meta-card {
  margin: 12px 0 16px;
}

.meta-card :deep(.el-descriptions__table),
.meta-card :deep(.el-descriptions__cell) {
  border-color: rgba(130, 154, 196, 0.24);
}

.meta-card :deep(.el-descriptions__label.el-descriptions__cell.is-bordered-label) {
  background: rgba(20, 31, 51, 0.92);
  color: rgba(190, 203, 227, 0.8);
}

.meta-card :deep(.el-descriptions__content.el-descriptions__cell.is-bordered-content) {
  background: rgba(10, 16, 29, 0.88);
  color: var(--user-text-primary);
  word-break: break-word;
}

.event-timeline {
  margin-top: 8px;
}

.event-timeline :deep(.el-timeline-item__timestamp) {
  color: rgba(175, 190, 216, 0.7);
}

.event-timeline :deep(.el-timeline-item__content > .el-card) {
  border: 1px solid rgba(130, 154, 196, 0.2);
  background: linear-gradient(180deg, rgba(12, 20, 34, 0.9), rgba(9, 15, 28, 0.9));
}

.node-title {
  margin: 0;
  color: var(--user-text-primary);
  font-weight: 600;
}

.node-detail {
  margin: 8px 0 0;
  color: rgba(194, 207, 231, 0.8);
  line-height: 1.6;
}

.muted {
  margin: 0;
  color: var(--user-text-secondary);
}

@media (max-width: 900px) {
  .curve-card-header {
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  h2 {
    font-size: 26px;
  }

  .step-row {
    padding: 8px;
  }

  .metric-card :deep(.el-card__body) {
    min-height: auto;
  }
}
</style>
