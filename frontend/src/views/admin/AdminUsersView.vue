<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  getAdminUserDetail,
  getAdminUsers,
  updateAdminUserStatus,
  type AdminUserDetailResponse,
  type AdminUserListItem,
  type AdminUserReportItem,
  type AdminUserTaskItem,
  type AdminUserWarningItem,
} from '@/api/adminUsers'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { formatEmotion, formatRiskLevel, formatTaskStatus, formatWarningStatus } from '@/utils/uiText'

type StatusFilter = '' | 'ACTIVE' | 'DISABLED'
type AccountTypeFilter = 'ALL' | 'REAL_ONLY' | 'TEST_ONLY'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detailLoading = ref(false)
const actionLoadingId = ref<number | null>(null)
const batchLoading = ref(false)
const adminRows = ref<AdminUserListItem[]>([])
const rows = ref<AdminUserListItem[]>([])
const total = ref(0)
const systemActiveSessions = ref(0)
const selection = ref<AdminUserListItem[]>([])
const errorState = ref<ErrorStatePayload | null>(null)
const detailError = ref<ErrorStatePayload | null>(null)
const detail = ref<AdminUserDetailResponse | null>(null)
const detailVisible = ref(false)

const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
  status: '' as StatusFilter,
  accountType: 'REAL_ONLY' as AccountTypeFilter,
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '启用中', value: 'ACTIVE' },
  { label: '已停用', value: 'DISABLED' },
]

const accountTypeOptions = [
  { label: '隐藏测试账号', value: 'REAL_ONLY' },
  { label: '仅看测试账号', value: 'TEST_ONLY' },
  { label: '显示全部账号', value: 'ALL' },
]

const currentProfile = computed(() => detail.value?.profile ?? null)
const isAdminProfile = computed(() => currentProfile.value?.role_code === 'ADMIN')
const currentTasks = computed<AdminUserTaskItem[]>(() => detail.value?.recentTasks ?? [])
const currentReports = computed<AdminUserReportItem[]>(() => detail.value?.recentReports ?? [])
const currentWarnings = computed<AdminUserWarningItem[]>(() => detail.value?.recentWarnings ?? [])

const userStats = computed(() => ({
  active: rows.value.filter((item) => item.status === 'ACTIVE').length,
  disabled: rows.value.filter((item) => item.status === 'DISABLED').length,
  testAccounts: rows.value.filter((item) => isTestAccount(item)).length,
  onlineSessions: rows.value.reduce((sum, item) => sum + Number(item.active_session_count || 0), 0),
}))

const adminStats = computed(() => ({
  total: adminRows.value.length,
  active: adminRows.value.filter((item) => item.status === 'ACTIVE').length,
  onlineSessions: adminRows.value.reduce((sum, item) => sum + Number(item.active_session_count || 0), 0),
}))

const selectedDisableable = computed(() =>
  selection.value.filter((item) => item.status === 'ACTIVE'),
)
const selectedIds = computed(() => selection.value.map((item) => item.id))

const isTestAccount = (row: Pick<AdminUserListItem, 'is_test_account'>) =>
  row.is_test_account === true || row.is_test_account === 1

const warningBreached = (row: AdminUserWarningItem) => row.breached === true || row.breached === 1
const statusTagType = (status?: string) => (status === 'ACTIVE' ? 'success' : 'warning')

const openRouteInNewTab = async (
  routeName: 'adminTaskInspect' | 'adminReportInspect',
  id?: number,
) => {
  if (!id) return
  const resolved = router.resolve({ name: routeName, params: { id } })
  window.open(resolved.href, '_blank', 'noopener,noreferrer')
}

const selectableRow = () => true

const syncDetailQuery = async (userId?: number) => {
  const currentQuery = { ...route.query }
  if (userId) {
    currentQuery.userId = String(userId)
  } else {
    delete currentQuery.userId
  }
  await router.replace({ query: currentQuery })
}

