<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const logsLoading = ref(false)
const rows = ref<ModelRegistryItem[]>([])
const switchLogs = ref<ModelSwitchLogItem[]>([])
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
  provider: 'openrouter',
  version: '',
  env: 'dev',
  status: 'OFFLINE',
})

const loadModels = async () => {
  loading.value = true
  errorState.value = null
  try {
    rows.value = await getAdminModels({
      modelType: filters.modelType || undefined,
      env: filters.env || undefined,
      status: filters.status || undefined,
    })
  } catch (error) {
    errorState.value = parseError(error, 'Failed to load model registry')
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
    logsErrorState.value = parseError(error, 'Failed to load model switch logs')
  } finally {
    logsLoading.value = false
  }
}

const openCreateDialog = () => {
  createDialogVisible.value = true
}

const submitCreate = async () => {
  if (!createForm.modelCode || !createForm.modelName || !createForm.version) {
    ElMessage.warning('modelCode / modelName / version are required')
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
    ElMessage.success('Model created')
    createDialogVisible.value = false
    await loadModels()
  } catch (error) {
    const parsed = parseError(error, 'Failed to create model')
    ElMessage.error(parsed.detail)
  }
}

const switchModel = async (row: ModelRegistryItem) => {
  try {
    const result = await ElMessageBox.prompt(
      `Switch model #${row.id} (${row.model_name} / ${row.version}) to ONLINE?`,
      'Confirm Switch',
      {
        confirmButtonText: 'Switch',
        cancelButtonText: 'Cancel',
        inputPlaceholder: 'switch reason (optional)',
        inputType: 'text',
      },
    )
    if (typeof result === 'string') {
      return
    }
    await switchAdminModel(row.id, result.value || undefined)
    ElMessage.success('Model switched')
    await Promise.all([loadModels(), loadSwitchLogs()])
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    const parsed = parseError(error, 'Failed to switch model')
    ElMessage.error(parsed.detail)
  }
}

const openLogs = async () => {
  logsDialogVisible.value = true
  await loadSwitchLogs()
}

onMounted(async () => {
  await loadModels()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>Model Governance</span>
        <div class="header-actions">
          <el-button @click="openLogs">Switch Logs</el-button>
          <el-button @click="loadModels">Refresh</el-button>
          <el-button type="primary" @click="openCreateDialog">Create Model</el-button>
        </div>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="Model Type">
        <el-input v-model="filters.modelType" placeholder="ASR/FUSION/..." clearable />
      </el-form-item>
      <el-form-item label="Env">
        <el-select v-model="filters.env" clearable style="width: 120px">
          <el-option label="dev" value="dev" />
          <el-option label="staging" value="staging" />
          <el-option label="prod" value="prod" />
        </el-select>
      </el-form-item>
      <el-form-item label="Status">
        <el-select v-model="filters.status" clearable style="width: 140px">
          <el-option label="ONLINE" value="ONLINE" />
          <el-option label="OFFLINE" value="OFFLINE" />
          <el-option label="ARCHIVED" value="ARCHIVED" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="loadModels">Search</el-button>
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
      title="No models"
      description="No model registry data for current filters."
      action-text="Reload"
      @action="loadModels"
    />
    <el-table v-else :data="rows" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="model_name" label="Name" min-width="170" />
      <el-table-column prop="model_code" label="Code" min-width="130" />
      <el-table-column prop="model_type" label="Type" width="140" />
      <el-table-column prop="version" label="Version" width="130" />
      <el-table-column prop="env" label="Env" width="100" />
      <el-table-column label="Status" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ONLINE' ? 'success' : 'info'">{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="published_at" label="Published At" min-width="180" />
      <el-table-column label="Action" width="130">
        <template #default="scope">
          <el-button
            type="primary"
            link
            :disabled="scope.row.status === 'ONLINE'"
            @click="switchModel(scope.row)"
          >
            Set ONLINE
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createDialogVisible" title="Create Model" width="620">
      <el-form label-width="120px">
        <el-form-item label="Model Code"><el-input v-model="createForm.modelCode" /></el-form-item>
        <el-form-item label="Model Name"><el-input v-model="createForm.modelName" /></el-form-item>
        <el-form-item label="Model Type">
          <el-select v-model="createForm.modelType" style="width: 200px">
            <el-option label="ASR" value="ASR" />
            <el-option label="AUDIO_EMOTION" value="AUDIO_EMOTION" />
            <el-option label="TEXT_SENTIMENT" value="TEXT_SENTIMENT" />
            <el-option label="FUSION" value="FUSION" />
            <el-option label="SCORING" value="SCORING" />
          </el-select>
        </el-form-item>
        <el-form-item label="Provider"><el-input v-model="createForm.provider" /></el-form-item>
        <el-form-item label="Version"><el-input v-model="createForm.version" /></el-form-item>
        <el-form-item label="Env">
          <el-select v-model="createForm.env" style="width: 160px">
            <el-option label="dev" value="dev" />
            <el-option label="staging" value="staging" />
            <el-option label="prod" value="prod" />
          </el-select>
        </el-form-item>
        <el-form-item label="Initial Status">
          <el-select v-model="createForm.status" style="width: 160px">
            <el-option label="OFFLINE" value="OFFLINE" />
            <el-option label="ONLINE" value="ONLINE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="submitCreate">Create</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logsDialogVisible" title="Model Switch Logs" width="900">
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
        title="No switch logs"
        description="No historical switch records found."
        action-text="Reload"
        @action="loadSwitchLogs"
      />
      <el-table v-else :data="switchLogs" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="model_type" label="Model Type" width="140" />
        <el-table-column prop="env" label="Env" width="90" />
        <el-table-column prop="from_model_id" label="From" width="110" />
        <el-table-column prop="to_model_id" label="To" width="110" />
        <el-table-column prop="switch_reason" label="Reason" min-width="220" show-overflow-tooltip />
        <el-table-column prop="switched_by" label="By" width="90" />
        <el-table-column prop="switched_at" label="Switched At" min-width="180" />
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
</style>
