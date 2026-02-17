import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { loginUser, registerUser, type AuthUser } from '@/api/auth'

export const USER_TOKEN_KEY = 'emotion-user-access-token'
export const USER_REFRESH_TOKEN_KEY = 'emotion-user-refresh-token'
export const USER_PROFILE_KEY = 'emotion-user-profile'

const readUserCache = (): AuthUser | null => {
  const raw = localStorage.getItem(USER_PROFILE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    localStorage.removeItem(USER_PROFILE_KEY)
    return null
  }
}

export const useUserAuthStore = defineStore('userAuth', () => {
  const token = ref<string>(localStorage.getItem(USER_TOKEN_KEY) ?? '')
  const refreshToken = ref<string>(localStorage.getItem(USER_REFRESH_TOKEN_KEY) ?? '')
  const currentUser = ref<AuthUser | null>(readUserCache())

  const isAuthenticated = computed(() => Boolean(token.value))
  const userRole = computed(() => currentUser.value?.role ?? null)

  const setSession = (session: {
    accessToken: string
    refreshToken?: string
    user?: AuthUser
  }) => {
    token.value = session.accessToken
    localStorage.setItem(USER_TOKEN_KEY, session.accessToken)
    if (session.refreshToken) {
      refreshToken.value = session.refreshToken
      localStorage.setItem(USER_REFRESH_TOKEN_KEY, session.refreshToken)
    }
    if (session.user) {
      currentUser.value = session.user
      localStorage.setItem(USER_PROFILE_KEY, JSON.stringify(session.user))
    }
  }

  const clearSession = async () => {
    token.value = ''
    refreshToken.value = ''
    currentUser.value = null
    localStorage.removeItem(USER_TOKEN_KEY)
    localStorage.removeItem(USER_REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_PROFILE_KEY)
  }

  const login = async (username: string, password: string) => {
    if (!username || !password) {
      throw new Error('用户名和密码不能为空')
    }
    const response = await loginUser({ username, password })
    if (response.user.role !== 'USER') {
      throw new Error('当前账号不是用户角色')
    }
    setSession(response)
  }

  const register = async (username: string, password: string, nickname?: string) => {
    if (!username || !password) {
      throw new Error('用户名和密码不能为空')
    }
    const response = await registerUser({ username, password, nickname })
    setSession(response)
  }

  return {
    token,
    refreshToken,
    currentUser,
    isAuthenticated,
    userRole,
    login,
    register,
    setSession,
    clearSession,
    clearToken: clearSession,
  }
})
