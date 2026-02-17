<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getNearbyPsyCenters, getPsyCentersByCity, type PsyCenter } from '@/api/psyCenter'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const mode = ref<'city' | 'nearby'>('city')
const cityCode = ref('310100')
const latitude = ref<number | null>(31.2304)
const longitude = ref<number | null>(121.4737)
const radiusKm = ref(10)

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const centers = ref<PsyCenter[]>([])

const searchCenters = async () => {
  loading.value = true
  errorState.value = null
  try {
    centers.value =
      mode.value === 'city'
        ? await getPsyCentersByCity(cityCode.value)
        : await getNearbyPsyCenters(Number(latitude.value), Number(longitude.value), radiusKm.value)
  } catch (error) {
    errorState.value = parseError(error, '心理中心数据加载失败')
  } finally {
    loading.value = false
  }
}

const locateAndSearch = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位')
    return
  }
  mode.value = 'nearby'
  loading.value = true
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      latitude.value = Number(position.coords.latitude.toFixed(6))
      longitude.value = Number(position.coords.longitude.toFixed(6))
      await searchCenters()
    },
    () => {
      loading.value = false
      ElMessage.warning('定位失败，请手动输入坐标')
    },
    { enableHighAccuracy: true, timeout: 6000 },
  )
}

void searchCenters()
</script>

<template>
  <div class="centers-page user-layout">
    <SectionBlock
      eyebrow="资源网络"
      title="心理支持中心"
      description="支持按城市编码或附近坐标查询；即使 API 异常页面也不会白屏。"
    >
      <div class="toolbar">
        <el-radio-group v-model="mode">
          <el-radio-button value="city">按城市</el-radio-button>
          <el-radio-button value="nearby">按附近</el-radio-button>
        </el-radio-group>

        <el-input
          v-if="mode === 'city'"
          v-model="cityCode"
          placeholder="城市编码，例如 310100"
          style="max-width: 220px"
        />

        <template v-else>
          <el-input-number v-model="latitude" :step="0.0001" :precision="6" controls-position="right" />
          <el-input-number v-model="longitude" :step="0.0001" :precision="6" controls-position="right" />
          <el-input-number v-model="radiusKm" :min="1" :max="100" controls-position="right" />
        </template>

        <el-button type="primary" @click="searchCenters">查询</el-button>
        <el-button @click="locateAndSearch">定位我</el-button>
      </div>

      <LoadingState v-if="loading" />
      <ErrorState
        v-else-if="errorState"
        :title="errorState.title"
        :detail="errorState.detail"
        :trace-id="errorState.traceId"
        @retry="searchCenters"
      />
      <EmptyState
        v-else-if="centers.length === 0"
        title="暂无心理中心数据"
        description="当前筛选条件下未找到机构，请尝试其他城市或半径。"
        action-text="重试"
        @action="searchCenters"
      />
      <template v-else>
        <div class="center-grid">
          <LoreCard
            v-for="center in centers"
            :key="`${center.id}-${center.name}`"
            :title="center.name"
            :subtitle="`${center.cityName || ''} ${center.district || ''}`.trim() || '地区未知'"
          >
            <p class="line"><strong>地址：</strong> {{ center.address || '暂无' }}</p>
            <p class="line"><strong>电话：</strong> {{ center.phone || '暂无' }}</p>
            <p class="line">
              <strong>坐标：</strong>
              {{ center.latitude ?? '-' }}, {{ center.longitude ?? '-' }}
            </p>
          </LoreCard>
        </div>
      </template>
    </SectionBlock>
  </div>
</template>

<style scoped>
.centers-page {
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.center-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.line {
  margin: 6px 0 0;
  color: #c9daf3;
  line-height: 1.6;
}

.line strong {
  color: #f2f7ff;
  font-weight: 600;
}

@media (max-width: 980px) {
  .center-grid {
    grid-template-columns: 1fr;
  }
}
</style>
