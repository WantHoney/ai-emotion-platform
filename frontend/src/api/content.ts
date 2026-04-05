import http from './http'

export type ContentCategory =
  | 'stress'
  | 'sleep'
  | 'anxiety'
  | 'emotion'
  | 'help-seeking'
  | 'communication'

export interface ContentTheme {
  scheduleDate?: string
  themeKey?: string
  themeTitle?: string
  themeSubtitle?: string
  status?: string
}

export interface ContentQuote {
  id: number
  content: string
  author?: string
  sortOrder?: number
  recommended?: boolean
  enabled?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ContentArticle {
  id: number
  title: string
  coverImageUrl?: string
  summary?: string
  recommendReason?: string
  fitFor?: string
  highlights: string[]
  readingMinutes?: number
  category?: string
  sourceName?: string
  sourceUrl?: string
  contentUrl?: string
  isExternal?: boolean
  difficultyTag?: string
  sortOrder?: number
  recommended?: boolean
  enabled?: boolean
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface ContentBook {
  id: number
  title: string
  author?: string
  coverImageUrl?: string
  description?: string
  category?: string
  recommendReason?: string
  fitFor?: string
  highlights: string[]
  purchaseUrl?: string
  sortOrder?: number
  recommended?: boolean
  enabled?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface DailyPackage {
  hasSchedule: boolean
  theme: ContentTheme | null
  quote: ContentQuote | null
  featuredArticle: ContentArticle | null
  featuredBook: ContentBook | null
  articles: ContentArticle[]
  books: ContentBook[]
}

export interface ContentCategoryHighlights {
  selectedCategory: string
  articles: ContentArticle[]
  books: ContentBook[]
}

export interface ContentHistoryItem {
  contentType: 'ARTICLE' | 'BOOK'
  contentId: number
  title: string
  coverImageUrl?: string
  description?: string
  category?: string
  subtitle?: string
  lastViewedAt?: string
  lastOutboundAt?: string
  viewCount?: number
}

export interface ContentHubPayload {
  selectedDate?: string
  todayDate?: string
  dailyPackage: DailyPackage
  categoryHighlights: ContentCategoryHighlights
  archiveDates: string[]
  recentHistory: ContentHistoryItem[]
}

export interface ArticleDetailPayload {
  article: ContentArticle
  relatedArticles: ContentArticle[]
  relatedBooks: ContentBook[]
}

export interface BookDetailPayload {
  book: ContentBook
  relatedBooks: ContentBook[]
  relatedArticles: ContentArticle[]
}

type ThemeRaw = Record<string, unknown>
type QuoteRaw = Record<string, unknown>
type ArticleRaw = Record<string, unknown>
type BookRaw = Record<string, unknown>
type DailyPackageRaw = Record<string, unknown>
type ContentHubPayloadRaw = Record<string, unknown>
type HistoryRaw = Record<string, unknown>

const toBool = (value: unknown) => value === true || value === 1 || value === '1'

const toNumber = (value: unknown, fallback?: number) => {
  if (value == null || value === '') return fallback
  const num = Number(value)
  return Number.isFinite(num) ? num : fallback
}

const toString = (value: unknown) => {
  if (value == null) return undefined
  const normalized = String(value).trim()
  return normalized || undefined
}

const toHighlights = (value: unknown) => {
  if (Array.isArray(value)) {
    return value
      .map((item) => toString(item))
      .filter((item): item is string => Boolean(item))
  }
  const text = toString(value)
  if (!text) return []
  return text
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export const normalizeContentTheme = (row: ThemeRaw | null | undefined): ContentTheme | null => {
  if (!row) return null
  return {
    scheduleDate: toString(row.scheduleDate ?? row.schedule_date),
    themeKey: toString(row.themeKey ?? row.theme_key),
    themeTitle: toString(row.themeTitle ?? row.theme_title),
    themeSubtitle: toString(row.themeSubtitle ?? row.theme_subtitle),
    status: toString(row.status),
  }
}

export const normalizeContentQuote = (row: QuoteRaw | null | undefined): ContentQuote | null => {
  if (!row) return null
  return {
    id: toNumber(row.id, 0) ?? 0,
    content: String(row.content ?? ''),
    author: toString(row.author),
    sortOrder: toNumber(row.sortOrder ?? row.sort_order),
    recommended: toBool(row.recommended ?? row.is_recommended),
    enabled: toBool(row.enabled ?? row.is_enabled),
    createdAt: toString(row.createdAt ?? row.created_at),
    updatedAt: toString(row.updatedAt ?? row.updated_at),
  }
}

export const normalizeContentArticle = (row: ArticleRaw | null | undefined): ContentArticle | null => {
  if (!row) return null
  return {
    id: toNumber(row.id, 0) ?? 0,
    title: String(row.title ?? ''),
    coverImageUrl: toString(row.coverImageUrl ?? row.cover_image_url),
    summary: toString(row.summary),
    recommendReason: toString(row.recommendReason ?? row.recommend_reason),
    fitFor: toString(row.fitFor ?? row.fit_for),
    highlights: toHighlights(row.highlights),
    readingMinutes: toNumber(row.readingMinutes ?? row.reading_minutes),
    category: toString(row.category),
    sourceName: toString(row.sourceName ?? row.source_name),
    sourceUrl: toString(row.sourceUrl ?? row.source_url ?? row.contentUrl ?? row.content_url),
    contentUrl: toString(row.contentUrl ?? row.content_url ?? row.sourceUrl ?? row.source_url),
    isExternal: toBool(row.isExternal ?? row.is_external),
    difficultyTag: toString(row.difficultyTag ?? row.difficulty_tag),
    sortOrder: toNumber(row.sortOrder ?? row.sort_order),
    recommended: toBool(row.recommended ?? row.is_recommended),
    enabled: toBool(row.enabled ?? row.is_enabled),
    publishedAt: toString(row.publishedAt ?? row.published_at),
    createdAt: toString(row.createdAt ?? row.created_at),
    updatedAt: toString(row.updatedAt ?? row.updated_at),
  }
}

export const normalizeContentBook = (row: BookRaw | null | undefined): ContentBook | null => {
  if (!row) return null
  return {
    id: toNumber(row.id, 0) ?? 0,
    title: String(row.title ?? ''),
    author: toString(row.author),
    coverImageUrl: toString(row.coverImageUrl ?? row.cover_image_url),
    description: toString(row.description),
    category: toString(row.category),
    recommendReason: toString(row.recommendReason ?? row.recommend_reason),
    fitFor: toString(row.fitFor ?? row.fit_for),
    highlights: toHighlights(row.highlights),
    purchaseUrl: toString(row.purchaseUrl ?? row.purchase_url),
    sortOrder: toNumber(row.sortOrder ?? row.sort_order),
    recommended: toBool(row.recommended ?? row.is_recommended),
    enabled: toBool(row.enabled ?? row.is_enabled),
    createdAt: toString(row.createdAt ?? row.created_at),
    updatedAt: toString(row.updatedAt ?? row.updated_at),
  }
}

const normalizeDailyPackage = (row: DailyPackageRaw | null | undefined): DailyPackage => ({
  hasSchedule: toBool(row?.hasSchedule ?? row?.has_schedule),
  theme: normalizeContentTheme((row?.theme as ThemeRaw | undefined) ?? null),
  quote: normalizeContentQuote((row?.quote as QuoteRaw | undefined) ?? null),
  featuredArticle: normalizeContentArticle((row?.featuredArticle as ArticleRaw | undefined) ?? null),
  featuredBook: normalizeContentBook((row?.featuredBook as BookRaw | undefined) ?? null),
  articles: Array.isArray(row?.articles)
    ? row.articles
        .map((item) => normalizeContentArticle(item as ArticleRaw))
        .filter((item): item is ContentArticle => Boolean(item))
    : [],
  books: Array.isArray(row?.books)
    ? row.books.map((item) => normalizeContentBook(item as BookRaw)).filter((item): item is ContentBook => Boolean(item))
    : [],
})

const normalizeHistory = (row: HistoryRaw): ContentHistoryItem => ({
  contentType: String(row.contentType ?? row.content_type ?? 'ARTICLE').toUpperCase() as 'ARTICLE' | 'BOOK',
  contentId: toNumber(row.contentId ?? row.content_id, 0) ?? 0,
  title: String(row.title ?? ''),
  coverImageUrl: toString(row.coverImageUrl ?? row.cover_image_url),
  description: toString(row.description),
  category: toString(row.category),
  subtitle: toString(row.subtitle),
  lastViewedAt: toString(row.lastViewedAt ?? row.last_viewed_at),
  lastOutboundAt: toString(row.lastOutboundAt ?? row.last_outbound_at),
  viewCount: toNumber(row.viewCount ?? row.view_count),
})

export const getContentHub = async (params?: { date?: string; category?: string }) => {
  const response = await http.get<ContentHubPayloadRaw>('/api/content-hub', {
    params,
  })
  const payload = response.data ?? {}
  const categoryHighlightsRaw = (payload.categoryHighlights ?? payload.category_highlights ?? {}) as Record<
    string,
    unknown
  >
  const archiveDatesRaw = payload.archiveDates ?? payload.archive_dates
  const recentHistoryRaw = payload.recentHistory ?? payload.recent_history
  const categoryArticlesRaw = categoryHighlightsRaw.articles
  const categoryBooksRaw = categoryHighlightsRaw.books
  return {
    selectedDate: toString(payload.selectedDate ?? payload.selected_date),
    todayDate: toString(payload.todayDate ?? payload.today_date),
    dailyPackage: normalizeDailyPackage((payload.dailyPackage ?? payload.daily_package) as DailyPackageRaw),
    categoryHighlights: {
      selectedCategory: String(
        categoryHighlightsRaw.selectedCategory ?? categoryHighlightsRaw.selected_category ?? '',
      ),
      articles: Array.isArray(categoryArticlesRaw)
        ? categoryArticlesRaw
            .map((item) => normalizeContentArticle(item as ArticleRaw))
            .filter((item): item is ContentArticle => Boolean(item))
        : [],
      books: Array.isArray(categoryBooksRaw)
        ? categoryBooksRaw
            .map((item) => normalizeContentBook(item as BookRaw))
            .filter((item): item is ContentBook => Boolean(item))
        : [],
    },
    archiveDates: Array.isArray(archiveDatesRaw)
      ? archiveDatesRaw.map((item) => String(item))
      : [],
    recentHistory: Array.isArray(recentHistoryRaw)
      ? recentHistoryRaw.map((item) => normalizeHistory(item as HistoryRaw))
      : [],
  } satisfies ContentHubPayload
}

export const getContentArticleDetail = async (id: number | string) => {
  const response = await http.get<Record<string, unknown>>(`/api/content/articles/${id}`)
  const payload = response.data ?? {}
  const relatedArticlesRaw = payload.relatedArticles ?? payload.related_articles
  const relatedBooksRaw = payload.relatedBooks ?? payload.related_books
  return {
    article: normalizeContentArticle((payload.article ?? null) as ArticleRaw) as ContentArticle,
    relatedArticles: Array.isArray(relatedArticlesRaw)
      ? relatedArticlesRaw
          .map((item) => normalizeContentArticle(item as ArticleRaw))
          .filter((item): item is ContentArticle => Boolean(item))
      : [],
    relatedBooks: Array.isArray(relatedBooksRaw)
      ? relatedBooksRaw
          .map((item) => normalizeContentBook(item as BookRaw))
          .filter((item): item is ContentBook => Boolean(item))
      : [],
  } satisfies ArticleDetailPayload
}

export const getContentBookDetail = async (id: number | string) => {
  const response = await http.get<Record<string, unknown>>(`/api/content/books/${id}`)
  const payload = response.data ?? {}
  const relatedBooksRaw = payload.relatedBooks ?? payload.related_books
  const relatedArticlesRaw = payload.relatedArticles ?? payload.related_articles
  return {
    book: normalizeContentBook((payload.book ?? null) as BookRaw) as ContentBook,
    relatedBooks: Array.isArray(relatedBooksRaw)
      ? relatedBooksRaw
          .map((item) => normalizeContentBook(item as BookRaw))
          .filter((item): item is ContentBook => Boolean(item))
      : [],
    relatedArticles: Array.isArray(relatedArticlesRaw)
      ? relatedArticlesRaw
          .map((item) => normalizeContentArticle(item as ArticleRaw))
          .filter((item): item is ContentArticle => Boolean(item))
      : [],
  } satisfies BookDetailPayload
}

export const postContentHistory = async (
  action: 'VIEW' | 'OUTBOUND',
  contentType: 'ARTICLE' | 'BOOK',
  contentId: number | string,
) => {
  await http.post('/api/content/history', {
    action,
    contentType,
    contentId: Number(contentId),
  })
}
