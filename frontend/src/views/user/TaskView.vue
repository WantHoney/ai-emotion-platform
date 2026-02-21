<script setup lang="ts">
import { DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getResult, type AnalysisTaskResultDetail, type RiskAssessmentPayload } from '@/api/task'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { useTaskPolling } from '@/composables/useTaskPolling'

const route = useRoute()
const router = useRouter()

const taskId = computed(() => Number(route.params.id))

const { pollingState, task, errorMessage, statusText, start } = useTaskPolling(taskId, {
  baseIntervalMs: 3000,
  maxIntervalMs: 15000,
  timeoutMs: 1800000,
  maxRetry: 8,
})

const taskResult = ref<AnalysisTaskResultDetail | null>(null)
const resultLoading = ref(false)

const WEIGHT_SAD = 0.45
const WEIGHT_ANGRY = 0.25
const WEIGHT_VAR_CONF = 0.1
const WEIGHT_VOICE_IN_PSI = 0.6
const WEIGHT_TEXT_IN_PSI = 0.4

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value))

const toNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

const fetchTaskResult = async () => {
  if (!Number.isFinite(taskId.value) || taskId.value <= 0) return
  resultLoading.value = true
  try {
    const response = await getResult(taskId.value)
    taskResult.value = response.data
  } catch {
    taskResult.value = null
  } finally {
    resultLoading.value = false
  }
}

watch(
  () => [taskId.value, task.value?.status] as const,
  async ([id, status]) => {
    if (!Number.isFinite(id) || id <= 0) {
      taskResult.value = null
      return
    }
    if (status === 'SUCCESS') {
      await fetchTaskResult()
      return
    }
    if (status === 'FAILED' || status === 'CANCELED') {
      taskResult.value = null
    }
  },
)

const displayTaskNo = computed(() => task.value?.taskNo || `TASK-${taskId.value}`)
const durationSeconds = computed(() => ((task.value?.durationMs ?? 0) / 1000).toFixed(2))

const riskAssessment = computed<RiskAssessmentPayload | null>(() => taskResult.value?.riskAssessment ?? null)

const confidencePercent = computed(() => {
  const confidence = taskResult.value?.overallConfidence ?? task.value?.result?.confidence
  if (confidence == null || Number.isNaN(confidence)) return 0
  const normalized = confidence <= 1 ? confidence * 100 : confidence
  return Math.round(clamp(normalized, 0, 100))
})

const riskScorePercent = computed(() => {
  const score = riskAssessment.value?.risk_score ?? task.value?.result?.risk_score ?? 0
  const normalized = score <= 1 ? score * 100 : score
  return Math.round(clamp(normalized, 0, 100))
})

const voiceRiskScore = computed(() => {
  const source = riskAssessment.value
  if (!source) return 0
  return 100 * (WEIGHT_SAD * source.p_sad + WEIGHT_ANGRY * source.p_angry + WEIGHT_VAR_CONF * source.var_conf)
})

const textRiskScore = computed(() => {
  const source = riskAssessment.value
  if (!source) return 0
  return 100 * source.text_neg
})

const riskLevel = computed(() => riskAssessment.value?.risk_level ?? task.value?.result?.risk_level ?? '-')

const confidenceColor = computed(() => {
  if (confidencePercent.value >= 80) return '#67c23a'
  if (confidencePercent.value >= 60) return '#e6a23c'
  return '#f56c6c'
})

const riskColor = computed(() => {
  if (riskScorePercent.value >= 70) return '#f56c6c'
  if (riskScorePercent.value >= 40) return '#e6a23c'
  return '#67c23a'
})

const statusTagType = computed(() => {
  if (pollingState.value === 'success') return 'success'
  if (pollingState.value === 'error') return 'danger'
  return 'warning'
})

const parsedRawJson = computed<Record<string, unknown> | null>(() => {
  const raw = taskResult.value?.rawJson
  if (!raw) return null
  try {
    return JSON.parse(raw) as Record<string, unknown>
  } catch {
    return null
  }
})

const textFusionInfo = computed(() => {
  const root = parsedRawJson.value
  const textNegNode = (root?.textNeg ?? null) as Record<string, unknown> | null
  const textSentimentNode = (root?.textSentiment ?? null) as Record<string, unknown> | null
  const textNegFusionNode = (root?.textNegFusion ?? null) as Record<string, unknown> | null

  return {
    lexiconNeg: toNumber(textNegNode?.textNeg),
    modelNeg: toNumber(textSentimentNode?.negativeScore),
    fusedNeg: toNumber(textNegFusionNode?.fusedTextNeg) ?? riskAssessment.value?.text_neg,
    lexiconWeight: toNumber(textNegFusionNode?.lexiconWeight),
    modelWeight: toNumber(textNegFusionNode?.modelWeight),
  }
})

