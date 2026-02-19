<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getReportList, type ReportSummary } from '@/api/report'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import BadgeTag from '@/components/ui/BadgeTag.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const router = useRouter()
const loading = ref(false)
const rows = ref<ReportSummary[]>([])
const total = ref(0)
const errorState = ref<ErrorStatePayload | null>(null)

const query = reactive({
  page: 1,
  pageSize: 9,
  keyword: '',
  riskLevel: '' as '' | 'low' | 'medium' | 'high',
  emotion: '',
})

const toTone = (risk?: string): 'low' | 'medium' | 'high' | 'neutral' => {
  const value = (risk ?? '').toLowerCase()
  if (value.includes('low')) return 'low'
  if (value.includes('medium')) return 'medium'
  if (value.includes('high')) return 'high'
  return 'neutral'
}

const loadReports = async () => {
  loading.value = true
  errorState.value = null
  try {
    const { data } = await getReportList(query)
    rows.value = data.items ?? []
    total.value = Number(data.total ?? 0)
  } catch (error) {
    errorState.value = parseError(error, '报告列表加载失败')
  } finally {
    loading.value = false
  }
}

void loadReports()
</script>

<template>
  <div class="reports-page user-layout">
    <SectionBlock
      eyebrow="历史归档"
      title="报告中心"
      description="按条件筛选历史报告，避免信息拥挤。"
    >
      <div class="filters">
        <el-input v-model="query.keyword" placeholder="按报告ID/任务ID搜索" clearable />
        <el-input v-model="query.emotion" placeholder="按情绪筛选，例如 SAD" clearable />
        <el-select v-model="query.riskLevel" clearable placeholder="风险等级" style="width: 140px">
          <el-option label="低风险" value="low" />
          <el-option label="中风险" value="medium" />
          <el-option label="高风险" value="high" />
        </el-select>
        <el-button @click="router.push('/app/tasks')">进行中任务</el-button>
        <el-button type="primary" @click="loadReports">查询</el-button>
      </div>

      <LoadingState v-if="loading" />
      <ErrorState
        v-else-if="errorState"
        :title="errorState.title"
        :detail="errorState.detail"
        :trace-id="errorState.traceId"
        @retry="loadReports"
      />
      <EmptyState
        v-else-if="rows.length === 0"
        title="暂无报告"
        description="请调整筛选条件，或先上传语音生成报告。"
        action-text="重新加载"
        @action="loadReports"
      />
      <template v-else>
        <div class="report-cards">
          <LoreCard
            v-for="item in rows"
            :key="item.id"
            :title="item.reportNo || `REPORT-${item.id}`"
            :subtitle="item.taskNo || `TASK-${item.taskId}`"
            interactive
            @click="router.push(`/app/reports/${item.id}`)"
          >
            <div class="card-meta">
              <BadgeTag :tone="toTone(item.riskLevel)" :text="item.riskLevel || '风险未知'" />
              <span>{{ item.overall || '情绪未知' }}</span>
            </div>
            <p class="created-at">{{ item.createdAt || '时间未知' }}</p>
            <p class="id-hint">报告ID: {{ item.id }} | 任务ID: {{ item.taskId }}</p>
            <template #footer>
              <el-button type="primary" text @click.stop="router.push(`/app/reports/${item.id}`)">查看详情</el-button>
            </template>
          </LoreCard>
        </div>

        <div class="pager">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.pageSize"
            layout="total, prev, pager, next"
            :total="total"
            @change="loadReports"
          />
        </div>
      </template>
    </SectionBlock>
  </div>
</template>

<style scoped>
.reports-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.filters {
  display: grid;
  grid-template-columns: 1.3fr 1fr 140px auto auto;
  gap: 10px;
}

.report-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #c4d6f3;
  margin-bottom: 6px;
}

.created-at {
  margin: 0;
  color: #a9bedf;
  font-size: 13px;
}

.id-hint {
  margin: 4px 0 0;
  color: #8aa2c8;
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

@media (max-width: 960px) {
  .filters {
    grid-template-columns: 1fr;
  }

  .report-cards {
    grid-template-columns: 1fr;
  }
}
</style>

