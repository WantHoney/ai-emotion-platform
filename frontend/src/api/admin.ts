import http from './http'

export type ContentType = 'BANNER' | 'ARTICLE' | 'BOOK' | 'PRACTICE'

export interface LightDashboardData {
  uploadCount?: number
  reportCount?: number
  contentClickCount?: number
  [key: string]: number | string | undefined
}

export const getLightDashboard = async () => {
  const response = await http.get('/api/admin/dashboard/light')
  return response.data as LightDashboardData
}

export const postContentClick = async (contentType: ContentType, contentId: string | number) => {
  await http.post('/api/admin/content-events/click', {
    contentType,
    contentId,
  })
}
