import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElButton, ElMessage, ElNotification } from 'element-plus'
import { h } from 'vue'

import {
  ADMIN_PROFILE_KEY,
  ADMIN_REFRESH_TOKEN_KEY,
  ADMIN_TOKEN_KEY,
} from '@/stores/adminAuth'
import {
  USER_PROFILE_KEY,
  USER_REFRESH_TOKEN_KEY,
  USER_TOKEN_KEY,
} from '@/stores/userAuth'
import { useUiStore } from '@/stores/ui'
import { pinia } from '@/stores'
import { toErrorMessage } from '@/utils/errorMessage'

export interface ApiError {
  status?: number
  code: 'NETWORK' | 'UNAUTHORIZED' | 'TIMEOUT' | 'SERVER' | 'UNKNOWN'
  message: string
  traceId?: string
  details?: unknown
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/',
  timeout: 12000,
})

const isAdminRequest = (url?: string) => {
  if (!url) return false
  return (
    url.startsWith('/api/admin') ||
    url.startsWith('api/admin') ||
    url.startsWith('/api/system') ||
    url.startsWith('api/system')
  )
}

const isAuthRequest = (url?: string) => {
  if (!url) return false
  return url.startsWith('/api/auth') || url.startsWith('api/auth')
}

const isLogoutRequest = (url?: string) => {
  if (!url) return false
  return url.endsWith('/api/auth/logout') || url.endsWith('api/auth/logout')
}

const normalizeApiError = (error: unknown): ApiError => {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<{ message?: unknown; traceId?: string }>

    if (axiosError.code === 'ECONNABORTED') {
      return { code: 'TIMEOUT', message: '请求超时，请稍后重试。' }
    }

    if (!axiosError.response) {
      return { code: 'NETWORK', message: '网络异常，请检查网络连接。' }
    }

    const status = axiosError.response.status
    const traceId =
      axiosError.response.headers['x-trace-id'] ?? axiosError.response.data?.traceId ?? undefined

    if (status === 401) {
      return { status, code: 'UNAUTHORIZED', message: '登录状态失效，请重新登录。', traceId }
    }

    if (status >= 500) {
      return {
        status,
        code: 'SERVER',
        message: toErrorMessage(axiosError.response.data?.message, '服务异常，请稍后重试。'),
        traceId,
        details: axiosError.response.data,
      }
    }

    return {
      status,
      code: 'UNKNOWN',
      message: toErrorMessage(axiosError.response.data?.message, '请求失败，请稍后重试。'),
      traceId,
      details: axiosError.response.data,
    }
  }

  if (typeof error === 'object' && error !== null && 'message' in error) {
    const candidate = (error as { message?: unknown }).message
    return { code: 'UNKNOWN', message: toErrorMessage(candidate, '发生未知错误，请稍后重试。') }
  }

  return { code: 'UNKNOWN', message: toErrorMessage(error, '发生未知错误，请稍后重试。') }
}

const showApiErrorToast = (apiError: ApiError) => {
  if (!apiError.traceId) {
    ElMessage.error(apiError.message)
    return
  }

  ElNotification({
    title: '接口请求失败',
    type: 'error',
    duration: 8000,
    message: h('div', { class: 'api-error-toast' }, [
      h('div', apiError.message),
      h('div', { style: 'margin-top: 6px; display:flex; align-items:center; gap:8px;' }, [
        h('span', { style: 'font-size:12px;' }, `traceId: ${apiError.traceId}`),
        h(
          ElButton,
          {
            size: 'small',
            text: true,
            onClick: async () => {
              if (apiError.traceId && navigator?.clipboard) {
                await navigator.clipboard.writeText(apiError.traceId)
              }
              ElMessage.success('traceId 已复制')
            },
          },
          () => '复制',
        ),
      ]),
    ]),
  })
}

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const uiStore = useUiStore(pinia)
  uiStore.startLoading()

  if (isAuthRequest(config.url) && !isLogoutRequest(config.url)) {
    return config
  }

  const tokenKey = isAdminRequest(config.url) ? ADMIN_TOKEN_KEY : USER_TOKEN_KEY
  const token = localStorage.getItem(tokenKey)
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

http.interceptors.response.use(
  (response) => {
    const uiStore = useUiStore(pinia)
    uiStore.stopLoading()
    return response
  },
  (error) => {
    const uiStore = useUiStore(pinia)
    uiStore.stopLoading()

    const apiError = normalizeApiError(error)
    const requestUrl = (error as AxiosError)?.config?.url
    const adminRequest = isAdminRequest(requestUrl)

    if (apiError.code === 'UNAUTHORIZED') {
      if (adminRequest) {
        localStorage.removeItem(ADMIN_TOKEN_KEY)
        localStorage.removeItem(ADMIN_REFRESH_TOKEN_KEY)
        localStorage.removeItem(ADMIN_PROFILE_KEY)
        ElMessage.warning('管理员登录已过期，请重新登录。')
      } else {
        localStorage.removeItem(USER_TOKEN_KEY)
        localStorage.removeItem(USER_REFRESH_TOKEN_KEY)
        localStorage.removeItem(USER_PROFILE_KEY)
        ElMessage.warning('用户登录已过期，请重新登录。')
      }
    } else {
      showApiErrorToast(apiError)
    }

    return Promise.reject(apiError)
  },
)

export default http
export { normalizeApiError }
