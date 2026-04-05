export interface NarrativeAdviceBuckets {
  instant: string[]
  longTerm: string[]
  resource: string[]
}

export interface NarrativePayload {
  status?: string
  provider?: string
  model?: string
  summary?: string
  explanation?: string
  adviceBuckets: NarrativeAdviceBuckets
  personalizedAdvice: string[]
  safetyNotice?: string
  error?: string
}

export interface NarrativeMetaLike {
  status?: string
  model?: string
}

const toStringValue = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

const toStringArray = (value: unknown): string[] => {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => toStringValue(item))
    .filter((item): item is string => Boolean(item))
}

const flattenAdviceBuckets = (buckets: NarrativeAdviceBuckets): string[] => {
  const items = new Set<string>()
  buckets.instant.forEach((item) => items.add(item))
  buckets.longTerm.forEach((item) => items.add(item))
  buckets.resource.forEach((item) => items.add(item))
  return [...items]
}

export const splitAdviceText = (value?: string | null): string[] => {
  if (!value) return []
  return value
    .split(/[\r\n;；]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export const formatNarrativeModel = (model?: string): string | null => {
  const normalized = toStringValue(model)
  if (!normalized) return null
  if (/^gemma4(?::|$)/i.test(normalized)) return `Gemma 4（${normalized}）`
  if (/^gemma(?::|$)/i.test(normalized)) return `Gemma（${normalized}）`
  return normalized
}

export const buildNarrativeSourceNote = (narrative?: NarrativeMetaLike | null): string => {
  if (narrative?.status === 'ready') {
    const modelLabel = formatNarrativeModel(narrative.model)
    const modelName = modelLabel?.startsWith('Gemma 4') ? 'Gemma 4 模型' : '本地模型'
    return `以下解释与建议由本地部署的 ${modelName} 辅助生成，数据不会上传至外部服务。`
  }
  if (narrative?.status === 'disabled') {
    return '当前未启用本地模型增强，以下内容为系统基础建议。'
  }
  return '当前以系统基础建议为主，本地模型结果暂未成功生成。'
}

export const buildNarrativeTechNote = (narrative?: NarrativeMetaLike | null): string | null => {
  if (narrative?.status !== 'ready') return null
  const modelLabel = formatNarrativeModel(narrative.model)
  return modelLabel ? `模型：${modelLabel}` : null
}

export const parseNarrativeFromRawJson = (rawJson?: string | null): NarrativePayload | null => {
  if (!rawJson) return null

  try {
    const root = JSON.parse(rawJson) as Record<string, unknown>
    const node = (root.narrative ?? null) as Record<string, unknown> | null
    if (!node) return null

    const adviceBucketsNode = (node.adviceBuckets ?? null) as Record<string, unknown> | null
    const adviceBuckets: NarrativeAdviceBuckets = {
      instant: toStringArray(adviceBucketsNode?.instant),
      longTerm: toStringArray(adviceBucketsNode?.longTerm),
      resource: toStringArray(adviceBucketsNode?.resource),
    }

    const personalizedAdvice = toStringArray(node.personalizedAdvice)
    return {
      status: toStringValue(node.status),
      provider: toStringValue(node.provider),
      model: toStringValue(node.model),
      summary: toStringValue(node.summary),
      explanation: toStringValue(node.explanation),
      adviceBuckets,
      personalizedAdvice: personalizedAdvice.length ? personalizedAdvice : flattenAdviceBuckets(adviceBuckets),
      safetyNotice: toStringValue(node.safetyNotice),
      error: toStringValue(node.error),
    }
  } catch {
    return null
  }
}
