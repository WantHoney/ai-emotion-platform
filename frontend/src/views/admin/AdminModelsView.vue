<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  createAdminModel,
  getAdminModelSwitchLogs,
  getAdminModels,
  switchAdminModel,
  type ModelRegistryItem,
  type ModelSwitchLogItem,
} from '@/api/governance'
import { getSystemStatus, type RuntimeModelInfo, type SystemStatus } from '@/api/system'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { formatEnv, formatModelStatus, formatModelType } from '@/utils/uiText'

type RuntimeKey = 'asr' | 'audioEmotion' | 'text' | 'fusion' | 'psi'

const loading = ref(false)
const logsLoading = ref(false)
const rows = ref<ModelRegistryItem[]>([])
const switchLogs = ref<ModelSwitchLogItem[]>([])
const systemStatus = ref<SystemStatus | null>(null)
const errorState = ref<ErrorStatePayload | null>(null)
const logsErrorState = ref<ErrorStatePayload | null>(null)

const createDialogVisible = ref(false)
const logsDialogVisible = ref(false)

const filters = reactive({
  modelType: '',
  env: '',
  status: '',
})

const createForm = reactive({
  modelCode: '',
  modelName: '',
  modelType: 'FUSION',
  provider: 'local',
  version: '',
  env: 'prod',
  status: 'OFFLINE',
})

const environmentOptions = [
  { label: '开发环境', value: 'dev' },
  { label: '预发环境', value: 'staging' },
  { label: '生产环境', value: 'prod' },
]

const modelStatusOptions = [
  { label: '在线', value: 'ONLINE' },
  { label: '离线', value: 'OFFLINE' },
  { label: '已归档', value: 'ARCHIVED' },
]

const modelTypeOptions = [
  { label: formatModelType('ASR'), value: 'ASR' },
  { label: formatModelType('AUDIO_EMOTION'), value: 'AUDIO_EMOTION' },
  { label: formatModelType('TEXT_SENTIMENT'), value: 'TEXT_SENTIMENT' },
  { label: formatModelType('FUSION'), value: 'FUSION' },
  { label: formatModelType('SCORING'), value: 'SCORING' },
]

const runtimeEnvHint = computed(
  () => systemStatus.value?.runtime?.registryEnvHint || systemStatus.value?.config?.runtimeRegistryEnvHint || 'prod',
)

const runtimeModels = computed(() => {
  const models = systemStatus.value?.runtime?.models
  return {
    asr: models?.asr,
    audioEmotion: models?.audioEmotion,
    text: models?.text,
    fusion: models?.fusion,
    psi: models?.psi,
  }
})

const runtimeRows = computed(() =>
  [runtimeModels.value.asr, runtimeModels.value.audioEmotion, runtimeModels.value.text, runtimeModels.value.fusion, runtimeModels.value.psi].filter(
    (item): item is RuntimeModelInfo => Boolean(item),
  ),
)

const runtimeMismatchRows = computed(() =>
  rows.value.filter((row) => {
    const runtime = runtimeForRow(row)
    if (!runtime || !isCurrentEnv(row)) return false
    return row.status === 'ONLINE' && !matchesRuntime(row, runtime)
  }),
)

const loadModels = async () => {
  loading.value = true
  errorState.value = null
  try {
    const [modelRows, statusResponse] = await Promise.all([
      getAdminModels({
        modelType: filters.modelType || undefined,
        env: filters.env || undefined,
        status: filters.status || undefined,
      }),
      getSystemStatus(),
    ])
    rows.value = modelRows
    systemStatus.value = statusResponse.data
  } catch (error) {
    errorState.value = parseError(error, '模型治理数据加载失败')
  } finally {
    loading.value = false
  }
}

const loadSwitchLogs = async () => {
  logsLoading.value = true
  logsErrorState.value = null
  try {
    switchLogs.value = await getAdminModelSwitchLogs({
      modelType: filters.modelType || undefined,
      env: filters.env || undefined,
      limit: 50,
    })
  } catch (error) {
    logsErrorState.value = parseError(error, '模型切换日志加载失败')
  } finally {
    logsLoading.value = false
  }
}

const runtimeKeyByType = (modelType?: string): RuntimeKey | null => {
  switch ((modelType || '').toUpperCase()) {
    case 'ASR':
      return 'asr'
    case 'AUDIO_EMOTION':
      return 'audioEmotion'
    case 'TEXT_SENTIMENT':
      return 'text'
    case 'FUSION':
      return 'fusion'
    case 'SCORING':
      return 'psi'
    default:
      return null
  }
}

const runtimeForRow = (row: ModelRegistryItem) => {
  const runtimeKey = runtimeKeyByType(row.model_type)
  return runtimeKey ? runtimeModels.value[runtimeKey] : undefined
}

const isCurrentEnv = (row: ModelRegistryItem) => row.env === runtimeEnvHint.value

