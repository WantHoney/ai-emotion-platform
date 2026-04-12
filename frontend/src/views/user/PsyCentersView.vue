<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'

import { getNearbyPsyCenters, getPsyCentersByCity, type PsyCenter } from '@/api/psyCenter'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import MediaFeatureCard from '@/components/ui/MediaFeatureCard.vue'
import SmartImage from '@/components/ui/SmartImage.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import { PSY_CENTER_CITY_OPTIONS, PSY_CENTER_CITY_REFERENCES, SOURCE_LEVEL_LABELS } from '@/constants/contentMeta'
import { resolvePsyCenterPosterUrl } from '@/utils/contentMedia'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const route = useRoute()
const cityCode = ref('310100')
const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const centers = ref<PsyCenter[]>([])
const queryMode = ref<'city' | 'nearby'>('city')
const lastCoords = ref<{ latitude: number; longitude: number } | null>(null)
const nearbyOutOfCoverage = ref(false)
const nearbyCityLabel = ref('')
const radiusKm = 15
const PSY_CENTER_HERO_VERSION = '20260404-sd35-v1'
const SUPPORTED_CITY_OPTION_MAP = new Map<string, string>(PSY_CENTER_CITY_OPTIONS.map((item) => [item.value, item.label]))
const SUPPORTED_CITY_LABEL_TEXT = PSY_CENTER_CITY_OPTIONS.map((item) => item.label).join('、')

const currentCityLabel = computed(
  () => PSY_CENTER_CITY_OPTIONS.find((item) => item.value === cityCode.value)?.label || '当前城市',
)
const locationBadgeLabel = computed(() => {
  if (queryMode.value !== 'nearby') {
    return currentCityLabel.value
  }
  return nearbyCityLabel.value ? `附近 · ${nearbyCityLabel.value}` : '附近机构'
})
const nearbyRecommendedCity = computed(() => {
  if (!nearbyOutOfCoverage.value || !lastCoords.value) {
    return null
  }

  const toRadians = (value: number) => (value * Math.PI) / 180
  const distanceBetween = (latitudeA: number, longitudeA: number, latitudeB: number, longitudeB: number) => {
    const earthRadiusKm = 6371
    const deltaLatitude = toRadians(latitudeB - latitudeA)
    const deltaLongitude = toRadians(longitudeB - longitudeA)
    const normalizedLatitudeA = toRadians(latitudeA)
    const normalizedLatitudeB = toRadians(latitudeB)
    const haversine =
      Math.sin(deltaLatitude / 2) ** 2 +
      Math.cos(normalizedLatitudeA) * Math.cos(normalizedLatitudeB) * Math.sin(deltaLongitude / 2) ** 2
    return 2 * earthRadiusKm * Math.asin(Math.sqrt(haversine))
  }

  return PSY_CENTER_CITY_REFERENCES.reduce<(typeof PSY_CENTER_CITY_REFERENCES)[number] | null>((closest, candidate) => {
    if (!lastCoords.value) {
      return closest
    }

    const candidateDistance = distanceBetween(
      lastCoords.value.latitude,
      lastCoords.value.longitude,
      candidate.latitude,
      candidate.longitude,
    )

    if (!closest) {
      return candidate
    }

    const closestDistance = distanceBetween(
      lastCoords.value.latitude,
      lastCoords.value.longitude,
      closest.latitude,
      closest.longitude,
    )

    return candidateDistance < closestDistance ? candidate : closest
  }, null)
})

const recommendedCount = computed(() => centers.value.filter((center) => center.recommended).length)
const sourceTrackedCount = computed(() => centers.value.filter((center) => center.sourceUrl?.trim()).length)
const officialCount = computed(() => centers.value.filter((center) => center.sourceLevel === 'official').length)

const querySummary = computed(() => {
  if (queryMode.value === 'nearby' && nearbyOutOfCoverage.value) {
    return nearbyRecommendedCity.value
      ? `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。推荐优先查看最近的已覆盖城市：${nearbyRecommendedCity.value.label}。`
      : `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。你可以切换到已覆盖城市继续查看。`
  }
  if (queryMode.value === 'nearby') {
    return nearbyCityLabel.value
      ? `已为你定位到 ${nearbyCityLabel.value} 附近 ${radiusKm}km 范围内的支持入口，优先展示可直接联系、来源清晰的机构。`
      : `已为你定位 ${radiusKm}km 范围内的支持入口，优先展示可直接联系、来源清晰的机构。`
  }
  return `${currentCityLabel.value} 当前已整理 ${centers.value.length} 个支持机构，你可以先从里面挑一个联系。`
})

