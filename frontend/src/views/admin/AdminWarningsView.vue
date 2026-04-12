<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

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
import {
  SLA_LABEL,
  formatEmotion,
  formatRiskLevel,
  formatWarningActionType,
  formatWarningStatus,
} from '@/utils/uiText'

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

type SnapshotLike = Record<string, unknown>

const router = useRouter()

const loading = ref(false)
const rows = ref<WarningEventItem[]>([])
const total = ref(0)
const errorState = ref<ErrorStatePayload | null>(null)
const selection = ref<WarningEventItem[]>([])
const batchLoading = ref(false)
const rowActionId = ref<number | null>(null)
const rowActionType = ref<string | null>(null)

const actionsLoading = ref(false)
const actionsError = ref<ErrorStatePayload | null>(null)
const actionsDrawer = ref(false)
const currentWarning = ref<WarningEventItem | null>(null)
const actionRows = ref<WarningActionItem[]>([])
let latestActionRequestId = 0

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
  { label: '新建', value: 'NEW' },
  { label: '已确认', value: 'ACKED' },
  { label: '跟进中', value: 'FOLLOWING' },
  { label: '已结案', value: 'RESOLVED' },
  { label: '已关闭', value: 'CLOSED' },
]

const riskOptions = [
  { label: '全部风险', value: '' },
  { label: '低风险', value: 'LOW' },
  { label: '中风险', value: 'MEDIUM' },
  { label: '高风险', value: 'HIGH' },
]

const flowLabels = ['新建', '已确认', '跟进中', '已结案']

const isBreached = (row: WarningEventItem) => row.breached === true || row.breached === 1
const isClosed = (row: WarningEventItem) => row.status === 'RESOLVED' || row.status === 'CLOSED'

const canAck = (row: WarningEventItem) => row.status === 'NEW'
const canFollow = (row: WarningEventItem) => !isClosed(row)
const canResolve = (row: WarningEventItem) => !isClosed(row)

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

const currentTriggerReason = computed(() => summarizeTriggerReason(currentWarning.value))
const currentRiskSummary = computed(() => summarizeRisk(currentWarning.value))
const currentRelatedSummary = computed(() => summarizeRelated(currentWarning.value))
const currentLatestAction = computed(() => summarizeLatestAction(currentWarning.value))

