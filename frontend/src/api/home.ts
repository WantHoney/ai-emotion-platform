import http from './http'
import {
  normalizeContentArticle,
  normalizeContentBook,
  normalizeContentQuote,
  normalizeContentTheme,
  type ContentArticle,
  type ContentBook,
  type ContentQuote,
  type ContentTheme,
} from './content'

export interface BannerItem {
  id: number
  title: string
  imageUrl: string
  linkUrl?: string
  sortOrder: number
  recommended: boolean
  enabled: boolean
  startsAt?: string
  endsAt?: string
  createdAt?: string
  updatedAt?: string
}

export type TodayQuote = ContentQuote
export type RecommendedArticle = ContentArticle
export type RecommendedBook = ContentBook

export interface SelfHelpEntry {
  key: string
  title: string
  path: string
}

export interface HomePayload {
  todayDate?: string
  todayTheme: ContentTheme | null
  banners: BannerItem[]
  todayQuote: TodayQuote | null
  todayFeaturedArticle: RecommendedArticle | null
  todayFeaturedBook: RecommendedBook | null
  todayArticles: RecommendedArticle[]
  todayBooks: RecommendedBook[]
  recommendedArticles: RecommendedArticle[]
  recommendedBooks: RecommendedBook[]
  selfHelpEntries: SelfHelpEntry[]
}

type BannerRaw = Record<string, unknown>
type ThemeRaw = Record<string, unknown>
type QuoteRaw = Record<string, unknown>
type ArticleRaw = Record<string, unknown>
type BookRaw = Record<string, unknown>
type HomePayloadRaw = Record<string, unknown>

const toBool = (value: unknown) => value === true || value === 1 || value === '1'

const toNumber = (value: unknown, fallback = 0) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

const toString = (value: unknown) => {
  if (value == null) return undefined
  const normalized = String(value).trim()
  return normalized || undefined
}

const normalizeBanner = (row: BannerRaw): BannerItem => ({
  id: toNumber(row.id),
  title: String(row.title ?? ''),
  imageUrl: String(row.imageUrl ?? row.image_url ?? ''),
  linkUrl: toString(row.linkUrl ?? row.link_url),
  sortOrder: toNumber(row.sortOrder ?? row.sort_order, 100),
  recommended: toBool(row.recommended ?? row.is_recommended),
  enabled: toBool(row.enabled ?? row.is_enabled),
  startsAt: toString(row.startsAt ?? row.starts_at),
  endsAt: toString(row.endsAt ?? row.ends_at),
  createdAt: toString(row.createdAt ?? row.created_at),
  updatedAt: toString(row.updatedAt ?? row.updated_at),
})

const normalizeArticles = (value: unknown) =>
  Array.isArray(value)
    ? value
        .map((item) => normalizeContentArticle(item as ArticleRaw))
        .filter((item): item is RecommendedArticle => Boolean(item))
    : []

const normalizeBooks = (value: unknown) =>
  Array.isArray(value)
    ? value.map((item) => normalizeContentBook(item as BookRaw)).filter((item): item is RecommendedBook => Boolean(item))
    : []

const normalizeHomePayload = (payload: HomePayloadRaw): HomePayload => {
  const todayArticles = normalizeArticles(payload.todayArticles ?? payload.today_articles)
  const todayBooks = normalizeBooks(payload.todayBooks ?? payload.today_books)
  const recommendedArticles = normalizeArticles(payload.recommendedArticles ?? payload.recommended_articles)
  const recommendedBooks = normalizeBooks(payload.recommendedBooks ?? payload.recommended_books)
  const selfHelpEntriesRaw = payload.selfHelpEntries ?? payload.self_help_entries

  return {
    todayDate: toString(payload.todayDate ?? payload.today_date),
    todayTheme: normalizeContentTheme((payload.todayTheme ?? payload.today_theme ?? null) as ThemeRaw),
    banners: Array.isArray(payload.banners) ? payload.banners.map((item) => normalizeBanner(item as BannerRaw)) : [],
    todayQuote: normalizeContentQuote((payload.todayQuote ?? payload.today_quote ?? null) as QuoteRaw),
    todayFeaturedArticle: normalizeContentArticle(
      (payload.todayFeaturedArticle ?? payload.today_featured_article ?? null) as ArticleRaw,
    ),
    todayFeaturedBook: normalizeContentBook(
      (payload.todayFeaturedBook ?? payload.today_featured_book ?? null) as BookRaw,
    ),
    todayArticles,
    todayBooks,
    recommendedArticles: recommendedArticles.length ? recommendedArticles : todayArticles,
    recommendedBooks: recommendedBooks.length ? recommendedBooks : todayBooks,
    selfHelpEntries: Array.isArray(selfHelpEntriesRaw)
      ? selfHelpEntriesRaw.map((item) => item as SelfHelpEntry)
      : [],
  }
}

export const getHomeContent = async () => {
  const response = await http.get<HomePayloadRaw>('/api/home')
  return normalizeHomePayload(response.data ?? {})
}
