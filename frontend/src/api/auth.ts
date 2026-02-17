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

interface AuthUserRaw {
  id?: number
  userId?: number
  username?: string
  role?: AuthRole
  nickname?: string
}

interface AuthResponseRaw {
  accessToken?: string
  refreshToken?: string
  accessExpiresIn?: number
  expiresIn?: number
  user?: AuthUserRaw
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

const normalizeAuthUser = (raw?: AuthUserRaw): AuthUser => ({
  id: Number(raw?.id ?? raw?.userId ?? 0),
  username: String(raw?.username ?? ''),
  role: (raw?.role ?? 'USER') as AuthRole,
  nickname: raw?.nickname,
})

const normalizeAuthResponse = (raw: AuthResponseRaw): AuthResponse => ({
  accessToken: String(raw.accessToken ?? ''),
  refreshToken: raw.refreshToken,
  expiresIn: Number(raw.expiresIn ?? raw.accessExpiresIn ?? 0),
  user: normalizeAuthUser(raw.user),
})

export const registerUser = async (payload: RegisterPayload) => {
  const response = await http.post<AuthResponseRaw>('/api/auth/register', payload)
  return normalizeAuthResponse(response.data)
}

export const loginUser = async (payload: LoginPayload) => {
  const response = await http.post<AuthResponseRaw>('/api/auth/login', payload)
  return normalizeAuthResponse(response.data)
}

export const loginAdmin = async (payload: LoginPayload) => {
  const response = await http.post<AuthResponseRaw>('/api/auth/admin/login', payload)
  return normalizeAuthResponse(response.data)
}

export const refreshToken = async (refreshTokenValue?: string) => {
  const response = await http.post('/api/auth/refresh', {
    refreshToken: refreshTokenValue,
  })
  return response.data as AuthTokens
}

export const getCurrentUser = async () => {
  const response = await http.get<AuthUserRaw>('/api/auth/me')
  return normalizeAuthUser(response.data)
}

export const logout = async () => {
  await http.post('/api/auth/logout')
}
