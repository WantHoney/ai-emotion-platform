import http from './http'

export interface StartAnalysisResponse {
  taskId: number
}

export const startAnalysis = (audioId: number) => {
  return http.post<StartAnalysisResponse>(`/api/audio/${audioId}/analysis/start`)
}
