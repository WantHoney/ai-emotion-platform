import http from './http'

export interface AdminUserListItem {
  id: number
  username: string
  status: string
  role_code: string
  is_test_account?: number | boolean
  created_at?: string
  updated_at?: string
  last_login_at?: string
  task_count: number
  report_count: number
  warning_count: number
  active_session_count: number
}

export interface AdminUserTaskItem {
  id: number
  audio_file_id?: number
  original_name?: string
  status: string
  trace_id?: string
  report_id?: number
  created_at?: string
  updated_at?: string
}

export interface AdminUserReportItem {
  id: number
  task_id?: number
  audio_id?: number
  original_name?: string
  risk_level?: string
  overall_emotion?: string
  created_at?: string
}

export interface AdminUserWarningItem {
  id: number
  task_id?: number
  report_id?: number
  risk_score?: number
  risk_level?: string
  top_emotion?: string
  status: string
  breached?: number | boolean
  created_at?: string
}

export interface AdminUserDetailResponse {
  profile: AdminUserListItem
  recentTasks: AdminUserTaskItem[]
  recentReports: AdminUserReportItem[]
  recentWarnings: AdminUserWarningItem[]
}

export interface PaginatedResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  systemActiveSessions?: number
}

export const getAdminUsers = async (params: {
  page: number
  pageSize: number
  keyword?: string
  role?: string
  status?: string
  accountType?: 'ALL' | 'REAL_ONLY' | 'TEST_ONLY'
}) => {
  const response = await http.get<PaginatedResponse<AdminUserListItem>>('/api/admin/users', { params })
  return response.data
}

export const getAdminUserDetail = async (userId: number) => {
  const response = await http.get<AdminUserDetailResponse>(`/api/admin/users/${userId}`)
  return response.data
}

export const updateAdminUserStatus = async (userId: number, status: 'ACTIVE' | 'DISABLED') => {
  const response = await http.put(`/api/admin/users/${userId}/status`, { status })
  return response.data
}
