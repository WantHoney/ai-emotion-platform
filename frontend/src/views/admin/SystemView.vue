<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { getSystemStatus, type SystemStatus } from '@/api/system'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const data = ref<SystemStatus | null>(null)
const errorState = ref<ErrorStatePayload | null>(null)

const suggestions = computed(() => {
  const list: string[] = []
  if (data.value?.ser?.status === 'DOWN') list.push('SER 服务离线，建议先恢复 SER 后再提交分析任务。')
  if ((data.value?.metrics?.avgSerLatencyMs ?? 0) > 2000) list.push('SER 延迟较高，建议增加超时阈值或排查网络链路。')
  if (!list.length) list.push('系统整体健康，可继续处理任务。')
  return list
})

const loadStatus = async () => {
  loading.value = true
  errorState.value = null
  try {
    const res = await getSystemStatus()
    data.value = res.data
  } catch (error) {
    errorState.value = parseError(error, '系统状态加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadStatus()
})
</script>

<template>
  <el-card>
    <template #header>系统状态</template>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadStatus"
    />
    <EmptyState
      v-else-if="!data"
      title="暂无系统数据"
      description="尚未拉取到监控数据。"
      action-text="重新刷新"
      @action="loadStatus"
    />
    <template v-else>
      <el-descriptions border :column="3">
        <el-descriptions-item label="backend">{{ data.backend?.status ?? '-' }} / {{ data.backend?.latencyMs ?? '-' }}ms</el-descriptions-item>
        <el-descriptions-item label="db">{{ data.db?.status ?? '-' }} / {{ data.db?.latencyMs ?? '-' }}ms</el-descriptions-item>
        <el-descriptions-item label="ser">{{ data.ser?.status ?? '-' }} / {{ data.ser?.latencyMs ?? '-' }}ms</el-descriptions-item>
      </el-descriptions>

      <el-row :gutter="16" class="mt-16">
        <el-col :span="6"><el-statistic title="运行中任务" :value="data.metrics?.runningTasks ?? 0" /></el-col>
        <el-col :span="6"><el-statistic title="排队任务" :value="data.metrics?.queuedTasks ?? 0" /></el-col>
        <el-col :span="6"><el-statistic title="24h失败任务" :value="data.metrics?.failedTasks24h ?? 0" /></el-col>
        <el-col :span="6"><el-statistic title="SER平均延迟" :value="data.metrics?.avgSerLatencyMs ?? 0" suffix="ms" /></el-col>
      </el-row>

      <el-card class="mt-16">
        <template #header>配置与建议</template>
        <p>SER_BASE_URL: {{ data.config?.serBaseUrl ?? '-' }}</p>
        <p>请求超时: {{ data.config?.requestTimeoutMs ?? '-' }} ms</p>
        <el-alert v-for="item in suggestions" :key="item" :title="item" type="warning" show-icon :closable="false" class="mb-8" />
      </el-card>
    </template>
  </el-card>
</template>

<style scoped>
.mt-16 {
  margin-top: 16px;
}

.mb-8 {
  margin-bottom: 8px;
}
</style>
