<script setup lang="ts">
import { computed, ref } from 'vue'

import { getNearbyPsyCenters, getPsyCentersByCity, type PsyCenter } from '@/api/psyCenter'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const mode = ref<'city' | 'nearby'>('city')
const cityCode = ref('310100')
const latitude = ref<number | null>(31.2304)
const longitude = ref<number | null>(121.4737)
const radiusKm = ref(10)

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const centers = ref<PsyCenter[]>([])

const hasResult = computed(() => centers.value.length > 0)

const searchCenters = async () => {
  loading.value = true
  errorState.value = null
  try {
    centers.value =
      mode.value === 'city'
        ? await getPsyCentersByCity(cityCode.value)
        : await getNearbyPsyCenters(Number(latitude.value), Number(longitude.value), radiusKm.value)
  } catch (error) {
    errorState.value = parseError(error, '心理中心资源加载失败')
  } finally {
    loading.value = false
  }
}

void searchCenters()
</script>

<template>
  <el-card>
    <template #header>心理中心资源查询</template>

    <el-alert
      title="附近查询当前仅按 radiusKm 过滤，不返回 distanceKm，排序规则以后端配置为准。"
      type="info"
      :closable="false"
      class="mb-12"
      show-icon
    />

    <el-form inline>
      <el-form-item label="查询方式">
        <el-radio-group v-model="mode">
          <el-radio-button value="city">按城市</el-radio-button>
          <el-radio-button value="nearby">按附近</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="mode === 'city'" label="城市编码">
        <el-input v-model="cityCode" placeholder="如 310100" />
      </el-form-item>

      <template v-else>
        <el-form-item label="纬度"><el-input-number v-model="latitude" :step="0.0001" :precision="6" /></el-form-item>
        <el-form-item label="经度"><el-input-number v-model="longitude" :step="0.0001" :precision="6" /></el-form-item>
        <el-form-item label="半径(km)"><el-input-number v-model="radiusKm" :min="1" :max="100" /></el-form-item>
      </template>

      <el-form-item>
        <el-button type="primary" @click="searchCenters">查询</el-button>
      </el-form-item>
    </el-form>

    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="searchCenters"
    />

    <el-empty v-else-if="!hasResult" description="暂无心理中心资源" />

    <el-table v-else :data="centers" class="mt-16">
      <el-table-column prop="name" label="机构名称" min-width="220" />
      <el-table-column prop="cityCode" label="城市编码" width="120" />
      <el-table-column prop="address" label="地址" min-width="260" />
      <el-table-column prop="phone" label="联系电话" width="150" />
      <el-table-column label="坐标" min-width="220">
        <template #default="scope">{{ scope.row.latitude ?? '-' }}, {{ scope.row.longitude ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="推荐" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.isRecommended ? 'success' : 'info'">{{ scope.row.isRecommended ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.mt-16 {
  margin-top: 16px;
}

.mb-12 {
  margin-bottom: 12px;
}
</style>
