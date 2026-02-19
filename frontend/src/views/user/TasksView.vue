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

const router = useRouter()
const loading = ref(false)
const rows = ref<AnalysisTask[]>([])
const total = ref(0)
const errorState = ref<ErrorStatePayload | null>(null)
let refreshTimer: number | null = null
let isRequestInFlight = false
let queuedLoadMode: 'silent' | 'normal' | null = null

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
      errorState.value = parseError(error, 'Task list load failed')
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

const displayTaskNo = (row: AnalysisTask) => row.taskNo || `TASK-${row.id}`

const copyTaskNo = async (row: AnalysisTask) => {
  try {
    await navigator.clipboard.writeText(displayTaskNo(row))
    ElMessage.success('Task number copied')
  } catch {
    ElMessage.warning('Copy failed, please copy manually')
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
    <template #header>Task Center</template>
    <p class="auto-refresh-tip">Auto refresh runs every 5 seconds without request re-entry.</p>

    <el-form inline>
      <el-form-item label="Status">
        <el-select v-model="query.status" clearable style="width: 140px">
          <el-option label="PENDING" value="PENDING" />
          <el-option label="RUNNING" value="RUNNING" />
          <el-option label="RETRY_WAIT" value="RETRY_WAIT" />
          <el-option label="SUCCESS" value="SUCCESS" />
          <el-option label="FAILED" value="FAILED" />
          <el-option label="CANCELED" value="CANCELED" />
        </el-select>
      </el-form-item>
      <el-form-item label="Search">
        <el-input v-model="query.keyword" placeholder="Task No / 任务ID / traceId" clearable />
      </el-form-item>
      <el-form-item label="Sort">
        <el-select v-model="query.sortBy" style="width: 140px">
          <el-option label="Created" value="createdAt" />
          <el-option label="Updated" value="updatedAt" />
          <el-option label="Status" value="status" />
        </el-select>
        <el-select v-model="query.sortOrder" style="width: 100px; margin-left: 8px">
          <el-option label="Desc" value="desc" />
          <el-option label="Asc" value="asc" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="loadTasks">Query</el-button>
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
      title="No Tasks"
      description="No task records under current filter conditions."
      action-text="Refresh"
      @action="loadTasks"
    />
    <template v-else>
      <el-table :data="rows" border>
        <el-table-column label="Task No" min-width="260">
          <template #default="scope">
            <div class="task-no-cell">
              <div class="task-no-row">
                <strong>{{ displayTaskNo(scope.row) }}</strong>
                <el-button link type="primary" :icon="DocumentCopy" @click="copyTaskNo(scope.row)">Copy</el-button>
              </div>
              <span>任务ID: {{ scope.row.id }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="Status" width="120" />
        <el-table-column prop="attemptCount" label="Retries" width="100" />
        <el-table-column prop="traceId" label="traceId" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="Updated At" min-width="180" />
        <el-table-column label="Actions" width="200">
          <template #default="scope">
            <el-button link type="primary" @click="router.push(`/app/tasks/${scope.row.id}`)">Detail</el-button>
            <el-button link type="primary" @click="router.push(`/app/tasks/${scope.row.id}/timeline`)">Timeline</el-button>
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
