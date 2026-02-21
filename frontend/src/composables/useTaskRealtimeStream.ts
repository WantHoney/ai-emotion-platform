import { onMounted, onUnmounted, ref, watch, type Ref } from 'vue'

import type { TaskRealtimeSnapshot } from '@/api/task'
import { USER_TOKEN_KEY } from '@/stores/userAuth'

type RealtimeState = 'idle' | 'connecting' | 'open' | 'closed' | 'error'

const TERMINAL_CLOSE_CODES = new Set([4400, 4401, 4403, 1008, 1002])

const normalizeWsBase = () => {
  const rawBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? window.location.origin
  const baseUrl = rawBaseUrl.startsWith('http://') || rawBaseUrl.startsWith('https://')
    ? new URL(rawBaseUrl)
    : new URL(rawBaseUrl, window.location.origin)

  const wsProtocol = baseUrl.protocol === 'https:' ? 'wss:' : 'ws:'
  let basePath = baseUrl.pathname === '/' ? '' : baseUrl.pathname.replace(/\/$/, '')
  if (basePath.endsWith('/api')) {
    basePath = basePath.slice(0, -4)
  }
  return `${wsProtocol}//${baseUrl.host}${basePath}`
}

export const useTaskRealtimeStream = (taskIdRef: Ref<number>) => {
  const snapshot = ref<TaskRealtimeSnapshot | null>(null)
  const state = ref<RealtimeState>('idle')
  const errorMessage = ref('')
  const reconnectCount = ref(0)
  const lastMessageAt = ref<number | null>(null)

  let socket: WebSocket | null = null
  let reconnectTimer: number | null = null
  let manualStop = false

  const clearReconnectTimer = () => {
    if (reconnectTimer != null) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const closeSocket = () => {
    if (socket) {
      socket.onopen = null
      socket.onclose = null
      socket.onerror = null
      socket.onmessage = null
      if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
        socket.close(1000, 'client_closed')
      }
      socket = null
    }
  }

  const scheduleReconnect = () => {
    if (manualStop) return
    const delay = Math.min(1000 * 2 ** reconnectCount.value, 10000)
    clearReconnectTimer()
    reconnectTimer = window.setTimeout(() => {
      connect()
    }, delay)
  }

  const connect = () => {
    clearReconnectTimer()
    closeSocket()

    const taskId = taskIdRef.value
    if (!Number.isFinite(taskId) || taskId <= 0) {
      state.value = 'error'
      errorMessage.value = '任务ID无效'
      return
    }

    const accessToken = localStorage.getItem(USER_TOKEN_KEY)
    if (!accessToken) {
      state.value = 'error'
      errorMessage.value = '缺少登录令牌，无法建立实时连接'
      return
    }

    const wsBase = normalizeWsBase()
    const wsUrl = `${wsBase}/ws/tasks/stream?taskId=${taskId}&accessToken=${encodeURIComponent(accessToken)}`

    state.value = 'connecting'
    const ws = new WebSocket(wsUrl)
    socket = ws

    ws.onopen = () => {
      state.value = 'open'
      errorMessage.value = ''
      reconnectCount.value = 0
    }

    ws.onmessage = (event: MessageEvent<string>) => {
      lastMessageAt.value = Date.now()
      try {
        const payload = JSON.parse(event.data) as TaskRealtimeSnapshot
        if (payload.event !== 'snapshot') return
        snapshot.value = payload
      } catch {
        // ignore malformed payload
      }
    }

    ws.onerror = () => {
      state.value = 'error'
      errorMessage.value = '实时通道异常'
    }

    ws.onclose = (event) => {
      socket = null
      state.value = 'closed'

      const terminalSnapshot = snapshot.value?.terminal === true
      if (manualStop || terminalSnapshot) return

      if (TERMINAL_CLOSE_CODES.has(event.code)) {
        errorMessage.value = `实时连接关闭（${event.code}）`
        return
      }

      reconnectCount.value += 1
      scheduleReconnect()
    }
  }

  const start = () => {
    manualStop = false
    connect()
  }

  const stop = () => {
    manualStop = true
    clearReconnectTimer()
    closeSocket()
    state.value = 'closed'
  }

  watch(taskIdRef, () => {
    snapshot.value = null
    reconnectCount.value = 0
    if (manualStop) return
    connect()
  })

  onMounted(() => {
    start()
  })

  onUnmounted(() => {
    stop()
  })

  return {
    snapshot,
    state,
    errorMessage,
    reconnectCount,
    lastMessageAt,
    start,
    stop,
  }
}
