<script setup lang="ts">
import { computed, ref } from 'vue'
import { Expand, Fold, Moon, Promotion, Setting, Sunny, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import PageContainer from '@/components/layout/PageContainer.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sidebarOpen = ref(true)
const isDark = ref(false)

type NavItem = {
  label: string
  path: string
  requiresAuth: boolean
  role?: 'USER' | 'ADMIN'
}

const publicNavItems: NavItem[] = [
  { label: 'Home', path: '/home', requiresAuth: false },
  { label: 'About', path: '/about', requiresAuth: false },
]

const userNavItems: NavItem[] = [
  { label: 'Upload', path: '/upload', requiresAuth: true, role: 'USER' },
  { label: 'Tasks', path: '/tasks', requiresAuth: true, role: 'USER' },
  { label: 'Reports', path: '/reports', requiresAuth: true, role: 'USER' },
  { label: 'Trends', path: '/trends', requiresAuth: true, role: 'USER' },
  { label: 'Psy Centers', path: '/psy-centers', requiresAuth: true, role: 'USER' },
]

const adminNavItems: NavItem[] = [
  { label: 'System', path: '/system', requiresAuth: true, role: 'ADMIN' },
  { label: 'Models', path: '/admin/models', requiresAuth: true, role: 'ADMIN' },
  { label: 'Rules', path: '/admin/rules', requiresAuth: true, role: 'ADMIN' },
  { label: 'Warnings', path: '/admin/warnings', requiresAuth: true, role: 'ADMIN' },
  { label: 'Content', path: '/admin/content', requiresAuth: true, role: 'ADMIN' },
  { label: 'Analytics', path: '/admin/analytics', requiresAuth: true, role: 'ADMIN' },
]

const visibleUserNavItems = computed(() => (authStore.userRole === 'USER' ? userNavItems : []))
const visibleAdminNavItems = computed(() => (authStore.userRole === 'ADMIN' ? adminNavItems : []))
const visibleNavItems = computed(() => [
  ...publicNavItems,
  ...visibleUserNavItems.value,
  ...visibleAdminNavItems.value,
])

const activeMenu = computed(() => {
  const path = route.path
  const found = visibleNavItems.value.find((item) => path === item.path || path.startsWith(`${item.path}/`))
  if (found) {
    return found.path
  }
  if (path.startsWith('/admin/')) {
    return '/admin/analytics'
  }
  return '/home'
})

const pageTitle = computed(() => String(route.meta.title ?? 'AI Emotion Console'))
const pageDescription = computed(() =>
  String(route.meta.description ?? 'Emotion analysis and warning management workspace'),
)
const breadcrumbs = computed(() => {
  const items = route.meta.breadcrumb
  return Array.isArray(items) ? items.map(String) : ['Workspace']
})

const logout = async () => {
  await authStore.clearToken()
  await router.push('/login')
}

const goLogin = async () => {
  await router.push({ path: '/login', query: { redirect: route.fullPath } })
}

const handleSelect = async (index: string) => {
  const target = visibleNavItems.value.find((item) => item.path === index)
  if (!target) return

  if (target.requiresAuth && !authStore.isAuthenticated) {
    ElMessage.warning('Please login first.')
    await goLogin()
    return
  }
  await router.push(index)
}

const toggleTheme = () => {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark-theme', isDark.value)
}
</script>

<template>
  <el-container class="main-layout">
    <el-aside :width="sidebarOpen ? '248px' : '72px'" class="sider">
      <div class="brand">
        <span v-if="sidebarOpen">Emotion Console</span>
        <span v-else>EC</span>
      </div>
      <el-menu :default-active="activeMenu" :collapse="!sidebarOpen" @select="handleSelect">
        <el-menu-item v-for="item in publicNavItems" :key="item.path" :index="item.path">
          {{ item.label }}
        </el-menu-item>
        <el-menu-item-group v-if="visibleUserNavItems.length" title="User Portal">
          <el-menu-item v-for="item in visibleUserNavItems" :key="item.path" :index="item.path">
            {{ item.label }}
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group v-if="visibleAdminNavItems.length" title="Admin Console">
          <el-menu-item v-for="item in visibleAdminNavItems" :key="item.path" :index="item.path">
            {{ item.label }}
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button circle text @click="sidebarOpen = !sidebarOpen">
            <el-icon><Fold v-if="sidebarOpen" /><Expand v-else /></el-icon>
          </el-button>
          <div>
            <h2 class="top-title">{{ pageTitle }}</h2>
            <el-breadcrumb separator="/">
              <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </div>

        <div class="header-actions">
          <el-button circle text @click="toggleTheme">
            <el-icon><Sunny v-if="isDark" /><Moon v-else /></el-icon>
          </el-button>
          <el-button circle text><el-icon><Setting /></el-icon></el-button>

          <template v-if="authStore.isAuthenticated">
            <el-dropdown>
              <el-button text>
                <el-icon><User /></el-icon>
                <span style="margin-left: 6px">
                  {{ authStore.currentUser?.username }} ({{ authStore.userRole }})
                </span>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="logout">Logout</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else type="primary" @click="goLogin">
            <el-icon><Promotion /></el-icon>
            Login / Register
          </el-button>
        </div>
      </el-header>

      <el-main class="content-wrap">
        <PageContainer :title="pageTitle" :description="pageDescription">
          <router-view />
        </PageContainer>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
}

.sider {
  border-right: 1px solid var(--el-border-color);
  transition: width 0.2s ease;
  background: #fff;
}

.brand {
  font-weight: 700;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid var(--el-border-color);
}

.header {
  border-bottom: 1px solid var(--el-border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  height: 72px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.top-title {
  margin: 0;
  font-size: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.content-wrap {
  background: var(--app-bg);
  padding: 0;
}
</style>
