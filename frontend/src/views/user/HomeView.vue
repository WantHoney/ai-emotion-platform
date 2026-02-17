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
import { useUserAuthStore } from '@/stores/userAuth'

const router = useRouter()
const authStore = useUserAuthStore()

const home = ref<HomePayload | null>(null)
const loadingHome = ref(false)
const homeError = ref<ErrorStatePayload | null>(null)

const cityCode = ref('310100')
const centers = ref<PsyCenter[]>([])
const loadingCenters = ref(false)
const centerError = ref<ErrorStatePayload | null>(null)

const cityOptions = [
  { label: '上海', value: '310100' },
  { label: '北京', value: '110100' },
  { label: '广州', value: '440100' },
  { label: '深圳', value: '440300' },
  { label: '杭州', value: '330100' },
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
  ElMessage.info('请先登录用户账号再访问该功能')
  await router.push({ path: '/app/login', query: { redirect: path } })
}

const handlePrimaryAction = async () => {
  await goProtectedPath('/app/upload')
}

const handleSecondaryAction = async () => {
  await goProtectedPath('/app/reports')
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
    homeError.value = parseError(error, '首页内容加载失败')
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
      try {
        centers.value = await getNearbyPsyCenters(position.coords.latitude, position.coords.longitude, 20)
      } catch (error) {
        centerError.value = parseError(error, '附近心理中心加载失败')
      } finally {
        loadingCenters.value = false
      }
    },
    () => {
      loadingCenters.value = false
      ElMessage.warning('定位失败，请手动切换城市。')
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
        eyebrow="情绪智能门户"
        title="AI 语音情绪分析与心理状态预警系统"
        subtitle="上传或录制语音，执行声学+文本多模态分析，生成可解释报告并提供建议与资源引导。"
        primary-text="开始上传/录音"
        secondary-text="查看报告"
        @primary="handlePrimaryAction"
        @secondary="handleSecondaryAction"
      >
        <div class="hero-badge-row">
          <span class="hero-chip">声学 + 文本多模态融合</span>
          <span class="hero-chip">任务进度实时可见</span>
          <span class="hero-chip">可解释风险评分</span>
        </div>
      </HeroSection>

      <SectionBlock
        eyebrow="核心流程"
        title="三步闭环"
        description="从语音采集到预警建议的一体化链路。"
      >
        <div class="step-grid">
          <LoreCard title="01 上传 / 录音" subtitle="分片上传、进度追踪、账号绑定。">
            语音采集支持浏览器录音与本地文件上传，可中断续传。
          </LoreCard>
          <LoreCard title="02 分析识别" subtitle="ASR + 声学情绪 + 文本语义融合。">
            系统联合语音信号与转写文本，推断情绪分布与风险倾向。
          </LoreCard>
          <LoreCard title="03 报告 / 预警" subtitle="风险评分、预警等级、建议与资源。">
            结果结构化可追踪，便于长期随访与干预闭环。
          </LoreCard>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="创新能力"
        title="系统亮点"
        description="面向毕设展示：架构清晰、流程可落地、治理能力可扩展。"
      >
        <div class="innovation-grid">
          <LoreCard title="多模态融合" subtitle="声学情绪 + 文本语义">采用 late fusion 融合两种信号，在噪声场景下更稳健。</LoreCard>
          <LoreCard title="实时体验" subtitle="任务队列 + 进度 + 兜底状态">用户可持续看到处理进度，不再面对白屏等待。</LoreCard>
          <LoreCard title="可解释评分" subtitle="评分贡献 + 趋势预警">报告展示置信度、风险等级与可执行建议。</LoreCard>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="内容推荐"
        title="语录、文章与书籍"
        description="通过持续运营内容，让系统不仅“能分析”，也能“可陪伴”。"
      >
        <LoreCard v-if="home?.todayQuote" :title="`今日语录`" :subtitle="home.todayQuote.author || '佚名'">
          {{ home.todayQuote.content }}
        </LoreCard>

        <div class="content-grid">
          <div>
            <h3 class="group-title">推荐文章</h3>
            <div class="h-scroll">
              <LoreCard
                v-for="item in home?.recommendedArticles ?? []"
                :key="`article-${item.id}`"
                :title="item.title"
                :subtitle="item.summary || '点击卡片查看文章'"
                interactive
                @click="openArticle(item)"
              />
            </div>
          </div>

          <div>
            <h3 class="group-title">推荐书籍</h3>
            <div class="h-scroll">
              <LoreCard
                v-for="item in home?.recommendedBooks ?? []"
                :key="`book-${item.id}`"
                :title="item.title"
                :subtitle="item.author || '推荐阅读'"
                interactive
                @click="openBook(item)"
              >
                {{ item.description || '点击卡片打开购买或详情页面。' }}
              </LoreCard>
            </div>
          </div>
        </div>
      </SectionBlock>

      <SectionBlock
        eyebrow="支持资源"
        title="心理中心"
        description="可按城市筛选或定位附近机构；接口异常仅影响本区块，不会导致页面崩溃。"
      >
        <div class="resource-toolbar">
          <el-select v-model="cityCode" style="width: 180px" @change="loadCentersByCity">
            <el-option v-for="item in cityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button @click="locateNearbyCenters">定位附近</el-button>
          <el-button type="primary" plain @click="loadCentersByCity">刷新</el-button>
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
          title="暂无心理中心数据"
          description="当前资源列表不可用，请切换城市或稍后重试。"
          action-text="重试"
          @action="loadCentersByCity"
        />
        <div v-else class="center-grid">
          <LoreCard
            v-for="center in centers"
            :key="String(center.id)"
            :title="center.name"
            :subtitle="`${center.cityName || ''} ${center.district || ''}`.trim()"
          >
            <p class="center-line">{{ center.address || '暂无地址信息' }}</p>
            <p class="center-line">{{ center.phone || '暂无电话信息' }}</p>
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
