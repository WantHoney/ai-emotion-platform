<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { getAdminModels, getWarningRules, type ModelRegistryItem, type WarningRuleItem } from '@/api/governance'
import { getSystemStatus, type SystemStatus } from '@/api/system'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { JSON_LABEL, SLA_LABEL, formatEnv, formatModelType } from '@/utils/uiText'

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)

const rules = ref<WarningRuleItem[]>([])
const models = ref<ModelRegistryItem[]>([])
const systemStatus = ref<SystemStatus | null>(null)

const activeRule = computed(
  () => rules.value.find((item) => item.enabled === true || item.enabled === 1) ?? rules.value[0] ?? null,
)
const enabledRuleCount = computed(() =>
  rules.value.filter((item) => item.enabled === true || item.enabled === 1).length,
)
const registryEnvHint = computed(
  () => systemStatus.value?.runtime?.registryEnvHint || systemStatus.value?.config?.runtimeRegistryEnvHint || 'prod',
)
const onlineModels = computed(() =>
  models.value.filter((item) => item.status === 'ONLINE' && item.env === registryEnvHint.value),
)

const emotionComboPreview = computed(() => {
  const combo = activeRule.value?.emotion_combo_json
  if (!combo) return `${JSON_LABEL}: {}`
  return typeof combo === 'string' ? combo : JSON.stringify(combo, null, 2)
})

