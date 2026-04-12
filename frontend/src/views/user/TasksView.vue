<script setup lang="ts">
import { DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getTaskList, type AnalysisTask, type TaskStatus } from '@/api/task'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import {
  TASK_STATUS_LABELS,
  TRACE_ID_LABEL,
  formatSortOrder,
  formatTaskSortBy,
  formatTaskStatus,
} from '@/utils/uiText'

const router = useRouter()
const loading = ref(false)
const rows = ref<AnalysisTask[]>([])
const total = ref(0)
const errorState = ref<ErrorStatePayload | null>(null)
let refreshTimer: number | null = null
let isRequestInFlight = false
let queuedLoadMode: 'silent' | 'normal' | null = null

const statusOptions: Array<{ label: string; value: TaskStatus }> = Object.entries(TASK_STATUS_LABELS).map(
  ([value, label]) => ({ label, value: value as TaskStatus }),
)

const query = reactive({
  page: 1,
  pageSize: 10,
  status: '' as TaskStatus | '',
  keyword: '',
  sortBy: 'createdAt' as 'createdAt' | 'updatedAt' | 'status',
  sortOrder: 'desc' as 'asc' | 'desc',
})

const queueLoad = (silent: boolean) => {
  if (queuedLoadMode === 'normal') return
  queuedLoadMode = silent ? 'silent' : 'normal'
}

const loadTasks = async (options: { silent?: boolean } = {}) => {
  const silent = options.silent === true

  if (isRequestInFlight) {
    queueLoad(silent)
    if (!silent) {
      loading.value = true
      errorState.value = null
    }
    return
  }

  isRequestInFlight = true
  if (!silent) {
    loading.value = true
    errorState.value = null
  }

  try {
    const { data } = await getTaskList(query)
    rows.value = data.items ?? data.list ?? []
    total.value = data.total
    if (!silent) {
      errorState.value = null
    }
  } catch (error) {
    if (!silent) {
      errorState.value = parseError(error, '任务列表加载失败')
    }
  } finally {
    if (!silent) {
      loading.value = false
    }
    isRequestInFlight = false

    const nextMode = queuedLoadMode
    queuedLoadMode = null
    if (nextMode) {
      void loadTasks({ silent: nextMode === 'silent' })
    }
  }
}

const displayTaskNo = (row: AnalysisTask) => row.taskNo || `任务-${row.id}`

const copyTaskNo = async (row: AnalysisTask) => {
  try {
    await navigator.clipboard.writeText(displayTaskNo(row))
    ElMessage.success('任务编号已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

onMounted(() => {
  void loadTasks()
  refreshTimer = window.setInterval(() => {
    void loadTasks({ silent: true })
  }, 5000)
})

onUnmounted(() => {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <el-card>
    <template #header>任务中心</template>
    <p class="auto-refresh-tip">这里会自动刷新，方便你查看任务进度。</p>

    <el-form inline>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width: 140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="搜索">
        <el-input v-model="query.keyword" placeholder="任务编号 / 任务 ID" clearable />
      </el-form-item>
      <el-form-item label="排序">
        <el-select v-model="query.sortBy" style="width: 140px">
          <el-option :label="formatTaskSortBy('createdAt')" value="createdAt" />
          <el-option :label="formatTaskSortBy('updatedAt')" value="updatedAt" />
          <el-option :label="formatTaskSortBy('status')" value="status" />
        </el-select>
        <el-select v-model="query.sortOrder" style="width: 100px; margin-left: 8px">
          <el-option :label="formatSortOrder('desc')" value="desc" />
          <el-option :label="formatSortOrder('asc')" value="asc" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="loadTasks">查询</el-button>
    </el-form>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadTasks"
    />
    <EmptyState
      v-else-if="rows.length === 0"
      title="暂无任务"
      description="当前筛选下还没有任务。"
      action-text="刷新列表"
      @action="loadTasks"
    />
    <template v-else>
      <el-table :data="rows" border>
        <el-table-column label="任务编号" min-width="260">
          <template #default="scope">
            <div class="task-no-cell">
              <div class="task-no-row">
                <strong>{{ displayTaskNo(scope.row) }}</strong>
                <el-button link type="primary" :icon="DocumentCopy" @click="copyTaskNo(scope.row)">复制</el-button>
              </div>
              <span>任务 ID：{{ scope.row.id }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="scope">{{ formatTaskStatus(scope.row.status) }}</template>
        </el-table-column>
        <el-table-column prop="attemptCount" label="重试次数" width="100" />
        <el-table-column prop="traceId" :label="TRACE_ID_LABEL" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button link type="primary" @click="router.push(`/app/tasks/${scope.row.id}`)">查看详情</el-button>
            <el-button link type="primary" @click="router.push(`/app/tasks/${scope.row.id}/timeline`)">查看时间线</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @change="loadTasks"
        />
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.task-no-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.task-no-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.task-no-cell span {
  font-size: 12px;
  color: #64748b;
}

.auto-refresh-tip {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
