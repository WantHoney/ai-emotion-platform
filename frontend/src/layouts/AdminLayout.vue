<script setup lang="ts">
import { computed, ref } from 'vue'
import { Expand, Fold, Promotion, UserFilled } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

import PageContainer from '@/components/layout/PageContainer.vue'
import { useAdminAuthStore } from '@/stores/adminAuth'

const route = useRoute()
const router = useRouter()
const adminAuthStore = useAdminAuthStore()

const sidebarOpen = ref(true)

const adminNavItems = [
  { label: '看板', path: '/admin/dashboard' },
  { label: '轮播图管理', path: '/admin/content/banners' },
  { label: '语录管理', path: '/admin/content/quotes' },
  { label: '文章管理', path: '/admin/content/articles' },
  { label: '心理中心管理', path: '/admin/psy-centers' },
  { label: '预警处置台', path: '/admin/warnings' },
  { label: '系统设置', path: '/admin/settings' },
  { label: '模型治理', path: '/admin/models' },
  { label: '预警规则', path: '/admin/rules' },
  { label: '系统状态', path: '/admin/system' },
]

const activeAdminPath = computed(() => {
  const found = adminNavItems.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  return found?.path ?? '/admin/dashboard'
})

const pageTitle = computed(() => String(route.meta.title ?? '管理控制台'))
const pageDescription = computed(() =>
  String(route.meta.description ?? '模型、规则、内容与预警管理工作台'),
)
const breadcrumbs = computed(() => {
  const items = route.meta.breadcrumb
  return Array.isArray(items) ? items.map(String) : ['管理端']
})

const hidePageHeader = computed(() => Boolean(route.meta.hidePageHeader))

const logout = async () => {
  await adminAuthStore.clearSession()
  await router.push('/admin/login')
}

const handleAdminSelect = async (path: string) => {
  await router.push(path)
}
</script>

<template>
  <div class="admin-layout">
    <el-container class="admin-shell">
      <el-aside :width="sidebarOpen ? '252px' : '74px'" class="admin-sider">
        <div class="admin-brand">{{ sidebarOpen ? '情绪预警管理台' : 'EA' }}</div>
        <el-menu :default-active="activeAdminPath" :collapse="!sidebarOpen" @select="handleAdminSelect">
          <el-menu-item-group title="管理中心">
            <el-menu-item v-for="item in adminNavItems" :key="item.path" :index="item.path">
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
            <el-button text @click="router.push('/app/home')">
              <el-icon><Promotion /></el-icon>
              <span>打开用户端</span>
            </el-button>
            <el-dropdown>
              <el-button text>
                <el-icon><UserFilled /></el-icon>
                <span style="margin-left: 6px">
                  {{ adminAuthStore.currentUser?.username }}（管理员）
                </span>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
