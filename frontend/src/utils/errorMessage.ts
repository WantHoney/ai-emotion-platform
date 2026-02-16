const isRecord = (value: unknown): value is Record<string, unknown> => {
  return typeof value === 'object' && value !== null
}

export const toErrorMessage = (value: unknown, fallback = 'Request failed'): string => {
  if (typeof value === 'string') {
    const message = value.trim()
    return message || fallback
  }

  if (value instanceof Error) {
    return toErrorMessage(value.message, fallback)
  }

  if (isRecord(value)) {
    if (typeof value.message === 'string' && value.message.trim()) {
      return value.message.trim()
    }
    if (typeof value.error === 'string' && value.error.trim()) {
      return value.error.trim()
    }
    try {
      return JSON.stringify(value)
    } catch {
      return fallback
    }
  }

  if (value == null) {
    return fallback
  }

  return String(value)
}
