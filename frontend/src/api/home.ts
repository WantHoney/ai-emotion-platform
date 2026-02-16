import http from './http'

export interface BannerItem {
  id: number
  title: string
  imageUrl: string
  linkUrl: string
  sortOrder: number
  recommended: boolean
  enabled: boolean
  startsAt?: string
  endsAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface TodayQuote {
  id: number
  content: string
  author?: string
  sortOrder: number
  recommended: boolean
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface RecommendedArticle {
  id: number
  title: string
  coverImageUrl?: string
  summary?: string
  contentUrl?: string
  sortOrder: number
  recommended: boolean
  enabled: boolean
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface RecommendedBook {
  id: number
  title: string
  author?: string
  coverImageUrl?: string
  description?: string
  purchaseUrl?: string
  sortOrder: number
  recommended: boolean
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface SelfHelpEntry {
  key: string
  title: string
  path: string
}

export interface HomePayload {
  banners: BannerItem[]
  todayQuote: TodayQuote | null
  recommendedArticles: RecommendedArticle[]
  recommendedBooks: RecommendedBook[]
  selfHelpEntries: SelfHelpEntry[]
}

export const getHomeContent = async () => {
  const response = await http.get<HomePayload>('/api/home')
  return response.data
}
