<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Bell,
  Document,
  DocumentCopy,
  Expand,
  Fold,
  Promotion,
  Setting,
  UserFilled,
} from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getAdminInbox,
  updateAdminInboxPreferences,
  updateAdminInboxState,
  type AdminInboxCategory,
  type AdminInboxItem,
  type AdminInboxPreferences,
  type AdminInboxSummary,
} from '@/api/adminInbox'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { parseError, type ErrorStatePayload } from '@/utils/error'

type AdminNavItem = {
  label: string
  path: string
}

type AdminNavGroup = {
  key: string
  label: string
  icon: Component
  items: AdminNavItem[]
}

type InboxFilter = 'all' | 'unread' | 'priority'

const SIDEBAR_STORAGE_KEY = 'ai-emotion-admin-sidebar-open'
const INBOX_STALE_MS = 60_000
const INBOX_CATEGORY_KEYS: AdminInboxCategory[] = ['warning', 'system', 'model', 'schedule', 'psy_center']
const ADMIN_INBOX_DRAWER_VARS = {
  '--el-drawer-bg-color': '#0b1220',
  '--el-bg-color': '#0b1220',
  '--el-bg-color-page': '#0b1220',
  '--el-text-color-primary': '#eef5ff',
  '--el-text-color-regular': '#dce9ff',
  '--el-text-color-secondary': '#b6c8e3',
  '--el-border-color': 'rgba(87, 113, 158, 0.34)',
  '--el-fill-color-blank': 'transparent',
  '--el-fill-color-light': 'rgba(17, 28, 49, 0.88)',
} as const

const route = useRoute()
const router = useRouter()
const adminAuthStore = useAdminAuthStore()

const adminNavGroups: AdminNavGroup[] = [
  {
    key: 'overview',
    label: '总览',
    icon: Promotion,
    items: [
      { label: '管理看板', path: '/admin/dashboard' },
      { label: '用户管理', path: '/admin/users' },
    ],
  },
  {
    key: 'content',
    label: '内容运营',
    icon: Document,
    items: [
      { label: '轮播图管理', path: '/admin/content/banners' },
      { label: '语录管理', path: '/admin/content/quotes' },
      { label: '文章管理', path: '/admin/content/articles' },
      { label: '书籍管理', path: '/admin/content/books' },
      { label: '书籍总览', path: '/admin/content/books-overview' },
      { label: '每日排期', path: '/admin/content/schedules' },
    ],
  },
  {
    key: 'governance',
    label: '风险治理',
    icon: DocumentCopy,
    items: [
      { label: '预警处置台', path: '/admin/warnings' },
      { label: '预警规则', path: '/admin/rules' },
      { label: '心理中心管理', path: '/admin/psy-centers' },
    ],
  },
  {
    key: 'system',
    label: '模型与系统',
    icon: Setting,
    items: [
      { label: '模型治理', path: '/admin/models' },
      { label: '系统设置', path: '/admin/settings' },
      { label: '系统状态', path: '/admin/system' },
    ],
  },
]

const createEmptyInboxSummary = (): AdminInboxSummary => ({
  total: 0,
  highPriority: 0,
  unread: 0,
  categoryCounts: {},
})

const createDefaultInboxPreferences = (): AdminInboxPreferences => ({
  categories: {
    warning: true,
    system: true,
    model: true,
    schedule: true,
    psy_center: true,
  },
})

const readSidebarState = () => {
  if (typeof window === 'undefined') return true
  const stored = window.localStorage.getItem(SIDEBAR_STORAGE_KEY)
  if (stored == null) return true
  return stored === '1'
}

const allAdminNavItems = adminNavGroups.flatMap((group) => group.items)
const sidebarOpen = ref(readSidebarState())
const expandedGroupKey = ref('overview')

const activeAdminPath = computed(() => {
  const found = allAdminNavItems.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  return found?.path ?? '/admin/dashboard'
})

const activeGroupKey = computed(() => {
  const foundGroup = adminNavGroups.find((group) =>
    group.items.some((item) => route.path === item.path || route.path.startsWith(`${item.path}/`)),
  )
  return foundGroup?.key ?? 'overview'
})

const pageTitle = computed(() => String(route.meta.title ?? '管理控制台'))
const pageDescription = computed(() =>
  String(route.meta.description ?? '统一查看模型、规则、内容与预警处置状态。'),
)
const breadcrumbs = computed(() => {
  const items = route.meta.breadcrumb
  return Array.isArray(items) ? items.map(String) : ['管理端']
})
const hidePageHeader = computed(() => Boolean(route.meta.hidePageHeader))

