<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  createWarningRule,
  getWarningRules,
  toggleWarningRule,
  updateWarningRule,
  type WarningRuleItem,
} from '@/api/governance'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const rows = ref<WarningRuleItem[]>([])
const errorState = ref<ErrorStatePayload | null>(null)

const dialogVisible = ref(false)
const editMode = ref<'create' | 'edit'>('create')
const currentRuleId = ref<number | null>(null)

const form = reactive({
  ruleCode: '',
  ruleName: '',
  description: '',
  enabled: true,
  priority: 100,
  lowThreshold: 40,
  mediumThreshold: 60,
  highThreshold: 80,
  emotionComboText: '{"required":["SAD"],"optional":["ANGRY"]}',
  trendWindowDays: 7,
  triggerCount: 1,
  suggestTemplateCode: 'WARN_HIGH_FOLLOWUP',
})

const safeParseJson = (value: string) => {
  const text = value.trim()
  if (!text) return {}
  return JSON.parse(text) as Record<string, unknown>
}

const toEnabledBoolean = (value: number | boolean) => {
  if (typeof value === 'boolean') return value
  return value === 1
}

const resetForm = () => {
  form.ruleCode = ''
  form.ruleName = ''
  form.description = ''
  form.enabled = true
  form.priority = 100
  form.lowThreshold = 40
  form.mediumThreshold = 60
  form.highThreshold = 80
  form.emotionComboText = '{"required":["SAD"],"optional":["ANGRY"]}'
  form.trendWindowDays = 7
  form.triggerCount = 1
  form.suggestTemplateCode = 'WARN_HIGH_FOLLOWUP'
}

