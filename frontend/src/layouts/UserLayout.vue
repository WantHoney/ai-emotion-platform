<script setup lang="ts">
import { computed, nextTick, onMounted, onUpdated, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter, type LocationQuery, type LocationQueryRaw } from 'vue-router'

import UserAuthDialog from '@/components/auth/UserAuthDialog.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import AppShell, { type AppNavItem } from '@/components/ui/AppShell.vue'
import AmbientSideTicker from '@/components/ui/AmbientSideTicker.vue'
import { useUserAuthStore } from '@/stores/userAuth'
import { useUserThemeStore } from '@/stores/userTheme'

const route = useRoute()
const router = useRouter()
const userAuthStore = useUserAuthStore()
const userThemeStore = useUserThemeStore()

type NavItem = AppNavItem & {
  requiresAuth: boolean
}

type AuthTab = 'login' | 'register'

const orderedNavItems: NavItem[] = [
  { label: '首页', path: '/app/home', requiresAuth: false },
  { label: '语音上传', path: '/app/upload', requiresAuth: true },
  { label: '任务中心', path: '/app/tasks', requiresAuth: true },
  { label: '报告中心', path: '/app/reports', requiresAuth: true },
  { label: '趋势分析', path: '/app/trends', requiresAuth: true },
  { label: '心理中心', path: '/app/psy-centers', requiresAuth: true },
  { label: '内容专栏', path: '/app/content', requiresAuth: false },
  { label: '个人中心', path: '/app/profile', requiresAuth: true },
]

const stripAuthQuery = (query: LocationQuery): LocationQueryRaw => {
  const next: LocationQueryRaw = {}
  Object.entries(query).forEach(([key, value]) => {
    if (value == null || key === 'auth' || key === 'redirect' || key === 'tab') {
      return
    }
    next[key] = value
  })
  return next
}

const userTopNavItems = computed(() => orderedNavItems.map((item) => ({ label: item.label, path: item.path })))

const activeUserPath = computed(() => {
  const found = userTopNavItems.value.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  return found?.path ?? '/app/home'
})

const pageTitle = computed(() => String(route.meta.title ?? '情绪档案库'))
const pageDescription = computed(
  () => String(route.meta.description ?? '语音情绪分析与心理状态自助评估'),
)
const hidePageHeader = computed(() => Boolean(route.meta.hidePageHeader))

const currentPathWithoutAuthQuery = computed(() =>
  router.resolve({
    path: route.path,
    query: stripAuthQuery(route.query),
    hash: route.hash,
  }).fullPath,
)

const authDialogOpen = computed(() => route.query.auth === '1')
const authDialogTab = computed<AuthTab>(() => (route.query.tab === 'register' ? 'register' : 'login'))
const authDialogRedirect = computed(() => {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/')) {
    return redirect
  }
  return currentPathWithoutAuthQuery.value
})

const openAuthDialog = async (options?: { redirect?: string; tab?: AuthTab }) => {
  const redirect =
    typeof options?.redirect === 'string' && options.redirect.startsWith('/')
      ? options.redirect
      : currentPathWithoutAuthQuery.value

  await router.push({
    path: route.path,
    query: {
      ...stripAuthQuery(route.query),
      auth: '1',
      redirect,
      tab: options?.tab ?? 'login',
    },
    hash: route.hash,
  })
}

const closeAuthDialog = async () => {
  if (!authDialogOpen.value) {
    return
  }

  await router.replace({
    path: route.path,
    query: stripAuthQuery(route.query),
    hash: route.hash,
  })
}

const handleAuthSuccess = async (path: string) => {
  await router.replace(path)
}

const logout = async () => {
  await userAuthStore.clearSession()
  ElMessage.success('账号已退出登录')
  await router.push('/app/home')
}

const goLogin = async () => {
  await openAuthDialog({ tab: 'login' })
}

const navigateByItem = async (path: string, options?: { requiresAuth?: boolean }) => {
  if (options?.requiresAuth && !userAuthStore.isAuthenticated) {
    ElMessage.warning('请先登录用户账号。')
    await openAuthDialog({ redirect: path, tab: 'login' })
    return
  }
  await router.push(path)
}

const handleUserNavigate = async (path: string) => {
  const target = orderedNavItems.find((item) => item.path === path)
  await navigateByItem(path, { requiresAuth: target?.requiresAuth })
}

const syncThemeAfterRender = async () => {
  await nextTick()
  userThemeStore.syncDom()
}

onMounted(() => {
  void syncThemeAfterRender()
})

onUpdated(() => {
  userThemeStore.syncDom()
})

watch(
  () => route.fullPath,
  () => {
    void syncThemeAfterRender()
  },
)

watch(
  () => userThemeStore.mode,
  () => {
    void syncThemeAfterRender()
  },
)
</script>

<template>
  <div class="user-layout">
    <div class="user-layout__shell">
      <AmbientSideTicker />
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

      <UserAuthDialog
        :open="authDialogOpen"
        :initial-tab="authDialogTab"
        :redirect-path="authDialogRedirect"
        @close="closeAuthDialog"
        @success="handleAuthSuccess"
      />
    </div>
  </div>
</template>

<style scoped>
.user-layout {
  position: relative;
}

.user-layout__shell {
  position: relative;
  z-index: 1;
}
</style>