const itemMark = (label: string) => label.slice(0, 1)
const isGroupOpen = (groupKey: string) => sidebarOpen.value && expandedGroupKey.value === groupKey

const inboxVisible = ref(false)
const inboxLoading = ref(false)
const inboxErrorState = ref<ErrorStatePayload | null>(null)
const inboxItems = ref<AdminInboxItem[]>([])
const inboxSummary = ref<AdminInboxSummary>(createEmptyInboxSummary())
const inboxPreferences = ref<AdminInboxPreferences>(createDefaultInboxPreferences())
const inboxGeneratedAt = ref<string>()
const inboxFilter = ref<InboxFilter>('all')
const inboxArchivedView = ref(false)
const inboxPendingWarningsOnly = ref(false)
const inboxLastLoadedAt = ref(0)

const isInboxItemRead = (item: AdminInboxItem) => item.read === true
const isHighPriority = (item: AdminInboxItem) => item.level === 'critical' || item.level === 'warning'

const unreadCount = computed(() => inboxItems.value.filter((item) => !isInboxItemRead(item)).length)
const unreadBadgeValue = computed(() => (unreadCount.value > 99 ? '99+' : unreadCount.value))

const filteredInboxItems = computed(() => {
  switch (inboxFilter.value) {
    case 'unread':
      return inboxItems.value.filter((item) => !isInboxItemRead(item))
    case 'priority':
      return inboxItems.value.filter(isHighPriority)
    default:
      return inboxItems.value
  }
})

const inboxCategoryToggles = computed(() => [
  { key: 'warning' as const, label: '预警动态' },
  { key: 'system' as const, label: '系统状态' },
  { key: 'model' as const, label: '模型变更' },
  { key: 'schedule' as const, label: '排期更新' },
  { key: 'psy_center' as const, label: '心理中心' },
])

const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(SIDEBAR_STORAGE_KEY, sidebarOpen.value ? '1' : '0')
  }
  if (sidebarOpen.value) {
    expandedGroupKey.value = activeGroupKey.value
  }
}

const handleGroupToggle = (groupKey: string) => {
  if (!sidebarOpen.value) {
    sidebarOpen.value = true
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(SIDEBAR_STORAGE_KEY, '1')
    }
  }
  expandedGroupKey.value = groupKey
}

const handleAdminSelect = async (path: string) => {
  await router.push(path)
}

const loadInbox = async () => {
  inboxLoading.value = true
  inboxErrorState.value = null
  try {
    const data = await getAdminInbox({
      limit: 40,
      archived: inboxArchivedView.value,
      pendingWarningsOnly: inboxPendingWarningsOnly.value,
    })
    inboxItems.value = Array.isArray(data.items) ? data.items : []
    inboxSummary.value = data.summary ?? createEmptyInboxSummary()
    inboxPreferences.value = data.preferences ?? createDefaultInboxPreferences()
    inboxGeneratedAt.value = data.generatedAt
    inboxLastLoadedAt.value = Date.now()
  } catch (error) {
    inboxErrorState.value = parseError(error, '消息中心加载失败')
  } finally {
    inboxLoading.value = false
  }
}

const openInbox = async () => {
  inboxVisible.value = true
  if (!inboxItems.value.length || Date.now() - inboxLastLoadedAt.value > INBOX_STALE_MS) {
    await loadInbox()
  }
}

const markInboxItemRead = async (item: AdminInboxItem) => {
  if (!item.id || isInboxItemRead(item)) return
  await updateAdminInboxState({
    items: [
      {
        messageId: item.id,
        sourceType: item.sourceType,
        sourceId: item.sourceId,
      },
    ],
    read: true,
  })
  item.read = true
}

const markAllInboxRead = async () => {
  const unreadItems = inboxItems.value.filter((item) => !isInboxItemRead(item))
  if (!unreadItems.length) return
  await updateAdminInboxState({
    items: unreadItems.map((item) => ({
      messageId: item.id,
      sourceType: item.sourceType,
      sourceId: item.sourceId,
    })),
    read: true,
  })
  unreadItems.forEach((item) => {
    item.read = true
  })
  ElMessage.success('已将当前消息标记为已读')
}

