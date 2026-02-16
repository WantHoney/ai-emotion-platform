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
  isRecommended?: boolean
  enabled?: boolean
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
  recommended?: boolean
  isRecommended?: boolean
  enabled?: boolean
  is_enabled?: boolean
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

const normalizeCenter = (row: PsyCenterRaw): PsyCenter => {
  return {
    id: row.id ?? '',
    name: row.name ?? '',
    cityCode: row.cityCode ?? row.city_code,
    cityName: row.cityName ?? row.city_name,
    district: row.district,
    address: row.address,
    phone: row.phone,
    latitude: toNumber(row.latitude),
    longitude: toNumber(row.longitude),
    isRecommended: row.isRecommended ?? row.recommended,
    enabled: row.enabled ?? row.is_enabled,
    createdAt: row.createdAt ?? row.created_at,
    updatedAt: row.updatedAt ?? row.updated_at,
  }
}

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
