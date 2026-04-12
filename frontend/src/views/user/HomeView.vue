<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter, type RouteLocationRaw } from 'vue-router'

import { getHomeContent, type HomePayload } from '@/api/home'
import { getNearbyPsyCenters, getPsyCentersByCity, type PsyCenter } from '@/api/psyCenter'
import ArticleFeatureCard from '@/components/content/ArticleFeatureCard.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import HeroSection from '@/components/ui/HeroSection.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import MediaFeatureCard from '@/components/ui/MediaFeatureCard.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import SmartImage from '@/components/ui/SmartImage.vue'
import { ARTICLE_CATEGORY_LABELS, PSY_CENTER_CITY_OPTIONS, PSY_CENTER_CITY_REFERENCES, SOURCE_LEVEL_LABELS } from '@/constants/contentMeta'
import { useUserAuthStore } from '@/stores/userAuth'
import { resolvePsyCenterPosterUrl } from '@/utils/contentMedia'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const router = useRouter()
const authStore = useUserAuthStore()

const home = ref<HomePayload | null>(null)
const loadingHome = ref(false)
const homeError = ref<ErrorStatePayload | null>(null)

const cityCode = ref('310100')
const centers = ref<PsyCenter[]>([])
const loadingCenters = ref(false)
const centerError = ref<ErrorStatePayload | null>(null)
const PSY_CENTER_RADIUS_KM = 15
const centerQueryMode = ref<'city' | 'nearby'>('city')
const nearbyOutOfCoverage = ref(false)
const nearbyCityLabel = ref('')
const nearbyCoords = ref<{ latitude: number; longitude: number } | null>(null)
const SUPPORTED_CITY_OPTION_MAP = new Map<string, string>(PSY_CENTER_CITY_OPTIONS.map((item) => [item.value, item.label]))
const SUPPORTED_CITY_LABEL_TEXT = PSY_CENTER_CITY_OPTIONS.map((item) => item.label).join('、')

const featuredArticle = computed(() => home.value?.todayFeaturedArticle || home.value?.todayArticles[0] || null)
const featuredBook = computed(() => home.value?.todayFeaturedBook || home.value?.todayBooks[0] || null)
const previewCenters = computed(() => centers.value.slice(0, 4))
const todayThemeLabel = computed(() => {
  const key = home.value?.todayTheme?.themeKey
  return key ? ARTICLE_CATEGORY_LABELS[key] || key : '今日主题'
})
const nearbyRecommendedCity = computed(() => {
  if (!nearbyOutOfCoverage.value || !nearbyCoords.value) {
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
    if (!nearbyCoords.value) {
      return closest
    }

    const candidateDistance = distanceBetween(
      nearbyCoords.value.latitude,
      nearbyCoords.value.longitude,
      candidate.latitude,
      candidate.longitude,
    )

    if (!closest) {
      return candidate
    }

    const closestDistance = distanceBetween(
      nearbyCoords.value.latitude,
      nearbyCoords.value.longitude,
      closest.latitude,
      closest.longitude,
    )

    return candidateDistance < closestDistance ? candidate : closest
  }, null)
})
const centerToolbarHint = computed(() => {
  if (centerQueryMode.value !== 'nearby') {
    return ''
  }
  if (nearbyOutOfCoverage.value) {
    return nearbyRecommendedCity.value
      ? `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。推荐优先查看最近的已覆盖城市：${nearbyRecommendedCity.value.label}。`
      : `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。你可以切换到以上城市继续查看。`
  }
  return `已根据定位切换到 ${nearbyCityLabel.value || '附近城市'}，下方先展示附近能联系到的机构。`
})
const centerEmptyDescription = computed(() =>
  nearbyOutOfCoverage.value
    ? nearbyRecommendedCity.value
      ? `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。推荐优先查看最近的已覆盖城市：${nearbyRecommendedCity.value.label}。`
      : `当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}。请切换到已覆盖城市继续查看。`
    : '当前资源列表不可用，请切换城市或稍后重试。',
)
const centerEmptyActionText = computed(() =>
  nearbyOutOfCoverage.value
    ? nearbyRecommendedCity.value
      ? `查看推荐城市：${nearbyRecommendedCity.value.label}`
      : '查看当前城市'
    : '重试',
)
const centerSectionFooterText = computed(() => {
  return '更多资源可以前往心理中心继续查看。'
})

