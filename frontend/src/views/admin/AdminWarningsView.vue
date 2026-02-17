<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
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

type WarningStatus = '' | 'NEW' | 'ACKED' | 'FOLLOWING' | 'RESOLVED' | 'CLOSED'
type RiskLevel = '' | 'LOW' | 'MEDIUM' | 'HIGH'
type BreachedFilter = '' | 'Y' | 'N'

type WarningTimelineNode = {
  key: string
  title: string
  timestamp?: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  note?: string
}

const loading = ref(false)
const rows = ref<WarningEventItem[]>([])
const total = ref(0)
const errorState = ref<ErrorStatePayload | null>(null)
const selection = ref<WarningEventItem[]>([])
const batchLoading = ref(false)

const actionsLoading = ref(false)
const actionsError = ref<ErrorStatePayload | null>(null)
const actionsDrawer = ref(false)
const currentWarning = ref<WarningEventItem | null>(null)
const actionRows = ref<WarningActionItem[]>([])

const query = reactive({
  page: 1,
  pageSize: 10,
  status: '' as WarningStatus,
  riskLevel: '' as RiskLevel,
  breached: '' as BreachedFilter,
  keyword: '',
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: 'NEW', value: 'NEW' },
  { label: 'ACKED', value: 'ACKED' },
  { label: 'FOLLOWING', value: 'FOLLOWING' },
  { label: 'RESOLVED', value: 'RESOLVED' },
  { label: 'CLOSED', value: 'CLOSED' },
]

const riskOptions = [
  { label: '全部风险', value: '' },
  { label: 'LOW', value: 'LOW' },
  { label: 'MEDIUM', value: 'MEDIUM' },
  { label: 'HIGH', value: 'HIGH' },
]

const flowLabels = ['新建', '已确认', '跟进中', '已结案']

const isBreached = (row: WarningEventItem) => row.breached === true || row.breached === 1
const isClosed = (row: WarningEventItem) => row.status === 'RESOLVED' || row.status === 'CLOSED'

const canAck = (row: WarningEventItem) => row.status === 'NEW'
const canFollow = (row: WarningEventItem) => !isClosed(row)
const canResolve = (row: WarningEventItem) => !isClosed(row)

const filteredRows = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (query.breached === 'Y' && !isBreached(row)) return false
    if (query.breached === 'N' && isBreached(row)) return false
    if (!keyword) return true

    const fields = [
      String(row.id ?? ''),
      String(row.user_mask ?? ''),
      String(row.task_id ?? ''),
      String(row.report_id ?? ''),
      String(row.top_emotion ?? ''),
      String(row.status ?? ''),
      String(row.risk_level ?? ''),
    ].map((v) => v.toLowerCase())

    return fields.some((value) => value.includes(keyword))
  })
})

const selectionIds = computed(() => selection.value.map((row) => row.id))
const selectedCount = computed(() => selection.value.length)
const selectedAckable = computed(() => selection.value.filter(canAck))
const selectedFollowable = computed(() => selection.value.filter(canFollow))
const selectedResolvable = computed(() => selection.value.filter(canResolve))

const currentFlowStep = computed(() => {
  const status = currentWarning.value?.status
  if (status === 'ACKED') return 1
  if (status === 'FOLLOWING') return 2
  if (status === 'RESOLVED' || status === 'CLOSED') return 3
  return 0
})

const currentSummary = computed(() => {
  if (!currentWarning.value) return '-'
  return `风险=${currentWarning.value.risk_level}，评分=${currentWarning.value.risk_score}，状态=${currentWarning.value.status}`
})

