<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import AppShell, { type AppNavItem } from '@/components/ui/AppShell.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useUserAuthStore } from '@/stores/userAuth'

const route = useRoute()
const router = useRouter()
const userAuthStore = useUserAuthStore()

type NavItem = AppNavItem & {
  requiresAuth: boolean
}

const orderedNavItems: NavItem[] = [
  { label: '首页', path: '/app/home', requiresAuth: false },
  { label: '语音上传', path: '/app/upload', requiresAuth: true },
  { label: '任务中心', path: '/app/tasks', requiresAuth: true },
  { label: '报告中心', path: '/app/reports', requiresAuth: true },
  { label: '趋势分析', path: '/app/trends', requiresAuth: true },
  { label: '心理中心', path: '/app/psy-centers', requiresAuth: true },
  { label: '内容专栏', path: '/app/content', requiresAuth: false },
  { label: '关于', path: '/app/about', requiresAuth: false },
  { label: '个人中心', path: '/app/profile', requiresAuth: true },
]

const userTopNavItems = computed(() => {
  const items = orderedNavItems.filter((item) => !item.requiresAuth || userAuthStore.isAuthenticated)
  return items.map((item) => ({ label: item.label, path: item.path }))
})

const activeUserPath = computed(() => {
  const found = userTopNavItems.value.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  return found?.path ?? '/app/home'
})

const pageTitle = computed(() => String(route.meta.title ?? '情绪档案库'))
const pageDescription = computed(() => String(route.meta.description ?? '语音情绪分析与心理状态自助评估'))
const hidePageHeader = computed(() => Boolean(route.meta.hidePageHeader))

const logout = async () => {
  await userAuthStore.clearSession()
  await router.push('/app/login')
}

const goLogin = async () => {
  await router.push({ path: '/app/login', query: { redirect: route.fullPath } })
}

const navigateByItem = async (path: string, options?: { requiresAuth?: boolean }) => {
  if (options?.requiresAuth && !userAuthStore.isAuthenticated) {
    ElMessage.warning('请先登录用户账号。')
    await goLogin()
    return
  }
  await router.push(path)
}

const handleUserNavigate = async (path: string) => {
  const target = orderedNavItems.find((item) => item.path === path)
  await navigateByItem(path, { requiresAuth: target?.requiresAuth })
}
</script>

<template>
  <div class="user-layout">
    <AppShell
      :nav-items="userTopNavItems"
      :active-path="activeUserPath"
      :authenticated="userAuthStore.isAuthenticated"
      :username="userAuthStore.currentUser?.username"
      role="USER"
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