const goProtectedPath = async (path: RouteLocationRaw) => {
  const resolvedPath = router.resolve(path).fullPath
  if (authStore.userRole === 'USER') {
    await router.push(path)
    return
  }
  ElMessage.info('请先登录用户账号再访问该功能')
  await router.push({ path: '/app/login', query: { redirect: resolvedPath } })
}

const handlePrimaryAction = async () => {
  await goProtectedPath('/app/upload')
}

const handleSecondaryAction = async () => {
  await goProtectedPath('/app/reports')
}

const openPsyCenters = async () => {
  const query =
    centerQueryMode.value === 'nearby' && nearbyCoords.value
      ? {
          mode: 'nearby',
          latitude: String(nearbyCoords.value.latitude),
          longitude: String(nearbyCoords.value.longitude),
          cityCode: cityCode.value,
        }
      : {
          cityCode: cityCode.value,
        }

  await goProtectedPath({ path: '/app/psy-centers', query })
}

const openContentHub = async () => {
  await router.push({
    path: '/app/content',
    query: home.value?.todayDate ? { date: home.value.todayDate } : undefined,
  })
}

const openArticle = async (id: number) => {
  await router.push(`/app/content/articles/${id}`)
}

const openBook = async (id: number) => {
  await router.push(`/app/content/books/${id}`)
}

const resolveCenterPreviewImage = (center: PsyCenter) => {
  return resolvePsyCenterPosterUrl(center.cityCode, center.seedKey)
}

const openRecommendedCity = async () => {
  if (!nearbyRecommendedCity.value) {
    await loadCentersByCity()
    return
  }

  cityCode.value = nearbyRecommendedCity.value.value
  await loadCentersByCity()
}

const resetNearbyCenterState = () => {
  centerQueryMode.value = 'city'
  nearbyOutOfCoverage.value = false
  nearbyCityLabel.value = ''
  nearbyCoords.value = null
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
  centerQueryMode.value = 'nearby'
  nearbyOutOfCoverage.value = rows.length === 0
  syncNearbyCitySelection(rows)
}

const loadHome = async () => {
  loadingHome.value = true
  homeError.value = null
  try {
    home.value = await getHomeContent()
  } catch (error) {
    homeError.value = parseError(error, '首页内容加载失败')
  } finally {
    loadingHome.value = false
  }
}

const loadCentersByCity = async () => {
  resetNearbyCenterState()
  loadingCenters.value = true
  centerError.value = null
  try {
    centers.value = await getPsyCentersByCity(cityCode.value)
  } catch (error) {
    centerError.value = parseError(error, '心理中心资源加载失败')
  } finally {
    loadingCenters.value = false
  }
}

const locateNearbyCenters = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位功能')
    return
  }

  centerError.value = null
  loadingCenters.value = true
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      nearbyCoords.value = {
        latitude: Number(position.coords.latitude.toFixed(6)),
        longitude: Number(position.coords.longitude.toFixed(6)),
      }
      try {
        const rows = await getNearbyPsyCenters(nearbyCoords.value.latitude, nearbyCoords.value.longitude, PSY_CENTER_RADIUS_KM)
        applyNearbyCenters(rows)
        if (rows.length === 0) {
          const recommendationText = nearbyRecommendedCity.value ? `，推荐查看 ${nearbyRecommendedCity.value.label}` : ''
          ElMessage.info(`当前定位附近暂无已收录机构，现阶段仅覆盖 ${SUPPORTED_CITY_LABEL_TEXT}${recommendationText}`)
        }
      } catch (error) {
        centerError.value = parseError(error, '附近心理中心加载失败')
      } finally {
        loadingCenters.value = false
      }
    },
    () => {
      loadingCenters.value = false
      ElMessage.warning('定位失败，请手动切换城市')
    },
    { enableHighAccuracy: true, timeout: 6000 },
  )
}

onMounted(async () => {
  await Promise.all([loadHome(), loadCentersByCity()])
})
</script>

