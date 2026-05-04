import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

export type UserThemeMode = 'dark' | 'light'

const USER_THEME_KEY = 'emotion-user-theme'

const isBrowser = () => typeof window !== 'undefined' && typeof document !== 'undefined'

const readStoredTheme = (): UserThemeMode => {
  if (!isBrowser()) return 'dark'
  const raw = localStorage.getItem(USER_THEME_KEY)
  return raw === 'light' ? 'light' : 'dark'
}

const applyThemeToDom = (theme: UserThemeMode) => {
  if (!isBrowser()) return
  document.documentElement.dataset.userTheme = theme
  document.body.dataset.userTheme = theme
  document.documentElement.style.colorScheme = theme
}

let domGuardAttached = false

const attachDomGuard = (getTheme: () => UserThemeMode) => {
  if (!isBrowser() || domGuardAttached) return

  const ensureTheme = () => {
    const activeTheme = getTheme()
    if (
      document.documentElement.dataset.userTheme !== activeTheme ||
      document.body.dataset.userTheme !== activeTheme ||
      document.documentElement.style.colorScheme !== activeTheme
    ) {
      applyThemeToDom(activeTheme)
    }
  }

  const observer = new MutationObserver(() => {
    ensureTheme()
  })

  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-user-theme', 'style'],
  })

  observer.observe(document.body, {
    attributes: true,
    attributeFilter: ['data-user-theme'],
  })

  domGuardAttached = true
  ensureTheme()
}

export const useUserThemeStore = defineStore('userTheme', () => {
  const mode = ref<UserThemeMode>(readStoredTheme())
  const initialized = ref(false)

  const isLight = computed(() => mode.value === 'light')

  watch(
    mode,
    (nextMode) => {
      if (isBrowser()) {
        localStorage.setItem(USER_THEME_KEY, nextMode)
      }
      applyThemeToDom(nextMode)
    },
    { flush: 'post' },
  )

  const initialize = () => {
    if (initialized.value) {
      attachDomGuard(() => mode.value)
      applyThemeToDom(mode.value)
      return
    }
    mode.value = readStoredTheme()
    attachDomGuard(() => mode.value)
    applyThemeToDom(mode.value)
    initialized.value = true
  }

  const setMode = (nextMode: UserThemeMode) => {
    if (mode.value === nextMode) {
      applyThemeToDom(nextMode)
      return
    }
    mode.value = nextMode
  }

  const toggleMode = () => {
    setMode(mode.value === 'dark' ? 'light' : 'dark')
  }

  const syncDom = () => {
    attachDomGuard(() => mode.value)
    applyThemeToDom(mode.value)
  }

  return {
    mode,
    isLight,
    initialize,
    setMode,
    toggleMode,
    syncDom,
  }
})