const serFusionInfo = computed(() => {
  const root = parsedRawJson.value
  const serNode = (root?.ser ?? null) as Record<string, unknown> | null
  const fusionNode = (serNode?.fusion ?? null) as Record<string, unknown> | null
  const scoresNode = (fusionNode?.scores ?? null) as Record<string, unknown> | null

  return {
    enabled: Boolean(fusionNode?.enabled),
    ready: Boolean(fusionNode?.ready),
    label: typeof fusionNode?.label === 'string' ? fusionNode.label : undefined,
    confidence: toNumber(fusionNode?.confidence),
    error: typeof fusionNode?.error === 'string' ? fusionNode.error : undefined,
    scoreAngry: toNumber(scoresNode?.ANGRY),
    scoreHappy: toNumber(scoresNode?.HAPPY),
    scoreNeutral: toNumber(scoresNode?.NEUTRAL),
    scoreSad: toNumber(scoresNode?.SAD),
  }
})

const psiContributionRows = computed(() => {
  const source = riskAssessment.value
  if (!source) return []

  const sadPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_SAD * source.p_sad
  const angryPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_ANGRY * source.p_angry
  const varPart = 100 * WEIGHT_VOICE_IN_PSI * WEIGHT_VAR_CONF * source.var_conf
  const textPart = 100 * WEIGHT_TEXT_IN_PSI * source.text_neg

  const rows = [
    { key: 'sad', label: '悲伤', value: sadPart, formula: '0.6*100*0.45*p_sad' },
    { key: 'angry', label: '愤怒', value: angryPart, formula: '0.6*100*0.25*p_angry' },
    { key: 'var', label: '波动', value: varPart, formula: '0.6*100*0.10*var_conf' },
    { key: 'text', label: '文本', value: textPart, formula: '0.4*100*text_neg' },
  ]

  const total = Math.max(riskScorePercent.value, 0.0001)
  return rows.map((row) => ({
    ...row,
    percent: clamp((row.value / total) * 100, 0, 100),
  }))
})

const contributionStatus = (key: string): 'success' | 'warning' | 'exception' => {
  if (key === 'text') return 'success'
  if (key === 'sad' || key === 'angry') return 'warning'
  return 'exception'
}

