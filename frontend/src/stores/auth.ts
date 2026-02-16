import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { loginAdmin, loginUser, logout, registerUser, type AuthRole, type AuthUser } from '@/api/auth'

const TOKEN_KEY = 'emotion-access-token'
const REFRESH_TOKEN_KEY = 'emotion-refresh-token'
const USER_KEY = 'emotion-user-profile'

const readUserCache = (): AuthUser | null => {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_TOKEN_KEY) ?? '')
  const currentUser = ref<AuthUser | null>(readUserCache())

  const isAuthenticated = computed(() => Boolean(token.value))
  const userRole = computed<AuthRole | null>(() => currentUser.value?.role ?? null)

  const setSession = (session: {
    accessToken: string
    refreshToken?: string
    user?: AuthUser
  }) => {
    token.value = session.accessToken
    localStorage.setItem(TOKEN_KEY, session.accessToken)

    if (session.refreshToken) {
      refreshToken.value = session.refreshToken
      localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken)
    }

    if (session.user) {
      currentUser.value = session.user
      localStorage.setItem(USER_KEY, JSON.stringify(session.user))
    }
  }

  const clearToken = async () => {
    if (token.value) {
      await logout().catch(() => undefined)
    }

    token.value = ''
    refreshToken.value = ''
    currentUser.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  const login = async (username: string, password: string, role: AuthRole) => {
    if (!username || !password) {
      throw new Error('用户名和密码不能为空')
    }

    const payload = { username, password }
    const response = role === 'ADMIN' ? await loginAdmin(payload) : await loginUser(payload)
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
    clearToken,
  }
})

export { TOKEN_KEY, REFRESH_TOKEN_KEY }
