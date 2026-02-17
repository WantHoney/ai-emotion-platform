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
    errorState.value = parseError(error, 'Failed to load psychology centers')
  } finally {
    loading.value = false
  }
}

const locateAndSearch = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('Current browser does not support geolocation')
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
      ElMessage.warning('Unable to fetch location, please input coordinates manually')
    },
    { enableHighAccuracy: true, timeout: 6000 },
  )
}

void searchCenters()
</script>

<template>
  <div class="centers-page user-layout">
    <SectionBlock
      eyebrow="Resource Network"
      title="Psychological Support Centers"
      description="Search support institutions by city code or nearby coordinates. This page remains stable even if API fails."
    >
      <div class="toolbar">
        <el-radio-group v-model="mode">
          <el-radio-button value="city">By City</el-radio-button>
          <el-radio-button value="nearby">By Nearby</el-radio-button>
        </el-radio-group>

        <el-input
          v-if="mode === 'city'"
          v-model="cityCode"
          placeholder="City code, e.g. 310100"
          style="max-width: 220px"
        />

        <template v-else>
          <el-input-number v-model="latitude" :step="0.0001" :precision="6" controls-position="right" />
          <el-input-number v-model="longitude" :step="0.0001" :precision="6" controls-position="right" />
          <el-input-number v-model="radiusKm" :min="1" :max="100" controls-position="right" />
        </template>

        <el-button type="primary" @click="searchCenters">Search</el-button>
        <el-button @click="locateAndSearch">Locate Me</el-button>
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
        title="No center data"
        description="No institution found under current filter. Try another city or radius."
        action-text="Retry"
        @action="searchCenters"
      />
      <template v-else>
        <div class="center-grid">
          <LoreCard
            v-for="center in centers"
            :key="`${center.id}-${center.name}`"
            :title="center.name"
            :subtitle="`${center.cityName || ''} ${center.district || ''}`.trim() || 'Unknown district'"
          >
            <p class="line"><strong>Address:</strong> {{ center.address || 'N/A' }}</p>
            <p class="line"><strong>Phone:</strong> {{ center.phone || 'N/A' }}</p>
            <p class="line">
              <strong>Coordinate:</strong>
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
