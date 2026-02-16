<script setup lang="ts">
import { computed, ref } from 'vue'
import { Setting, Moon, Sunny, User, Fold, Expand, Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import PageContainer from '@/components/layout/PageContainer.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sidebarOpen = ref(true)
const isDark = ref(false)

const navItems = computed(() => {
  const items = [
    { label: '首页', path: '/home', requiresAuth: false },
    { label: '上传', path: '/upload', requiresAuth: true },
    { label: '任务', path: '/tasks', requiresAuth: true },
    { label: '报告', path: '/reports', requiresAuth: true },
    { label: '心理中心', path: '/psy-centers', requiresAuth: true },
    { label: '系统', path: '/system', requiresAuth: true, role: 'ADMIN' },
    { label: '关于', path: '/about', requiresAuth: false },
  ]

  return items.filter((item) => !item.role || authStore.userRole === item.role)
})

const activeMenu = computed(() => {
  const path = route.path
  const found = navItems.value.find((item) => path === item.path || path.startsWith(`${item.path}/`))
  return found?.path ?? '/home'
})

const pageTitle = computed(() => String(route.meta.title ?? 'AI Emotion 控制台'))
const pageDescription = computed(() => String(route.meta.description ?? '统一资源管理工作台'))
const breadcrumbs = computed(() => {
  const items = route.meta.breadcrumb
  return Array.isArray(items) ? items.map(String) : ['工作台']
})

const logout = async () => {
  await authStore.clearToken()
  await router.push('/login')
}

const goLogin = async () => {
  await router.push({ path: '/login', query: { redirect: route.fullPath } })
}

const handleSelect = async (index: string) => {
  const target = navItems.value.find((item) => item.path === index)
  if (!target) {
    return
  }

  if (target.requiresAuth && !authStore.isAuthenticated) {
    ElMessage.warning('请先登录后再访问该功能。')
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
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item>
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
          <el-button circle text @click="toggleTheme"><el-icon><Sunny v-if="isDark" /><Moon v-else /></el-icon></el-button>
          <el-button circle text><el-icon><Setting /></el-icon></el-button>

          <template v-if="authStore.isAuthenticated">
            <el-dropdown>
              <el-button text>
                <el-icon><User /></el-icon>
                <span style="margin-left: 6px">{{ authStore.currentUser?.username }}（{{ authStore.userRole }}）</span>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else type="primary" @click="goLogin">
            <el-icon><Promotion /></el-icon>
            登录 / 注册
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
