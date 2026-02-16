<script setup lang="ts">
import { computed } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'

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

const activeGroup = computed(() => {
  const found = props.navItems.find(
    (item) => props.activePath === item.path || props.activePath.startsWith(`${item.path}/`),
  )
  return found?.path ?? '/home'
})
</script>

<template>
  <div class="app-shell">
    <div class="shell-bg"></div>
    <header class="shell-header">
      <button class="brand" @click="emit('navigate', '/home')">
        <span class="brand-dot"></span>
        <span class="brand-text">Emotion Atlas</span>
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
        <template v-if="authenticated">
          <el-dropdown>
            <button class="user-pill">
              <el-icon><UserFilled /></el-icon>
              <span>{{ username }}</span>
              <span class="role-tag">{{ role }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="emit('logout')">Logout</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <button v-else class="auth-button" @click="emit('login')">Login</button>
      </div>
    </header>

    <main class="shell-main">
      <slot />
    </main>

    <footer class="shell-footer">AI Voice Emotion Analysis and Mental Risk Warning Platform</footer>
  </div>
</template>

<style scoped>
.app-shell {
  position: relative;
  min-height: 100vh;
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
  background-image: radial-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 3px 3px;
  opacity: 0.2;
}

.shell-header {
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(12px);
  background: rgba(8, 13, 24, 0.68);
  border-bottom: 1px solid var(--user-border);
  display: grid;
  grid-template-columns: 220px 1fr auto;
  align-items: center;
  gap: 18px;
  padding: 12px 24px;
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
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 8px 14px;
  background: transparent;
  color: var(--user-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-link:hover {
  color: var(--user-text-primary);
  border-color: rgba(201, 174, 130, 0.46);
}

.nav-link.active {
  color: var(--user-text-primary);
  border-color: rgba(201, 174, 130, 0.8);
  background: rgba(201, 174, 130, 0.14);
}

.shell-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.auth-button,
.user-pill {
  border-radius: 999px;
  border: 1px solid var(--user-border);
  background: rgba(18, 28, 47, 0.72);
  color: var(--user-text-primary);
  font-size: 12px;
  padding: 8px 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
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
    padding: 12px 14px;
  }
}
</style>
