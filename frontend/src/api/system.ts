import http from './http'

export interface ServiceHealth {
  status: 'UP' | 'DOWN' | 'DEGRADED'
  latencyMs?: number
  message?: string
}

export interface SystemStatus {
  backend: ServiceHealth
  db: ServiceHealth
  ser: ServiceHealth
  metrics: {
    runningTasks: number
    queuedTasks: number
    failedTasks24h: number
    avgSerLatencyMs: number
  }
  config: {
    serBaseUrl?: string
    requestTimeoutMs?: number
  }
}

export const getSystemStatus = () => {
  return http.get<SystemStatus>('/api/system/status')
}