const setInboxArchiveState = async (item: AdminInboxItem, archived: boolean) => {
  await updateAdminInboxState({
    items: [
      {
        messageId: item.id,
        sourceType: item.sourceType,
        sourceId: item.sourceId,
      },
    ],
    archived,
  })
  await loadInbox()
}

const toggleInboxCategory = async (category: AdminInboxCategory, enabled: boolean) => {
  const payload = {
    warningEnabled:
      category === 'warning' ? enabled : inboxPreferences.value.categories.warning ?? true,
    systemEnabled:
      category === 'system' ? enabled : inboxPreferences.value.categories.system ?? true,
    modelEnabled: category === 'model' ? enabled : inboxPreferences.value.categories.model ?? true,
    scheduleEnabled:
      category === 'schedule' ? enabled : inboxPreferences.value.categories.schedule ?? true,
    psyCenterEnabled:
      category === 'psy_center' ? enabled : inboxPreferences.value.categories.psy_center ?? true,
  }
  inboxPreferences.value = await updateAdminInboxPreferences(payload)
  await loadInbox()
}

const handleInboxItemClick = async (item: AdminInboxItem) => {
  await markInboxItemRead(item)
  if (item.route && route.path !== item.route) {
    await router.push(item.route)
  }
  inboxVisible.value = false
}

const formatInboxCategory = (category: AdminInboxCategory) => {
  switch (category) {
    case 'warning':
      return '预警动态'
    case 'system':
      return '系统状态'
    case 'model':
      return '模型变更'
    case 'schedule':
      return '排期更新'
    case 'psy_center':
      return '心理中心'
    default:
      return '消息'
  }
}