const matchesRuntime = (row: ModelRegistryItem, runtime?: RuntimeModelInfo) => {
  if (!runtime) return false
  const haystack = [runtime.registryComparable, runtime.rawValue, runtime.label]
    .filter(Boolean)
    .join(' | ')
    .toLowerCase()
  const needles = [row.version, row.model_code, row.model_name]
    .filter(Boolean)
    .map((item) => String(item).toLowerCase())
  return needles.some((needle) => needle.length > 2 && haystack.includes(needle))
}

const registryStateText = (row: ModelRegistryItem) => formatModelStatus(row.status)

const runtimeStateText = (row: ModelRegistryItem) => {
  if (!isCurrentEnv(row)) return '非当前环境'
  const runtime = runtimeForRow(row)
  if (!runtime) return '运行态未知'
  const matched = matchesRuntime(row, runtime)
  if (matched && row.status === 'ONLINE') return '登记在线 / 运行中'
  if (matched) return '运行中但登记未在线'
  if (row.status === 'ONLINE') return '登记在线 / 未生效'
  return '未运行'
}

const runtimeStateTag = (row: ModelRegistryItem) => {
  const text = runtimeStateText(row)
  if (text === '登记在线 / 运行中') return 'success'
  if (text === '运行中但登记未在线') return 'warning'
  if (text === '登记在线 / 未生效') return 'danger'
  return 'info'
}

const runtimeDetail = (row: ModelRegistryItem) => {
  const runtime = runtimeForRow(row)
  if (!runtime || !isCurrentEnv(row)) return '-'
  return runtime.label
}

const openCreateDialog = () => {
  createDialogVisible.value = true
}

const submitCreate = async () => {
  if (!createForm.modelCode || !createForm.modelName || !createForm.version) {
    ElMessage.warning('模型编码、模型名称和版本不能为空')
    return
  }
  try {
    await createAdminModel({
      modelCode: createForm.modelCode,
      modelName: createForm.modelName,
      modelType: createForm.modelType,
      provider: createForm.provider || undefined,
      version: createForm.version,
      env: createForm.env,
      status: createForm.status,
      metrics: {},
      config: {},
    })
    ElMessage.success('模型创建成功')
    createDialogVisible.value = false
    await loadModels()
  } catch (error) {
    const parsed = parseError(error, '创建模型失败')
    ElMessage.error(parsed.detail)
  }
}

const switchModel = async (row: ModelRegistryItem) => {
  try {
    const result = await ElMessageBox.prompt(
      `确认将模型 #${row.id}（${row.model_name} / ${row.version}）切换为在线吗？`,
      '确认切换',
      {
        confirmButtonText: '确认切换',
        cancelButtonText: '取消',
        inputPlaceholder: '切换原因（可选）',
        inputType: 'text',
      },
    )
    if (typeof result === 'string') return
    await switchAdminModel(row.id, result.value || undefined)
    ElMessage.success('模型切换已写入注册表，请继续核对“实际运行状态”是否同步生效。')
    await Promise.all([loadModels(), loadSwitchLogs()])
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    const parsed = parseError(error, '模型切换失败')
    ElMessage.error(parsed.detail)
  }
}

const openLogs = async () => {
  logsDialogVisible.value = true
  await loadSwitchLogs()
}