const heroHeadline = computed(() =>
  queryMode.value === 'nearby'
    ? nearbyOutOfCoverage.value
      ? '当前定位附近暂无已收录机构'
      : '优先给出你附近可直接联系的心理支持机构'
    : `在${currentCityLabel.value}优先找到可直接联系的心理支持机构`,
)
const emptyDescription = computed(() =>
  nearbyOutOfCoverage.value
    ? nearbyRecommendedCity.value
      ? `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。推荐优先查看最近的已覆盖城市：${nearbyRecommendedCity.value.label}。`
      : `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。请切换到已覆盖城市继续查看。`
    : '当前筛选条件下还没有机构信息。',
)
const emptyActionText = computed(() =>
  nearbyOutOfCoverage.value
    ? nearbyRecommendedCity.value
      ? `查看推荐城市：${nearbyRecommendedCity.value.label}`
      : '查看当前城市'
    : '重新加载',
)
const toolbarHintText = computed(() => {
  if (queryMode.value !== 'nearby') {
    return ''
  }
  if (nearbyOutOfCoverage.value) {
    return nearbyRecommendedCity.value
      ? `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。推荐优先查看最近的已覆盖城市：${nearbyRecommendedCity.value.label}。`
      : `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。你可以切换到以上城市继续查看。`
  }
  return `已根据定位结果同步到 ${nearbyCityLabel.value || '附近城市'}，下方优先展示定位命中的机构。`
})

const heroFacts = computed(() => [
  {
    label: '当前机构',
    value: String(centers.value.length).padStart(2, '0'),
    hint: queryMode.value === 'nearby' ? '附近结果' : '当前城市',
  },
  {
    label: '优先联系',
    value: String(recommendedCount.value).padStart(2, '0'),
    hint: '建议先看',
  },
  {
    label: '官方来源',
    value: String(officialCount.value).padStart(2, '0'),
    hint: '已附官网',
  },
])

const heroGuideTitle = computed(() =>
  queryMode.value === 'nearby' ? '怎么使用这页' : `在${currentCityLabel.value}可以这样查看`,
)

const heroPrinciples = [
  '仅保留精神卫生中心、精神专科医院和明确心理支持机构',
  '支持按城市切换，也支持直接定位附近',
  '地址、电话和来源都会一起展示，方便你自己判断',
]

const cityCoverage = PSY_CENTER_CITY_OPTIONS.map((item) => item.label)

const resolveCenterImage = (center: PsyCenter) => {
  return resolvePsyCenterPosterUrl(center.cityCode, center.seedKey)
}

const resetNearbyState = () => {
  queryMode.value = 'city'
  nearbyOutOfCoverage.value = false
  nearbyCityLabel.value = ''
}

const syncNearbyCitySelection = (rows: PsyCenter[]) => {
  const firstCityCode = rows[0]?.cityCode?.trim()
  const matchedCityLabel = firstCityCode ? SUPPORTED_CITY_OPTION_MAP.get(firstCityCode) : undefined

  if (firstCityCode && matchedCityLabel) {
    cityCode.value = firstCityCode
  }

  nearbyCityLabel.value = matchedCityLabel || rows[0]?.cityName?.trim() || ''
}

const applyNearbyCenters = (rows: PsyCenter[]) => {
  centers.value = rows
  queryMode.value = 'nearby'
  nearbyOutOfCoverage.value = rows.length === 0
  syncNearbyCitySelection(rows)
}

const loadByCity = async () => {
  resetNearbyState()
  loading.value = true
  errorState.value = null
  try {
    centers.value = await getPsyCentersByCity(cityCode.value)
  } catch (error) {
    errorState.value = parseError(error, '心理中心数据加载失败')
  } finally {
    loading.value = false
  }
}

const refreshCurrent = async () => {
  if (queryMode.value === 'nearby' && lastCoords.value) {
    loading.value = true
    errorState.value = null
    try {
      const rows = await getNearbyPsyCenters(lastCoords.value.latitude, lastCoords.value.longitude, radiusKm)
      applyNearbyCenters(rows)
    } catch (error) {
      errorState.value = parseError(error, '附近心理中心加载失败')
    } finally {
      loading.value = false
    }
    return
  }
  await loadByCity()
}

