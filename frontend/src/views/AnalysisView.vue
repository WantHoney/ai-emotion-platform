<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getTask, type AnalysisTask } from '@/api/task'

const route = useRoute()
const task = ref<AnalysisTask | null>(null)
const loading = ref(false)

const taskId = computed(() => Number(route.params.taskId))

let pollTimer: number | null = null

const stopPoll = () => {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

const pollTask = async () => {
  if (!Number.isFinite(taskId.value) || taskId.value <= 0) {
    ElMessage.error('无效的 taskId')
    stopPoll()
    return
  }

  loading.value = true
  try {
    const { data } = await getTask(taskId.value)
    task.value = data

    if (['SUCCESS', 'FAILED', 'CANCELED'].includes(data.status)) {
      stopPoll()
    }
  } catch {
    ElMessage.error('获取任务状态失败')
    stopPoll()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await pollTask()
  pollTimer = window.setInterval(pollTask, 3000)
})

onUnmounted(() => {
  stopPoll()
})
</script>

<template>
  <div class="page-wrap">
    <el-card class="card" shadow="hover" v-loading="loading">
      <template #header>任务详情 #{{ taskId }}</template>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="任务状态">{{ task?.status ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Overall 情绪">{{ task?.result?.overall ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Confidence">{{ task?.result?.confidence ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Risk Score">{{ task?.result?.risk_score ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Risk Level">{{ task?.result?.risk_level ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Advice">{{ task?.result?.advice_text ?? '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.page-wrap {
  max-width: 840px;
  margin: 48px auto;
  padding: 0 16px;
}
</style>