<template>
  <div class="home-page user-layout">
    <LoadingState v-if="loadingHome && !home" />
    <ErrorState
      v-else-if="homeError && !home"
      :title="homeError.title"
      :detail="homeError.detail"
      :trace-id="homeError.traceId"
      @retry="loadHome"
    />
    <template v-else>
      <HeroSection
        eyebrow="从这里开始"
        title="AI 语音情绪分析与心理支持平台"
        subtitle="录音或上传音频后，你可以继续查看分析结果、报告和后续支持内容。"
        primary-text="开始上传 / 录音"
        secondary-text="查看报告"
        @primary="handlePrimaryAction"
        @secondary="handleSecondaryAction"
      >
        <div class="hero-badge-row">
          <span class="hero-chip">录音或上传都可以</span>
          <span class="hero-chip">进度会自动更新</span>
          <span class="hero-chip">结果会连到报告和支持内容</span>
        </div>

        <template #bottom>
          <div class="home-hero-flow">
            <header class="home-hero-flow__header">
              <p class="home-hero-flow__eyebrow">怎么使用</p>
              <h2>三步就够了</h2>
              <p class="home-hero-flow__description">
                先上传，再看结果，最后按需要继续看报告、内容或支持资源。
              </p>
            </header>

            <div class="step-grid">
              <LoreCard title="01 上传 / 录音" subtitle="录音或选择音频文件">
                可以直接用浏览器录音，也可以上传本地音频文件。
              </LoreCard>
              <LoreCard title="02 开始分析" subtitle="系统会整理这段语音的结果">
                系统会结合声音和转写内容，整理出情绪倾向和风险线索。
              </LoreCard>
              <LoreCard title="03 查看结果" subtitle="报告、内容和支持资源都会接上">
                你可以继续看报告、内容专栏，或去心理中心找进一步支持。
              </LoreCard>
            </div>
          </div>
        </template>
      </HeroSection>

      <SectionBlock
        eyebrow="今日内容"
        title="从今天的内容开始"
        description="先看一句语录，再选一篇文章或一本书；如果今天正需要一点支撑，就从这里开始。"
      >
        <div class="content-entry-layout content-fade-rise">
          <section class="content-entry-hero">
            <div class="content-entry-hero__main">
              <div class="content-entry-hero__chips">
                <span class="hero-chip hero-chip--accent">{{ todayThemeLabel }}</span>
                <span class="hero-chip">{{ home?.todayDate || '今日内容' }}</span>
              </div>
              <p class="content-entry-hero__eyebrow">今日主轴</p>
              <h3>{{ home?.todayTheme?.themeTitle || '先给情绪一个容器' }}</h3>
              <p class="content-entry-hero__summary">
                {{ home?.todayTheme?.themeSubtitle || '把难受从一团里拆出来，看见它、命名它，再决定往哪里走。' }}
              </p>
              <div class="content-entry-hero__actions">
                <el-button type="primary" @click="openContentHub">进入内容专栏</el-button>
                <p>先从今天这组内容里挑一个入口，再决定是否继续往下深读。</p>
              </div>
            </div>

            <aside class="content-entry-hero__quote">
              <p class="content-entry-hero__quote-label">今日语录</p>
              <blockquote>
                {{ home?.todayQuote?.content || '允许自己慢一点，不是退步，而是在给情绪留出被看见的时间。' }}
              </blockquote>
              <p v-if="home?.todayQuote?.author" class="content-entry-hero__quote-author">{{ home.todayQuote.author }}</p>
            </aside>
          </section>

          <div class="content-entry-stage">
            <aside v-if="featuredBook" class="content-entry-book-spotlight">
              <div class="content-entry-book-spotlight__head">
                <p class="content-entry-stage__eyebrow">延展阅读</p>
                <h3>今天配套的一本书</h3>
                <p>如果今天这条线索对你有帮助，这本书适合继续往深处读。</p>
              </div>

              <div class="content-entry-book-spotlight__shelf">
                <div class="content-entry-book-spotlight__cover-frame">
                  <div class="content-entry-book-spotlight__cover">
                    <SmartImage :src="featuredBook.coverImageUrl" :alt="featuredBook.title" kind="book" fit="cover" />
                  </div>
                </div>

                <div class="content-entry-book-spotlight__meta">
                  <span v-if="featuredBook.category" class="pill pill-muted">
                    {{ ARTICLE_CATEGORY_LABELS[featuredBook.category] || featuredBook.category }}
                  </span>
                  <h4>{{ featuredBook.title }}</h4>
                  <p class="content-entry-book-spotlight__author">{{ featuredBook.author || '推荐阅读' }}</p>
                  <p class="content-entry-book-spotlight__summary">
                    {{ featuredBook.recommendReason || featuredBook.description || '先在站内看推荐理由，再决定是否继续深读。' }}
                  </p>
                  <p v-if="featuredBook.highlights?.[0]" class="content-entry-book-spotlight__highlight">
                    {{ featuredBook.highlights[0] }}
                  </p>
                </div>
              </div>

              <el-button type="primary" plain class="content-entry-book-spotlight__action" @click="openBook(featuredBook.id)">
                查看书籍导读
              </el-button>
            </aside>

            <section v-if="featuredArticle" class="content-entry-article-spotlight">
              <div class="content-entry-article-spotlight__head">
                <p class="content-entry-stage__eyebrow">主推文章</p>
                <h3>今天先看这篇文章</h3>
                <p>如果你今天想先读一篇更直接的内容，就从这里开始。</p>
              </div>
              <ArticleFeatureCard
                :article="featuredArticle"
                dense
                :highlight-limit="1"
                :show-action="false"
              />
              <el-button type="primary" plain class="content-entry-article-spotlight__action" @click="openArticle(featuredArticle.id)">
                查看文章导读
              </el-button>
            </section>
          </div>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="支持资源"
        title="心理中心"
        description="支持按城市切换或定位附近，也会附上来源信息，方便你自己核对。"
      >
        <div class="resource-toolbar">
          <el-select v-model="cityCode" style="width: 180px" @change="loadCentersByCity">
            <el-option v-for="item in PSY_CENTER_CITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button @click="locateNearbyCenters">定位附近</el-button>
          <el-button type="primary" plain @click="loadCentersByCity">刷新</el-button>
        </div>
        <p v-if="centerToolbarHint" class="resource-toolbar__hint">{{ centerToolbarHint }}</p>

        <LoadingState v-if="loadingCenters" />
        <ErrorState
          v-else-if="centerError"
          :title="centerError.title"
          :detail="centerError.detail"
          :trace-id="centerError.traceId"
          @retry="loadCentersByCity"
        />
        <EmptyState
          v-else-if="centers.length === 0"
          title="暂无心理中心数据"
          :description="centerEmptyDescription"
          :action-text="centerEmptyActionText"
          @action="nearbyOutOfCoverage ? openRecommendedCity() : loadCentersByCity()"
        />
        <div v-else class="center-grid">
          <MediaFeatureCard
            v-for="center in previewCenters"
            :key="String(center.id)"
            image-kind="psy"
            :image-url="resolveCenterPreviewImage(center)"
            :image-alt="center.name"
            :title="center.name"
            :subtitle="`${center.cityName || ''} ${center.district || ''}`.trim() || '心理支持机构'"
            :description="center.address || '暂无地址信息'"
          >
            <template #meta>
              <span class="pill pill-muted">{{ center.cityName || '城市待补充' }}</span>
              <span class="pill pill-accent">{{ SOURCE_LEVEL_LABELS[center.sourceLevel || ''] || '来源备注' }}</span>
            </template>
            <div class="center-detail">
              <p>{{ center.phone || '暂无联系电话' }}</p>
              <p>{{ center.sourceName || '待补充来源说明' }}</p>
            </div>
          </MediaFeatureCard>
        </div>
        <div v-if="previewCenters.length > 0" class="center-section-footer">
          <p class="center-section-footer__text">{{ centerSectionFooterText }}</p>
          <el-button type="primary" plain @click="openPsyCenters">查看更多心理中心</el-button>
        </div>
      </SectionBlock>
    </template>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: var(--content-gap-3);
}