const currentTimelineNodes = computed<WarningTimelineNode[]>(() => {
  if (!currentWarning.value) return []
  const row = currentWarning.value
  const snapshot = getSnapshot(row)
  const runtime = getNestedRecord(snapshot, 'runtime')
  const nodes: WarningTimelineNode[] = [
    {
      key: `created-${row.id}`,
      title: '预警触发',
      timestamp: row.created_at,
      type: 'primary',
      note: summarizeTriggerReason(row) || summarizeRisk(row),
    },
  ]

  if (row.first_acked_at) {
    nodes.push({
      key: `acked-${row.id}`,
      title: '首次确认',
      timestamp: row.first_acked_at,
      type: 'warning',
      note: '预警已被人工确认，开始进入跟进流程。',
    })
  }

  if (row.first_followed_at) {
    nodes.push({
      key: `followed-${row.id}`,
      title: '首次跟进',
      timestamp: row.first_followed_at,
      type: 'warning',
      note: '预警已进入处置跟进阶段。',
    })
  }

  if (row.resolved_at) {
    nodes.push({
      key: `resolved-${row.id}`,
      title: '结案完成',
      timestamp: row.resolved_at,
      type: 'success',
      note: '预警处置闭环已完成。',
    })
  }

  if (row.sla_deadline_at) {
    nodes.push({
      key: `deadline-${row.id}`,
      title: `${SLA_LABEL} 截止`,
      timestamp: row.sla_deadline_at,
      type: isBreached(row) ? 'danger' : 'info',
      note: isBreached(row)
        ? `已超时，建议复盘触发原因与处置链路。`
        : `当前仍处于 ${SLA_LABEL} 时限内。`,
    })
  }

  if (runtime && typeof runtime.trendHitCount !== 'undefined') {
    nodes.push({
      key: `trend-${row.id}`,
      title: '规则命中说明',
      timestamp: row.created_at,
      type: 'info',
      note: `趋势窗口命中 ${runtime.trendHitCount ?? 0} 次，命中条件：${summarizeTriggerReason(row)}`,
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
      title: `动作：${formatWarningActionType(actionType)}`,
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
  return diff >= 0 ? `剩余 ${h} 小时 ${m} 分钟` : `超时 ${h} 小时 ${m} 分钟`
}

function asRecord(value: unknown): SnapshotLike | null {
  if (!value) return null
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value) as unknown
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? (parsed as SnapshotLike) : null
    } catch {
      return null
    }
  }
  return typeof value === 'object' && !Array.isArray(value) ? (value as SnapshotLike) : null
}

function getNestedRecord(source: SnapshotLike | null, key: string) {
  return asRecord(source?.[key])
}

function getSnapshot(row: WarningEventItem | null) {
  return asRecord(row?.trigger_snapshot)
}

function getWorkflow(row: WarningEventItem | null) {
  return getNestedRecord(getSnapshot(row), 'workflow')
}

function getRule(row: WarningEventItem | null) {
  return getNestedRecord(getSnapshot(row), 'rule')
}

function getRuntime(row: WarningEventItem | null) {
  return getNestedRecord(getSnapshot(row), 'runtime')
}

function summarizeTriggerReason(row: WarningEventItem | null) {
  if (!row) return '-'
  const rule = getRule(row)
  const runtime = getRuntime(row)
  const ruleName = stringify(rule?.ruleName || rule?.ruleCode)
  const trendHits = stringify(runtime?.trendHitCount)
  const parts = [
    ruleName ? `规则：${ruleName}` : '',
    trendHits ? `趋势命中：${trendHits}` : '',
    row.top_emotion ? `主情绪：${formatEmotion(row.top_emotion)}` : '',
  ].filter(Boolean)
  return parts.length ? parts.join(' / ') : '当前预警由规则命中后自动触发。'
}

function summarizeRisk(row: WarningEventItem | null) {
  if (!row) return '-'
  const runtime = getRuntime(row)
  const riskScore = stringify(runtime?.riskScore) || String(row.risk_score ?? '-')
  const riskLevel = stringify(runtime?.riskLevel) || row.risk_level
  const emotion = stringify(runtime?.overallEmotion) || row.top_emotion
  return `风险等级 ${formatRiskLevel(riskLevel)} / 风险分 ${riskScore} / 主情绪 ${formatEmotion(emotion)}`
}

function summarizeRelated(row: WarningEventItem | null) {
  if (!row) return '-'
  const parts = [
    row.task_id ? `任务 #${row.task_id}` : '',
    row.report_id ? `报告 #${row.report_id}` : '',
    row.user_mask ? `用户 ${row.user_mask}` : '',
  ].filter(Boolean)
  return parts.length ? parts.join(' / ') : '暂无关联对象'
}

function summarizeLatestAction(row: WarningEventItem | null) {
  if (!row) return '-'
  const workflow = getWorkflow(row)
  if (!workflow) return '尚未产生人工处置动作'
  const action = formatWarningActionType(stringify(workflow.lastAction))
  const status = formatWarningStatus(stringify(workflow.lastStatus))
  const note = stringify(workflow.lastActionNote)
  const updatedAt = stringify(workflow.updatedAt)
  return [action, status !== '-' ? `状态：${status}` : '', updatedAt ? `时间：${updatedAt}` : '', note ? `备注：${note}` : '']
    .filter(Boolean)
    .join(' / ')
}

function stringify(value: unknown) {
  if (value == null) return ''
  const normalized = String(value).trim()
  return normalized
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
      breached: query.breached || undefined,
      keyword: query.keyword.trim() || undefined,
    })
    rows.value = response.items ?? []
    total.value = response.total ?? 0
    selection.value = []
    if (currentWarning.value) {
      currentWarning.value = rows.value.find((item) => item.id === currentWarning.value?.id) ?? currentWarning.value
    }
  } catch (error) {
    errorState.value = parseError(error, '预警事件加载失败')
  } finally {
    loading.value = false
  }
}

