import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { loginAdmin, type AuthUser } from '@/api/auth'

export const ADMIN_TOKEN_KEY = 'emotion-admin-access-token'
export const ADMIN_REFRESH_TOKEN_KEY = 'emotion-admin-refresh-token'
export const ADMIN_PROFILE_KEY = 'emotion-admin-profile'

const readAdminCache = (): AuthUser | null => {
  const raw = localStorage.getItem(ADMIN_PROFILE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    localStorage.removeItem(ADMIN_PROFILE_KEY)
    return null
  }
}

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const token = ref<string>(localStorage.getItem(ADMIN_TOKEN_KEY) ?? '')
  const refreshToken = ref<string>(localStorage.getItem(ADMIN_REFRESH_TOKEN_KEY) ?? '')
  const currentUser = ref<AuthUser | null>(readAdminCache())

  const isAuthenticated = computed(() => Boolean(token.value))

  const setSession = (session: {
    accessToken: string
    refreshToken?: string
    user?: AuthUser
  }) => {
    token.value = session.accessToken
    localStorage.setItem(ADMIN_TOKEN_KEY, session.accessToken)
    if (session.refreshToken) {
      refreshToken.value = session.refreshToken
      localStorage.setItem(ADMIN_REFRESH_TOKEN_KEY, session.refreshToken)
    }
    if (session.user) {
      currentUser.value = session.user
      localStorage.setItem(ADMIN_PROFILE_KEY, JSON.stringify(session.user))
    }
  }

  const clearSession = async () => {
    token.value = ''
    refreshToken.value = ''
    currentUser.value = null
    localStorage.removeItem(ADMIN_TOKEN_KEY)
    localStorage.removeItem(ADMIN_REFRESH_TOKEN_KEY)
    localStorage.removeItem(ADMIN_PROFILE_KEY)
  }

  const login = async (username: string, password: string) => {
    if (!username || !password) {
      throw new Error('用户名和密码不能为空')
    }
    const response = await loginAdmin({ username, password })
    if (response.user.role !== 'ADMIN') {
      throw new Error('当前账号不是管理员角色')
    }
    setSession(response)
  }

  return {
    token,
    refreshToken,
    currentUser,
    isAuthenticated,
    login,
    setSession,
    clearSession,
  }
})
