<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { getSystemStatus, type RuntimeModelInfo, type SystemStatus } from '@/api/system'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { SER_LABEL, formatEnv, formatModelType, formatServiceStatus } from '@/utils/uiText'

const loading = ref(false)
const data = ref<SystemStatus | null>(null)
const errorState = ref<ErrorStatePayload | null>(null)

const runtimeModels = computed<RuntimeModelInfo[]>(() => {
  const models = data.value?.runtime?.models
  if (!models) return []
  return [models.asr, models.audioEmotion, models.text, models.fusion, models.psi].filter(
    (item): item is RuntimeModelInfo => Boolean(item),
  )
})

const suggestions = computed(() => {
  const list: string[] = []
  if (data.value?.backend?.status !== 'UP') {
    list.push('后端服务异常，建议优先检查 Spring Boot 日志、数据库连接与最近一次热更新。')
  }
  if (data.value?.ser?.status === 'DOWN') {
    list.push(`${SER_LABEL} 当前离线，建议先恢复语音服务后再提交分析任务。`)
  }
  if ((data.value?.metrics?.avgSerLatencyMs ?? 0) > 2000) {
    list.push(`${SER_LABEL} 平均延迟偏高，建议核对本地模型加载、GPU 占用与音频处理链路。`)
  }
  if (data.value?.runtime?.textScoringProvider === 'ollama' && data.value?.runtime?.aiMode === 'mock') {
    list.push('当前为 mock + 本地语义混合模式，适合开发与答辩演示，请在对外说明时明确口径。')
  }
  if (!list.length) {
    list.push('系统整体运行正常，当前配置适合继续演示与管理端校验。')
  }
  return list
})