const loadActions = async (warningId: number) => {
  const requestId = ++latestActionRequestId
  actionsLoading.value = true
  actionsError.value = null
  actionRows.value = []
  try {
    const rowsValue = await getWarningActions(warningId)
    if (requestId !== latestActionRequestId || currentWarning.value?.id !== warningId) {
      return
    }
    actionRows.value = rowsValue
  } catch (error) {
    if (requestId === latestActionRequestId) {
      actionsError.value = parseError(error, '预警处置时间线加载失败')
    }
  } finally {
    if (requestId === latestActionRequestId) {
      actionsLoading.value = false
    }
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

const openRouteInNewTab = async (routeName: 'adminTaskInspect' | 'adminReportInspect', id?: number) => {
  if (!id) return
  const resolved = router.resolve({ name: routeName, params: { id } })
  window.open(resolved.href, '_blank', 'noopener,noreferrer')
}

const promptActionNote = async (title: string, placeholder: string) => {
  try {
    const result = await ElMessageBox.prompt('请输入处置备注（可选）', title, {
      inputType: 'textarea',
      inputPlaceholder: placeholder,
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      showInput: true,
    })
    return typeof result === 'string' ? '' : result.value ?? ''
  } catch {
    return null
  }
}

const refreshDrawerIfNeeded = async (affectedIds: number[]) => {
  if (!currentWarning.value || !affectedIds.includes(currentWarning.value.id)) return
  const refreshed = rows.value.find((item) => item.id === currentWarning.value?.id)
  if (refreshed) currentWarning.value = refreshed
  await loadActions(currentWarning.value.id)
}

const runAction = async (
  targets: WarningEventItem[],
  options: {
    actionType: string
    nextStatus?: string
    title: string
    placeholder: string
  },
) => {
  const note = await promptActionNote(options.title, options.placeholder)
  if (note === null) return

  const targetIds = targets.map((item) => item.id)
  const isBatch = targets.length > 1
  if (isBatch) {
    batchLoading.value = true
  } else {
    rowActionId.value = targets[0]?.id ?? null
    rowActionType.value = options.actionType
  }

  let successCount = 0
  const failedIds: number[] = []

  try {
    for (const row of targets) {
      try {
        await postWarningAction(row.id, {
          actionType: options.actionType,
          actionNote: note || undefined,
          nextStatus: options.nextStatus,
        })
        successCount += 1
      } catch {
        failedIds.push(row.id)
      }
    }

    await loadWarnings()
    await refreshDrawerIfNeeded(targetIds)

    if (successCount > 0 && failedIds.length === 0) {
      ElMessage.success(`${options.title}已完成，共处理 ${successCount} 条。`)
      return
    }

    if (successCount > 0) {
      ElMessage.warning(`${options.title}部分完成：成功 ${successCount} 条，失败 ${failedIds.length} 条（ID: ${failedIds.join(', ')}）。`)
      return
    }

    ElMessage.error(`${options.title}未成功处理任何记录。`)
  } finally {
    batchLoading.value = false
    rowActionId.value = null
    rowActionType.value = null
  }
}

const markAcked = async (row: WarningEventItem) => {
  await runAction([row], {
    actionType: 'MARK_FOLLOWED',
    nextStatus: 'ACKED',
    title: `确认预警 #${row.id}`,
    placeholder: '记录确认依据，例如已查看报告、已联系相关人员等。',
  })
}

const markFollowing = async (row: WarningEventItem) => {
  await runAction([row], {
    actionType: 'ADD_NOTE',
    nextStatus: 'FOLLOWING',
    title: `跟进预警 #${row.id}`,
    placeholder: '补充当前跟进情况、下一步动作或外部联系情况。',
  })
}

const markResolved = async (row: WarningEventItem) => {
  await runAction([row], {
    actionType: 'RESOLVE',
    nextStatus: 'RESOLVED',
    title: `结案预警 #${row.id}`,
    placeholder: '填写结案说明，例如已完成复核、已给出建议、无需继续跟进等。',
  })
}

const batchAck = async () => {
  const rowsValue = selectedAckable.value
  if (!rowsValue.length) {
    ElMessage.warning('当前选中项里没有可确认的预警。')
    return
  }
  await runAction(rowsValue, {
    actionType: 'MARK_FOLLOWED',
    nextStatus: 'ACKED',
    title: `批量确认（${rowsValue.length} 条）`,
    placeholder: '补充批量确认说明，可留空。',
  })
}

const batchFollow = async () => {
  const rowsValue = selectedFollowable.value
  if (!rowsValue.length) {
    ElMessage.warning('当前选中项里没有可跟进的预警。')
    return
  }
  await runAction(rowsValue, {
    actionType: 'ADD_NOTE',
    nextStatus: 'FOLLOWING',
    title: `批量跟进（${rowsValue.length} 条）`,
    placeholder: '补充批量跟进说明，可留空。',
  })
}

const batchResolve = async () => {
  const rowsValue = selectedResolvable.value
  if (!rowsValue.length) {
    ElMessage.warning('当前选中项里没有可结案的预警。')
    return
  }
  await runAction(rowsValue, {
    actionType: 'RESOLVE',
    nextStatus: 'RESOLVED',
    title: `批量结案（${rowsValue.length} 条）`,
    placeholder: '补充批量结案说明，可留空。',
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
  () => [query.status, query.riskLevel, query.breached, query.keyword],
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

onBeforeUnmount(() => {
  latestActionRequestId += 1
  if (filterTimer) {
    clearTimeout(filterTimer)
    filterTimer = null
  }
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
        <el-form-item :label="`${SLA_LABEL}状态`">
          <el-radio-group v-model="query.breached">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="N">时限内</el-radio-button>
            <el-radio-button label="Y">已超时</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="ID / 用户标识 / 任务 / 报告 / 情绪"
            style="width: 260px"
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
      v-else-if="rows.length === 0"
      title="暂无预警事件"
      description="当前筛选条件下还没有可处置的预警记录。"
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
        <span class="batch-note">选中 ID：{{ selectionIds.join(', ') || '-' }}</span>
      </div>

      <el-table :data="rows" border row-key="id" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="user_mask" label="用户标识" width="130" />
        <el-table-column label="关联对象" min-width="180">
          <template #default="scope">
            <div class="related-links">
              <el-button
                v-if="scope.row.task_id"
                type="primary"
                link
                @click="openRouteInNewTab('adminTaskInspect', scope.row.task_id)"
              >
                任务 #{{ scope.row.task_id }}
              </el-button>
              <el-button
                v-if="scope.row.report_id"
                type="primary"
                link
                @click="openRouteInNewTab('adminReportInspect', scope.row.report_id)"
              >
                报告 #{{ scope.row.report_id }}
              </el-button>
              <span v-if="!scope.row.task_id && !scope.row.report_id" class="muted-text">暂无</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="主情绪" width="100">
          <template #default="scope">{{ formatEmotion(scope.row.top_emotion) }}</template>
        </el-table-column>
        <el-table-column label="风险" width="110">
          <template #default="scope">
            <el-tag :type="riskType(scope.row.risk_level)">{{ formatRiskLevel(scope.row.risk_level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="risk_score" label="风险分" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="scope">{{ formatWarningStatus(scope.row.status) }}</template>
        </el-table-column>
        <el-table-column label="触发原因" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <div class="summary-cell">{{ summarizeTriggerReason(scope.row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="最近处置" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <div class="summary-cell">{{ summarizeLatestAction(scope.row) }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="SLA_LABEL" min-width="170">
          <template #default="scope">
            <el-tag :type="breachedType(scope.row)">
              {{ isBreached(scope.row) ? '已超时' : '时限内' }}
            </el-tag>
            <div class="sla-gap">{{ formatDeadlineGap(scope.row.sla_deadline_at) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" min-width="165" />
        <el-table-column label="操作" min-width="320" fixed="right">
          <template #default="scope">
            <el-button type="primary" link @click="openTimeline(scope.row)">时间线</el-button>
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
            <el-button
              type="primary"
              link
              :loading="rowActionId === scope.row.id && rowActionType === 'MARK_FOLLOWED'"
              :disabled="!canAck(scope.row)"
              @click="markAcked(scope.row)"
            >
              确认
            </el-button>
            <el-button
              type="warning"
              link
              :loading="rowActionId === scope.row.id && rowActionType === 'ADD_NOTE'"
              :disabled="!canFollow(scope.row)"
              @click="markFollowing(scope.row)"
            >
              跟进
            </el-button>
            <el-button
              type="success"
              link
              :loading="rowActionId === scope.row.id && rowActionType === 'RESOLVE'"
              :disabled="!canResolve(scope.row)"
              @click="markResolved(scope.row)"
            >
              结案
            </el-button>
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
      <div v-if="currentWarning" class="detail-grid">
        <el-card shadow="never">
          <template #header>触发原因</template>
          <p class="detail-main">{{ currentTriggerReason }}</p>
        </el-card>
        <el-card shadow="never">
          <template #header>风险摘要</template>
          <p class="detail-main">{{ currentRiskSummary }}</p>
        </el-card>
        <el-card shadow="never">
          <template #header>关联对象</template>
          <div class="detail-related">
            <p class="detail-main">{{ currentRelatedSummary }}</p>
            <div class="detail-actions">
              <el-button
                v-if="currentWarning.task_id"
                type="primary"
                plain
                size="small"
                @click="openRouteInNewTab('adminTaskInspect', currentWarning.task_id)"
              >
                打开任务
              </el-button>
              <el-button
                v-if="currentWarning.report_id"
                type="primary"
                plain
                size="small"
                @click="openRouteInNewTab('adminReportInspect', currentWarning.report_id)"
              >
                打开报告
              </el-button>
            </div>
          </div>
        </el-card>
        <el-card shadow="never">
          <template #header>最近处置</template>
          <p class="detail-main">{{ currentLatestAction }}</p>
        </el-card>
      </div>

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
        description="该预警事件还没有产生可展示的处置时间线。"
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

.batch-note,
.muted-text,
.sla-gap {
  color: var(--admin-text-muted);
  font-size: 12px;
}

.related-links {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.summary-cell {
  line-height: 1.5;
  color: var(--admin-text-secondary);
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.detail-grid {
  margin-bottom: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-main {
  margin: 0;
  line-height: 1.7;
  color: var(--admin-text-primary);
}

.detail-related {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
  color: var(--admin-text-secondary);
  line-height: 1.5;
}

@media (max-width: 960px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