.hero-badge-row {
  margin-top: 18px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-chip {
  display: inline-flex;
  border-radius: var(--content-radius-pill);
  border: 1px solid var(--content-border-2);
  background: var(--content-surface-2);
  color: #d7e7ff;
  font-size: 12px;
  padding: 6px 11px;
  box-shadow: var(--content-shadow-1);
}

.home-hero-flow {
  display: grid;
  gap: var(--content-gap-3);
}

.home-hero-flow__header {
  display: grid;
  gap: 10px;
  max-width: 720px;
}

.home-hero-flow__eyebrow {
  margin: 0;
  color: #8dc5c8;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.home-hero-flow__header h2 {
  margin: 0;
  color: #f8fafc;
  font-size: clamp(28px, 4.4vw, 50px);
  line-height: 1.06;
  font-family: var(--font-display);
}

.home-hero-flow__description {
  margin: 0;
  max-width: 58ch;
  color: #b8c7df;
  line-height: 1.72;
}

.step-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.home-hero-flow :deep(.lore-card) {
  height: 100%;
  padding: 18px;
}

.home-hero-flow :deep(.head h3) {
  font-size: 16px;
}

.content-entry-layout {
  display: grid;
  gap: var(--content-gap-4);
}

.content-entry-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(280px, 0.84fr);
  gap: var(--content-gap-4);
  padding: 28px;
  border-radius: var(--content-radius-3);
  border: 1px solid var(--content-border-2);
  background: var(--content-surface-3);
  box-shadow: var(--content-shadow-3);
}

