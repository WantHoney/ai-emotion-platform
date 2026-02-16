<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  getWarningActions,
  getWarnings,
  postWarningAction,
  type WarningActionItem,
  type WarningEventItem,
} from '@/api/governance'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const rows = ref<WarningEventItem[]>([])
const total = ref(0)
const errorState = ref<ErrorStatePayload | null>(null)

const actionsLoading = ref(false)
const actionsError = ref<ErrorStatePayload | null>(null)
const actionsDrawer = ref(false)
const currentWarning = ref<WarningEventItem | null>(null)
const actionRows = ref<WarningActionItem[]>([])

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

const loadActions = async (warningId: number) => {
  actionsLoading.value = true
  actionsError.value = null
  try {
    actionRows.value = await getWarningActions(warningId)
  } catch (error) {
    actionsError.value = parseError(error, 'Failed to load warning action timeline')
  } finally {
    actionsLoading.value = false
  }
}

const riskType = (riskLevel: string) => {
  const value = riskLevel.toUpperCase()
  if (value === 'HIGH') return 'danger'
  if (value === 'MEDIUM') return 'warning'
  if (value === 'LOW') return 'success'
  return 'info'
}

const breachedType = (row: WarningEventItem) => {
  const breached = row.breached === true || row.breached === 1
  if (breached) return 'danger'
  if (row.status === 'RESOLVED' || row.status === 'CLOSED') return 'success'
  return 'info'
}

const formatDeadlineGap = (deadline?: string) => {
  if (!deadline) return '-'
  const time = new Date(deadline).getTime()
  if (Number.isNaN(time)) return '-'
  const diff = time - Date.now()
  const absMin = Math.round(Math.abs(diff) / 60000)
  const h = Math.floor(absMin / 60)
  const m = absMin % 60
  return diff >= 0 ? `T-${h}h ${m}m` : `T+${h}h ${m}m`
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
    if (currentWarning.value?.id === row.id) {
      await loadActions(row.id)
    }
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

const openTimeline = async (row: WarningEventItem) => {
  currentWarning.value = row
  actionsDrawer.value = true
  await loadActions(row.id)
}

const currentSummary = computed(() => {
  if (!currentWarning.value) return '-'
  return `Risk=${currentWarning.value.risk_level}, Score=${currentWarning.value.risk_score}, Status=${currentWarning.value.status}`
})

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
        <el-table-column label="SLA" min-width="180">
          <template #default="scope">
            <el-tag :type="breachedType(scope.row)">
              {{ scope.row.breached === true || scope.row.breached === 1 ? 'BREACHED' : 'IN SLA' }}
            </el-tag>
            <div class="sla-gap">{{ formatDeadlineGap(scope.row.sla_deadline_at) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="Created At" min-width="170" />
        <el-table-column label="Actions" min-width="320">
          <template #default="scope">
            <el-button type="primary" link @click="openTimeline(scope.row)">Timeline</el-button>
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

    <el-drawer v-model="actionsDrawer" title="Warning Timeline" size="40%">
      <p class="timeline-summary">{{ currentSummary }}</p>

      <LoadingState v-if="actionsLoading" />
      <ErrorState
        v-else-if="actionsError"
        :title="actionsError.title"
        :detail="actionsError.detail"
        :trace-id="actionsError.traceId"
        @retry="currentWarning && loadActions(currentWarning.id)"
      />
      <EmptyState
        v-else-if="actionRows.length === 0"
        title="No action records"
        description="No disposal timeline yet for this warning event."
        action-text="Reload"
        @action="currentWarning && loadActions(currentWarning.id)"
      />
      <el-timeline v-else>
        <el-timeline-item
          v-for="item in actionRows"
          :key="item.id"
          :timestamp="item.created_at"
          placement="top"
        >
          <el-card>
            <p><strong>{{ item.action_type }}</strong></p>
            <p v-if="item.action_note">{{ item.action_note }}</p>
            <p v-if="item.template_code">template={{ item.template_code }}</p>
            <p v-if="item.operator_id">operator={{ item.operator_id }}</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </el-card>
</template>

<style scoped>
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sla-gap {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.timeline-summary {
  margin: 0 0 12px;
  color: #334155;
}
</style>