const copyTaskNo = async () => {
  try {
    await navigator.clipboard.writeText(displayTaskNo.value)
    ElMessage.success('任务编号已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

onMounted(() => {
  void start()
})
</script>

<template>
  <div class="task-detail-page">
    <el-card class="hero-card" shadow="never">
      <div class="hero-header">
        <div>
          <p class="hero-subtitle">情绪分析任务</p>
          <h2>任务编号 {{ displayTaskNo }}</h2>
          <p class="task-id-tip">任务ID: {{ taskId }}</p>
        </div>
        <div class="header-actions">
          <el-tag effect="dark" :type="statusTagType">{{ statusText }}</el-tag>
          <el-button size="small" :icon="DocumentCopy" @click="copyTaskNo">复制编号</el-button>
        </div>
      </div>

      <LoadingState v-if="pollingState === 'loading' && !task" />
      <ErrorState
        v-else-if="pollingState === 'error'"
        title="任务加载失败"
        :detail="errorMessage"
        :trace-id="task?.traceId"
        @retry="start"
      />
      <EmptyState
        v-else-if="!task"
        title="任务不可用"
        description="该任务暂时不可用，可能仍在初始化中。"
        action-text="重新加载"
        @action="start"
      />
      <div v-else class="metric-grid">
        <el-card class="metric-item" shadow="hover">
          <p>综合情绪</p>
          <h3>{{ task.result?.overall ?? '-' }}</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>风险等级</p>
          <h3>{{ riskLevel }}</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>处理耗时</p>
          <h3>{{ durationSeconds }}s</h3>
        </el-card>
        <el-card class="metric-item" shadow="hover">
          <p>重试次数</p>
          <h3>{{ task.attemptCount ?? '-' }}</h3>
        </el-card>
      </div>
    </el-card>

    <template v-if="task?.result">
      <el-row :gutter="16" class="chart-row">
        <el-col :xs="24" :md="12">
          <el-card shadow="hover">
            <template #header>置信度</template>
            <el-progress type="dashboard" :percentage="confidencePercent" :color="confidenceColor" :stroke-width="12" />
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="hover">
            <template #header>风险分数</template>
            <el-progress type="dashboard" :percentage="riskScorePercent" :color="riskColor" :stroke-width="12" />
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="fusion-row">
        <el-col :xs="24" :lg="12">
          <el-card shadow="hover">
            <template #header>融合结果（语音分 / 文本分 / PSI）</template>
            <el-descriptions border :column="1">
              <el-descriptions-item label="语音分">{{ voiceRiskScore.toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="文本分">{{ textRiskScore.toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="融合分(PSI)">{{ riskScorePercent.toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="风险等级">{{ riskLevel }}</el-descriptions-item>
              <el-descriptions-item label="text_neg(负向)">
                {{ riskAssessment?.text_neg != null ? riskAssessment.text_neg.toFixed(4) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="SER融合就绪">
                {{ serFusionInfo.ready ? '是' : serFusionInfo.enabled ? '否' : '已禁用' }}
              </el-descriptions-item>
              <el-descriptions-item label="SER融合标签">
                {{ serFusionInfo.label ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="SER融合置信度">
                {{ serFusionInfo.confidence != null ? `${(serFusionInfo.confidence * 100).toFixed(2)}%` : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="SER概率(怒/喜/中/悲)">
                {{
                  serFusionInfo.scoreAngry != null &&
                  serFusionInfo.scoreHappy != null &&
                  serFusionInfo.scoreNeutral != null &&
                  serFusionInfo.scoreSad != null
                    ? `${serFusionInfo.scoreAngry.toFixed(4)} / ${serFusionInfo.scoreHappy.toFixed(4)} / ${serFusionInfo.scoreNeutral.toFixed(4)} / ${serFusionInfo.scoreSad.toFixed(4)}`
                    : '-'
                }}
              </el-descriptions-item>
              <el-descriptions-item v-if="serFusionInfo.error" label="SER融合错误">
                {{ serFusionInfo.error }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="hover">
            <template #header>文本融合细项（词典 + 模型）</template>
            <el-descriptions border :column="1">
              <el-descriptions-item label="词典负向分">
                {{ textFusionInfo.lexiconNeg != null ? textFusionInfo.lexiconNeg.toFixed(4) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="模型负向分">
                {{ textFusionInfo.modelNeg != null ? textFusionInfo.modelNeg.toFixed(4) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="融合负向分">
                {{ textFusionInfo.fusedNeg != null ? textFusionInfo.fusedNeg.toFixed(4) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="融合权重">
                {{
                  textFusionInfo.lexiconWeight != null && textFusionInfo.modelWeight != null
                    ? `${textFusionInfo.lexiconWeight.toFixed(2)} : ${textFusionInfo.modelWeight.toFixed(2)}`
                    : '-'
                }}
              </el-descriptions-item>
            </el-descriptions>
            <p v-if="resultLoading" class="loading-tip">正在同步任务结果细节...</p>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover">
        <template #header>PSI 贡献项</template>
        <div v-if="psiContributionRows.length" class="psi-list">
          <article v-for="row in psiContributionRows" :key="row.key" class="psi-item">
            <div class="psi-header">
              <strong>{{ row.label }}</strong>
              <span>{{ row.value.toFixed(2) }} ({{ row.percent.toFixed(1) }}%)</span>
            </div>
            <el-progress :percentage="Number(row.percent.toFixed(1))" :status="contributionStatus(row.key)" :stroke-width="10" />
            <p class="psi-formula">{{ row.formula }}</p>
          </article>
        </div>
        <EmptyState
          v-else
          title="暂无贡献项数据"
          description="风险贡献细项暂未生成。"
          action-text="刷新任务"
          @action="start"
        />
      </el-card>

      <el-card shadow="hover">
        <template #header>任务详情</template>
        <el-descriptions border :column="2">
          <el-descriptions-item label="任务状态">{{ task.status ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ task.errorMessage ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="Trace ID">{{ task.traceId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="SER 延迟">{{ task.serLatencyMs ?? '-' }}ms</el-descriptions-item>
          <el-descriptions-item label="建议" :span="2">{{ task.result?.advice_text ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          title="分析在服务端持续执行，离开当前页面只会停止前端轮询。"
          type="info"
          :closable="false"
          class="polling-hint"
          show-icon
        />

        <div class="actions">
          <el-button @click="router.push('/app/tasks')">返回列表</el-button>
          <el-button @click="router.push(`/app/tasks/${taskId}/timeline`)">查看时间线</el-button>
          <el-button type="primary" @click="router.push('/app/reports')">前往报告中心</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.task-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card {
  border: 0;
  background: linear-gradient(135deg, #eff6ff 0%, #faf5ff 100%);
}

.hero-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.hero-subtitle {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.hero-header h2 {
  margin: 6px 0 0;
  font-size: 24px;
  color: #1e293b;
}

.task-id-tip {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-item :deep(.el-card__body) {
  padding: 14px;
}

.metric-item p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.metric-item h3 {
  margin: 8px 0 0;
  font-size: 20px;
  color: #0f172a;
}

.chart-row {
  margin: 0;
}

.fusion-row {
  margin: 0;
}

.loading-tip {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 12px;
}

.psi-list {
  display: grid;
  gap: 12px;
}

.psi-item {
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  padding: 12px;
}

.psi-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  gap: 8px;
}

.psi-formula {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
}

.polling-hint {
  margin-top: 12px;
}

.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}

@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>

