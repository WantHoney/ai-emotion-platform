export type MediaImageKind = 'article' | 'book' | 'psy'

const PSY_MEDIA_VERSION = '20260404-posters-v5-anime'

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
