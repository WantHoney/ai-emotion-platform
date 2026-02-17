<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import {
  getAdminModels,
  getWarningRules,
  updateWarningRule,
  type ModelRegistryItem,
  type WarningRuleItem,
} from '@/api/governance'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const loading = ref(false)
const saving = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)

const rules = ref<WarningRuleItem[]>([])
const models = ref<ModelRegistryItem[]>([])
const selectedRuleId = ref<number | null>(null)

const ruleForm = reactive({
  ruleName: '',
  description: '',
  enabled: true,
  priority: 100,
  lowThreshold: 40,
  mediumThreshold: 60,
  highThreshold: 80,
  trendWindowDays: 7,
  triggerCount: 1,
  suggestTemplateCode: '',
  slaLowMinutes: 1440,
  slaMediumMinutes: 720,
  slaHighMinutes: 240,
  emotionComboJson: '{}',
})

const selectedRule = computed(() => rules.value.find((item) => item.id === selectedRuleId.value) ?? null)

const syncFormFromRule = (rule: WarningRuleItem) => {
  selectedRuleId.value = rule.id
  ruleForm.ruleName = rule.rule_name
  ruleForm.description = rule.description ?? ''
  ruleForm.enabled = rule.enabled === true || rule.enabled === 1
  ruleForm.priority = rule.priority
  ruleForm.lowThreshold = rule.low_threshold
  ruleForm.mediumThreshold = rule.medium_threshold
  ruleForm.highThreshold = rule.high_threshold
  ruleForm.trendWindowDays = rule.trend_window_days
  ruleForm.triggerCount = rule.trigger_count
  ruleForm.suggestTemplateCode = rule.suggest_template_code ?? ''
  ruleForm.slaLowMinutes = rule.sla_low_minutes ?? 1440
  ruleForm.slaMediumMinutes = rule.sla_medium_minutes ?? 720
  ruleForm.slaHighMinutes = rule.sla_high_minutes ?? 240
  ruleForm.emotionComboJson = JSON.stringify(rule.emotion_combo_json ?? {}, null, 2)
}

const handleRuleChange = (val: string | number) => {
  const target = rules.value.find((item) => item.id === Number(val))
  if (target) {
    syncFormFromRule(target)
  }
}

const loadData = async () => {
  loading.value = true
  errorState.value = null
  try {
    const [ruleRows, modelRows] = await Promise.all([
      getWarningRules(),
      getAdminModels({ env: 'prod' }),
    ])
    rules.value = ruleRows
    models.value = modelRows
    const firstRule = ruleRows[0]
    if (firstRule) {
      syncFormFromRule(firstRule)
    }
  } catch (error) {
    errorState.value = parseError(error, '系统设置数据加载失败')
  } finally {
    loading.value = false
  }
}

const saveRule = async () => {
  if (!selectedRule.value) return
  if (!(ruleForm.lowThreshold <= ruleForm.mediumThreshold && ruleForm.mediumThreshold <= ruleForm.highThreshold)) {
    ElMessage.warning('阈值需满足 low <= medium <= high')
    return
  }

  let emotionCombo: Record<string, unknown> = {}
  try {
    emotionCombo = JSON.parse(ruleForm.emotionComboJson || '{}') as Record<string, unknown>
  } catch {
    ElMessage.warning('情绪组合 JSON 格式不合法')
    return
  }

  saving.value = true
  try {
    await updateWarningRule(selectedRule.value.id, {
      ruleName: ruleForm.ruleName,
      description: ruleForm.description || undefined,
      enabled: ruleForm.enabled,
      priority: ruleForm.priority,
      lowThreshold: ruleForm.lowThreshold,
      mediumThreshold: ruleForm.mediumThreshold,
      highThreshold: ruleForm.highThreshold,
      emotionCombo,
      trendWindowDays: ruleForm.trendWindowDays,
      triggerCount: ruleForm.triggerCount,
      suggestTemplateCode: ruleForm.suggestTemplateCode || undefined,
      slaLowMinutes: ruleForm.slaLowMinutes,
      slaMediumMinutes: ruleForm.slaMediumMinutes,
      slaHighMinutes: ruleForm.slaHighMinutes,
    })
    ElMessage.success('规则参数已保存')
    await loadData()
  } catch (error) {
    const parsed = parseError(error, '保存规则参数失败')
    ElMessage.error(parsed.detail)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadData()
})
</script>

<template>
  <div class="settings-page">
    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadData"
    />
    <template v-else>
      <el-card shadow="hover">
        <template #header>
          <div class="header-row">
            <span>预警阈值与规则参数</span>
            <el-button @click="loadData">刷新</el-button>
          </div>
        </template>

        <EmptyState
          v-if="rules.length === 0"
          title="暂无预警规则"
          description="请先在预警规则页创建至少一条规则。"
          action-text="重新加载"
          @action="loadData"
        />
        <template v-else>
          <el-form label-width="140px">
            <el-form-item label="当前规则">
              <el-select
                :model-value="selectedRuleId"
                style="width: 420px"
                @change="handleRuleChange"
              >
                <el-option v-for="item in rules" :key="item.id" :label="`${item.rule_name} (${item.rule_code})`" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="规则名称">
              <el-input v-model="ruleForm.ruleName" />
            </el-form-item>
            <el-form-item label="规则描述">
              <el-input v-model="ruleForm.description" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="ruleForm.enabled" />
            </el-form-item>
            <el-form-item label="阈值 (低/中/高)">
              <el-input-number v-model="ruleForm.lowThreshold" :min="0" :max="100" />
              <el-input-number v-model="ruleForm.mediumThreshold" :min="0" :max="100" />
              <el-input-number v-model="ruleForm.highThreshold" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="趋势窗口/触发次数">
              <el-input-number v-model="ruleForm.trendWindowDays" :min="1" :max="180" />
              <el-input-number v-model="ruleForm.triggerCount" :min="1" :max="100" />
            </el-form-item>
            <el-form-item label="SLA (低/中/高 分钟)">
              <el-input-number v-model="ruleForm.slaLowMinutes" :min="1" :max="20160" />
              <el-input-number v-model="ruleForm.slaMediumMinutes" :min="1" :max="20160" />
              <el-input-number v-model="ruleForm.slaHighMinutes" :min="1" :max="20160" />
            </el-form-item>
            <el-form-item label="建议模板编码">
              <el-input v-model="ruleForm.suggestTemplateCode" />
            </el-form-item>
            <el-form-item label="情绪组合 JSON">
              <el-input v-model="ruleForm.emotionComboJson" type="textarea" :rows="5" />
            </el-form-item>
          </el-form>
          <div class="actions">
            <el-button type="primary" :loading="saving" @click="saveRule">保存参数</el-button>
          </div>
        </template>
      </el-card>

      <el-card shadow="hover">
        <template #header>模型版本信息（只读）</template>
        <EmptyState
          v-if="models.length === 0"
          title="暂无模型注册数据"
          description="请在模型治理页创建或同步模型版本。"
          action-text="刷新"
          @action="loadData"
        />
        <el-table v-else :data="models" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="model_name" label="模型名称" min-width="160" />
          <el-table-column prop="model_type" label="模型类型" width="130" />
          <el-table-column prop="version" label="版本" width="130" />
          <el-table-column prop="env" label="环境" width="90" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="published_at" label="发布时间" min-width="170" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions {
  display: flex;
  justify-content: flex-end;
}

:deep(.el-form-item .el-input-number + .el-input-number) {
  margin-left: 10px;
}
</style>
