<script setup lang="ts">
import { computed, ref } from 'vue'
import { Expand, Fold, Promotion, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import AppShell, { type AppNavItem } from '@/components/ui/AppShell.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sidebarOpen = ref(true)

type NavItem = AppNavItem & {
  requiresAuth: boolean
  role?: 'USER' | 'ADMIN'
}

const publicNavItems: NavItem[] = [
  { label: 'Home', path: '/home', requiresAuth: false },
  { label: 'Content', path: '/content', requiresAuth: false },
  { label: 'About', path: '/about', requiresAuth: false },
]

const userNavItems: NavItem[] = [
  { label: 'Upload', path: '/upload', requiresAuth: true, role: 'USER' },
  { label: 'Reports', path: '/reports', requiresAuth: true, role: 'USER' },
  { label: 'Trends', path: '/trends', requiresAuth: true, role: 'USER' },
  { label: 'Psy Centers', path: '/psy-centers', requiresAuth: true, role: 'USER' },
  { label: 'Profile', path: '/profile', requiresAuth: true, role: 'USER' },
]

const adminNavItems: NavItem[] = [
  { label: 'Analytics', path: '/admin/analytics', requiresAuth: true, role: 'ADMIN' },
  { label: 'System', path: '/system', requiresAuth: true, role: 'ADMIN' },
  { label: 'Models', path: '/admin/models', requiresAuth: true, role: 'ADMIN' },
  { label: 'Rules', path: '/admin/rules', requiresAuth: true, role: 'ADMIN' },
  { label: 'Warnings', path: '/admin/warnings', requiresAuth: true, role: 'ADMIN' },
  { label: 'Content', path: '/admin/content', requiresAuth: true, role: 'ADMIN' },
]

const adminContextPaths = new Set(['/system'])

const isAdminContext = computed(() => {
  return (
    route.path.startsWith('/admin') ||
    adminContextPaths.has(route.path) ||
    route.meta.requiresRole === 'ADMIN'
  )
})

const userTopNavItems = computed(() => {
  const items = [...publicNavItems]
  if (authStore.userRole === 'USER') {
    items.push(...userNavItems)
  }
  return items.map((item) => ({ label: item.label, path: item.path }))
})

const visibleAdminNavItems = computed(() => {
  if (authStore.userRole !== 'ADMIN') return []
  return adminNavItems
})

const activeUserPath = computed(() => {
  const found = userTopNavItems.value.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  return found?.path ?? '/home'
})

const activeAdminPath = computed(() => {
  const found = visibleAdminNavItems.value.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  return found?.path ?? '/admin/analytics'
})

const pageTitle = computed(() => String(route.meta.title ?? 'AI Emotion Console'))
const pageDescription = computed(() =>
  String(route.meta.description ?? 'Emotion analysis and warning management workspace'),
)
const breadcrumbs = computed(() => {
  const items = route.meta.breadcrumb
  return Array.isArray(items) ? items.map(String) : ['Workspace']
})

const hidePageHeader = computed(() => Boolean(route.meta.hidePageHeader))

const logout = async () => {
  await authStore.clearToken()
  await router.push('/login')
}

const goLogin = async () => {
  await router.push({ path: '/login', query: { redirect: route.fullPath } })
}

const navigateByItem = async (path: string, options?: { requiresAuth?: boolean }) => {
  if (options?.requiresAuth && !authStore.isAuthenticated) {
    ElMessage.warning('Please login first.')
    await goLogin()
    return
  }
  await router.push(path)
}

const handleUserNavigate = async (path: string) => {
  const target = [...publicNavItems, ...userNavItems].find((item) => item.path === path)
  await navigateByItem(path, { requiresAuth: target?.requiresAuth })
}

const handleAdminSelect = async (path: string) => {
  const target = adminNavItems.find((item) => item.path === path)
  await navigateByItem(path, { requiresAuth: target?.requiresAuth })
}
</script>

<template>
  <div v-if="isAdminContext" class="admin-layout">
    <el-container class="admin-shell">
      <el-aside :width="sidebarOpen ? '252px' : '74px'" class="admin-sider">
        <div class="admin-brand">{{ sidebarOpen ? 'Emotion Admin' : 'EA' }}</div>
        <el-menu :default-active="activeAdminPath" :collapse="!sidebarOpen" @select="handleAdminSelect">
          <el-menu-item-group title="Console">
            <el-menu-item v-for="item in visibleAdminNavItems" :key="item.path" :index="item.path">
              {{ item.label }}
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="admin-header">
          <div class="admin-header-left">
            <el-button circle text @click="sidebarOpen = !sidebarOpen">
              <el-icon><Fold v-if="sidebarOpen" /><Expand v-else /></el-icon>
            </el-button>
            <div>
              <h2 class="admin-title">{{ pageTitle }}</h2>
              <el-breadcrumb separator="/">
                <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</el-breadcrumb-item>
              </el-breadcrumb>
            </div>
          </div>
          <div class="admin-actions">
            <el-button text @click="router.push('/home')">
              <el-icon><Promotion /></el-icon>
              <span>Open User Site</span>
            </el-button>
            <template v-if="authStore.isAuthenticated">
              <el-dropdown>
                <el-button text>
                  <el-icon><UserFilled /></el-icon>
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
          </div>
        </el-header>
        <el-main class="admin-content">
          <PageContainer
            variant="admin"
            :title="pageTitle"
            :description="pageDescription"
            :hide-header="hidePageHeader"
          >
            <router-view />
          </PageContainer>
        </el-main>
      </el-container>
    </el-container>
  </div>

  <div v-else class="user-layout">
    <AppShell
      :nav-items="userTopNavItems"
      :active-path="activeUserPath"
      :authenticated="authStore.isAuthenticated"
      :username="authStore.currentUser?.username"
      :role="authStore.userRole"
      @navigate="handleUserNavigate"
      @login="goLogin"
      @logout="logout"
    >
      <PageContainer
        variant="user"
        :title="pageTitle"
        :description="pageDescription"
        :hide-header="hidePageHeader"
      >
        <router-view />
      </PageContainer>
    </AppShell>
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
}

.admin-sider {
  border-right: 1px solid rgba(71, 92, 127, 0.44);
  transition: width 0.2s ease;
}

.admin-brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #d6e3fa;
  letter-spacing: 0.09em;
  font-size: 14px;
  text-transform: uppercase;
  border-bottom: 1px solid rgba(71, 92, 127, 0.44);
}

.admin-header {
  height: 74px;
  border-bottom: 1px solid rgba(71, 92, 127, 0.44);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.admin-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-title {
  margin: 0;
  font-size: 17px;
  color: #dce9ff;
}

.admin-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-content {
  padding: 0;
}
</style>