.content-entry-hero__main,
.content-entry-hero__quote,
.content-entry-stage,
.content-entry-article-spotlight,
.content-entry-book-spotlight,
.content-entry-article-spotlight__head,
.content-entry-book-spotlight__head,
.content-entry-book-spotlight__meta {
  display: grid;
}

.content-entry-hero__main,
.content-entry-hero__quote {
  gap: var(--content-gap-3);
}

.content-entry-hero__chips,
.content-entry-hero__actions {
  display: flex;
  align-items: center;
  gap: var(--content-gap-2);
  flex-wrap: wrap;
}

.content-entry-hero__eyebrow,
.content-entry-stage__eyebrow,
.content-entry-hero__quote-label {
  margin: 0;
  color: #8dc5c8;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.hero-chip--accent {
  color: #f5fff8;
  background: var(--content-chip-accent-surface);
}

.content-entry-hero__main h3,
.content-entry-article-spotlight__head h3,
.content-entry-book-spotlight__head h3 {
  margin: 0;
  color: #f6fbff;
  font-family: var(--font-display);
}

.content-entry-hero__main h3 {
  font-size: clamp(34px, 4.6vw, 56px);
  line-height: 1.04;
}

.content-entry-hero__summary,
.content-entry-article-spotlight__head p:last-child,
.content-entry-book-spotlight__head p:last-child,
.content-entry-hero__actions p,
.content-entry-hero__quote-author {
  margin: 0;
  color: #a9c0e3;
  line-height: 1.7;
}

.content-entry-hero__summary {
  max-width: 58ch;
  font-size: 15px;
}

.content-entry-hero__actions p {
  color: #96afd0;
}

.content-entry-hero__quote {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  min-height: 100%;
  gap: 14px;
  padding: 22px 24px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-inset);
  box-shadow: var(--content-shadow-1);
}

.content-entry-hero__quote blockquote {
  margin: 0;
  color: #f6fbff;
  font-family: var(--font-display);
  font-size: clamp(22px, 2.8vw, 32px);
  line-height: 1.34;
}

.content-entry-stage {
  gap: var(--content-gap-3);
  grid-template-columns: minmax(360px, 1.08fr) minmax(320px, 0.92fr);
  align-items: stretch;
}

.content-entry-stage__article {
  min-width: 0;
  height: 100%;
}

.content-entry-article-spotlight {
  height: 100%;
  gap: var(--content-gap-3);
  grid-template-rows: auto 1fr auto;
  padding: 20px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
  box-shadow: var(--content-shadow-1);
}

.content-entry-article-spotlight__head {
  gap: 8px;
}

.content-entry-article-spotlight__head h3 {
  font-size: 24px;
  line-height: 1.12;
}

.content-entry-article-spotlight :deep(.article-card) {
  grid-template-columns: 1fr;
  padding: 16px;
}

.content-entry-article-spotlight :deep(.article-card__cover) {
  min-height: 182px;
}

.content-entry-book-spotlight {
  height: 100%;
  gap: var(--content-gap-3);
  grid-template-rows: auto 1fr auto;
  padding: 20px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
  box-shadow: var(--content-shadow-1);
}

