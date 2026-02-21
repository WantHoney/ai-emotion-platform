import { computed, onUnmounted, ref, watch, type Ref } from 'vue'

import { getTask, type AnalysisTask } from '@/api/task'
import type { ApiError } from '@/api/http'

type PollingState = 'idle' | 'loading' | 'success' | 'error'

interface UseTaskPollingOptions {
  baseIntervalMs?: number
  maxIntervalMs?: number
  timeoutMs?: number
  maxRetry?: number
}

const FINAL_STATES = ['SUCCESS', 'FAILED', 'CANCELED'] as const
const STATUS_TEXT_MAP: Record<string, string> = {
  PENDING: '待处理',
  RUNNING: '处理中',
  RETRY_WAIT: '等待重试',
  SUCCESS: '处理成功',
  FAILED: '处理失败',
  CANCELED: '已取消',
}

const classifyPollingError = (error: ApiError) => {
  switch (error.code) {
    case 'NETWORK':
      return '网络异常，已自动重试。'
    case 'TIMEOUT':
      return '请求超时，已自动重试。'
    case 'UNAUTHORIZED':
      return '登录失效，请重新登录。'
    case 'SERVER':
      return error.message || '服务暂不可用，已自动重试。'
    default:
      return error.message || '请求失败，已自动重试。'
  }
}

export const useTaskPolling = (taskIdRef: Ref<number>, options: UseTaskPollingOptions = {}) => {
  const { baseIntervalMs = 2000, maxIntervalMs = 10000, timeoutMs = 180000, maxRetry = 5 } = options

  const pollingState = ref<PollingState>('idle')
  const task = ref<AnalysisTask | null>(null)
  const errorMessage = ref('')
  const retryCount = ref(0)

  let timer: number | null = null
  let startedAt = 0

  const clearTimer = () => {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  const stop = () => {
    clearTimer()
  }

  const scheduleNextPoll = (customDelay?: number) => {
    const fallbackDelay = Math.min(baseIntervalMs * 2 ** retryCount.value, maxIntervalMs)
    const nextDelay = Math.max(1000, customDelay ?? fallbackDelay)
    timer = window.setTimeout(() => {
      void pollTask()
    }, nextDelay)
  }

  const pollTask = async () => {
    if (!Number.isFinite(taskIdRef.value) || taskIdRef.value <= 0) {
      pollingState.value = 'error'
      errorMessage.value = '任务ID无效。'
      stop()
      return
    }

    if (!startedAt) {
      startedAt = Date.now()
    }

    if (Date.now() - startedAt > timeoutMs) {
      pollingState.value = 'error'
      errorMessage.value = '任务处理超时，请稍后重试。'
      stop()
      return
    }

    pollingState.value = 'loading'

    try {
      const { data } = await getTask(taskIdRef.value)
      task.value = data
      retryCount.value = 0

      if ((FINAL_STATES as readonly string[]).includes(data.status)) {
        pollingState.value = data.status === 'SUCCESS' ? 'success' : 'error'
        if (data.status !== 'SUCCESS') {
          errorMessage.value = data.errorMessage ?? '任务执行失败。'
        }
        stop()
        return
      }

      const nextRunAt = data.nextRunAt ? new Date(data.nextRunAt).getTime() : Number.NaN
      if (data.status === 'RETRY_WAIT' && Number.isFinite(nextRunAt)) {
        scheduleNextPoll(nextRunAt - Date.now())
        return
      }

      scheduleNextPoll()
    } catch (error) {
      retryCount.value += 1

      const e = error as ApiError
      errorMessage.value = classifyPollingError(e)

      if (retryCount.value > maxRetry) {
        pollingState.value = 'error'
        errorMessage.value = `${errorMessage.value} 已达到最大重试次数。`
        stop()
        return
      }

      scheduleNextPoll()
    }
  }

  const start = async () => {
    stop()
    startedAt = 0
    retryCount.value = 0
    errorMessage.value = ''
    task.value = null
    await pollTask()
  }

  watch(taskIdRef, () => {
    void start()
  })

  onUnmounted(() => {
    stop()
  })

  const statusText = computed(() => {
    if (!task.value) return '等待任务启动'
    const code = task.value.status || '-'
    const localized = STATUS_TEXT_MAP[code] ?? '处理中'
    return `${localized} (${code})`
  })

  return {
    pollingState,
    task,
    errorMessage,
    retryCount,
    statusText,
    start,
    stop,
  }
}