onMounted(() => {
  void loadModels()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>模型治理</span>
        <div class="header-actions">
          <el-button @click="openLogs">切换日志</el-button>
          <el-button @click="loadModels">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">新增模型</el-button>
        </div>
      </div>
    </template>

    <el-alert
      title="本页重点回答两个问题：模型在注册表里是什么状态、系统当前实际运行的又是哪一个版本。"
      type="info"
      :closable="false"
      class="mb-16"
    />

    <el-alert
      v-if="runtimeMismatchRows.length"
      type="warning"
      :closable="false"
      class="mb-16"
      title="检测到“登记在线”和“实际运行状态”不一致，请优先核对运行态后再对外说明当前模型版本。"
    />

    <el-card shadow="never" class="runtime-card">
      <template #header>当前实际运行模型</template>
      <EmptyState
        v-if="runtimeRows.length === 0"
        title="暂无运行态摘要"
        description="系统状态接口还没有返回运行中的模型信息。"
        action-text="刷新"
        @action="loadModels"
      />
      <el-row v-else :gutter="12">
        <el-col v-for="item in runtimeRows" :key="item.modelType" :xs="24" :md="12" :lg="8">
          <div class="runtime-item">
            <div class="runtime-item__meta">
              <span>{{ formatModelType(item.modelType) }}</span>
              <el-tag :type="item.status === 'UP' ? 'success' : item.status === 'UNKNOWN' ? 'info' : 'warning'">
                {{ item.status }}
              </el-tag>
            </div>
            <strong>{{ item.label }}</strong>
            <p>{{ item.detail || item.source }}</p>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-form inline class="mt-16">
      <el-form-item label="模型类型">
        <el-input v-model="filters.modelType" placeholder="输入模型类型代码" clearable />
      </el-form-item>
      <el-form-item label="环境">
        <el-select v-model="filters.env" clearable style="width: 120px">
          <el-option v-for="item in environmentOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable style="width: 140px">
          <el-option v-for="item in modelStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="loadModels">查询</el-button>
    </el-form>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadModels"
    />
    <EmptyState
      v-else-if="rows.length === 0"
      title="暂无模型数据"
      description="当前筛选条件下没有模型注册记录。"
      action-text="重新加载"
      @action="loadModels"
    />
    <el-table v-else :data="rows" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="model_name" label="模型名称" min-width="170" />
      <el-table-column prop="model_code" label="模型编码" min-width="130" />
      <el-table-column label="类型" width="140">
        <template #default="scope">{{ formatModelType(scope.row.model_type) }}</template>
      </el-table-column>
      <el-table-column prop="version" label="登记版本" min-width="150" />
      <el-table-column label="环境" width="100">
        <template #default="scope">{{ formatEnv(scope.row.env) }}</template>
      </el-table-column>
      <el-table-column label="注册状态" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ONLINE' ? 'success' : 'info'">
            {{ registryStateText(scope.row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="实际运行状态" min-width="170">
        <template #default="scope">
          <el-tag :type="runtimeStateTag(scope.row)">{{ runtimeStateText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前运行标识" min-width="220" show-overflow-tooltip>
        <template #default="scope">{{ runtimeDetail(scope.row) }}</template>
      </el-table-column>
      <el-table-column prop="published_at" label="发布时间" min-width="180" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="scope">
          <el-button
            type="primary"
            link
            :disabled="scope.row.status === 'ONLINE'"
            @click="switchModel(scope.row)"
          >
            设为在线
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createDialogVisible" title="新增模型" width="620">
      <el-alert
        class="mb-16"
        type="info"
        :closable="false"
        title="本页更适合维护模型注册表与切换记录，指标和配置可在后续补充。"
      />
      <el-form label-width="120px">
        <el-form-item label="模型编码"><el-input v-model="createForm.modelCode" /></el-form-item>
        <el-form-item label="模型名称"><el-input v-model="createForm.modelName" /></el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="createForm.modelType" style="width: 200px">
            <el-option v-for="item in modelTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提供方"><el-input v-model="createForm.provider" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="createForm.version" /></el-form-item>
        <el-form-item label="环境">
          <el-select v-model="createForm.env" style="width: 160px">
            <el-option v-for="item in environmentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始状态">
          <el-select v-model="createForm.status" style="width: 160px">
            <el-option label="离线" value="OFFLINE" />
            <el-option label="在线" value="ONLINE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logsDialogVisible" title="模型切换日志" width="980">
      <el-card shadow="never" class="mb-16">
        <template #header>切换后应核对的当前运行状态</template>
        <el-row :gutter="12">
          <el-col v-for="item in runtimeRows" :key="`logs-${item.modelType}`" :xs="24" :md="12">
            <div class="runtime-check-row">
              <span>{{ formatModelType(item.modelType) }}</span>
              <strong>{{ item.label }}</strong>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <LoadingState v-if="logsLoading" />
      <ErrorState
        v-else-if="logsErrorState"
        :title="logsErrorState.title"
        :detail="logsErrorState.detail"
        :trace-id="logsErrorState.traceId"
        @retry="loadSwitchLogs"
      />
      <EmptyState
        v-else-if="switchLogs.length === 0"
        title="暂无切换日志"
        description="还没有查询到历史模型切换记录。"
        action-text="重新加载"
        @action="loadSwitchLogs"
      />
      <el-table v-else :data="switchLogs" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="模型类型" width="140">
          <template #default="scope">{{ formatModelType(scope.row.model_type) }}</template>
        </el-table-column>
        <el-table-column label="环境" width="90">
          <template #default="scope">{{ formatEnv(scope.row.env) }}</template>
        </el-table-column>
        <el-table-column prop="from_model_id" label="来源模型" width="110" />
        <el-table-column prop="to_model_id" label="目标模型" width="110" />
        <el-table-column prop="switch_reason" label="切换原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="switched_by" label="操作人" width="90" />
        <el-table-column prop="switched_at" label="切换时间" min-width="180" />
      </el-table>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-16 {
  margin-top: 16px;
}

.runtime-card {
  margin-bottom: 16px;
}

.runtime-item {
  height: 100%;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(96, 119, 158, 0.16);
  background: rgba(12, 21, 38, 0.76);
  display: grid;
  gap: 10px;
}

.runtime-item__meta,
.runtime-check-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.runtime-item__meta span {
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.runtime-item strong,
.runtime-check-row strong {
  color: var(--admin-text-primary);
}

.runtime-item p {
  margin: 0;
  color: var(--admin-text-secondary);
  line-height: 1.6;
}

.runtime-check-row {
  padding: 10px 0;
  border-bottom: 1px solid rgba(96, 119, 158, 0.16);
}

.runtime-check-row:last-child {
  border-bottom: none;
}
</style>
