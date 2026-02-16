<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { getHomeContent, type HomePayload, type RecommendedArticle, type RecommendedBook } from '@/api/home'
import { postContentClick } from '@/api/admin'
import { getNearbyPsyCenters, getPsyCentersByCity, type PsyCenter } from '@/api/psyCenter'
import HeroSection from '@/components/ui/HeroSection.vue'
import SectionBlock from '@/components/ui/SectionBlock.vue'
import LoreCard from '@/components/ui/LoreCard.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const home = ref<HomePayload | null>(null)
const loadingHome = ref(false)
const homeError = ref<ErrorStatePayload | null>(null)

const cityCode = ref('310100')
const centers = ref<PsyCenter[]>([])
const loadingCenters = ref(false)
const centerError = ref<ErrorStatePayload | null>(null)

const cityOptions = [
  { label: 'Shanghai', value: '310100' },
  { label: 'Beijing', value: '110100' },
  { label: 'Guangzhou', value: '440100' },
  { label: 'Shenzhen', value: '440300' },
  { label: 'Hangzhou', value: '330100' },
]

const trackContentClick = (contentType: 'BANNER' | 'ARTICLE' | 'BOOK' | 'PRACTICE', contentId: string | number) => {
  if (!authStore.isAuthenticated) return
  void postContentClick(contentType, contentId).catch(() => undefined)
}

const goProtectedPath = async (path: string) => {
  if (authStore.userRole === 'USER') {
    await router.push(path)
    return
  }
  ElMessage.info('Login as user to access this feature')
  await router.push({ path: '/login', query: { redirect: path } })
}

const handlePrimaryAction = async () => {
  await goProtectedPath('/upload')
}

const handleSecondaryAction = async () => {
  await goProtectedPath('/reports')
}

const openArticle = (item: RecommendedArticle) => {
  trackContentClick('ARTICLE', item.id)
  if (item.contentUrl) {
    window.open(item.contentUrl, '_blank', 'noopener,noreferrer')
  }
}

const openBook = (item: RecommendedBook) => {
  trackContentClick('BOOK', item.id)
  if (item.purchaseUrl) {
    window.open(item.purchaseUrl, '_blank', 'noopener,noreferrer')
  }
}

const loadHome = async () => {
  loadingHome.value = true
  homeError.value = null
  try {
    home.value = await getHomeContent()
  } catch (error) {
    homeError.value = parseError(error, 'Failed to load home content')
  } finally {
    loadingHome.value = false
  }
}

const loadCentersByCity = async () => {
  loadingCenters.value = true
  centerError.value = null
  try {
    centers.value = await getPsyCentersByCity(cityCode.value)
  } catch (error) {
    centerError.value = parseError(error, 'Failed to load psychology centers')
  } finally {
    loadingCenters.value = false
  }
}