const loadData = async () => {
  loading.value = true
  errorState.value = null
  try {
    const [ruleRows, modelRows, statusResponse] = await Promise.all([
      getWarningRules(),
      getAdminModels(),
      getSystemStatus(),
    ])
    rules.value = ruleRows
    models.value = modelRows
    systemStatus.value = statusResponse.data
  } catch (error) {
    errorState.value = parseError(error, '系统设置摘要加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadData()
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
      <el-alert
        class="page-alert"
        type="info"
        :closable="false"
        title="本页只展示当前生效规则、运行模式与在线模型摘要。如需修改规则，请前往“预警规则”页。"
      />

      <el-row :gutter="16">
        <el-col :xs="24" :md="6">
          <el-card shadow="hover">
            <div class="summary-title">启用规则数</div>
            <div class="summary-value">{{ enabledRuleCount }}</div>
            <div class="summary-note">规则编辑入口统一收口在“预警规则”页。</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="6">
          <el-card shadow="hover">
            <div class="summary-title">当前生效规则</div>
            <div class="summary-value summary-value--compact">{{ activeRule?.rule_name || '-' }}</div>
            <div class="summary-note">{{ activeRule?.rule_code || '当前还没有启用规则' }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="6">
          <el-card shadow="hover">
            <div class="summary-title">风险阈值</div>
            <div class="summary-value summary-value--compact">
              {{ activeRule ? `${activeRule.low_threshold} / ${activeRule.medium_threshold} / ${activeRule.high_threshold}` : '-' }}
            </div>
            <div class="summary-note">低 / 中 / 高风险阈值</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="6">
          <el-card shadow="hover">
            <div class="summary-title">{{ SLA_LABEL }}</div>
            <div class="summary-value summary-value--compact">
              {{
                activeRule
                  ? `${activeRule.sla_low_minutes ?? '-'} / ${activeRule.sla_medium_minutes ?? '-'} / ${activeRule.sla_high_minutes ?? '-'}`
                  : '-'
              }}
            </div>
            <div class="summary-note">低 / 中 / 高风险响应时限（分钟）</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt-16">
        <el-col :xs="24" :md="11">
          <el-card shadow="hover">
            <template #header>当前生效规则摘要</template>
            <EmptyState
              v-if="!activeRule"
              title="暂无规则摘要"
              description="请先在预警规则页创建并启用规则。"
              action-text="刷新"
              @action="loadData"
            />
            <template v-else>
              <div class="detail-item">
                <span>规则名称</span>
                <strong>{{ activeRule.rule_name }}</strong>
              </div>
              <div class="detail-item">
                <span>规则编码</span>
                <strong>{{ activeRule.rule_code }}</strong>
              </div>
              <div class="detail-item">
                <span>优先级</span>
                <strong>{{ activeRule.priority }}</strong>
              </div>
              <div class="detail-item">
                <span>趋势窗口 / 触发次数</span>
                <strong>{{ activeRule.trend_window_days }} 天 / {{ activeRule.trigger_count }} 次</strong>
              </div>
              <div class="detail-item">
                <span>建议模板</span>
                <strong>{{ activeRule.suggest_template_code || '-' }}</strong>
              </div>
              <div class="detail-item detail-item--block">
                <span>规则说明</span>
                <p>{{ activeRule.description || '当前规则未填写额外说明。' }}</p>
              </div>
              <div class="detail-item detail-item--block">
                <span>情绪组合配置（{{ JSON_LABEL }}）</span>
                <pre class="json-preview">{{ emotionComboPreview }}</pre>
              </div>
            </template>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="13">
          <el-card shadow="hover">
            <template #header>运行模式与环境摘要</template>
            <div class="detail-item">
              <span>当前模式</span>
              <strong>{{ systemStatus?.runtime?.aiMode || '-' }}</strong>
            </div>
            <div class="detail-item">
              <span>模型登记环境</span>
              <strong>{{ formatEnv(registryEnvHint) }}</strong>
            </div>
            <div class="detail-item">
              <span>文本评分 Provider</span>
              <strong>{{ systemStatus?.runtime?.textScoringProvider || '-' }}</strong>
            </div>
            <div class="detail-item">
              <span>叙事生成 Provider</span>
              <strong>{{ systemStatus?.runtime?.narrativeProvider || '-' }}</strong>
            </div>
            <div class="detail-item">
              <span>文本回退到 SER</span>
              <strong>{{ systemStatus?.runtime?.textScoringFallbackToSer ? '已开启' : '未开启' }}</strong>
            </div>
            <div class="detail-item detail-item--block">
              <span>模式说明</span>
              <p>{{ systemStatus?.runtime?.modeDescription || '暂无模式说明。' }}</p>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover" class="mt-16">
        <template #header>当前在线模型摘要（只读）</template>
        <EmptyState
          v-if="onlineModels.length === 0"
          title="暂无在线模型登记"
          description="当前环境下还没有登记为在线的模型记录。"
          action-text="刷新"
          @action="loadData"
        />
        <el-table v-else :data="onlineModels" border>
          <el-table-column label="模型类型" width="150">
            <template #default="scope">{{ formatModelType(scope.row.model_type) }}</template>
          </el-table-column>
          <el-table-column prop="model_name" label="模型名称" min-width="180" />
          <el-table-column prop="version" label="登记版本" min-width="160" />
          <el-table-column label="环境" width="120">
            <template #default="scope">{{ formatEnv(scope.row.env) }}</template>
          </el-table-column>
          <el-table-column prop="provider" label="提供方" width="140" />
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

.page-alert {
  margin-bottom: 4px;
}

.mt-16 {
  margin-top: 16px;
}

.summary-title,
.detail-item span {
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.summary-value {
  margin-top: 10px;
  color: var(--admin-text-primary);
  font-size: 30px;
  font-weight: 700;
}

.summary-value--compact {
  font-size: 20px;
  line-height: 1.4;
}

.summary-note,
.detail-item p {
  margin: 10px 0 0;
  color: var(--admin-text-secondary);
  line-height: 1.6;
}

.detail-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(96, 119, 158, 0.16);
}

.detail-item--block {
  display: block;
}

.detail-item:last-child {
  border-bottom: none;
}

.json-preview {
  margin: 10px 0 0;
  padding: 12px;
  border-radius: 12px;
  background: rgba(12, 21, 38, 0.76);
  color: var(--admin-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
