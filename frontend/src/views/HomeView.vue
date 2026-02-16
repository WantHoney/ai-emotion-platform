<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getLightDashboard, postContentClick, type ContentType, type LightDashboardData } from '@/api/admin'
import { getHomeContent, type BannerItem, type HomePayload, type RecommendedArticle, type RecommendedBook, type SelfHelpEntry } from '@/api/home'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { parseError, type ErrorStatePayload } from '@/utils/error'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const home = ref<HomePayload | null>(null)
const dashboard = ref<LightDashboardData | null>(null)
const errorState = ref<ErrorStatePayload | null>(null)

const loadHome = async () => {
  loading.value = true
  errorState.value = null
  try {
    const [homeData, dashboardData] = await Promise.all([getHomeContent(), getLightDashboard().catch(() => null)])
    home.value = homeData
    dashboard.value = dashboardData
  } catch (error) {
    errorState.value = parseError(error, '首页数据加载失败')
  } finally {
    loading.value = false
  }
}

const reportClick = (contentType: ContentType, contentId: string | number) => {
  void postContentClick(contentType, contentId).catch(() => undefined)
}

const openBanner = (item: BannerItem) => {
  reportClick('BANNER', item.id)
  if (item.linkUrl) {
    window.open(item.linkUrl, '_blank', 'noopener,noreferrer')
  }
}

const openArticle = (item: RecommendedArticle) => {
  reportClick('ARTICLE', item.id)
  if (item.contentUrl) {
    window.open(item.contentUrl, '_blank', 'noopener,noreferrer')
  }
}

const openBook = (item: RecommendedBook) => {
  reportClick('BOOK', item.id)
  if (item.purchaseUrl) {
    window.open(item.purchaseUrl, '_blank', 'noopener,noreferrer')
  }
}

const usePractice = (entry: SelfHelpEntry) => {
  reportClick('PRACTICE', entry.key)

  if (!authStore.isAuthenticated && !entry.path.startsWith('http')) {
    ElMessage.warning('请先登录后再使用该功能。')
    void router.push({ path: '/login', query: { redirect: entry.path || '/upload' } })
    return
  }

  if (entry.path.startsWith('http')) {
    window.open(entry.path, '_blank', 'noopener,noreferrer')
    return
  }
  void router.push(entry.path || '/upload')
}

onMounted(() => {
  void loadHome()
})
</script>

<template>
  <div class="home-page">
    <LoadingState v-if="loading" />
    <ErrorState
      v-else-if="errorState"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadHome"
    />
    <EmptyState
      v-else-if="!home"
      title="暂无首页数据"
      description="请确认后端 /api/home 接口返回内容。"
      action-text="重新加载"
      @action="loadHome"
    />
    <template v-else>
      <el-row v-if="dashboard" :gutter="16" class="mt-16">
        <el-col :xs="24" :sm="8"><el-statistic title="上传总量" :value="Number(dashboard.uploadCount ?? 0)" /></el-col>
        <el-col :xs="24" :sm="8"><el-statistic title="报告总量" :value="Number(dashboard.reportCount ?? 0)" /></el-col>
        <el-col :xs="24" :sm="8"><el-statistic title="内容点击" :value="Number(dashboard.contentClickCount ?? 0)" /></el-col>
      </el-row>

      <el-card v-if="home.banners.length" class="mt-16">
        <template #header>精选 Banner</template>
        <el-carousel height="260px" indicator-position="outside">
          <el-carousel-item v-for="item in home.banners" :key="item.id">
            <div class="banner-item" @click="openBanner(item)">
              <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.title" class="banner-image" />
              <div class="banner-text">
                <h3>{{ item.title }}</h3>
                <p>发现今天的心理成长内容</p>
              </div>
            </div>
          </el-carousel-item>
        </el-carousel>
      </el-card>

      <el-card v-if="home.todayQuote" class="mt-16">
        <template #header>今日一句</template>
        <blockquote class="quote">“{{ home.todayQuote.content }}”</blockquote>
        <p v-if="home.todayQuote.author" class="quote-author">—— {{ home.todayQuote.author }}</p>
      </el-card>

      <el-row :gutter="16" class="mt-16">
        <el-col :xs="24" :md="12">
          <el-card>
            <template #header>推荐专栏</template>
            <el-empty v-if="!home.recommendedArticles.length" description="暂无推荐专栏" />
            <div v-else class="card-grid">
              <article v-for="item in home.recommendedArticles" :key="item.id" class="content-card" @click="openArticle(item)">
                <h4>{{ item.title }}</h4>
                <p>{{ item.summary || '查看专栏详情' }}</p>
              </article>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="12">
          <el-card>
            <template #header>推荐书籍</template>
            <el-empty v-if="!home.recommendedBooks.length" description="暂无推荐书籍" />
            <div v-else class="card-grid">
              <article v-for="item in home.recommendedBooks" :key="item.id" class="content-card" @click="openBook(item)">
                <h4>{{ item.title }}</h4>
                <p>{{ item.author || item.description || '点击查看书籍' }}</p>
              </article>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="mt-16">
        <template #header>自助练习入口</template>
        <el-empty v-if="!home.selfHelpEntries.length" description="暂无练习入口" />
        <div v-else class="practice-list">
          <el-button v-for="entry in home.selfHelpEntries" :key="entry.key" type="primary" plain @click="usePractice(entry)">
            {{ entry.title }}
          </el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.mt-16 {
  margin-top: 16px;
}

.banner-item {
  position: relative;
  height: 100%;
  border-radius: 12px;
  overflow: hidden;
  background: #0f172a;
  cursor: pointer;
}

.banner-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.6;
}

.banner-text {
  position: absolute;
  inset: 0;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 16px;
}

.quote {
  margin: 0;
  color: #334155;
  font-size: 18px;
}

.quote-author {
  margin-top: 8px;
  color: #64748b;
}

.card-grid {
  display: grid;
  gap: 12px;
}

.content-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
}

.content-card h4 {
  margin: 0;
}

.content-card p {
  margin: 6px 0 0;
  color: #64748b;
}

.practice-list {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