const loadUsers = async () => {
  loading.value = true
  errorState.value = null
  try {
    const keyword = query.keyword.trim() || undefined
    const status = query.status || undefined
    const [userResponse, adminResponse] = await Promise.all([
      getAdminUsers({
        page: query.page,
        pageSize: query.pageSize,
        keyword,
        role: 'USER',
        status,
        accountType: query.accountType,
      }),
      getAdminUsers({
        page: 1,
        pageSize: 20,
        keyword,
        role: 'ADMIN',
        status,
      }),
    ])

    rows.value = userResponse.items ?? []
    adminRows.value = adminResponse.items ?? []
    total.value = Number(userResponse.total ?? 0)
    systemActiveSessions.value = Number(userResponse.systemActiveSessions ?? 0)
    selection.value = []
  } catch (error) {
    errorState.value = parseError(error, '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

const loadUserDetail = async (userId: number) => {
  detailLoading.value = true
  detailError.value = null
  detailVisible.value = true
  try {
    detail.value = await getAdminUserDetail(userId)
  } catch (error) {
    detailError.value = parseError(error, '用户详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

const openUserDetail = async (userId: number) => {
  await syncDetailQuery(userId)
}

const closeDetail = async () => {
  detailVisible.value = false
  detail.value = null
  detailError.value = null
  await syncDetailQuery(undefined)
}

const refreshCurrentDetail = async () => {
  const userId = Number(route.query.userId)
  if (!Number.isFinite(userId) || userId <= 0) return
  await loadUserDetail(userId)
}

const toggleUserStatus = async (row: AdminUserListItem) => {
  if (row.role_code === 'ADMIN') {
    ElMessage.warning('管理员账号不能在这里停用')
    return
  }

  const nextStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionText = nextStatus === 'ACTIVE' ? '启用' : '停用'

  try {
    await ElMessageBox.confirm(
      `${actionText}用户 ${row.username} 后会立即影响该账号后续访问，确认继续吗？`,
      `${actionText}确认`,
      {
        type: nextStatus === 'ACTIVE' ? 'info' : 'warning',
        confirmButtonText: actionText,
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  actionLoadingId.value = row.id
  try {
    await updateAdminUserStatus(row.id, nextStatus)
    ElMessage.success(`已${actionText}用户 ${row.username}`)
    await loadUsers()
    if (currentProfile.value?.id === row.id) {
      await refreshCurrentDetail()
    }
  } catch (error) {
    const parsed = parseError(error, `${actionText}用户失败`)
    ElMessage.error(parsed.detail)
  } finally {
    actionLoadingId.value = null
  }
}

const handleSelectionChange = (rowsValue: AdminUserListItem[]) => {
  selection.value = rowsValue
}

const batchDisableSelected = async () => {
  const targets = selectedDisableable.value
  if (!targets.length) {
    ElMessage.warning('当前没有可批量停用的启用账号')
    return
  }

  const targetNames = targets.slice(0, 5).map((item) => item.username).join('、')
  try {
    await ElMessageBox.confirm(
      `将批量停用 ${targets.length} 个普通用户。${targetNames ? `示例：${targetNames}` : ''}`,
      '批量停用确认',
      {
        type: 'warning',
        confirmButtonText: '批量停用',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  batchLoading.value = true
  let successCount = 0
  const failedNames: string[] = []
  try {
    for (const row of targets) {
      try {
        await updateAdminUserStatus(row.id, 'DISABLED')
        successCount += 1
      } catch {
        failedNames.push(row.username)
      }
    }

    await loadUsers()
    if (currentProfile.value && targets.some((item) => item.id === currentProfile.value?.id)) {
      await refreshCurrentDetail()
    }

    if (successCount > 0 && failedNames.length === 0) {
      ElMessage.success(`已批量停用 ${successCount} 个普通用户`)
      return
    }
    if (successCount > 0) {
      ElMessage.warning(`已停用 ${successCount} 个普通用户，另有 ${failedNames.length} 个失败`)
      return
    }
    ElMessage.error('批量停用未成功执行')
  } finally {
    batchLoading.value = false
  }
}

const resetFilters = async () => {
  query.keyword = ''
  query.status = ''
  query.accountType = 'REAL_ONLY'
  query.page = 1
  await loadUsers()
}

const handlePageChange = async (page: number) => {
  query.page = page
  await loadUsers()
}

const handleSizeChange = async (pageSize: number) => {
  query.pageSize = pageSize
  query.page = 1
  await loadUsers()
}

let filterTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => [query.keyword, query.status, query.accountType],
  () => {
    if (filterTimer) clearTimeout(filterTimer)
    filterTimer = setTimeout(() => {
      query.page = 1
      void loadUsers()
    }, 180)
  },
)

watch(
  () => route.query.userId,
  (value) => {
    const userId = Number(value)
    if (!Number.isFinite(userId) || userId <= 0) {
      detailVisible.value = false
      detail.value = null
      detailError.value = null
      return
    }
    void loadUserDetail(userId)
  },
  { immediate: true },
)

onMounted(async () => {
  await loadUsers()
})

onBeforeUnmount(() => {
  if (filterTimer) {
    clearTimeout(filterTimer)
    filterTimer = null
  }
})
</script>

<template>
  <el-card shadow="hover">
    <template #header>
      <div class="header-row">
        <span>用户管理</span>
        <div class="header-actions">
          <el-button
            type="warning"
            plain
            :disabled="selectedDisableable.length === 0"
            :loading="batchLoading"
            @click="batchDisableSelected"
          >
            批量停用已选普通用户
          </el-button>
          <el-button @click="loadUsers">刷新</el-button>
        </div>
      </div>
    </template>

    <div class="stats-grid">
      <div class="stat-card">
        <span>当前筛选普通用户总数</span>
        <strong>{{ total }}</strong>
      </div>
      <div class="stat-card">
        <span>当前页启用普通用户</span>
        <strong>{{ userStats.active }}</strong>
      </div>
      <div class="stat-card">
        <span>当前页测试账号</span>
        <strong>{{ userStats.testAccounts }}</strong>
      </div>
      <div class="stat-card">
        <span>系统在线会话</span>
        <strong>{{ systemActiveSessions }}</strong>
      </div>
    </div>

    <div class="ops-hint">
      管理员账号单独成组展示，不再按普通用户任务、报告和预警口径解释。普通用户区域默认隐藏测试账号，可切换视图后批量停用。
    </div>

    <el-form inline class="filter-row">
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="用户 ID / 用户名"
          style="width: 220px"
        />
      </el-form-item>
      <el-form-item label="账号视图">
        <el-select v-model="query.accountType" style="width: 170px">
          <el-option
            v-for="item in accountTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" style="width: 140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="resetFilters">重置筛选</el-button>
      </el-form-item>
    </el-form>

    <div class="batch-note" v-if="selection.length">
      已选 {{ selection.length }} 个普通用户，ID：{{ selectedIds.join(', ') }}
    </div>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadUsers"
    />
    <template v-else>
      <section v-if="adminRows.length" class="group-block">
        <div class="section-header">
          <div>
            <h3>管理员账号</h3>
            <p>只展示账号状态、最近登录和在线会话，不沿用普通用户使用统计。</p>
          </div>
          <div class="section-badges">
            <span class="section-badge">管理员 {{ adminStats.total }}</span>
            <span class="section-badge">启用中 {{ adminStats.active }}</span>
            <span class="section-badge">在线会话 {{ adminStats.onlineSessions }}</span>
          </div>
        </div>

        <el-table :data="adminRows" border size="small" row-key="id">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column label="管理员账号" min-width="220">
            <template #default="scope">
              <el-button type="primary" link @click="openUserDetail(scope.row.id)">
                {{ scope.row.username }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="scope">
              <el-tag :type="statusTagType(scope.row.status)">
                {{ scope.row.status === 'ACTIVE' ? '启用中' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="created_at" label="注册时间" min-width="180" />
          <el-table-column label="最近登录" min-width="180">
            <template #default="scope">{{ scope.row.last_login_at || '-' }}</template>
          </el-table-column>
          <el-table-column prop="active_session_count" label="在线会话" width="110" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click="openUserDetail(scope.row.id)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="group-block">
        <div class="section-header">
          <div>
            <h3>普通用户</h3>
            <p>这里才展示任务、报告、预警和测试账号识别，用于日常用户运营与清理。</p>
          </div>
          <div class="section-badges">
            <span class="section-badge">当前页启用 {{ userStats.active }}</span>
            <span class="section-badge">当前页停用 {{ userStats.disabled }}</span>
            <span class="section-badge">当前页会话 {{ userStats.onlineSessions }}</span>
          </div>
        </div>

        <EmptyState
          v-if="rows.length === 0"
          title="暂无普通用户数据"
          description="当前筛选条件下没有找到普通用户记录。"
          action-text="重新加载"
          @action="loadUsers"
        />
        <template v-else>
          <el-table :data="rows" border row-key="id" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="50" :selectable="selectableRow" />
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column label="用户名" min-width="220" show-overflow-tooltip>
              <template #default="scope">
                <div class="user-cell">
                  <el-button type="primary" link @click="openUserDetail(scope.row.id)">
                    {{ scope.row.username }}
                  </el-button>
                  <el-tag v-if="isTestAccount(scope.row)" size="small" type="warning" effect="light">
                    测试账号
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="scope">
                <el-tag :type="statusTagType(scope.row.status)">
                  {{ scope.row.status === 'ACTIVE' ? '启用中' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="240">
              <template #default="scope">
                <div class="meta-cell">
                  <div class="meta-row">
                    <span class="meta-label">注册</span>
                    <span class="meta-value">{{ scope.row.created_at || '-' }}</span>
                  </div>
                  <div class="meta-row">
                    <span class="meta-label">登录</span>
                    <span class="meta-value">{{ scope.row.last_login_at || '-' }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="使用概览" min-width="240">
              <template #default="scope">
                <div class="summary-grid">
                  <span class="summary-pill">
                    <b>{{ scope.row.task_count }}</b>
                    <small>任务</small>
                  </span>
                  <span class="summary-pill">
                    <b>{{ scope.row.report_count }}</b>
                    <small>报告</small>
                  </span>
                  <span class="summary-pill">
                    <b>{{ scope.row.warning_count }}</b>
                    <small>预警</small>
                  </span>
                  <span class="summary-pill">
                    <b>{{ scope.row.active_session_count }}</b>
                    <small>会话</small>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="scope">
                <div class="action-cell">
                  <el-button type="primary" link @click="openUserDetail(scope.row.id)">查看详情</el-button>
                  <el-button
                    :type="scope.row.status === 'ACTIVE' ? 'danger' : 'success'"
                    link
                    :loading="actionLoadingId === scope.row.id"
                    @click="toggleUserStatus(scope.row)"
                  >
                    {{ scope.row.status === 'ACTIVE' ? '停用' : '启用' }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              :current-page="query.page"
              :page-size="query.pageSize"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </template>
      </section>
    </template>
  </el-card>

  <el-drawer
    :model-value="detailVisible"
    title="用户详情"
    size="48%"
    @close="closeDetail"
  >
    <LoadingState v-if="detailLoading" />
    <ErrorState
      v-else-if="detailError"
      :title="detailError.title"
      :detail="detailError.detail"
      :trace-id="detailError.traceId"
      @retry="refreshCurrentDetail"
    />
    <template v-else-if="currentProfile">
      <div class="detail-grid">
        <el-card shadow="never">
          <template #header>基本信息</template>
          <div class="detail-lines">
            <p><span>用户名</span><strong>{{ currentProfile.username }}</strong></p>
            <p><span>用户 ID</span><strong>#{{ currentProfile.id }}</strong></p>
            <p><span>角色</span><strong>{{ currentProfile.role_code }}</strong></p>
            <p>
              <span>账号类型</span>
              <strong>{{ isTestAccount(currentProfile) ? '测试账号' : isAdminProfile ? '管理账号' : '普通账号' }}</strong>
            </p>
            <p><span>状态</span><strong>{{ currentProfile.status === 'ACTIVE' ? '启用中' : '已停用' }}</strong></p>
            <p><span>注册时间</span><strong>{{ currentProfile.created_at || '-' }}</strong></p>
            <p><span>最近登录</span><strong>{{ currentProfile.last_login_at || '-' }}</strong></p>
          </div>
        </el-card>

        <el-card v-if="isAdminProfile" shadow="never">
          <template #header>管理账号概览</template>
          <div class="detail-stats single-stat">
            <div class="detail-stat">
              <span>在线会话</span>
              <strong>{{ currentProfile.active_session_count }}</strong>
            </div>
          </div>
          <p class="admin-note">
            管理员账号单独展示，不按普通用户任务、报告和预警口径解读。若存在历史调试残留数据，也不会作为管理画像的核心指标。
          </p>
        </el-card>

        <el-card v-else shadow="never">
          <template #header>使用概览</template>
          <div class="detail-stats">
            <div class="detail-stat">
              <span>任务</span>
              <strong>{{ currentProfile.task_count }}</strong>
            </div>
            <div class="detail-stat">
              <span>报告</span>
              <strong>{{ currentProfile.report_count }}</strong>
            </div>
            <div class="detail-stat">
              <span>预警</span>
              <strong>{{ currentProfile.warning_count }}</strong>
            </div>
            <div class="detail-stat">
              <span>在线会话</span>
              <strong>{{ currentProfile.active_session_count }}</strong>
            </div>
          </div>
        </el-card>
      </div>

      <template v-if="!isAdminProfile">
        <el-card shadow="never" class="section-card">
          <template #header>最近任务</template>
          <el-table v-if="currentTasks.length" :data="currentTasks" size="small" border>
            <el-table-column prop="id" label="任务 ID" width="90" />
            <el-table-column prop="audio_file_id" label="音频" width="90" />
            <el-table-column prop="original_name" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="120">
              <template #default="scope">{{ formatTaskStatus(scope.row.status) }}</template>
            </el-table-column>
            <el-table-column prop="created_at" label="创建时间" min-width="160" />
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <el-button type="primary" link @click="openRouteInNewTab('adminTaskInspect', scope.row.id)">
                  查看任务
                </el-button>
                <el-button
                  v-if="scope.row.report_id"
                  type="primary"
                  link
                  @click="openRouteInNewTab('adminReportInspect', scope.row.report_id)"
                >
                  查看报告
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <EmptyState
            v-else
            title="暂无任务记录"
            description="这个用户目前还没有任务数据。"
            action-text="刷新详情"
            @action="refreshCurrentDetail"
          />
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header>最近报告</template>
          <el-table v-if="currentReports.length" :data="currentReports" size="small" border>
            <el-table-column prop="id" label="报告 ID" width="90" />
            <el-table-column prop="task_id" label="任务" width="90" />
            <el-table-column prop="original_name" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column label="风险等级" width="120">
              <template #default="scope">{{ formatRiskLevel(scope.row.risk_level) }}</template>
            </el-table-column>
            <el-table-column label="主情绪" width="120">
              <template #default="scope">{{ formatEmotion(scope.row.overall_emotion) }}</template>
            </el-table-column>
            <el-table-column prop="created_at" label="生成时间" min-width="160" />
            <el-table-column label="操作" width="120">
              <template #default="scope">
                <el-button type="primary" link @click="openRouteInNewTab('adminReportInspect', scope.row.id)">
                  查看报告
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <EmptyState
            v-else
            title="暂无报告记录"
            description="这个用户目前还没有报告数据。"
            action-text="刷新详情"
            @action="refreshCurrentDetail"
          />
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header>最近预警</template>
          <el-table v-if="currentWarnings.length" :data="currentWarnings" size="small" border>
            <el-table-column prop="id" label="预警 ID" width="90" />
            <el-table-column label="风险等级" width="120">
              <template #default="scope">{{ formatRiskLevel(scope.row.risk_level) }}</template>
            </el-table-column>
            <el-table-column label="主情绪" width="120">
              <template #default="scope">{{ formatEmotion(scope.row.top_emotion) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="scope">{{ formatWarningStatus(scope.row.status) }}</template>
            </el-table-column>
            <el-table-column label="SLA" width="100">
              <template #default="scope">
                <el-tag :type="warningBreached(scope.row) ? 'danger' : 'info'">
                  {{ warningBreached(scope.row) ? '已超时' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="触发时间" min-width="160" />
            <el-table-column label="操作" width="200">
              <template #default="scope">
                <el-button
                  v-if="scope.row.task_id"
                  type="primary"
                  link
                  @click="openRouteInNewTab('adminTaskInspect', scope.row.task_id)"
                >
                  查看任务
                </el-button>
                <el-button
                  v-if="scope.row.report_id"
                  type="primary"
                  link
                  @click="openRouteInNewTab('adminReportInspect', scope.row.report_id)"
                >
                  查看报告
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <EmptyState
            v-else
            title="暂无预警记录"
            description="这个用户目前还没有预警事件。"
            action-text="刷新详情"
            @action="refreshCurrentDetail"
          />
        </el-card>
      </template>
    </template>
  </el-drawer>
</template>

<style scoped>
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.stats-grid {
  margin-bottom: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 14px 16px;
  border: 1px solid rgba(96, 119, 158, 0.16);
  border-radius: 14px;
  background: rgba(9, 18, 36, 0.32);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-card span,
.ops-hint,
.batch-note {
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.stat-card strong {
  color: var(--admin-text-primary);
  font-size: 28px;
  line-height: 1;
}

.ops-hint,
.filter-row,
.batch-note {
  margin-bottom: 16px;
}

.group-block + .group-block {
  margin-top: 20px;
}

.section-header {
  margin-bottom: 12px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.section-header h3 {
  margin: 0;
  color: var(--admin-text-primary);
  font-size: 16px;
}

.section-header p {
  margin: 6px 0 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.section-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.section-badge {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(96, 119, 158, 0.12);
  color: var(--admin-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.meta-label {
  width: 28px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  flex-shrink: 0;
}

.meta-value {
  min-width: 0;
  color: var(--admin-text-primary);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.summary-pill {
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(9, 18, 36, 0.36);
  border: 1px solid rgba(96, 119, 158, 0.12);
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.summary-pill b {
  color: var(--admin-text-primary);
  font-size: 16px;
}

.summary-pill small {
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.action-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.detail-grid {
  margin-bottom: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-lines {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-lines p {
  margin: 0;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--admin-text-primary);
}

.detail-lines span {
  color: var(--admin-text-secondary);
}

.detail-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-stats.single-stat {
  grid-template-columns: 1fr;
}

.detail-stat {
  padding: 14px;
  border-radius: 14px;
  background: rgba(9, 18, 36, 0.36);
  border: 1px solid rgba(96, 119, 158, 0.12);
}

.detail-stat span {
  display: block;
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.detail-stat strong {
  display: block;
  margin-top: 8px;
  color: var(--admin-text-primary);
  font-size: 28px;
}

.admin-note {
  margin: 12px 0 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.section-card + .section-card {
  margin-top: 16px;
}

@media (max-width: 1080px) {
  .section-header,
  .detail-grid {
    grid-template-columns: 1fr;
    display: grid;
  }

  .section-badges {
    justify-content: flex-start;
  }
}
</style>
