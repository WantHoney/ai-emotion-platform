import http from './http'

export type AuthRole = 'USER' | 'ADMIN'

export interface AuthTokens {
  accessToken: string
  refreshToken?: string
  expiresIn?: number
}

export interface AuthUser {
  id: number
  username: string
  role: AuthRole
  nickname?: string
}

export interface AuthResponse extends AuthTokens {
  user: AuthUser
}

export interface RegisterPayload {
  username: string
  password: string
  nickname?: string
}

export interface LoginPayload {
  username: string
  password: string
}

export const registerUser = async (payload: RegisterPayload) => {
  const response = await http.post('/api/auth/register', payload)
  return response.data as AuthResponse
}

export const loginUser = async (payload: LoginPayload) => {
  const response = await http.post('/api/auth/login', payload)
  return response.data as AuthResponse
}

export const loginAdmin = async (payload: LoginPayload) => {
  const response = await http.post('/api/auth/admin/login', payload)
  return response.data as AuthResponse
}

export const refreshToken = async (refreshTokenValue?: string) => {
  const response = await http.post('/api/auth/refresh', {
    refreshToken: refreshTokenValue,
  })
  return response.data as AuthTokens
}

export const getCurrentUser = async () => {
  const response = await http.get('/api/auth/me')
  return response.data as AuthUser
}

export const logout = async () => {
  await http.post('/api/auth/logout')
}
