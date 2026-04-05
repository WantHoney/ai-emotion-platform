import http from './http'
import {
  normalizeContentArticle,
  normalizeContentBook,
  normalizeContentQuote,
  normalizeContentTheme,
  type ContentArticle,
  type ContentBook,
  type DailyPackage,
} from './content'

export interface CmsBanner {
  id: number
  title: string
  imageUrl: string
  linkUrl?: string
  sortOrder?: number
  recommended: boolean
  enabled: boolean
  startsAt?: string
  endsAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface CmsQuote {
  id: number
  content: string
  author?: string
  sortOrder?: number
  recommended: boolean
  enabled: boolean
  seedKey?: string
  dataSource?: string
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CmsArticle extends Omit<ContentArticle, 'highlights'> {
  highlights?: string
  seedKey?: string
  dataSource?: string
  isActive: boolean
}

export interface CmsBook extends Omit<ContentBook, 'highlights'> {
  highlights?: string
  seedKey?: string
  dataSource?: string
  isActive: boolean
}

export interface CmsScheduleItem {
  id?: number
  scheduleId?: number
  contentType: 'ARTICLE' | 'BOOK'
  contentId: number
  slotRole: 'FEATURED' | 'SECONDARY'
  sortOrder?: number
  contentTitle?: string
  contentCategory?: string
}

export interface CmsSchedule {
  id: number
  scheduleDate: string
  themeKey: string
  themeTitle: string
  themeSubtitle?: string
  quoteId: number
  quoteContent?: string
  status: 'ACTIVE' | 'DRAFT'
  items: CmsScheduleItem[]
  createdAt?: string
  updatedAt?: string
}

export interface CmsSchedulePreview extends DailyPackage {}

export interface AdminPsyCenter {
  id: number
  name: string
  cityCode: string
  cityName: string
  district?: string
  address: string
  phone?: string
  latitude?: number
  longitude?: number
  sourceName?: string
  sourceUrl?: string
  sourceLevel?: string
  recommended: boolean
  enabled: boolean
  seedKey?: string
  dataSource?: string
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

export type CmsContentType = 'banner' | 'quote' | 'article' | 'book'

type RawRow = Record<string, unknown>

const toBool = (value: unknown) => value === true || value === 1 || value === '1'

const toNumber = (value: unknown) => {
  if (value == null || value === '') return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

const toString = (value: unknown) => {
  if (value == null) return undefined
  const normalized = String(value).trim()
  return normalized || undefined
}

const toHighlightsText = (value: unknown) => {
  if (Array.isArray(value)) {
    return value
      .map((item) => toString(item))
      .filter((item): item is string => Boolean(item))
      .join('\n')
  }
  return toString(value)
}

const normalizeBanner = (row: RawRow): CmsBanner => ({
  id: Number(row.id ?? 0),
  title: String(row.title ?? ''),
  imageUrl: String(row.imageUrl ?? row.image_url ?? ''),
  linkUrl: toString(row.linkUrl ?? row.link_url),
  sortOrder: toNumber(row.sortOrder ?? row.sort_order),
  recommended: toBool(row.recommended ?? row.is_recommended),
  enabled: toBool(row.enabled ?? row.is_enabled),
  startsAt: toString(row.startsAt ?? row.starts_at),
  endsAt: toString(row.endsAt ?? row.ends_at),
  createdAt: toString(row.createdAt ?? row.created_at),
  updatedAt: toString(row.updatedAt ?? row.updated_at),
})

const normalizeQuote = (row: RawRow): CmsQuote => {
  const quote = normalizeContentQuote(row)!
  return {
    ...quote,
    recommended: quote.recommended ?? false,
    enabled: quote.enabled ?? false,
    seedKey: toString(row.seedKey ?? row.seed_key),
    dataSource: toString(row.dataSource ?? row.data_source),
    isActive: toBool(row.isActive ?? row.is_active),
  }
}

const normalizeArticle = (row: RawRow): CmsArticle => {
  const article = normalizeContentArticle(row)!
  return {
    ...article,
    highlights: toHighlightsText(row.highlights),
    seedKey: toString(row.seedKey ?? row.seed_key),
    dataSource: toString(row.dataSource ?? row.data_source),
    isActive: toBool(row.isActive ?? row.is_active),
  }
}

const normalizeBook = (row: RawRow): CmsBook => {
  const book = normalizeContentBook(row)!
  return {
    ...book,
    highlights: toHighlightsText(row.highlights),
    seedKey: toString(row.seedKey ?? row.seed_key),
    dataSource: toString(row.dataSource ?? row.data_source),
    isActive: toBool(row.isActive ?? row.is_active),
  }
}

const normalizeScheduleItem = (row: RawRow): CmsScheduleItem => ({
  id: toNumber(row.id),
  scheduleId: toNumber(row.scheduleId ?? row.schedule_id),
  contentType: String(row.contentType ?? row.content_type ?? 'ARTICLE').toUpperCase() as 'ARTICLE' | 'BOOK',
  contentId: Number(row.contentId ?? row.content_id ?? 0),
  slotRole: String(row.slotRole ?? row.slot_role ?? 'SECONDARY').toUpperCase() as 'FEATURED' | 'SECONDARY',
  sortOrder: toNumber(row.sortOrder ?? row.sort_order),
  contentTitle: toString(row.contentTitle ?? row.content_title),
  contentCategory: toString(row.contentCategory ?? row.content_category),
})

const normalizeSchedule = (row: RawRow): CmsSchedule => ({
  id: Number(row.id ?? 0),
  scheduleDate: String(row.scheduleDate ?? row.schedule_date ?? ''),
  themeKey: String(row.themeKey ?? row.theme_key ?? ''),
  themeTitle: String(row.themeTitle ?? row.theme_title ?? ''),
  themeSubtitle: toString(row.themeSubtitle ?? row.theme_subtitle),
  quoteId: Number(row.quoteId ?? row.quote_id ?? 0),
  quoteContent: toString(row.quoteContent ?? row.quote_content),
  status: String(row.status ?? 'ACTIVE').toUpperCase() as 'ACTIVE' | 'DRAFT',
  items: Array.isArray(row.items) ? row.items.map((item) => normalizeScheduleItem(item as RawRow)) : [],
  createdAt: toString(row.createdAt ?? row.created_at),
  updatedAt: toString(row.updatedAt ?? row.updated_at),
})

const normalizeSchedulePreview = (row: RawRow): CmsSchedulePreview => ({
  hasSchedule: toBool(row.hasSchedule ?? row.has_schedule),
  theme: normalizeContentTheme((row.theme ?? null) as RawRow),
  quote: normalizeContentQuote((row.quote ?? null) as RawRow),
  featuredArticle: normalizeContentArticle((row.featuredArticle ?? row.featured_article ?? null) as RawRow),
  featuredBook: normalizeContentBook((row.featuredBook ?? row.featured_book ?? null) as RawRow),
  articles: Array.isArray(row.articles)
    ? row.articles
        .map((item) => normalizeContentArticle(item as RawRow))
        .filter((item): item is ContentArticle => Boolean(item))
    : [],
  books: Array.isArray(row.books)
    ? row.books.map((item) => normalizeContentBook(item as RawRow)).filter((item): item is ContentBook => Boolean(item))
    : [],
})

const normalizeAdminPsyCenter = (row: RawRow): AdminPsyCenter => ({
  id: Number(row.id ?? 0),
  name: String(row.name ?? ''),
  cityCode: String(row.cityCode ?? row.city_code ?? ''),
  cityName: String(row.cityName ?? row.city_name ?? ''),
  district: toString(row.district),
  address: String(row.address ?? ''),
  phone: toString(row.phone),
  latitude: toNumber(row.latitude),
  longitude: toNumber(row.longitude),
  sourceName: toString(row.sourceName ?? row.source_name),
  sourceUrl: toString(row.sourceUrl ?? row.source_url),
  sourceLevel: toString(row.sourceLevel ?? row.source_level),
  recommended: toBool(row.recommended ?? row.is_recommended),
  enabled: toBool(row.enabled ?? row.is_enabled),
  seedKey: toString(row.seedKey ?? row.seed_key),
  dataSource: toString(row.dataSource ?? row.data_source),
  isActive: toBool(row.isActive ?? row.is_active),
  createdAt: toString(row.createdAt ?? row.created_at),
  updatedAt: toString(row.updatedAt ?? row.updated_at),
})

export const getBanners = async () => {
  const response = await http.get<RawRow[]>('/api/admin/banners')
  return Array.isArray(response.data) ? response.data.map(normalizeBanner) : []
}

export const createBanner = async (payload: Omit<CmsBanner, 'id'>) => {
  await http.post('/api/admin/banners', payload)
}

export const updateBanner = async (id: number, payload: Omit<CmsBanner, 'id'>) => {
  await http.put(`/api/admin/banners/${id}`, payload)
}

export const deleteBanner = async (id: number) => {
  await http.delete(`/api/admin/banners/${id}`)
}

export const getQuotes = async () => {
  const response = await http.get<RawRow[]>('/api/admin/quotes')
  return Array.isArray(response.data) ? response.data.map(normalizeQuote) : []
}

export const createQuote = async (payload: Omit<CmsQuote, 'id'>) => {
  await http.post('/api/admin/quotes', payload)
}

export const updateQuote = async (id: number, payload: Omit<CmsQuote, 'id'>) => {
  await http.put(`/api/admin/quotes/${id}`, payload)
}

export const deleteQuote = async (id: number) => {
  await http.delete(`/api/admin/quotes/${id}`)
}

export const getArticles = async () => {
  const response = await http.get<RawRow[]>('/api/admin/articles')
  return Array.isArray(response.data) ? response.data.map(normalizeArticle) : []
}

export const createArticle = async (payload: Omit<CmsArticle, 'id'>) => {
  await http.post('/api/admin/articles', payload)
}

export const updateArticle = async (id: number, payload: Omit<CmsArticle, 'id'>) => {
  await http.put(`/api/admin/articles/${id}`, payload)
}

export const deleteArticle = async (id: number) => {
  await http.delete(`/api/admin/articles/${id}`)
}

export const getBooks = async () => {
  const response = await http.get<RawRow[]>('/api/admin/books')
  return Array.isArray(response.data) ? response.data.map(normalizeBook) : []
}

export const createBook = async (payload: Omit<CmsBook, 'id'>) => {
  await http.post('/api/admin/books', payload)
}

export const updateBook = async (id: number, payload: Omit<CmsBook, 'id'>) => {
  await http.put(`/api/admin/books/${id}`, payload)
}

export const deleteBook = async (id: number) => {
  await http.delete(`/api/admin/books/${id}`)
}

export const getSchedules = async (date?: string) => {
  const response = await http.get<RawRow[]>('/api/admin/content/schedules', {
    params: date ? { date } : undefined,
  })
  return Array.isArray(response.data) ? response.data.map(normalizeSchedule) : []
}

export const createSchedule = async (payload: Omit<CmsSchedule, 'id' | 'quoteContent' | 'createdAt' | 'updatedAt'>) => {
  await http.post('/api/admin/content/schedules', payload)
}

export const updateSchedule = async (
  id: number,
  payload: Omit<CmsSchedule, 'id' | 'quoteContent' | 'createdAt' | 'updatedAt'>,
) => {
  await http.put(`/api/admin/content/schedules/${id}`, payload)
}

export const deleteSchedule = async (id: number) => {
  await http.delete(`/api/admin/content/schedules/${id}`)
}

export const previewSchedule = async (date: string) => {
  const response = await http.get<RawRow>('/api/admin/content/schedules/preview', {
    params: { date },
  })
  return normalizeSchedulePreview(response.data ?? {})
}

export const getAdminPsyCenters = async () => {
  const response = await http.get<RawRow[]>('/api/admin/psy-centers')
  return Array.isArray(response.data) ? response.data.map(normalizeAdminPsyCenter) : []
}

export const createAdminPsyCenter = async (payload: Omit<AdminPsyCenter, 'id'>) => {
  await http.post('/api/admin/psy-centers', payload)
}

export const updateAdminPsyCenter = async (id: number, payload: Omit<AdminPsyCenter, 'id'>) => {
  await http.put(`/api/admin/psy-centers/${id}`, payload)
}

export const deleteAdminPsyCenter = async (id: number) => {
  await http.delete(`/api/admin/psy-centers/${id}`)
}

export const exportPsyCentersCsv = async () => {
  const response = await http.get<string>('/api/admin/psy-centers/export', {
    responseType: 'text',
  })
  return response.data
}

export const importPsyCentersCsv = async (csvContent: string) => {
  const response = await http.post<{ imported: number }>('/api/admin/psy-centers/import', csvContent, {
    headers: { 'Content-Type': 'text/plain' },
  })
  return response.data
}
