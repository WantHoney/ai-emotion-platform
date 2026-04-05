import http from './http'

export interface PsyCenter {
  id: number | string
  name: string
  cityCode?: string
  cityName?: string
  district?: string
  address?: string
  phone?: string
  latitude?: number
  longitude?: number
  sourceName?: string
  sourceUrl?: string
  sourceLevel?: string
  recommended?: boolean
  enabled?: boolean
  isActive?: boolean
  seedKey?: string
  dataSource?: string
  createdAt?: string
  updatedAt?: string
}

interface PsyCenterRaw {
  id?: number | string
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
  sourceName?: string
  source_name?: string
  sourceUrl?: string
  source_url?: string
  sourceLevel?: string
  source_level?: string
  recommended?: boolean | number
  isRecommended?: boolean | number
  enabled?: boolean | number
  is_enabled?: boolean | number
  isActive?: boolean | number
  seedKey?: string
  seed_key?: string
  dataSource?: string
  data_source?: string
  createdAt?: string
  created_at?: string
  updatedAt?: string
  updated_at?: string
}

const toNumber = (value: unknown) => {
  if (typeof value === 'number') return value
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isNaN(parsed) ? undefined : parsed
  }
  return undefined
}

const toBool = (value: unknown) => value === true || value === 1 || value === '1'

const normalizeCenter = (row: PsyCenterRaw): PsyCenter => ({
  id: row.id ?? '',
  name: row.name ?? '',
  cityCode: row.cityCode ?? row.city_code,
  cityName: row.cityName ?? row.city_name,
  district: row.district,
  address: row.address,
  phone: row.phone,
  latitude: toNumber(row.latitude),
  longitude: toNumber(row.longitude),
  sourceName: row.sourceName ?? row.source_name,
  sourceUrl: row.sourceUrl ?? row.source_url,
  sourceLevel: row.sourceLevel ?? row.source_level,
  recommended: toBool(row.recommended ?? row.isRecommended),
  enabled: toBool(row.enabled ?? row.is_enabled),
  isActive: row.isActive == null ? undefined : toBool(row.isActive),
  seedKey: row.seedKey ?? row.seed_key,
  dataSource: row.dataSource ?? row.data_source,
  createdAt: row.createdAt ?? row.created_at,
  updatedAt: row.updatedAt ?? row.updated_at,
})

const normalizeList = (rows: PsyCenterRaw[]) => rows.map(normalizeCenter)

export const getPsyCentersByCity = async (cityCode: string) => {
  const response = await http.get<PsyCenterRaw[]>('/api/psy-centers', { params: { cityCode } })
  return normalizeList(response.data)
}

export const getNearbyPsyCenters = async (latitude: number, longitude: number, radiusKm: number) => {
  const response = await http.get<PsyCenterRaw[]>('/api/psy-centers', {
    params: { latitude, longitude, radiusKm },
  })
  return normalizeList(response.data)
}
