import http from './http'

export interface PsyCenter {
  id: number | string
  name: string
  cityCode?: string
  address?: string
  phone?: string
  latitude?: number
  longitude?: number
  isRecommended?: boolean
  enabled?: boolean
  createdAt?: string
  updatedAt?: string
}

export const getPsyCentersByCity = async (cityCode: string) => {
  const response = await http.get<PsyCenter[]>('/api/psy-centers', { params: { cityCode } })
  return response.data
}

export const getNearbyPsyCenters = async (latitude: number, longitude: number, radiusKm: number) => {
  const response = await http.get<PsyCenter[]>('/api/psy-centers', {
    params: { latitude, longitude, radiusKm },
  })
  return response.data
}