const formatInboxTime = (value?: string) => {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const logout = async () => {
  await adminAuthStore.clearSession()
  ElMessage.success('已退出管理员账号')
  await router.push('/admin/login')
}

watch(activeGroupKey, (groupKey) => {
  if (sidebarOpen.value) {
    expandedGroupKey.value = groupKey
  }
})

watch([inboxArchivedView, inboxPendingWarningsOnly], () => {
  if (inboxVisible.value) {
    void loadInbox()
  }
})

watch(
  () => adminAuthStore.isAuthenticated,
  (authed) => {
    if (authed && !inboxItems.value.length) {
      void loadInbox()
    }
  },
  { immediate: true },
)

onMounted(() => {
  expandedGroupKey.value = activeGroupKey.value
})
</script>

<template>
  <div class="admin-layout">
    <el-container class="admin-shell">
      <el-aside :width="sidebarOpen ? '264px' : '80px'" class="admin-sider" :class="{ 'is-collapsed': !sidebarOpen }">
        <div class="admin-brand">
          <div class="admin-brand__title">{{ sidebarOpen ? '情绪预警管理台' : '预' }}</div>
          <div v-if="sidebarOpen" class="admin-brand__subtitle">治理与内容运营后台</div>
        </div>

        <nav class="admin-nav" aria-label="管理端导航">
          <section
            v-for="group in adminNavGroups"
            :key="group.key"
            class="admin-nav-group"
            :class="{
              'is-open': isGroupOpen(group.key),
              'is-active-group': activeGroupKey === group.key,
            }"
          >
            <button
              type="button"
              class="admin-group-toggle"
              :title="sidebarOpen ? '' : group.label"
              @click="handleGroupToggle(group.key)"
            >
              <el-icon class="admin-group__icon">
                <component :is="group.icon" />
              </el-icon>
              <span v-if="sidebarOpen" class="admin-group__text">
                <span class="admin-group__label">{{ group.label }}</span>
              </span>
              <el-icon v-if="sidebarOpen" class="admin-group__caret">
                <ArrowDown />
              </el-icon>
            </button>

            <div v-if="isGroupOpen(group.key)" class="admin-group-items">
              <button
                v-for="item in group.items"
                :key="item.path"
                type="button"
                class="admin-menu-link"
                :class="{ 'is-active': activeAdminPath === item.path }"
                :title="sidebarOpen ? '' : item.label"
                @click="handleAdminSelect(item.path)"
              >
                <span class="admin-menu__mark" aria-hidden="true">{{ itemMark(item.label) }}</span>
                <span class="admin-menu__label">{{ item.label }}</span>
              </button>
            </div>
          </section>
        </nav>
      </el-aside>

      <el-container>
        <el-header class="admin-header">
          <div class="admin-header-left">
            <el-button circle text @click="toggleSidebar">
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
            <el-badge class="admin-actions__badge" :hidden="unreadCount === 0" :value="unreadBadgeValue">
              <el-button text class="admin-header-button" @click="openInbox">
                <el-icon><Bell /></el-icon>
                <span>消息中心</span>
              </el-button>
            </el-badge>

            <el-button text class="admin-header-button" @click="router.push('/app/home')">
              <el-icon><Promotion /></el-icon>
              <span>打开用户端</span>
            </el-button>

            <el-dropdown>
              <el-button text class="admin-header-button">
                <el-icon><UserFilled /></el-icon>
                <span class="admin-user">{{ adminAuthStore.currentUser?.username }}（管理员）</span>
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

    <el-drawer
      v-model="inboxVisible"
      class="admin-inbox-drawer"
      :style="ADMIN_INBOX_DRAWER_VARS"
      modal-class="admin-inbox-overlay"
      header-class="admin-inbox-drawer__shell-header"
      body-class="admin-inbox-drawer__shell-body"
      size="420px"
      append-to-body
    >
      <template #header>
        <div class="admin-inbox-drawer__header">
          <div>
            <h3 class="admin-inbox-drawer__title">消息中心</h3>
            <p class="admin-inbox-drawer__desc">集中查看预警、系统、模型和运营变化</p>
          </div>
          <div class="admin-inbox-drawer__stats">
            <span class="admin-inbox-chip is-strong">{{ unreadCount }} 未读</span>
            <span class="admin-inbox-chip">{{ inboxSummary.highPriority }} 条优先关注</span>
          </div>
        </div>
      </template>

      <div class="admin-inbox">
        <div class="admin-inbox__toolbar">
          <div class="admin-inbox__filters">
            <button
              type="button"
              class="admin-inbox-filter"
              :class="{ 'is-active': inboxFilter === 'all' }"
              @click="inboxFilter = 'all'"
            >
              全部
            </button>
            <button
              type="button"
              class="admin-inbox-filter"
              :class="{ 'is-active': inboxFilter === 'unread' }"
              @click="inboxFilter = 'unread'"
            >
              未读
            </button>
            <button
              type="button"
              class="admin-inbox-filter"
              :class="{ 'is-active': inboxFilter === 'priority' }"
              @click="inboxFilter = 'priority'"
            >
              高优先级
            </button>
          </div>

          <div class="admin-inbox__tools">
            <el-button text size="small" @click="loadInbox">刷新</el-button>
            <el-button text size="small" :disabled="unreadCount === 0" @click="markAllInboxRead">
              全部已读
            </el-button>
          </div>
        </div>

        <p class="admin-inbox__summary">
          共 {{ inboxSummary.total }} 条消息
          <span v-if="typeof inboxSummary.unread === 'number'">，其中 {{ inboxSummary.unread }} 条未读</span>
          <span v-if="inboxGeneratedAt">，最近刷新 {{ formatInboxTime(inboxGeneratedAt) }}</span>
        </p>

        <div class="admin-inbox-settings">
          <div class="admin-inbox-settings__section">
            <div class="admin-inbox-settings__label">消息类别</div>
            <div class="admin-inbox-settings__chips">
              <button
                v-for="category in inboxCategoryToggles"
                :key="category.key"
                type="button"
                class="admin-inbox-toggle"
                :class="{ 'is-active': inboxPreferences.categories[category.key] !== false }"
                @click="toggleInboxCategory(category.key, inboxPreferences.categories[category.key] === false)"
              >
                {{ category.label }}
              </button>
            </div>
          </div>

          <div class="admin-inbox-settings__section is-inline">
            <label class="admin-inbox-switch">
              <input v-model="inboxPendingWarningsOnly" type="checkbox" />
              <span>只看未处理预警</span>
            </label>
            <label class="admin-inbox-switch">
              <input v-model="inboxArchivedView" type="checkbox" />
              <span>查看已归档</span>
            </label>
          </div>
        </div>

        <div v-if="inboxErrorState" class="admin-inbox-state is-error">
          <strong>{{ inboxErrorState.title }}</strong>
          <p>{{ inboxErrorState.detail }}</p>
          <el-button size="small" type="primary" plain @click="loadInbox">重新加载</el-button>
        </div>

        <div v-else-if="inboxLoading" class="admin-inbox-state">
          正在拉取最近变化...
        </div>

        <div v-else-if="filteredInboxItems.length === 0" class="admin-inbox-state">
          当前筛选下暂无消息。
        </div>

        <div v-else class="admin-inbox-list">
          <button
            v-for="item in filteredInboxItems"
            :key="item.id"
            type="button"
            class="admin-inbox-item"
            :class="{ 'is-unread': !isInboxItemRead(item) }"
            @click="handleInboxItemClick(item)"
          >
            <div class="admin-inbox-item__top">
              <div class="admin-inbox-item__eyebrow">
                <span class="admin-inbox-level" :class="`is-${item.level}`"></span>
                <span>{{ formatInboxCategory(item.category) }}</span>
                <span v-if="!isInboxItemRead(item)" class="admin-inbox-item__unread">未读</span>
              </div>
              <span class="admin-inbox-item__time">{{ formatInboxTime(item.occurredAt) }}</span>
            </div>

            <div class="admin-inbox-item__title">{{ item.title }}</div>
            <div v-if="item.detail" class="admin-inbox-item__detail">{{ item.detail }}</div>

            <div class="admin-inbox-item__footer">
              <span>{{ item.routeLabel ?? '查看详情' }}</span>
              <div class="admin-inbox-item__actions">
                <button
                  v-if="!item.archived"
                  type="button"
                  class="admin-inbox-action"
                  @click.stop="setInboxArchiveState(item, true)"
                >
                  归档
                </button>
                <button
                  v-else
                  type="button"
                  class="admin-inbox-action"
                  @click.stop="setInboxArchiveState(item, false)"
                >
                  取消归档
                </button>
                <span class="admin-inbox-item__jump">前往</span>
              </div>
            </div>
          </button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
}