const locateNearbyCenters = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('Geolocation is not supported in current browser')
    return
  }

  centerError.value = null
  loadingCenters.value = true
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      try {
        centers.value = await getNearbyPsyCenters(position.coords.latitude, position.coords.longitude, 20)
      } catch (error) {
        centerError.value = parseError(error, 'Failed to load nearby centers')
      } finally {
        loadingCenters.value = false
      }
    },
    () => {
      loadingCenters.value = false
      ElMessage.warning('Unable to acquire location. Please select city manually.')
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
        eyebrow="Emotion Intelligence Portal"
        title="AI Voice Emotion Analysis and Mental Risk Warning"
        subtitle="Upload or record voice, run multimodal analysis, and receive explainable reports with recommendations and support routes."
        primary-text="Start Recording"
        secondary-text="View Reports"
        @primary="handlePrimaryAction"
        @secondary="handleSecondaryAction"
      >
        <div class="hero-badge-row">
          <span class="hero-chip">Audio + Text multimodal fusion</span>
          <span class="hero-chip">Real-time task progress</span>
          <span class="hero-chip">Explainable risk scoring</span>
        </div>
      </HeroSection>

      <SectionBlock
        eyebrow="Workflow"
        title="Three-Step Closed Loop"
        description="An end-to-end chain from audio collection to warning and recommendation."
      >
        <div class="step-grid">
          <LoreCard title="01 Upload / Record" subtitle="Chunk upload, progress tracking, and secure account binding.">
            Voice collection supports browser recording and local file upload with resumable chunks.
          </LoreCard>
          <LoreCard title="02 Analyze" subtitle="ASR + acoustic emotion + text sentiment fusion model.">
            The system combines speech and transcript signals to infer emotion probabilities.
          </LoreCard>
          <LoreCard title="03 Report / Warning" subtitle="Risk score, warning level, suggestions and resources.">
            Output is structured and traceable for longitudinal follow-up and intervention.
          </LoreCard>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="Innovation"
        title="Core Differentiators"
        description="Built for graduation-level demonstration: clear architecture, practical workflow, and extensible governance hooks."
      >
        <div class="innovation-grid">
          <LoreCard title="Multimodal Fusion" subtitle="Acoustic emotion + text semantics">Late fusion of two modalities improves robustness in noisy scenarios.</LoreCard>
          <LoreCard title="Realtime Experience" subtitle="Task queue + progress + fallback states">Users can see processing status instead of waiting on a blank screen.</LoreCard>
          <LoreCard title="Explainable Score" subtitle="Score contribution + trend-aware warning">Reports expose confidence, risk level, and actionable recommendations.</LoreCard>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="Curated Content"
        title="Quote, Articles and Books"
        description="Operational content keeps the portal alive beyond one-off inference."
      >
        <LoreCard v-if="home?.todayQuote" :title="`Today's Quote`" :subtitle="home.todayQuote.author || 'Anonymous'">
          {{ home.todayQuote.content }}
        </LoreCard>

        <div class="content-grid">
          <div>
            <h3 class="group-title">Recommended Articles</h3>
            <div class="h-scroll">
              <LoreCard
                v-for="item in home?.recommendedArticles ?? []"
                :key="`article-${item.id}`"
                :title="item.title"
                :subtitle="item.summary || 'Click to open article'"
                interactive
                @click="openArticle(item)"
              />
            </div>
          </div>

          <div>
            <h3 class="group-title">Recommended Books</h3>
            <div class="h-scroll">
              <LoreCard
                v-for="item in home?.recommendedBooks ?? []"
                :key="`book-${item.id}`"
                :title="item.title"
                :subtitle="item.author || 'Recommended reading'"
                interactive
                @click="openBook(item)"
              >
                {{ item.description || 'Click card to open purchase or detail page.' }}
              </LoreCard>
            </div>
          </div>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="Support Resource"
        title="Psychology Centers"
        description="Choose city or locate nearby centers. API failures are isolated to this section without breaking the page."
      >
        <div class="resource-toolbar">
          <el-select v-model="cityCode" style="width: 180px" @change="loadCentersByCity">
            <el-option v-for="item in cityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button @click="locateNearbyCenters">Locate Nearby</el-button>
          <el-button type="primary" plain @click="loadCentersByCity">Refresh</el-button>
        </div>

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
          title="No center data"
          description="Resource list is temporarily unavailable. Please switch city or retry later."
          action-text="Retry"
          @action="loadCentersByCity"
        />
        <div v-else class="center-grid">
          <LoreCard
            v-for="center in centers"
            :key="String(center.id)"
            :title="center.name"
            :subtitle="`${center.cityName || ''} ${center.district || ''}`.trim()"
          >
            <p class="center-line">{{ center.address || 'Address unavailable' }}</p>
            <p class="center-line">{{ center.phone || 'Phone unavailable' }}</p>
          </LoreCard>
        </div>
      </SectionBlock>
    </template>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-badge-row {
  margin-top: 18px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-chip {
  display: inline-flex;
  border-radius: 999px;
  border: 1px solid rgba(174, 193, 225, 0.5);
  background: rgba(11, 23, 42, 0.6);
  color: #d7e7ff;
  font-size: 12px;
  padding: 6px 11px;
}

.step-grid,
.innovation-grid,
.center-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.content-grid {
  display: grid;
  gap: 16px;
}

.group-title {
  margin: 0 0 10px;
  color: #f5f9ff;
  font-family: var(--font-display);
  letter-spacing: 0.03em;
}

.h-scroll {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(260px, 320px);
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.resource-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.center-line {
  margin: 6px 0 0;
  color: #c4d5ef;
  line-height: 1.6;
}

@media (max-width: 980px) {
  .step-grid,
  .innovation-grid,
  .center-grid {
    grid-template-columns: 1fr;
  }
}
</style>