const handleEmptyAction = async () => {
  if (nearbyOutOfCoverage.value) {
    if (nearbyRecommendedCity.value) {
      cityCode.value = nearbyRecommendedCity.value.value
    }
    await loadByCity()
    return
  }
  await refreshCurrent()
}

const locateNearby = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位，请先切换城市查看机构列表。')
    return
  }

  loading.value = true
  errorState.value = null
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      lastCoords.value = {
        latitude: Number(position.coords.latitude.toFixed(6)),
        longitude: Number(position.coords.longitude.toFixed(6)),
      }
      try {
        const rows = await getNearbyPsyCenters(lastCoords.value.latitude, lastCoords.value.longitude, radiusKm)
        applyNearbyCenters(rows)
        if (rows.length === 0) {
          const recommendationText = nearbyRecommendedCity.value ? `，推荐查看 ${nearbyRecommendedCity.value.label}` : ''
          ElMessage.info(`当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}${recommendationText}`)
        }
      } catch (error) {
        errorState.value = parseError(error, '附近心理中心加载失败')
      } finally {
        loading.value = false
      }
    },
    () => {
      loading.value = false
      ElMessage.warning('定位失败，请先切换城市查看机构列表。')
    },
    { enableHighAccuracy: true, timeout: 6000 },
  )
}

onMounted(() => {
  const routeCityCode = typeof route.query.cityCode === 'string' ? route.query.cityCode.trim() : ''
  const routeMode = typeof route.query.mode === 'string' ? route.query.mode.trim() : ''
  const routeLatitude = typeof route.query.latitude === 'string' ? Number(route.query.latitude) : Number.NaN
  const routeLongitude = typeof route.query.longitude === 'string' ? Number(route.query.longitude) : Number.NaN

  if (routeCityCode) {
    cityCode.value = routeCityCode
  }

  if (routeMode === 'nearby' && Number.isFinite(routeLatitude) && Number.isFinite(routeLongitude)) {
    lastCoords.value = {
      latitude: routeLatitude,
      longitude: routeLongitude,
    }
    void refreshCurrent()
    return
  }

  void loadByCity()
})
</script>