const currentTimelineNodes = computed<WarningTimelineNode[]>(() => {
  if (!currentWarning.value) return []
  const row = currentWarning.value
  const nodes: WarningTimelineNode[] = [
    {
      key: `created-${row.id}`,
      title: '预警触发',
      timestamp: row.created_at,
      type: 'primary',
      note: `风险 ${row.risk_level} / 分数 ${row.risk_score}`,
    },
  ]

  if (row.first_acked_at) {
    nodes.push({
      key: `acked-${row.id}`,
      title: '首次确认',
      timestamp: row.first_acked_at,
      type: 'warning',
      note: '值班人员已确认',
    })
  }

  if (row.first_followed_at) {
    nodes.push({
      key: `followed-${row.id}`,
      title: '首次跟进',
      timestamp: row.first_followed_at,
      type: 'warning',
      note: '已进入跟进流程',
    })
  }

  if (row.resolved_at) {
    nodes.push({
      key: `resolved-${row.id}`,
      title: '结案完成',
      timestamp: row.resolved_at,
      type: 'success',
      note: '预警处置闭环完成',
    })
  }

  if (row.sla_deadline_at) {
    nodes.push({
      key: `deadline-${row.id}`,
      title: 'SLA 截止',
      timestamp: row.sla_deadline_at,
      type: isBreached(row) ? 'danger' : 'info',
      note: isBreached(row) ? '已发生超时' : '当前处于 SLA 期内',
    })
  }

  const actionItems = [...actionRows.value].sort((a, b) => {
    const timeA = new Date(a.created_at ?? '').getTime()
    const timeB = new Date(b.created_at ?? '').getTime()
    if (Number.isNaN(timeA) || Number.isNaN(timeB)) return 0
    return timeA - timeB
  })

  actionItems.forEach((item) => {
    const actionType = item.action_type?.toUpperCase() ?? 'ACTION'
    nodes.push({
      key: `action-${item.id}`,
      title: `动作：${actionType}`,
      timestamp: item.created_at,
      type: actionType === 'RESOLVE' ? 'success' : 'primary',
      note: item.action_note || item.template_code || undefined,
    })
  })

  return nodes
})

const riskType = (riskLevel: string) => {
  const value = riskLevel.toUpperCase()
  if (value === 'HIGH') return 'danger'
  if (value === 'MEDIUM') return 'warning'
  if (value === 'LOW') return 'success'
  return 'info'
}

