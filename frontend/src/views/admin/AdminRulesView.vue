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
  emotionComboText: '{"required":["SAD"],"forbidden":[],"minHits":{"SAD":1}}',
  trendWindowDays: 7,
  triggerCount: 1,
  suggestTemplateCode: 'WARN_HIGH_FOLLOWUP',
  slaLowMinutes: 1440,
  slaMediumMinutes: 720,
  slaHighMinutes: 240,
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
  form.emotionComboText = '{"required":["SAD"],"forbidden":[],"minHits":{"SAD":1}}'
  form.trendWindowDays = 7
  form.triggerCount = 1
  form.suggestTemplateCode = 'WARN_HIGH_FOLLOWUP'
  form.slaLowMinutes = 1440
  form.slaMediumMinutes = 720
  form.slaHighMinutes = 240
}

const loadRules = async () => {
  loading.value = true
  errorState.value = null
  try {
    rows.value = await getWarningRules()
  } catch (error) {
    errorState.value = parseError(error, '预警规则加载失败')
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
  form.slaLowMinutes = row.sla_low_minutes ?? 1440
  form.slaMediumMinutes = row.sla_medium_minutes ?? 720
  form.slaHighMinutes = row.sla_high_minutes ?? 240
  dialogVisible.value = true
}

const saveRule = async () => {
  if (!form.ruleName || (editMode.value === 'create' && !form.ruleCode)) {
    ElMessage.warning('ruleCode/ruleName 为必填项')
    return
  }
  if (!(form.lowThreshold <= form.mediumThreshold && form.mediumThreshold <= form.highThreshold)) {
    ElMessage.warning('阈值需满足 low <= medium <= high')
    return
  }
  if (!(form.slaLowMinutes > 0 && form.slaMediumMinutes > 0 && form.slaHighMinutes > 0)) {
    ElMessage.warning('SLA 分钟值必须为正数')
    return
  }

  let emotionCombo: Record<string, unknown>
  try {
    emotionCombo = safeParseJson(form.emotionComboText)
  } catch {
    ElMessage.warning('emotionCombo 必须是合法 JSON')
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
        slaLowMinutes: form.slaLowMinutes,
        slaMediumMinutes: form.slaMediumMinutes,
        slaHighMinutes: form.slaHighMinutes,
      })
      ElMessage.success('预警规则创建成功')
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
        slaLowMinutes: form.slaLowMinutes,
        slaMediumMinutes: form.slaMediumMinutes,
        slaHighMinutes: form.slaHighMinutes,
      })
      ElMessage.success('预警规则更新成功')
    }

    dialogVisible.value = false
    await loadRules()
  } catch (error) {
    const parsed = parseError(error, '保存预警规则失败')
    ElMessage.error(parsed.detail)
  }
}

const toggleRule = async (row: WarningRuleItem, enabled: boolean) => {
  try {
    await toggleWarningRule(row.id, enabled)
    ElMessage.success(enabled ? '规则已启用' : '规则已停用')
    await loadRules()
  } catch (error) {
    const parsed = parseError(error, '切换规则状态失败')
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
        <span>预警规则管理</span>
        <div class="header-actions">
          <el-button @click="loadRules">刷新</el-button>
          <el-button type="primary" @click="openCreate">新增规则</el-button>
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
      title="暂无预警规则"
      description="请至少创建一条规则，系统才能生成预警事件。"
      action-text="重新加载"
      @action="loadRules"
    />
    <el-table v-else :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="rule_code" label="规则编码" min-width="150" />
      <el-table-column prop="rule_name" label="规则名称" min-width="170" />
      <el-table-column prop="priority" label="优先级" width="90" />
      <el-table-column label="阈值" min-width="200">
        <template #default="scope">
          low={{ scope.row.low_threshold }}, medium={{ scope.row.medium_threshold }}, high={{ scope.row.high_threshold }}
        </template>
      </el-table-column>
      <el-table-column label="SLA(分钟)" min-width="180">
        <template #default="scope">
          L={{ scope.row.sla_low_minutes ?? 1440 }}, M={{ scope.row.sla_medium_minutes ?? 720 }},
          H={{ scope.row.sla_high_minutes ?? 240 }}
        </template>
      </el-table-column>
      <el-table-column prop="trend_window_days" label="窗口天数" width="100" />
      <el-table-column prop="trigger_count" label="触发次数" width="90" />
      <el-table-column label="启用" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.enabled === true || scope.row.enabled === 1 ? 'success' : 'info'">
            {{ scope.row.enabled === true || scope.row.enabled === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="scope">
          <el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button>
          <el-button
            type="warning"
            link
            v-if="scope.row.enabled === true || scope.row.enabled === 1"
            @click="toggleRule(scope.row, false)"
          >
            停用
          </el-button>
          <el-button type="success" link v-else @click="toggleRule(scope.row, true)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editMode === 'create' ? '新增预警规则' : '编辑预警规则'"
      width="760"
    >
      <el-form label-width="160px">
        <el-form-item label="规则编码">
          <el-input v-model="form.ruleCode" :disabled="editMode === 'edit'" />
        </el-form-item>
        <el-form-item label="规则名称"><el-input v-model="form.ruleName" /></el-form-item>
        <el-form-item label="规则描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="低风险阈值">
          <el-input-number v-model="form.lowThreshold" :min="0" :max="100" :step="1" />
        </el-form-item>
        <el-form-item label="中风险阈值">
          <el-input-number v-model="form.mediumThreshold" :min="0" :max="100" :step="1" />
        </el-form-item>
        <el-form-item label="高风险阈值">
          <el-input-number v-model="form.highThreshold" :min="0" :max="100" :step="1" />
        </el-form-item>
        <el-form-item label="情绪组合 JSON">
          <el-input v-model="form.emotionComboText" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="趋势窗口（天）">
          <el-input-number v-model="form.trendWindowDays" :min="1" :max="180" />
        </el-form-item>
        <el-form-item label="触发次数">
          <el-input-number v-model="form.triggerCount" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="低风险 SLA（分钟）">
          <el-input-number v-model="form.slaLowMinutes" :min="1" :max="20160" />
        </el-form-item>
        <el-form-item label="中风险 SLA（分钟）">
          <el-input-number v-model="form.slaMediumMinutes" :min="1" :max="20160" />
        </el-form-item>
        <el-form-item label="高风险 SLA（分钟）">
          <el-input-number v-model="form.slaHighMinutes" :min="1" :max="20160" />
        </el-form-item>
        <el-form-item label="建议模板编码">
          <el-input v-model="form.suggestTemplateCode" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
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