<template>
  <div class="centers-page user-layout">
    <SectionBlock
      headerless
      eyebrow="支持资源"
      title="心理中心"
      description="按城市筛选或定位附近，优先给出来源清晰、可直接联系的支持入口。"
    >
      <section class="hero-stage">
        <SmartImage
          class="hero-stage__image"
          :src="`/assets/illustrations/psy-center-hero.png?v=${PSY_CENTER_HERO_VERSION}`"
          alt="心理中心首页图"
          kind="psy"
          loading="eager"
        />
        <div class="hero-stage__scrim" />

        <div class="hero-stage__content">
          <div class="hero-copy">
            <div class="hero-badges">
              <span class="hero-badge hero-badge--city">{{ locationBadgeLabel }}</span>
              <span class="hero-badge">六城覆盖</span>
              <span class="hero-badge">已附来源 {{ sourceTrackedCount }}/{{ centers.length || 0 }}</span>
            </div>

            <h3>{{ heroHeadline }}</h3>
            <p class="hero-lead">
              {{ querySummary }}
            </p>
            <p class="hero-note">
              这页只保留精神卫生中心、精神专科医院和明确心理服务机构，不混入普通综合医院心理门诊。
            </p>

            <div class="hero-facts">
              <article v-for="fact in heroFacts" :key="fact.label" class="hero-fact">
                <span class="hero-fact__value">{{ fact.value }}</span>
                <span class="hero-fact__label">{{ fact.label }}</span>
                <span class="hero-fact__hint">{{ fact.hint }}</span>
              </article>
            </div>
          </div>

          <aside class="hero-proof">
            <p class="hero-proof__eyebrow">使用说明</p>
            <h4>{{ heroGuideTitle }}</h4>
            <ul class="hero-proof__list">
              <li v-for="item in heroPrinciples" :key="item">{{ item }}</li>
            </ul>
            <div class="hero-proof__cities">
              <span v-for="city in cityCoverage" :key="city">{{ city }}</span>
            </div>
          </aside>
        </div>

        <div class="hero-toolbar">
          <div class="hero-toolbar__copy">
            <span class="hero-toolbar__title">开始查询</span>
            <span class="hero-toolbar__subtitle">切换城市、定位附近或刷新当前结果</span>
          </div>
          <div class="hero-toolbar__actions">
            <el-select v-model="cityCode" style="width: 180px" @change="loadByCity">
              <el-option v-for="item in PSY_CENTER_CITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button @click="locateNearby">定位附近</el-button>
            <el-button type="primary" plain @click="refreshCurrent">刷新</el-button>
          </div>
        </div>
        <p v-if="toolbarHintText" class="hero-toolbar__hint">{{ toolbarHintText }}</p>
      </section>

      <LoadingState v-if="loading" />
      <ErrorState
        v-else-if="errorState"
        :title="errorState.title"
        :detail="errorState.detail"
        :trace-id="errorState.traceId"
        @retry="refreshCurrent"
      />
      <EmptyState
        v-else-if="centers.length === 0"
        title="暂无心理中心数据"
        :description="emptyDescription"
        :action-text="emptyActionText"
        @action="handleEmptyAction"
      />
      <div v-else class="card-grid">
        <MediaFeatureCard
          v-for="center in centers"
          :key="`${center.id}-${center.name}`"
          image-kind="psy"
          :image-url="resolveCenterImage(center)"
          image-alt="心理中心封面"
          image-fit="cover"
          :title="center.name"
          :subtitle="`${center.cityName || ''} ${center.district || ''}`.trim() || '心理支持机构'"
          :description="center.address || '暂无地址信息'"
        >
          <template #meta>
            <span class="pill pill-city">{{ center.cityName || '待补城市' }}</span>
            <span v-if="center.recommended" class="pill pill-recommend">优先联系</span>
            <span class="pill pill-source">{{ SOURCE_LEVEL_LABELS[center.sourceLevel || ''] || '来源备注' }}</span>
          </template>
          <div class="detail-list">
            <p><strong>联系电话：</strong>{{ center.phone || '暂无联系电话' }}</p>
            <p><strong>来源备注：</strong>{{ center.sourceName || '待补充来源' }}</p>
          </div>
          <template #footer>
            <a v-if="center.sourceUrl" :href="center.sourceUrl" target="_blank" rel="noopener noreferrer">查看来源页</a>
            <span v-else>来源页待补充</span>
          </template>
        </MediaFeatureCard>
      </div>
    </SectionBlock>
  </div>
</template>

<style scoped>
.centers-page {
  display: flex;
  flex-direction: column;
}

.hero-stage {
  position: relative;
  overflow: hidden;
  border-radius: 28px;
  border: 1px solid rgba(171, 192, 228, 0.2);
  min-height: 520px;
  background: #0a1320;
  box-shadow: 0 28px 60px rgba(4, 10, 21, 0.32);
}

.hero-stage :deep(.hero-stage__image) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.hero-stage__scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(8, 14, 24, 0.94) 0%, rgba(8, 14, 24, 0.82) 34%, rgba(8, 14, 24, 0.4) 68%, rgba(8, 14, 24, 0.62) 100%),
    linear-gradient(180deg, rgba(4, 10, 18, 0.06) 0%, rgba(4, 10, 18, 0.26) 100%);
}

.hero-stage__content {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(260px, 0.78fr);
  gap: 24px;
  padding: 28px 28px 18px;
  min-height: 418px;
  align-items: end;
}

.hero-copy {
  display: grid;
  gap: 16px;
  max-width: 720px;
}

.hero-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  color: #eff6ff;
  font-size: 12px;
  letter-spacing: 0.04em;
  background: rgba(15, 27, 44, 0.52);
  border: 1px solid rgba(192, 212, 236, 0.16);
  backdrop-filter: blur(14px);
}

.hero-badge--city {
  background: rgba(77, 116, 167, 0.38);
}

.hero-copy h3 {
  margin: 0;
  color: #f7fbff;
  font-size: clamp(32px, 4.8vw, 54px);
  line-height: 1.14;
  font-family: var(--font-display);
  max-width: 12ch;
}

.hero-lead {
  margin: 0;
  max-width: 56ch;
  color: #dbe9f8;
  font-size: 16px;
  line-height: 1.8;
}

.hero-note {
  margin: 0;
  max-width: 58ch;
  color: #9fd0bf;
  font-size: 14px;
  line-height: 1.7;
}

.hero-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  max-width: 720px;
}

.hero-fact {
  display: grid;
  gap: 6px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(8, 15, 26, 0.54);
  border: 1px solid rgba(179, 202, 227, 0.14);
  backdrop-filter: blur(14px);
}

