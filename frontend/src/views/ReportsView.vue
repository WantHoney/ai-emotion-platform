<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { getReportList, type ReportSummary } from '@/api/report'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const router = useRouter()
const loading = ref(false)
const rows = ref<ReportSummary[]>([])
const total = ref(0)
const errorState = ref<ErrorStatePayload | null>(null)

const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
  riskLevel: '' as '' | 'low' | 'medium' | 'high',
  emotion: '',
})

const loadReports = async () => {
  loading.value = true
  errorState.value = null
  try {
    const { data } = await getReportList(query)
    rows.value = data.items ?? data.list ?? []
    total.value = data.total
  } catch (error) {
    errorState.value = parseError(error, '报告列表加载失败')
  } finally {
    loading.value = false
  }
}

void loadReports()
</script>

<template>
  <el-card>
    <template #header>报告中心</template>

    <el-form inline>
      <el-form-item label="风险等级">
        <el-select v-model="query.riskLevel" clearable style="width: 140px">
          <el-option label="low" value="low" />
          <el-option label="medium" value="medium" />
          <el-option label="high" value="high" />
        </el-select>
      </el-form-item>
      <el-form-item label="情绪筛选">
        <el-input v-model="query.emotion" placeholder="如 calm/anxious" clearable />
      </el-form-item>
      <el-form-item label="搜索">
        <el-input v-model="query.keyword" placeholder="报告ID/任务ID" clearable />
      </el-form-item>
      <el-button type="primary" @click="loadReports">查询</el-button>
    </el-form>

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
      description="当前没有可展示的分析报告。"
      action-text="立即刷新"
      @action="loadReports"
    />
    <template v-else>
      <el-table :data="rows" border>
        <el-table-column prop="id" label="报告ID" width="100" />
        <el-table-column prop="taskId" label="关联任务" width="100" />
        <el-table-column prop="overall" label="综合情绪" width="120" />
        <el-table-column prop="riskLevel" label="风险" width="100" />
        <el-table-column prop="createdAt" label="生成时间" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="router.push(`/reports/${scope.row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @change="loadReports"
        />
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