const loadStatus = async () => {
  loading.value = true
  errorState.value = null
  try {
    const response = await getSystemStatus()
    data.value = response.data
  } catch (error) {
    errorState.value = parseError(error, '系统状态加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadStatus()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>系统状态</span>
        <el-button @click="loadStatus">刷新</el-button>
      </div>
    </template>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadStatus"
    />
    <EmptyState
      v-else-if="!data"
      title="暂无系统数据"
      description="还没有拉取到当前运行状态。"
      action-text="重新加载"
      @action="loadStatus"
    />
    <template v-else>
      <el-alert
        type="info"
        :closable="false"
        title="本页用于核对当前运行模式、服务健康状态和实际运行模型，适合作为运维与答辩排查入口。"
      />

      <el-row :gutter="16" class="mt-16">
        <el-col :xs="24" :md="8">
          <el-card shadow="never" class="status-card">
            <div class="status-card__title">后端服务</div>
            <div class="status-card__value">{{ formatServiceStatus(data.backend?.status) }}</div>
            <div class="status-card__meta">{{ data.backend?.latencyMs ?? '-' }} ms</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="8">
          <el-card shadow="never" class="status-card">
            <div class="status-card__title">数据库</div>
            <div class="status-card__value">{{ formatServiceStatus(data.db?.status) }}</div>
            <div class="status-card__meta">{{ data.db?.latencyMs ?? '-' }} ms</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="8">
          <el-card shadow="never" class="status-card">
            <div class="status-card__title">{{ SER_LABEL }}</div>
            <div class="status-card__value">{{ formatServiceStatus(data.ser?.status) }}</div>
            <div class="status-card__meta">{{ data.ser?.latencyMs ?? '-' }} ms</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt-16">
        <el-col :xs="24" :md="6"><el-statistic title="运行中任务" :value="data.metrics?.runningTasks ?? 0" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="排队任务" :value="data.metrics?.queuedTasks ?? 0" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic title="24 小时失败任务" :value="data.metrics?.failedTasks24h ?? 0" /></el-col>
        <el-col :xs="24" :md="6"><el-statistic :title="`${SER_LABEL} 平均延迟`" :value="data.metrics?.avgSerLatencyMs ?? 0" suffix="ms" /></el-col>
      </el-row>

      <el-row :gutter="16" class="mt-16">
        <el-col :xs="24" :md="10">
          <el-card shadow="never">
            <template #header>运行模式摘要</template>
            <div class="mode-item">
              <span>当前模式</span>
              <strong>{{ data.runtime?.aiMode || '-' }}</strong>
            </div>
            <div class="mode-item">
              <span>模型登记环境</span>
              <strong>{{ formatEnv(data.runtime?.registryEnvHint || data.config?.runtimeRegistryEnvHint) }}</strong>
            </div>
            <div class="mode-item">
              <span>文本评分 Provider</span>
              <strong>{{ data.runtime?.textScoringProvider || '-' }}</strong>
            </div>
            <div class="mode-item">
              <span>叙事生成 Provider</span>
              <strong>{{ data.runtime?.narrativeProvider || '-' }}</strong>
            </div>
            <div class="mode-item">
              <span>文本回退到 SER</span>
              <strong>{{ data.runtime?.textScoringFallbackToSer ? '已开启' : '未开启' }}</strong>
            </div>
            <p class="mode-description">{{ data.runtime?.modeDescription || '当前模式说明不可用。' }}</p>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="14">
          <el-card shadow="never">
            <template #header>当前实际运行模型</template>
            <EmptyState
              v-if="runtimeModels.length === 0"
              title="暂无运行模型摘要"
              description="系统尚未返回运行时模型信息。"
              action-text="刷新"
              @action="loadStatus"
            />
            <el-table v-else :data="runtimeModels" size="small" border>
              <el-table-column label="模块" width="140">
                <template #default="scope">{{ formatModelType(scope.row.modelType) }}</template>
              </el-table-column>
              <el-table-column prop="label" label="当前实际运行标识" min-width="220" show-overflow-tooltip />
              <el-table-column prop="source" label="来源" width="150" />
              <el-table-column label="状态" width="120">
                <template #default="scope">
                  <el-tag :type="scope.row.status === 'UP' ? 'success' : scope.row.status === 'UNKNOWN' ? 'info' : 'warning'">
                    {{ formatServiceStatus(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="detail" label="说明" min-width="220" show-overflow-tooltip />
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="mt-16">
        <template #header>配置与排查建议</template>
        <div class="config-grid">
          <div class="config-item">
            <span>{{ SER_LABEL }} 地址</span>
            <strong>{{ data.config?.serBaseUrl || '-' }}</strong>
          </div>
          <div class="config-item">
            <span>请求超时</span>
            <strong>{{ data.config?.requestTimeoutMs ?? '-' }} ms</strong>
          </div>
          <div class="config-item">
            <span>当前激活 Profile</span>
            <strong>{{ (data.runtime?.activeProfiles || []).join(', ') || 'default' }}</strong>
          </div>
        </div>
        <el-alert
          v-for="item in suggestions"
          :key="item"
          :title="item"
          type="warning"
          show-icon
          :closable="false"
          class="mt-12"
        />
      </el-card>
    </template>
  </el-card>
</template>

<style scoped>
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mt-16 {
  margin-top: 16px;
}

.mt-12 {
  margin-top: 12px;
}

.status-card {
  min-height: 132px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}

.status-card__title,
.mode-item span,
.config-item span {
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.status-card__value {
  color: var(--admin-text-primary);
  font-size: 26px;
  font-weight: 700;
}

.status-card__meta,
.mode-description {
  color: var(--admin-text-secondary);
  line-height: 1.6;
}

.mode-item,
.config-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(96, 119, 158, 0.16);
}

.mode-item:last-of-type,
.config-item:last-of-type {
  border-bottom: none;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px 18px;
}

:deep(.el-statistic__head) {
  color: var(--admin-text-secondary);
}

:deep(.el-statistic__content),
:deep(.el-statistic__number) {
  color: var(--admin-text-primary);
}
</style>
