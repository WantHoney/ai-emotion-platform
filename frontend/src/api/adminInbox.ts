import http from './http'

export type AdminInboxLevel = 'critical' | 'warning' | 'info' | 'success'
export type AdminInboxCategory = 'warning' | 'system' | 'model' | 'schedule' | 'psy_center'

export interface AdminInboxItem {
  id: string
  category: AdminInboxCategory
  level: AdminInboxLevel
  title: string
  detail?: string
  occurredAt?: string
  route?: string
  routeLabel?: string
  sourceType?: string
  sourceId?: string | number
  read?: boolean
  archived?: boolean
}

export interface AdminInboxSummary {
  total: number
  highPriority: number
  unread?: number
  categoryCounts: Partial<Record<AdminInboxCategory, number>>
}

export interface AdminInboxPreferences {
  categories: Partial<Record<AdminInboxCategory, boolean>>
}

export interface AdminInboxResponse {
  generatedAt?: string
  preferences?: AdminInboxPreferences
  summary: AdminInboxSummary
  items: AdminInboxItem[]
}

export const getAdminInbox = async (params?: {
  limit?: number
  archived?: boolean
  pendingWarningsOnly?: boolean
}) => {
  const response = await http.get<AdminInboxResponse>('/api/admin/inbox', { params })
  return response.data
}

export const updateAdminInboxPreferences = async (payload: {
  warningEnabled?: boolean
  systemEnabled?: boolean
  modelEnabled?: boolean
  scheduleEnabled?: boolean
  psyCenterEnabled?: boolean
}) => {
  const response = await http.put<AdminInboxPreferences>('/api/admin/inbox/preferences', payload)
  return response.data
}

export const updateAdminInboxState = async (payload: {
  items: Array<{
    messageId: string
    sourceType?: string
    sourceId?: string | number
  }>
  read?: boolean
  archived?: boolean
}) => {
  const response = await http.post<{ success: boolean }>('/api/admin/inbox/state', payload)
  return response.data
}
