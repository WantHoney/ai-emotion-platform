export type MediaImageKind = 'article' | 'book' | 'psy'

const PSY_MEDIA_VERSION = '20260412-cityposters-v5'
const PSY_CENTER_CITY_POSTER_MAP: Record<string, string> = {
  '110100': '/assets/psy-centers/cities/beijing-v4.png',
  '310100': '/assets/psy-centers/cities/shanghai-v4.png',
  '330100': '/assets/psy-centers/cities/hangzhou-v4.png',
  '350100': '/assets/psy-centers/cities/fuzhou-v4.png',
  '440100': '/assets/psy-centers/cities/guangzhou-v4.png',
  '440300': '/assets/psy-centers/cities/shenzhen-v6.png',
}
const PSY_CENTER_POSTER_VARIANTS = [
  '/assets/psy-centers/anime/brain-clinic.png',
  '/assets/psy-centers/anime/center-campus.png',
  '/assets/psy-centers/anime/counsel-room.png',
  '/assets/psy-centers/anime/hospital-specialty.png',
  '/assets/psy-centers/anime/recovery-garden.png',
] as const

const FALLBACK_IMAGE_MAP: Record<MediaImageKind, string> = {
  article: '/assets/articles/article-fallback.svg',
  book: '/assets/books/book-fallback.svg',
  psy: `/assets/illustrations/psy-center-fallback.svg?v=${PSY_MEDIA_VERSION}`,
}

export const fallbackImageFor = (kind: MediaImageKind) => FALLBACK_IMAGE_MAP[kind]

export const resolveImageUrl = (url: string | null | undefined, kind: MediaImageKind) => {
  const normalized = typeof url === 'string' ? url.trim() : ''
  return normalized || fallbackImageFor(kind)
}

const stableStringHash = (value: string) =>
  Array.from(value).reduce((acc, char) => acc * 31 + char.charCodeAt(0), 0)

export const resolvePsyCenterPosterUrl = (cityCode?: string | null, seedKey?: string | null) => {
  const normalizedCityCode = typeof cityCode === 'string' ? cityCode.trim() : ''
  if (normalizedCityCode && PSY_CENTER_CITY_POSTER_MAP[normalizedCityCode]) {
    return `${PSY_CENTER_CITY_POSTER_MAP[normalizedCityCode]}?v=${PSY_MEDIA_VERSION}`
  }

  const normalized = typeof seedKey === 'string' ? seedKey.trim() : ''
  if (!normalized) {
    return fallbackImageFor('psy')
  }

  const variantIndex = Math.abs(stableStringHash(normalized)) % PSY_CENTER_POSTER_VARIANTS.length
  return `${PSY_CENTER_POSTER_VARIANTS[variantIndex]}?v=${PSY_MEDIA_VERSION}`
}
