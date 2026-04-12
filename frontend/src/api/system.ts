import http from './http'

export interface ServiceHealth {
  status: 'UP' | 'DOWN' | 'DEGRADED'
  latencyMs?: number
  message?: string
}

export interface RuntimeModelInfo {
  modelType: string
  label: string
  source: string
  status: 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'
  rawValue?: string
  registryComparable?: string
  detail?: string
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
    runtimeRegistryEnvHint?: string
  }
  runtime: {
    registryEnvHint?: string
    activeProfiles?: string[]
    aiMode?: string
    modeDescription?: string
    textScoringProvider?: string
    textScoringFallbackToSer?: boolean
    narrativeProvider?: string
    models?: {
      asr?: RuntimeModelInfo
      audioEmotion?: RuntimeModelInfo
      text?: RuntimeModelInfo
      fusion?: RuntimeModelInfo
      psi?: RuntimeModelInfo
    }
  }
}

export const getSystemStatus = () => {
  return http.get<SystemStatus>('/api/system/status')
}