.admin-sider {
  border-right: 1px solid rgba(71, 92, 127, 0.44);
  transition: width 0.2s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.admin-brand {
  height: 76px;
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-bottom: 1px solid rgba(71, 92, 127, 0.44);
  overflow: hidden;
}

.admin-brand__title {
  color: #d6e3fa;
  letter-spacing: 0.08em;
  font-size: 14px;
  text-transform: uppercase;
}

.admin-brand__subtitle {
  color: rgba(214, 227, 250, 0.66);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.admin-sider.is-collapsed .admin-brand {
  padding: 0;
}

.admin-sider.is-collapsed .admin-brand__title {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(76, 168, 188, 0.18);
  color: #eef6ff;
  font-size: 16px;
  letter-spacing: 0;
  text-transform: none;
}

.admin-nav {
  flex: 1;
  padding: 12px 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
}

.admin-nav-group {
  border: 1px solid transparent;
  border-radius: 16px;
  background: rgba(14, 23, 39, 0.34);
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.admin-nav-group.is-active-group,
.admin-nav-group.is-open {
  border-color: rgba(71, 108, 172, 0.34);
  background: rgba(21, 33, 54, 0.78);
  box-shadow: inset 0 1px 0 rgba(126, 157, 214, 0.08);
}

.admin-group-toggle {
  width: 100%;
  border: none;
  background: transparent;
  color: #d8ebff;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 14px;
  cursor: pointer;
  text-align: left;
}

.admin-group-toggle:hover {
  background: rgba(53, 79, 122, 0.26);
}

.admin-group__icon {
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: rgba(87, 118, 176, 0.24);
  color: #d8ebff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.admin-group__text {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
}

.admin-group__label {
  min-width: 0;
  color: #f2f7ff;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}

.admin-group__caret {
  color: rgba(191, 211, 244, 0.78);
  transition: transform 0.2s ease;
}

.admin-nav-group.is-open .admin-group__caret {
  transform: rotate(0deg);
}

.admin-group-items {
  padding: 0 8px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.admin-menu-link {
  position: relative;
  width: 100%;
  border: none;
  background: transparent;
  color: #c2d3ef;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px 10px 14px;
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.admin-menu-link:hover {
  background: rgba(53, 79, 122, 0.3);
}

.admin-menu-link.is-active {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.22), rgba(27, 63, 122, 0.18));
  color: #f5f8ff;
  box-shadow:
    inset 3px 0 0 #72a8ff,
    0 10px 20px rgba(5, 11, 24, 0.18);
  transform: translateX(1px);
}

.admin-menu-link.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, #8ec5ff, #4d8dff);
}

.admin-menu__mark {
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: rgba(76, 168, 188, 0.16);
  color: #d8ebff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.admin-menu__label {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
}

.admin-sider.is-collapsed .admin-nav {
  padding-left: 10px;
  padding-right: 10px;
}

.admin-sider.is-collapsed .admin-nav-group {
  border-radius: 16px;
}

.admin-sider.is-collapsed .admin-group-toggle {
  justify-content: center;
  padding-left: 0;
  padding-right: 0;
}

.admin-sider.is-collapsed .admin-group__text,
.admin-sider.is-collapsed .admin-group__caret,
.admin-sider.is-collapsed .admin-group-items {
  display: none;
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

.admin-header-button {
  color: #cfe3ff;
  border-radius: 12px;
  --el-button-text-color: #cfe3ff;
  --el-button-hover-text-color: #f4f8ff;
  --el-button-active-text-color: #ffffff;
  --el-button-hover-bg-color: rgba(58, 86, 132, 0.28);
  --el-button-active-bg-color: rgba(71, 104, 160, 0.34);
  --el-button-outline-color: transparent;
  --el-fill-color-light: rgba(58, 86, 132, 0.28);
}

.admin-header-button:hover {
  color: #f4f8ff;
}

:deep(.admin-header-button.el-button.is-text),
:deep(.admin-header-button.el-button.is-link) {
  background: transparent;
  border: none;
  box-shadow: none;
}

:deep(.admin-header-button.el-button.is-text:hover),
:deep(.admin-header-button.el-button.is-text:focus-visible),
:deep(.admin-header-button.el-button.is-text.is-active),
:deep(.admin-header-button.el-button.is-link:hover),
:deep(.admin-header-button.el-button.is-link:focus-visible) {
  background: rgba(58, 86, 132, 0.28) !important;
  color: #f4f8ff !important;
}

:deep(.admin-header-button.el-button.is-text:active),
:deep(.admin-header-button.el-button.is-link:active) {
  background: rgba(71, 104, 160, 0.34) !important;
  color: #ffffff !important;
}

.admin-actions__badge {
  display: inline-flex;
}

.admin-user {
  margin-left: 6px;
}

.admin-content {
  padding: 0;
}

.admin-inbox-drawer__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.admin-inbox-drawer__title {
  margin: 0;
  font-size: 18px;
  color: #eef5ff;
}

.admin-inbox-drawer__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: rgba(222, 236, 255, 0.84);
}

.admin-inbox-drawer__stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.admin-inbox-chip {
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  color: #c9ddff;
  background: rgba(33, 48, 74, 0.78);
  border: 1px solid rgba(88, 117, 170, 0.24);
}

.admin-inbox-chip.is-strong {
  color: #f5f9ff;
  background: rgba(52, 86, 142, 0.78);
}

.admin-inbox {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}

.admin-inbox__toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.admin-inbox__filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.admin-inbox-filter {
  border: 1px solid rgba(82, 108, 156, 0.24);
  background: rgba(16, 24, 38, 0.6);
  color: #bfd3f4;
  padding: 7px 12px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 12px;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.admin-inbox-filter.is-active,
.admin-inbox-filter:hover {
  background: rgba(51, 82, 136, 0.5);
  border-color: rgba(120, 161, 230, 0.42);
  color: #f5f9ff;
}

.admin-inbox__tools {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.admin-inbox__summary {
  margin: 0;
  font-size: 13px;
  color: rgba(214, 228, 249, 0.82);
}

.admin-inbox-settings {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-radius: 18px;
  border: 1px solid rgba(72, 101, 147, 0.24);
  background: rgba(13, 22, 38, 0.74);
  padding: 14px;
}

.admin-inbox-settings__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-inbox-settings__section.is-inline {
  flex-direction: row;
  flex-wrap: wrap;
  gap: 14px;
}

.admin-inbox-settings__label {
  font-size: 12px;
  letter-spacing: 0.04em;
  color: rgba(197, 214, 241, 0.7);
}

.admin-inbox-settings__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.admin-inbox-toggle {
  border: 1px solid rgba(78, 104, 152, 0.26);
  background: rgba(17, 27, 44, 0.82);
  color: rgba(201, 218, 244, 0.82);
  padding: 7px 10px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 12px;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.admin-inbox-toggle.is-active {
  background: rgba(65, 103, 168, 0.58);
  border-color: rgba(129, 172, 248, 0.42);
  color: #f5f9ff;
}

.admin-inbox-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #d7e8ff;
  font-size: 13px;
}

.admin-inbox-switch input {
  accent-color: #7caeff;
}

.admin-inbox-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding-right: 2px;
}

.admin-inbox-item {
  width: 100%;
  border: 1px solid rgba(76, 101, 148, 0.24);
  background: rgba(15, 24, 40, 0.78);
  color: #d7e8ff;
  border-radius: 18px;
  padding: 14px 14px 12px;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.admin-inbox-item:hover {
  transform: translateY(-1px);
  border-color: rgba(108, 146, 214, 0.4);
  background: rgba(21, 33, 54, 0.92);
  box-shadow: 0 12px 24px rgba(4, 9, 20, 0.22);
}

.admin-inbox-item.is-unread {
  border-color: rgba(120, 168, 255, 0.44);
  box-shadow: inset 0 1px 0 rgba(144, 187, 255, 0.1);
}

.admin-inbox-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.admin-inbox-item__eyebrow {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: rgba(195, 213, 240, 0.78);
}

.admin-inbox-level {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-flex;
}

.admin-inbox-level.is-critical {
  background: #ff7a7a;
  box-shadow: 0 0 0 3px rgba(255, 122, 122, 0.14);
}

.admin-inbox-level.is-warning {
  background: #f4c56d;
  box-shadow: 0 0 0 3px rgba(244, 197, 109, 0.12);
}

.admin-inbox-level.is-info {
  background: #6fb4ff;
  box-shadow: 0 0 0 3px rgba(111, 180, 255, 0.12);
}

.admin-inbox-level.is-success {
  background: #7fd6b5;
  box-shadow: 0 0 0 3px rgba(127, 214, 181, 0.12);
}

.admin-inbox-item__unread {
  color: #f5f9ff;
  background: rgba(67, 103, 168, 0.58);
  border-radius: 999px;
  padding: 2px 8px;
}

.admin-inbox-item__time {
  font-size: 12px;
  color: rgba(195, 213, 240, 0.66);
  white-space: nowrap;
}

.admin-inbox-item__title {
  margin-top: 10px;
  font-size: 15px;
  font-weight: 700;
  color: #f2f7ff;
  line-height: 1.45;
}

.admin-inbox-item__detail {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(209, 224, 245, 0.76);
}

.admin-inbox-item__footer {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: #9fc2ff;
}

.admin-inbox-item__jump {
  color: rgba(208, 225, 248, 0.72);
}

.admin-inbox-item__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-inbox-action {
  border: none;
  background: transparent;
  color: #bcd9ff;
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}

.admin-inbox-action:hover {
  color: #ffffff;
}

.admin-inbox-state {
  border-radius: 18px;
  border: 1px solid rgba(70, 94, 134, 0.24);
  background: rgba(15, 24, 40, 0.66);
  padding: 18px;
  color: #d7e8ff;
  line-height: 1.7;
}

.admin-inbox-state.is-error {
  border-color: rgba(210, 112, 112, 0.3);
  background: rgba(50, 25, 31, 0.36);
}

.admin-inbox-state p {
  margin: 6px 0 12px;
}

:deep(.admin-actions__badge .el-badge__content) {
  background: #5b97ff;
  border-color: #0b1220;
}

:deep(.admin-inbox-drawer.el-drawer) {
  background:
    radial-gradient(circle at top left, rgba(74, 116, 193, 0.18), transparent 38%),
    linear-gradient(180deg, #111a2d 0%, #0b1220 100%);
  color: #e8f1ff;
}

:deep(.admin-inbox-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 20px 20px 10px;
  background: transparent;
}

:deep(.admin-inbox-drawer .el-drawer__body) {
  padding: 8px 20px 20px;
  overflow: hidden;
  background: transparent;
}

:deep(.admin-inbox-drawer .el-drawer__title) {
  color: #eef5ff;
}

:deep(.admin-inbox-drawer .el-drawer__close-btn) {
  color: rgba(225, 236, 255, 0.82);
}

:deep(.admin-inbox-drawer .el-drawer__close-btn:hover) {
  color: #ffffff;
}

:deep(.admin-inbox-drawer .el-button.is-text) {
  color: #d4e4ff;
}

:deep(.admin-inbox-drawer .el-button.is-text:hover) {
  color: #ffffff;
}

@media (max-width: 920px) {
  .admin-header {
    height: auto;
    padding: 12px 0;
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .admin-actions {
    width: 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
}
</style>
