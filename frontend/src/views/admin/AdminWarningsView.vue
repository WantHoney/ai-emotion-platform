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
    errorState.value = parseError(error, '预警事件加载失败')
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
    actionsError.value = parseError(error, '预警处置时间线加载失败')
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
    const promptRes = await ElMessageBox.prompt('请输入处置备注（可选）', title, {
      inputType: 'textarea',
      inputPlaceholder: notePlaceholder,
      confirmButtonText: '提交',
      cancelButtonText: '取消',
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
    ElMessage.success('处置动作已提交')
    await loadWarnings()
    if (currentWarning.value?.id === row.id) {
      await loadActions(row.id)
    }
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    const parsed = parseError(error, '提交处置动作失败')
    ElMessage.error(parsed.detail)
  }
}

const markAcked = async (row: WarningEventItem) => {
  await submitAction(row, 'MARK_FOLLOWED', 'ACKED', '标记为 ACKED', '例如：值班人员已确认')
}

const markFollowing = async (row: WarningEventItem) => {
  await submitAction(row, 'ADD_NOTE', 'FOLLOWING', '标记为 FOLLOWING', '例如：正在跟进回访')
}

const markResolved = async (row: WarningEventItem) => {
  await submitAction(row, 'RESOLVE', 'RESOLVED', '标记为 RESOLVED', '例如：回访完成并结案')
}

const openTimeline = async (row: WarningEventItem) => {
  currentWarning.value = row
  actionsDrawer.value = true
  await loadActions(row.id)
}

const currentSummary = computed(() => {
  if (!currentWarning.value) return '-'
  return `风险=${currentWarning.value.risk_level}，评分=${currentWarning.value.risk_score}，状态=${currentWarning.value.status}`
})

onMounted(async () => {
  await loadWarnings()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>预警处置台</span>
        <el-button @click="loadWarnings">刷新</el-button>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width: 130px">
          <el-option label="NEW" value="NEW" />
          <el-option label="ACKED" value="ACKED" />
          <el-option label="FOLLOWING" value="FOLLOWING" />
          <el-option label="RESOLVED" value="RESOLVED" />
          <el-option label="CLOSED" value="CLOSED" />
        </el-select>
      </el-form-item>
      <el-form-item label="风险等级">
        <el-select v-model="query.riskLevel" clearable style="width: 130px">
          <el-option label="LOW" value="LOW" />
          <el-option label="MEDIUM" value="MEDIUM" />
          <el-option label="HIGH" value="HIGH" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="loadWarnings">查询</el-button>
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
      title="暂无预警事件"
      description="当前筛选条件下未匹配到预警事件。"
      action-text="重新加载"
      @action="loadWarnings"
    />
    <template v-else>
      <el-table :data="rows" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="user_mask" label="用户脱敏标识" width="120" />
        <el-table-column prop="task_id" label="任务" width="100" />
        <el-table-column prop="report_id" label="报告" width="100" />
        <el-table-column prop="top_emotion" label="主情绪" width="120" />
        <el-table-column label="风险" width="120">
          <template #default="scope">
            <el-tag :type="riskType(scope.row.risk_level)">{{ scope.row.risk_level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="risk_score" label="风险分" width="100" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="SLA" min-width="180">
          <template #default="scope">
            <el-tag :type="breachedType(scope.row)">
              {{ scope.row.breached === true || scope.row.breached === 1 ? '已超时' : 'SLA 内' }}
            </el-tag>
            <div class="sla-gap">{{ formatDeadlineGap(scope.row.sla_deadline_at) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" min-width="170" />
        <el-table-column label="操作" min-width="320">
          <template #default="scope">
            <el-button type="primary" link @click="openTimeline(scope.row)">时间线</el-button>
            <el-button
              type="primary"
              link
              :disabled="scope.row.status !== 'NEW'"
              @click="markAcked(scope.row)"
            >
              确认
            </el-button>
            <el-button
              type="warning"
              link
              :disabled="scope.row.status === 'RESOLVED' || scope.row.status === 'CLOSED'"
              @click="markFollowing(scope.row)"
            >
              跟进
            </el-button>
            <el-button
              type="success"
              link
              :disabled="scope.row.status === 'RESOLVED' || scope.row.status === 'CLOSED'"
              @click="markResolved(scope.row)"
            >
              结案
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

    <el-drawer v-model="actionsDrawer" title="预警处置时间线" size="40%">
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
        title="暂无处置记录"
        description="该预警事件尚未产生处置时间线。"
        action-text="重新加载"
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
            <p v-if="item.template_code">模板编码：{{ item.template_code }}</p>
            <p v-if="item.operator_id">操作人：{{ item.operator_id }}</p>
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
