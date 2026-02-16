import http from './http'

export interface UploadAudioResponse {
  audioId: number
}

export interface UploadSessionInitResponse {
  sessionId: number
  uploadId: string
  status: string
  totalChunks: number
  receivedChunks: number
  expiresAt?: string
}

export interface UploadChunkResponse {
  uploadId: string
  chunkIndex: number
  receivedChunks: number
  totalChunks: number
  progressPercent: number
  completed: boolean
}

export interface UploadSessionStatusResponse {
  uploadId: string
  status: string
  totalChunks: number
  receivedChunks: number
  progressPercent: number
  uploadedChunkIndexes: number[]
  mergedAudioId?: number
  expiresAt?: string
}

export interface UploadSessionCompleteResponse {
  uploadId: string
  audioId: number
  taskId?: number | null
  fileName: string
  fileUrl: string
  status: string
}

export const uploadAudio = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)

  return http.post<UploadAudioResponse>('/api/audio/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export const initUploadSession = async (payload: {
  fileName: string
  contentType?: string
  fileSize?: number
  totalChunks: number
}) => {
  const response = await http.post<UploadSessionInitResponse>('/api/audio/upload-sessions/init', payload)
  return response.data
}

export const uploadSessionChunk = async (payload: {
  uploadId: string
  chunkIndex: number
  chunk: Blob
  onProgress?: (percent: number) => void
}) => {
  const formData = new FormData()
  formData.append('file', payload.chunk, `chunk-${payload.chunkIndex}`)

  const response = await http.put<UploadChunkResponse>(
    `/api/audio/upload-sessions/${payload.uploadId}/chunks/${payload.chunkIndex}`,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (event) => {
        if (!payload.onProgress) return
        const total = event.total ?? payload.chunk.size
        if (!total) return
        payload.onProgress(Math.min(100, Math.round((event.loaded * 100) / total)))
      },
    },
  )
  return response.data
}

export const getUploadSessionStatus = async (uploadId: string) => {
  const response = await http.get<UploadSessionStatusResponse>(`/api/audio/upload-sessions/${uploadId}`)
  return response.data
}

export const completeUploadSession = async (uploadId: string, autoStartTask = true) => {
  const response = await http.post<UploadSessionCompleteResponse>(
    `/api/audio/upload-sessions/${uploadId}/complete`,
    {
      autoStartTask,
    },
  )
  return response.data
}

export const cancelUploadSession = async (uploadId: string) => {
  const response = await http.delete<{ uploadId: string; status: string }>(
    `/api/audio/upload-sessions/${uploadId}`,
  )
  return response.data
}
