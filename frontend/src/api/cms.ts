import http from './http'

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
}

export interface CmsQuote {
  id: number
  content: string
  author?: string
  sortOrder?: number
  recommended: boolean
  enabled: boolean
}

export interface CmsArticle {
  id: number
  title: string
  coverImageUrl?: string
  summary?: string
  contentUrl?: string
  sortOrder?: number
  recommended: boolean
  enabled: boolean
  publishedAt?: string
}

export interface CmsBook {
  id: number
  title: string
  author?: string
  coverImageUrl?: string
  description?: string
  purchaseUrl?: string
  sortOrder?: number
  recommended: boolean
  enabled: boolean
}

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
  recommended: boolean
  enabled: boolean
}

export type CmsContentType = 'banner' | 'quote' | 'article' | 'book'

interface AdminPsyCenterRaw {
  id?: number
  name?: string
  cityCode?: string
  city_code?: string
  cityName?: string
  city_name?: string
  district?: string
  address?: string
  phone?: string
  latitude?: number | string
  longitude?: number | string
  recommended?: boolean | number
  is_recommended?: boolean | number
  enabled?: boolean | number
  is_enabled?: boolean | number
}

const toBool = (value: unknown) => value === true || value === 1 || value === '1'
const toNumber = (value: unknown) => {
  if (value == null || value === '') return undefined
  const num = Number(value)
  return Number.isFinite(num) ? num : undefined
}

const normalizeAdminPsyCenter = (row: AdminPsyCenterRaw): AdminPsyCenter => ({
  id: Number(row.id ?? 0),
  name: String(row.name ?? ''),
  cityCode: String(row.cityCode ?? row.city_code ?? ''),
  cityName: String(row.cityName ?? row.city_name ?? ''),
  district: row.district ?? undefined,
  address: String(row.address ?? ''),
  phone: row.phone ?? undefined,
  latitude: toNumber(row.latitude),
  longitude: toNumber(row.longitude),
  recommended: toBool(row.recommended ?? row.is_recommended),
  enabled: toBool(row.enabled ?? row.is_enabled),
})

export const getBanners = async () => {
  const response = await http.get<CmsBanner[]>('/api/admin/banners')
  return response.data
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
  const response = await http.get<CmsQuote[]>('/api/admin/quotes')
  return response.data
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
  const response = await http.get<CmsArticle[]>('/api/admin/articles')
  return response.data
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
  const response = await http.get<CmsBook[]>('/api/admin/books')
  return response.data
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

export const getAdminPsyCenters = async () => {
  const response = await http.get<AdminPsyCenterRaw[]>('/api/admin/psy-centers')
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
