import http from './http'

export interface UploadAudioResponse {
  audioId: number
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