const breachedType = (row: WarningEventItem) => {
  if (isBreached(row)) return 'danger'
  if (isClosed(row)) return 'success'
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
    selection.value = []
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

const handleSelectionChange = (rowsValue: WarningEventItem[]) => {
  selection.value = rowsValue
}

const openTimeline = async (row: WarningEventItem) => {
  currentWarning.value = row
  actionsDrawer.value = true
  await loadActions(row.id)
}

const promptActionNote = async (title: string, placeholder: string) => {
  const promptRes = await ElMessageBox.prompt('请输入处置备注（可选）', title, {
    inputType: 'textarea',
    inputPlaceholder: placeholder,
    confirmButtonText: '提交',
    cancelButtonText: '取消',
    showInput: true,
  })
  if (typeof promptRes === 'string') {
    return undefined
  }
  return (promptRes.value || '').trim() || undefined
}

const runAction = async (
  targetRows: WarningEventItem[],
  options: {
    actionType: string
    nextStatus: string
    title: string
    placeholder: string
  },
) => {
  if (!targetRows.length) {
    ElMessage.warning('请先选择至少一条预警事件')
    return
  }

  try {
    const note = await promptActionNote(options.title, options.placeholder)
    batchLoading.value = true
    const tasks = targetRows.map((row) =>
      postWarningAction(row.id, {
        actionType: options.actionType,
        actionNote: note,
        templateCode: row.risk_level === 'HIGH' ? 'WARN_HIGH_FOLLOWUP' : undefined,
        nextStatus: options.nextStatus,
      }),
    )
    const result = await Promise.allSettled(tasks)
    const successCount = result.filter((item) => item.status === 'fulfilled').length
    const failCount = result.length - successCount

    if (successCount > 0 && failCount === 0) {
      ElMessage.success(`已完成 ${successCount} 条处置动作`)
    } else if (successCount > 0) {
      ElMessage.warning(`已完成 ${successCount} 条，失败 ${failCount} 条`)
    } else {
      ElMessage.error('批量处置失败，请检查网络或权限')
    }

    await loadWarnings()
    if (currentWarning.value && targetRows.some((row) => row.id === currentWarning.value?.id)) {
      await loadActions(currentWarning.value.id)
    }
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    const parsed = parseError(error, '提交处置动作失败')
    ElMessage.error(parsed.detail)
  } finally {
    batchLoading.value = false
  }
}

const markAcked = async (row: WarningEventItem) => {
  if (!canAck(row)) {
    ElMessage.warning('仅 NEW 状态可执行确认')
    return
  }
  await runAction([row], {
    actionType: 'MARK_FOLLOWED',
    nextStatus: 'ACKED',
    title: `确认预警 #${row.id}`,
    placeholder: '例如：值班人员已确认',
  })
}

const markFollowing = async (row: WarningEventItem) => {
  if (!canFollow(row)) {
    ElMessage.warning('当前状态不可执行跟进')
    return
  }
  await runAction([row], {
    actionType: 'ADD_NOTE',
    nextStatus: 'FOLLOWING',
    title: `跟进预警 #${row.id}`,
    placeholder: '例如：已外呼并安排回访',
  })
}

const markResolved = async (row: WarningEventItem) => {
  if (!canResolve(row)) {
    ElMessage.warning('当前状态不可执行结案')
    return
  }
  await runAction([row], {
    actionType: 'RESOLVE',
    nextStatus: 'RESOLVED',
    title: `结案预警 #${row.id}`,
    placeholder: '例如：回访完成，用户状态稳定',
  })
}

const batchAck = async () => {
  const rowsValue = selectedAckable.value
  if (!rowsValue.length) {
    ElMessage.warning('所选项中无可确认事件（仅 NEW 状态可确认）')
    return
  }
  await runAction(rowsValue, {
    actionType: 'MARK_FOLLOWED',
    nextStatus: 'ACKED',
    title: `批量确认（${rowsValue.length} 条）`,
    placeholder: '批量处置备注（可选）',
  })
}

const batchFollow = async () => {
  const rowsValue = selectedFollowable.value
  if (!rowsValue.length) {
    ElMessage.warning('所选项中无可跟进事件')
    return
  }
  await runAction(rowsValue, {
    actionType: 'ADD_NOTE',
    nextStatus: 'FOLLOWING',
    title: `批量跟进（${rowsValue.length} 条）`,
    placeholder: '批量跟进备注（可选）',
  })
}

const batchResolve = async () => {
  const rowsValue = selectedResolvable.value
  if (!rowsValue.length) {
    ElMessage.warning('所选项中无可结案事件')
    return
  }
  await runAction(rowsValue, {
    actionType: 'RESOLVE',
    nextStatus: 'RESOLVED',
    title: `批量结案（${rowsValue.length} 条）`,
    placeholder: '批量结案备注（可选）',
  })
}

const resetFilters = async () => {
  query.status = ''
  query.riskLevel = ''
  query.breached = ''
  query.keyword = ''
  query.page = 1
  await loadWarnings()
}

const handlePageChange = async (page: number) => {
  query.page = page
  await loadWarnings()
}

const handleSizeChange = async (size: number) => {
  query.pageSize = size
  query.page = 1
  await loadWarnings()
}

let filterTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => [query.status, query.riskLevel],
  () => {
    if (filterTimer) clearTimeout(filterTimer)
    filterTimer = setTimeout(() => {
      query.page = 1
      void loadWarnings()
    }, 150)
  },
)

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

    <div class="filter-row">
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="query.riskLevel" style="width: 150px">
            <el-option v-for="item in riskOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="SLA状态">
          <el-radio-group v-model="query.breached">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="N">SLA 内</el-radio-button>
            <el-radio-button label="Y">已超时</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="ID/用户标识/任务/报告/情绪"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">重置筛选</el-button>
        </el-form-item>
      </el-form>
    </div>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadWarnings"
    />
    <EmptyState
      v-else-if="filteredRows.length === 0"
      title="暂无预警事件"
      description="当前筛选条件下未匹配到预警事件。"
      action-text="重新加载"
      @action="loadWarnings"
    />
    <template v-else>
      <div class="batch-bar">
        <span>已选 {{ selectedCount }} 条</span>
        <el-button
          type="primary"
          plain
          :loading="batchLoading"
          :disabled="selectedAckable.length === 0"
          @click="batchAck"
        >
          批量确认
        </el-button>
        <el-button
          type="warning"
          plain
          :loading="batchLoading"
          :disabled="selectedFollowable.length === 0"
          @click="batchFollow"
        >
          批量跟进
        </el-button>
        <el-button
          type="success"
          plain
          :loading="batchLoading"
          :disabled="selectedResolvable.length === 0"
          @click="batchResolve"
        >
          批量结案
        </el-button>
        <span class="batch-note">选中ID：{{ selectionIds.join(', ') || '-' }}</span>
      </div>

      <el-table :data="filteredRows" border row-key="id" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="user_mask" label="用户脱敏标识" width="120" />
        <el-table-column prop="task_id" label="任务" width="90" />
        <el-table-column prop="report_id" label="报告" width="90" />
        <el-table-column prop="top_emotion" label="主情绪" width="100" />
        <el-table-column label="风险" width="110">
          <template #default="scope">
            <el-tag :type="riskType(scope.row.risk_level)">{{ scope.row.risk_level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="risk_score" label="风险分" width="90" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="SLA" min-width="170">
          <template #default="scope">
            <el-tag :type="breachedType(scope.row)">
              {{ isBreached(scope.row) ? '已超时' : 'SLA 内' }}
            </el-tag>
            <div class="sla-gap">{{ formatDeadlineGap(scope.row.sla_deadline_at) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" min-width="165" />
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="scope">
            <el-button type="primary" link @click="openTimeline(scope.row)">时间线</el-button>
            <el-button type="primary" link :disabled="!canAck(scope.row)" @click="markAcked(scope.row)">确认</el-button>
            <el-button type="warning" link :disabled="!canFollow(scope.row)" @click="markFollowing(scope.row)">跟进</el-button>
            <el-button type="success" link :disabled="!canResolve(scope.row)" @click="markResolved(scope.row)">结案</el-button>
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

    <el-drawer v-model="actionsDrawer" title="预警处置时间线" size="42%">
      <p class="timeline-summary">{{ currentSummary }}</p>
      <el-steps :active="currentFlowStep" finish-status="success" simple class="flow-steps">
        <el-step v-for="item in flowLabels" :key="item" :title="item" />
      </el-steps>

      <LoadingState v-if="actionsLoading" />
      <ErrorState
        v-else-if="actionsError"
        :title="actionsError.title"
        :detail="actionsError.detail"
        :trace-id="actionsError.traceId"
        @retry="currentWarning && loadActions(currentWarning.id)"
      />
      <EmptyState
        v-else-if="currentTimelineNodes.length === 0"
        title="暂无处置记录"
        description="该预警事件尚未产生处置时间线。"
        action-text="重新加载"
        @action="currentWarning && loadActions(currentWarning.id)"
      />
      <el-timeline v-else>
        <el-timeline-item
          v-for="item in currentTimelineNodes"
          :key="item.key"
          :timestamp="item.timestamp"
          :type="item.type"
          placement="top"
        >
          <el-card shadow="never">
            <p class="timeline-title">{{ item.title }}</p>
            <p v-if="item.note" class="timeline-note">{{ item.note }}</p>
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

.filter-row {
  margin-bottom: 12px;
}

.batch-bar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.batch-note {
  color: #64748b;
  font-size: 12px;
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
  margin: 0 0 10px;
  color: #334155;
}

.flow-steps {
  margin-bottom: 16px;
}

.timeline-title {
  margin: 0;
  font-weight: 600;
}

.timeline-note {
  margin: 8px 0 0;
  color: #334155;
  line-height: 1.5;
}
</style>