.hero-fact__value {
  color: #f7fbff;
  font-size: 30px;
  line-height: 1;
  font-weight: 700;
}

.hero-fact__label {
  color: #dbe8f8;
  font-size: 14px;
  font-weight: 600;
}

.hero-fact__hint {
  color: #93abc8;
  font-size: 12px;
}

.hero-proof {
  align-self: stretch;
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 22px;
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(9, 18, 32, 0.72), rgba(9, 18, 32, 0.82)),
    radial-gradient(circle at top right, rgba(134, 208, 176, 0.18), transparent 38%);
  border: 1px solid rgba(171, 192, 228, 0.16);
  backdrop-filter: blur(16px);
}

.hero-proof__eyebrow {
  margin: 0;
  color: #8ac8c4;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.hero-proof h4 {
  margin: 0;
  color: #f5fbff;
  font-size: 24px;
  line-height: 1.3;
  font-family: var(--font-display);
}

.hero-proof__list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding-left: 18px;
  color: #d7e6f5;
  line-height: 1.7;
}

.hero-proof__cities {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-proof__cities span {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  color: #f0f7ff;
  font-size: 12px;
  background: rgba(67, 102, 150, 0.3);
}

.hero-toolbar {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  flex-wrap: wrap;
  padding: 18px 28px 24px;
  border-top: 1px solid rgba(171, 192, 228, 0.12);
  background: linear-gradient(180deg, rgba(8, 14, 24, 0.1), rgba(8, 14, 24, 0.38));
}

.hero-toolbar__hint {
  margin: 0;
  padding: 0 28px 24px;
  color: #9fc6cf;
  font-size: 13px;
  line-height: 1.7;
}

.hero-toolbar__copy {
  display: grid;
  gap: 4px;
}

.hero-toolbar__title {
  color: #f5fbff;
  font-size: 15px;
  font-weight: 600;
}

.hero-toolbar__subtitle {
  color: #93abc8;
  font-size: 13px;
  line-height: 1.6;
}

.hero-toolbar__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.card-grid {
  display: grid;
  gap: 14px;
}

.card-grid :deep(.media-card) {
  grid-template-columns: minmax(228px, 276px) minmax(0, 1fr);
  gap: 20px;
  min-height: 236px;
}

.card-grid :deep(.cover-wrap) {
  min-height: 224px;
  background:
    radial-gradient(circle at top left, rgba(129, 190, 247, 0.14), transparent 38%),
    linear-gradient(160deg, rgba(15, 25, 43, 0.96), rgba(9, 15, 26, 0.98));
}

.card-grid :deep(.smart-image) {
  border-radius: 0;
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.pill-city {
  color: #f2f8ff;
  background: rgba(72, 102, 145, 0.32);
}

.pill-recommend {
  color: #fff8e8;
  background: rgba(198, 155, 73, 0.26);
}

.pill-source {
  color: #f5fff9;
  background: rgba(109, 182, 139, 0.28);
}

.detail-list {
  display: grid;
  gap: 6px;
}

.detail-list p {
  margin: 0;
  color: #dce7f8;
  line-height: 1.65;
}

.detail-list strong {
  color: #f4f9ff;
}

.card-grid :deep(a) {
  color: #9fd7c4;
  text-decoration: none;
}

.card-grid :deep(a:hover) {
  text-decoration: underline;
}

@media (max-width: 1100px) {
  .hero-stage__content {
    grid-template-columns: 1fr;
    min-height: unset;
  }

  .hero-copy h3 {
    max-width: none;
  }
}

@media (max-width: 760px) {
  .hero-stage {
    min-height: 0;
  }

  .hero-stage__content {
    padding: 20px 20px 16px;
  }

  .hero-toolbar {
    padding: 16px 20px 20px;
  }

  .hero-toolbar__hint {
    padding: 0 20px 20px;
  }

  .hero-facts {
    grid-template-columns: 1fr;
  }

  .hero-toolbar__actions {
    width: 100%;
  }

  .hero-toolbar__actions :deep(.el-select),
  .hero-toolbar__actions :deep(.el-button) {
    flex: 1 1 100%;
  }

  .card-grid :deep(.media-card) {
    grid-template-columns: 1fr;
  }

  .card-grid :deep(.cover-wrap) {
    min-height: 220px;
  }
}
</style>
