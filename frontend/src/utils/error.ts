import axios from 'axios'

import { toErrorMessage } from './errorMessage'

export type ErrorStatePayload = {
  title: string
  detail: string
  traceId?: string
}

const isApiLikeError = (error: unknown): error is { message?: unknown; traceId?: unknown } => {
  return typeof error === 'object' && error !== null && 'message' in error
}

export const parseError = (error: unknown, fallbackTitle: string): ErrorStatePayload => {
  if (axios.isAxiosError(error)) {
    return {
      title: fallbackTitle,
      detail: toErrorMessage(error.response?.data?.message ?? error.message, '请求失败'),
      traceId: error.response?.data?.traceId as string | undefined,
    }
  }

  if (isApiLikeError(error)) {
    return {
      title: fallbackTitle,
      detail: toErrorMessage(error.message, '请求失败'),
      traceId: typeof error.traceId === 'string' ? error.traceId : undefined,
    }
  }

  return {
    title: fallbackTitle,
    detail: toErrorMessage(error, '发生未知错误，请稍后重试。'),
  }
}
