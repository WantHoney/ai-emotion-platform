<script setup lang="ts">
import { DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { useTaskPolling } from '@/composables/useTaskPolling'

type TimelineNode = {
  key: string
  title: string
  timestamp?: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  detail?: string
}

const route = useRoute()
const router = useRouter()

const taskId = computed(() => Number(route.params.id))
const { pollingState, task, errorMessage, statusText, start } = useTaskPolling(taskId, {
  baseIntervalMs: 3000,
  maxIntervalMs: 15000,
  timeoutMs: 1800000,
  maxRetry: 8,
})

const displayTaskNo = computed(() => task.value?.taskNo || `TASK-${taskId.value}`)

const flowStep = computed(() => {
  const status = task.value?.status
  if (status === 'RUNNING') return 1
  if (status === 'RETRY_WAIT') return 2
  if (status === 'SUCCESS') return 3
  if (status === 'FAILED' || status === 'CANCELED') return 4
  return 0
})

const timelineNodes = computed<TimelineNode[]>(() => {
  const data = task.value
  if (!data) return []

  const nodes: TimelineNode[] = [
    {
      key: `created-${data.id}`,
      title: '任务创建',
      timestamp: data.createdAt,
      type: 'primary',
      detail: `任务已入队，当前状态：${data.status}`,
    },
  ]

  if (data.startedAt) {
    nodes.push({
      key: `started-${data.id}`,
      title: '开始处理',
      timestamp: data.startedAt,
      type: 'warning',
      detail: `首次进入处理，当前累计尝试 ${data.attemptCount ?? 0} 次`,
    })
  }

  if ((data.attemptCount ?? 0) > 1) {
    nodes.push({
      key: `retry-${data.id}`,
      title: '发生重试',
      timestamp: data.updatedAt,
      type: 'info',
      detail: `累计尝试 ${data.attemptCount} 次，最大重试 ${data.maxAttempts ?? '-'} 次`,
    })
  }

  if (data.status === 'RETRY_WAIT') {
    nodes.push({
      key: `retry-wait-${data.id}`,
      title: '重试等待',
      timestamp: data.nextRunAt ?? data.updatedAt,
      type: 'warning',
      detail: data.nextRunAt ? `等待下一次执行：${data.nextRunAt}` : '等待调度器重新执行',
    })
  }

  if (data.status === 'RUNNING') {
    nodes.push({
      key: `running-${data.id}`,
      title: '处理中',
      timestamp: data.updatedAt,
      type: 'primary',
      detail: '任务正在执行中',
    })
  }

  if (data.status === 'SUCCESS') {
    nodes.push({
      key: `success-${data.id}`,
      title: '处理成功',
      timestamp: data.finishedAt ?? data.updatedAt,
      type: 'success',
      detail: '任务已完成，可查看报告详情',
    })
  }

  if (data.status === 'FAILED' || data.status === 'CANCELED') {
    nodes.push({
      key: `failed-${data.id}`,
      title: data.status === 'FAILED' ? '处理失败' : '任务取消',
      timestamp: data.finishedAt ?? data.updatedAt,
      type: 'danger',
      detail: data.errorMessage || '任务未完成',
    })
  }

  return nodes
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
          <p class="subtitle">任务轨迹</p>
          <h2>任务编号 {{ displayTaskNo }}</h2>
          <p class="tip">任务ID: {{ taskId }}</p>
        </div>
        <div class="header-actions">
          <el-button :icon="DocumentCopy" @click="copyTaskNo">复制编号</el-button>
          <el-button @click="router.push(`/app/tasks/${taskId}`)">返回详情</el-button>
        </div>
      </div>

      <LoadingState v-if="pollingState === 'loading' && !task" />
      <ErrorState
        v-else-if="pollingState === 'error' && !task"
        title="任务时间线加载失败"
        :detail="errorMessage"
        @retry="start"
      />
      <EmptyState
        v-else-if="!task"
        title="暂无任务数据"
        description="任务可能尚未创建完成，请稍后重试。"
        action-text="重新加载"
        @action="start"
      />
      <template v-else>
        <el-steps :active="flowStep" finish-status="success" simple class="step-row">
          <el-step title="创建" />
          <el-step title="执行" />
          <el-step title="重试" />
          <el-step title="完成" />
          <el-step title="结束" />
        </el-steps>

        <el-descriptions border :column="2" class="meta-card">
          <el-descriptions-item label="当前状态">{{ statusText }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">
            {{ task.attemptCount ?? 0 }} / {{ task.maxAttempts ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="下一次执行">{{ task.nextRunAt ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="Trace ID">{{ task.traceId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ task.errorMessage ?? '-' }}</el-descriptions-item>
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
}

.step-row {
  margin: 12px 0;
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
</style>