const loadRules = async () => {
  loading.value = true
  errorState.value = null
  try {
    rows.value = await getWarningRules()
  } catch (error) {
    errorState.value = parseError(error, 'Failed to load warning rules')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editMode.value = 'create'
  currentRuleId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: WarningRuleItem) => {
  editMode.value = 'edit'
  currentRuleId.value = row.id
  form.ruleCode = row.rule_code
  form.ruleName = row.rule_name
  form.description = row.description ?? ''
  form.enabled = toEnabledBoolean(row.enabled)
  form.priority = row.priority
  form.lowThreshold = row.low_threshold
  form.mediumThreshold = row.medium_threshold
  form.highThreshold = row.high_threshold
  form.emotionComboText = JSON.stringify(row.emotion_combo_json ?? {}, null, 2)
  form.trendWindowDays = row.trend_window_days
  form.triggerCount = row.trigger_count
  form.suggestTemplateCode = row.suggest_template_code ?? ''
  dialogVisible.value = true
}

const saveRule = async () => {
  if (!form.ruleName || (editMode.value === 'create' && !form.ruleCode)) {
    ElMessage.warning('ruleCode/ruleName are required')
    return
  }
  if (!(form.lowThreshold <= form.mediumThreshold && form.mediumThreshold <= form.highThreshold)) {
    ElMessage.warning('Thresholds must satisfy low <= medium <= high')
    return
  }

  let emotionCombo: Record<string, unknown>
  try {
    emotionCombo = safeParseJson(form.emotionComboText)
  } catch {
    ElMessage.warning('emotionCombo must be valid JSON')
    return
  }

  try {
    if (editMode.value === 'create') {
      await createWarningRule({
        ruleCode: form.ruleCode,
        ruleName: form.ruleName,
        description: form.description || undefined,
        enabled: form.enabled,
        priority: form.priority,
        lowThreshold: form.lowThreshold,
        mediumThreshold: form.mediumThreshold,
        highThreshold: form.highThreshold,
        emotionCombo,
        trendWindowDays: form.trendWindowDays,
        triggerCount: form.triggerCount,
        suggestTemplateCode: form.suggestTemplateCode || undefined,
      })
      ElMessage.success('Warning rule created')
    } else if (currentRuleId.value != null) {
      await updateWarningRule(currentRuleId.value, {
        ruleName: form.ruleName,
        description: form.description || undefined,
        enabled: form.enabled,
        priority: form.priority,
        lowThreshold: form.lowThreshold,
        mediumThreshold: form.mediumThreshold,
        highThreshold: form.highThreshold,
        emotionCombo,
        trendWindowDays: form.trendWindowDays,
        triggerCount: form.triggerCount,
        suggestTemplateCode: form.suggestTemplateCode || undefined,
      })
      ElMessage.success('Warning rule updated')
    }

    dialogVisible.value = false
    await loadRules()
  } catch (error) {
    const parsed = parseError(error, 'Failed to save warning rule')
    ElMessage.error(parsed.detail)
  }
}

const toggleRule = async (row: WarningRuleItem, enabled: boolean) => {
  try {
    await toggleWarningRule(row.id, enabled)
    ElMessage.success(enabled ? 'Rule enabled' : 'Rule disabled')
    await loadRules()
  } catch (error) {
    const parsed = parseError(error, 'Failed to toggle warning rule')
    ElMessage.error(parsed.detail)
  }
}

onMounted(async () => {
  await loadRules()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>Warning Rule Management</span>
        <div class="header-actions">
          <el-button @click="loadRules">Refresh</el-button>
          <el-button type="primary" @click="openCreate">Create Rule</el-button>
        </div>
      </div>
    </template>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadRules"
    />
    <EmptyState
      v-else-if="rows.length === 0"
      title="No warning rules"
      description="Create at least one rule so warning events can be generated."
      action-text="Reload"
      @action="loadRules"
    />
    <el-table v-else :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="rule_code" label="Rule Code" min-width="150" />
      <el-table-column prop="rule_name" label="Rule Name" min-width="170" />
      <el-table-column prop="priority" label="Priority" width="90" />
      <el-table-column label="Thresholds" min-width="200">
        <template #default="scope">
          low={{ scope.row.low_threshold }}, medium={{ scope.row.medium_threshold }}, high={{ scope.row.high_threshold }}
        </template>
      </el-table-column>
      <el-table-column prop="trend_window_days" label="Window(D)" width="100" />
      <el-table-column prop="trigger_count" label="Trigger N" width="90" />
      <el-table-column label="Enabled" width="90">
        <template #default="scope">
          <el-tag :type="(scope.row.enabled === true || scope.row.enabled === 1) ? 'success' : 'info'">
            {{ scope.row.enabled === true || scope.row.enabled === 1 ? 'Yes' : 'No' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Action" width="220">
        <template #default="scope">
          <el-button type="primary" link @click="openEdit(scope.row)">Edit</el-button>
          <el-button
            type="warning"
            link
            v-if="scope.row.enabled === true || scope.row.enabled === 1"
            @click="toggleRule(scope.row, false)"
          >
            Disable
          </el-button>
          <el-button type="success" link v-else @click="toggleRule(scope.row, true)">Enable</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editMode === 'create' ? 'Create Warning Rule' : 'Edit Warning Rule'"
      width="760"
    >
      <el-form label-width="160px">
        <el-form-item label="Rule Code">
          <el-input v-model="form.ruleCode" :disabled="editMode === 'edit'" />
        </el-form-item>
        <el-form-item label="Rule Name"><el-input v-model="form.ruleName" /></el-form-item>
        <el-form-item label="Description"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="Enabled"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="Priority">
          <el-input-number v-model="form.priority" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="Low Threshold">
          <el-input-number v-model="form.lowThreshold" :min="0" :max="100" :step="1" />
        </el-form-item>
        <el-form-item label="Medium Threshold">
          <el-input-number v-model="form.mediumThreshold" :min="0" :max="100" :step="1" />
        </el-form-item>
        <el-form-item label="High Threshold">
          <el-input-number v-model="form.highThreshold" :min="0" :max="100" :step="1" />
        </el-form-item>
        <el-form-item label="Emotion Combo JSON">
          <el-input v-model="form.emotionComboText" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="Trend Window Days">
          <el-input-number v-model="form.trendWindowDays" :min="1" :max="180" />
        </el-form-item>
        <el-form-item label="Trigger Count">
          <el-input-number v-model="form.triggerCount" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="Template Code">
          <el-input v-model="form.suggestTemplateCode" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" @click="saveRule">Save</el-button>
      </template>
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
