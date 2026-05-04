<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Moon, Sunny, UserFilled } from '@element-plus/icons-vue'

import { useUserThemeStore } from '@/stores/userTheme'

export type AppNavItem = {
  label: string
  path: string
}

const props = defineProps<{
  navItems: AppNavItem[]
  activePath: string
  authenticated: boolean
  username?: string
  role?: string | null
}>()

const emit = defineEmits<{
  navigate: [path: string]
  login: []
  logout: []
}>()

const userThemeStore = useUserThemeStore()
const shellHeaderRef = ref<HTMLElement | null>(null)
const shellHeaderHeight = ref(74)
let shellHeaderResizeObserver: ResizeObserver | null = null

const activeGroup = computed(() => {
  const found = props.navItems.find(
    (item) => props.activePath === item.path || props.activePath.startsWith(`${item.path}/`),
  )
  return found?.path ?? '/app/home'
})

const roleText = computed(() => {
  if (props.role === 'ADMIN') return '管理员'
  if (props.role === 'USER') return '用户'
  return props.role ?? ''
})

const themeToggleLabel = computed(() => (userThemeStore.isLight ? '夜间' : '明亮'))
const themeToggleTitle = computed(() =>
  userThemeStore.isLight ? '切换到夜间风格' : '切换到明亮风格',
)
const shellStyle = computed(() => ({
  '--shell-header-height': `${shellHeaderHeight.value}px`,
}))

const syncShellHeaderHeight = () => {
  shellHeaderHeight.value = shellHeaderRef.value?.offsetHeight || 74
}

onMounted(async () => {
  await nextTick()
  syncShellHeaderHeight()

  if (shellHeaderRef.value) {
    shellHeaderResizeObserver = new ResizeObserver(() => {
      syncShellHeaderHeight()
    })
    shellHeaderResizeObserver.observe(shellHeaderRef.value)
  }

  window.addEventListener('resize', syncShellHeaderHeight, { passive: true })
})

onBeforeUnmount(() => {
  shellHeaderResizeObserver?.disconnect()
  window.removeEventListener('resize', syncShellHeaderHeight)
})
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--light': userThemeStore.isLight }" :style="shellStyle">
    <div class="shell-bg"></div>
    <header ref="shellHeaderRef" class="shell-header">
      <button class="brand" @click="emit('navigate', '/app/home')">
        <span class="brand-dot"></span>
        <span class="brand-text">情绪档案库</span>
      </button>

      <nav class="shell-nav">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="nav-link"
          :class="{ active: activeGroup === item.path }"
          @click="emit('navigate', item.path)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="shell-actions">
        <button class="theme-toggle" :title="themeToggleTitle" @click="userThemeStore.toggleMode()">
          <el-icon>
            <Sunny v-if="!userThemeStore.isLight" />
            <Moon v-else />
          </el-icon>
          <span>{{ themeToggleLabel }}</span>
        </button>

        <template v-if="authenticated">
          <el-dropdown>
            <button class="user-pill">
              <el-icon><UserFilled /></el-icon>
              <span>{{ username }}</span>
              <span class="role-tag">{{ roleText }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="emit('logout')">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <button v-else class="auth-button" @click="emit('login')">登录</button>
      </div>
    </header>

    <main class="shell-main">
      <slot />
    </main>

    <footer class="shell-footer">语音情绪分析与心理状态预警平台</footer>
  </div>
</template>

<style scoped>
.app-shell {
  position: relative;
  min-height: 100vh;
  padding-top: var(--shell-header-height);
  color: var(--user-text-primary);
}

.shell-bg {
  position: fixed;
  inset: 0;
  z-index: -1;
  background:
    radial-gradient(circle at 18% 20%, rgba(195, 162, 110, 0.24), transparent 38%),
    radial-gradient(circle at 80% 16%, rgba(66, 159, 178, 0.18), transparent 36%),
    radial-gradient(circle at 50% 96%, rgba(87, 110, 162, 0.16), transparent 40%),
    var(--user-bg);
}

.shell-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--user-shell-noise-dot) 1px, transparent 1px);
  background-size: 3px 3px;
  opacity: var(--user-shell-noise-opacity);
}

.shell-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 30;
  width: 100%;
  backdrop-filter: blur(12px);
  background: var(--user-header-bg);
  border-bottom: 1px solid var(--user-border);
  display: grid;
  grid-template-columns: 220px 1fr auto;
  align-items: center;
  gap: 18px;
  padding: 6px 24px 8px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  color: var(--user-text-primary);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.06em;
  cursor: pointer;
}

.brand-dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: linear-gradient(120deg, #cbad7f, #55b2bd);
  box-shadow: 0 0 16px rgba(104, 209, 224, 0.8);
}

.brand-text {
  font-family: var(--font-display);
}

.shell-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 8px 14px;
  min-height: 36px;
  background: transparent;
  color: var(--user-text-secondary);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-link:hover {
  color: var(--user-text-primary);
  border-color: var(--user-border-accent-soft);
  background: var(--user-nav-hover-bg);
}

.nav-link.active {
  color: var(--user-text-primary);
  border-color: var(--user-border-accent-strong);
  background: var(--user-nav-active-bg);
}

.shell-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.theme-toggle,
.auth-button,
.user-pill {
  border-radius: 999px;
  border: 1px solid var(--user-border);
  background: var(--user-pill-bg);
  color: var(--user-text-primary);
  font-size: 12px;
  padding: 8px 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  line-height: 1;
  gap: 6px;
  cursor: pointer;
}

.theme-toggle:hover,
.auth-button:hover,
.user-pill:hover {
  border-color: var(--user-border-strong);
}

.app-shell--light .shell-header {
  background: rgba(251, 253, 255, 0.96);
  box-shadow: 0 8px 22px rgba(126, 149, 183, 0.08);
}

.role-tag {
  color: var(--user-text-secondary);
}

.shell-main {
  position: relative;
}

.shell-footer {
  margin-top: 56px;
  border-top: 1px solid var(--user-border);
  color: var(--user-text-secondary);
  text-align: center;
  font-size: 12px;
  letter-spacing: 0.08em;
  padding: 18px 12px 26px;
}

@media (max-width: 900px) {
  .shell-header {
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 6px 14px 10px;
  }
}
</style>
