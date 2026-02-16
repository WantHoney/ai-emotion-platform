<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { getWarnings, postWarningAction, type WarningEventItem } from '@/api/governance'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const rows = ref<WarningEventItem[]>([])
const total = ref(0)
const errorState = ref<ErrorStatePayload | null>(null)

const query = reactive({
  page: 1,
  pageSize: 10,
  status: '',
  riskLevel: '',
})

const loadWarnings = async () => {
  loading.value = true
  errorState.value = null
  try {
    const response = await getWarnings({
      page: query.page,
      pageSize: query.pageSize,
      status: query.status || undefined,
      riskLevel: query.riskLevel || undefined,
    })
    rows.value = response.items ?? []
    total.value = response.total ?? 0
  } catch (error) {
    errorState.value = parseError(error, 'Failed to load warning events')
  } finally {
    loading.value = false
  }
}

const riskType = (riskLevel: string) => {
  const value = riskLevel.toUpperCase()
  if (value === 'HIGH') return 'danger'
  if (value === 'MEDIUM') return 'warning'
  if (value === 'LOW') return 'success'
  return 'info'
}

const submitAction = async (
  row: WarningEventItem,
  actionType: string,
  nextStatus: string,
  title: string,
  notePlaceholder: string,
) => {
  try {
    const promptRes = await ElMessageBox.prompt('Input action note (optional)', title, {
      inputType: 'textarea',
      inputPlaceholder: notePlaceholder,
      confirmButtonText: 'Submit',
      cancelButtonText: 'Cancel',
      showInput: true,
    })
    if (typeof promptRes === 'string') {
      return
    }

    await postWarningAction(row.id, {
      actionType,
      actionNote: promptRes.value || undefined,
      templateCode: row.risk_level === 'HIGH' ? 'WARN_HIGH_FOLLOWUP' : undefined,
      nextStatus,
    })
    ElMessage.success('Action submitted')
    await loadWarnings()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    const parsed = parseError(error, 'Failed to submit warning action')
    ElMessage.error(parsed.detail)
  }
}

const markAcked = async (row: WarningEventItem) => {
  await submitAction(row, 'MARK_FOLLOWED', 'ACKED', 'Mark As ACKED', 'e.g. operator acknowledged')
}

const markFollowing = async (row: WarningEventItem) => {
  await submitAction(row, 'ADD_NOTE', 'FOLLOWING', 'Mark As FOLLOWING', 'e.g. follow-up in progress')
}

const markResolved = async (row: WarningEventItem) => {
  await submitAction(row, 'RESOLVE', 'RESOLVED', 'Mark As RESOLVED', 'e.g. callback completed')
}

onMounted(async () => {
  await loadWarnings()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>Warning Disposal Desk</span>
        <el-button @click="loadWarnings">Refresh</el-button>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="Status">
        <el-select v-model="query.status" clearable style="width: 130px">
          <el-option label="NEW" value="NEW" />
          <el-option label="ACKED" value="ACKED" />
          <el-option label="FOLLOWING" value="FOLLOWING" />
          <el-option label="RESOLVED" value="RESOLVED" />
          <el-option label="CLOSED" value="CLOSED" />
        </el-select>
      </el-form-item>
      <el-form-item label="Risk Level">
        <el-select v-model="query.riskLevel" clearable style="width: 130px">
          <el-option label="LOW" value="LOW" />
          <el-option label="MEDIUM" value="MEDIUM" />
          <el-option label="HIGH" value="HIGH" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="loadWarnings">Search</el-button>
    </el-form>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadWarnings"
    />
    <EmptyState
      v-else-if="rows.length === 0"
      title="No warning events"
      description="No warning event matches the selected filters."
      action-text="Reload"
      @action="loadWarnings"
    />
    <template v-else>
      <el-table :data="rows" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="user_mask" label="User Mask" width="120" />
        <el-table-column prop="task_id" label="Task" width="100" />
        <el-table-column prop="report_id" label="Report" width="100" />
        <el-table-column prop="top_emotion" label="Top Emotion" width="120" />
        <el-table-column label="Risk" width="120">
          <template #default="scope">
            <el-tag :type="riskType(scope.row.risk_level)">{{ scope.row.risk_level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="risk_score" label="Risk Score" width="100" />
        <el-table-column prop="status" label="Status" width="110" />
        <el-table-column prop="created_at" label="Created At" min-width="180" />
        <el-table-column label="Actions" min-width="260">
          <template #default="scope">
            <el-button
              type="primary"
              link
              :disabled="scope.row.status !== 'NEW'"
              @click="markAcked(scope.row)"
            >
              ACK
            </el-button>
            <el-button
              type="warning"
              link
              :disabled="scope.row.status === 'RESOLVED' || scope.row.status === 'CLOSED'"
              @click="markFollowing(scope.row)"
            >
              Follow
            </el-button>
            <el-button
              type="success"
              link
              :disabled="scope.row.status === 'RESOLVED' || scope.row.status === 'CLOSED'"
              @click="markResolved(scope.row)"
            >
              Resolve
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @change="loadWarnings"
        />
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
