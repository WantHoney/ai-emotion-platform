import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElButton, ElMessage, ElNotification } from 'element-plus'
import { h } from 'vue'

import { REFRESH_TOKEN_KEY, TOKEN_KEY } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { pinia } from '@/stores'

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

const normalizeApiError = (error: unknown): ApiError => {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<{ message?: string; traceId?: string }>

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
        message: axiosError.response.data?.message ?? '服务异常，请稍后重试。',
        traceId,
        details: axiosError.response.data,
      }
    }

    return {
      status,
      code: 'UNKNOWN',
      message: axiosError.response.data?.message ?? '请求失败，请稍后重试。',
      traceId,
      details: axiosError.response.data,
    }
  }

  return { code: 'UNKNOWN', message: '发生未知错误，请稍后重试。' }
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
      h('div', `${apiError.message}`),
      h('div', { style: 'margin-top: 6px; display:flex; align-items:center; gap:8px;' }, [
        h('span', { style: 'font-size:12px;' }, `traceId: ${apiError.traceId}`),
        h(
          ElButton,
          {
            size: 'small',
            text: true,
            onClick: async () => {
              await navigator.clipboard.writeText(apiError.traceId ?? '')
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

  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
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

    if (apiError.code === 'UNAUTHORIZED') {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
      localStorage.removeItem('emotion-user-profile')
      ElMessage.warning('登录已过期，请重新登录。')
    } else {
      showApiErrorToast(apiError)
    }

    return Promise.reject(apiError)
  },
)

export default http
export { normalizeApiError }
