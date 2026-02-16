import axios from 'axios'

export type ErrorStatePayload = {
  title: string
  detail: string
  traceId?: string
}

export const parseError = (error: unknown, fallbackTitle: string): ErrorStatePayload => {
  if (axios.isAxiosError(error)) {
    const detail = String(error.response?.data?.message ?? error.message ?? '请求失败')
    const traceId = error.response?.data?.traceId as string | undefined
    return {
      title: fallbackTitle,
      detail,
      traceId,
    }
  }

  if (error instanceof Error) {
    return { title: fallbackTitle, detail: error.message }
  }

  return {
    title: fallbackTitle,
    detail: '发生未知错误，请稍后重试。',
  }
}