.content-entry-book-spotlight__head {
  gap: 8px;
}

.content-entry-book-spotlight__head h3 {
  margin: 0;
  color: #f6fbff;
  font-family: var(--font-display);
  font-size: 24px;
  line-height: 1.12;
}

.content-entry-book-spotlight__head p:last-child {
  margin: 0;
  color: #a9c0e3;
  line-height: 1.7;
}

.content-entry-book-spotlight__shelf {
  display: grid;
  gap: var(--content-gap-3);
  align-content: start;
  justify-items: center;
}

.content-entry-book-spotlight__cover-frame {
  width: min(188px, 100%);
  padding: 12px;
  border-radius: var(--content-radius-1);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-inset);
  display: grid;
  gap: 10px;
}

.content-entry-book-spotlight__cover-frame::after {
  content: '';
  display: block;
  height: 10px;
  border-radius: var(--content-radius-pill);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
}

.content-entry-book-spotlight__cover {
  overflow: hidden;
  min-height: 272px;
  border-radius: var(--content-radius-1);
  box-shadow: var(--content-shadow-1);
}

.content-entry-book-spotlight__meta {
  width: 100%;
  gap: 10px;
}

.content-entry-book-spotlight__meta > .pill {
  width: fit-content;
  justify-self: start;
}

.content-entry-book-spotlight__meta h4,
.content-entry-book-spotlight__author,
.content-entry-book-spotlight__summary,
.content-entry-book-spotlight__highlight {
  margin: 0;
}

.content-entry-book-spotlight__meta h4 {
  color: #f4fbff;
  font-size: 18px;
  line-height: 1.35;
}

.content-entry-book-spotlight__author {
  color: #c1d2eb;
}

.content-entry-book-spotlight__summary,
.content-entry-book-spotlight__highlight {
  color: #aac0e2;
  line-height: 1.7;
}

.content-entry-book-spotlight__summary {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.content-entry-book-spotlight__highlight {
  padding-left: 14px;
  position: relative;
}

.content-entry-book-spotlight__highlight::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #8fc3c8;
}

.content-entry-article-spotlight__action,
.content-entry-book-spotlight__action {
  align-self: stretch;
}

.resource-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.resource-toolbar__hint {
  margin: 2px 0 0;
  color: #9fc6cf;
  font-size: 13px;
  line-height: 1.7;
}

.center-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.center-grid :deep(.media-card) {
  grid-template-columns: 144px minmax(0, 1fr);
  gap: 18px;
  min-height: 0;
  padding: 14px;
  border-radius: 20px;
}

.center-grid :deep(.cover-wrap) {
  min-height: 196px;
  background:
    radial-gradient(circle at top left, rgba(129, 190, 247, 0.14), transparent 38%),
    linear-gradient(160deg, rgba(15, 25, 43, 0.96), rgba(9, 15, 26, 0.98));
}

.center-grid :deep(.smart-image) {
  border-radius: 0;
}

.center-grid :deep(.head h3) {
  font-size: 18px;
  line-height: 1.35;
}

.center-grid :deep(.head p) {
  margin-top: 6px;
}

.center-grid :deep(.description) {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.center-grid :deep(.meta) {
  gap: 6px;
}

.center-section-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.center-section-footer__text {
  margin: 0;
  color: #9fb2cf;
  line-height: 1.7;
}

.center-detail {
  display: grid;
  gap: 4px;
}

.center-detail p {
  margin: 0;
  color: #c6d8ef;
  line-height: 1.55;
  font-size: 14px;
}

.pill {
  display: inline-flex;
  align-items: center;
  border-radius: var(--content-radius-pill);
  padding: 5px 10px;
  font-size: 12px;
}

.pill-muted {
  color: #d8e8f7;
  background: var(--content-chip-muted-surface);
}

.pill-accent {
  color: #f5fff8;
  background: var(--content-chip-accent-surface);
}

@media (max-width: 1100px) {
  .content-entry-hero,
  .content-entry-stage {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 980px) {
  .step-grid {
    grid-template-columns: 1fr;
  }

  .center-grid {
    grid-template-columns: 1fr;
  }
}
</style>
