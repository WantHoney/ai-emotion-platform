<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getContentArticleDetail, postContentHistory, type ArticleDetailPayload } from '@/api/content'
import ArticleFeatureCard from '@/components/content/ArticleFeatureCard.vue'
import BookShelfCard from '@/components/content/BookShelfCard.vue'
import SmartImage from '@/components/ui/SmartImage.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { ARTICLE_CATEGORY_LABELS, ARTICLE_DIFFICULTY_LABELS } from '@/constants/contentMeta'
import { useUserAuthStore } from '@/stores/userAuth'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const authStore = useUserAuthStore()

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const payload = ref<ArticleDetailPayload | null>(null)

const loadDetail = async () => {
  loading.value = true
  errorState.value = null
  try {
    payload.value = await getContentArticleDetail(route.params.id as string)
    if (authStore.isAuthenticated) {
      void postContentHistory('VIEW', 'ARTICLE', route.params.id as string).catch(() => undefined)
    }
  } catch (error) {
    errorState.value = parseError(error, '文章导读加载失败')
  } finally {
    loading.value = false
  }
}

const openArticle = async (id: number) => {
  await router.push(`/app/content/articles/${id}`)
}

const openBook = async (id: number) => {
  await router.push(`/app/content/books/${id}`)
}

const openSource = () => {
  const article = payload.value?.article
  const target = article?.sourceUrl || article?.contentUrl
  if (!target) return
  if (authStore.isAuthenticated) {
    void postContentHistory('OUTBOUND', 'ARTICLE', article.id).catch(() => undefined)
  }
  window.open(target, '_blank', 'noopener,noreferrer')
}

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <div class="content-detail-page user-layout">
    <LoadingState v-if="loading && !payload" />
    <ErrorState
      v-else-if="errorState && !payload"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadDetail"
    />
    <EmptyState
      v-else-if="!payload"
      title="文章不存在"
      description="未找到对应文章内容。"
      action-text="返回专栏"
      @action="router.push('/app/content')"
    />
    <template v-else>
      <section class="detail-hero">
        <div class="detail-hero__cover">
          <SmartImage :src="payload.article.coverImageUrl" :alt="payload.article.title" kind="article" fit="cover" />
        </div>

        <div class="detail-hero__body">
          <div class="detail-hero__meta">
            <span class="pill pill-muted">
              {{ ARTICLE_CATEGORY_LABELS[payload.article.category || ''] || '内容专栏' }}
            </span>
            <span v-if="payload.article.difficultyTag" class="pill pill-accent">
              {{ ARTICLE_DIFFICULTY_LABELS[payload.article.difficultyTag] || payload.article.difficultyTag }}
            </span>
            <span v-if="payload.article.readingMinutes" class="pill pill-soft">
              {{ payload.article.readingMinutes }} 分钟
            </span>
          </div>

          <div class="detail-hero__head">
            <p class="detail-hero__eyebrow">站内导读</p>
            <h1>{{ payload.article.title }}</h1>
            <p>{{ payload.article.summary }}</p>
          </div>

          <div class="detail-hero__notes">
            <div class="detail-note">
              <span>推荐理由</span>
              <p>{{ payload.article.recommendReason || '这篇内容适合用来建立问题的第一层理解。' }}</p>
            </div>
            <div class="detail-note">
              <span>适合谁</span>
              <p>{{ payload.article.fitFor || '适合此刻想先搞清楚自己发生了什么的人。' }}</p>
            </div>
          </div>

          <div class="detail-hero__actions">
            <el-button @click="router.push('/app/content')">返回专栏</el-button>
            <el-button type="primary" @click="openSource">前往原始来源</el-button>
          </div>
        </div>
      </section>

      <section class="detail-section">
        <div class="section-head">
          <h2>读完你会得到什么</h2>
          <p>先在站内把内容价值和适配人群看清楚，再决定是否去原始来源深入阅读。</p>
        </div>
        <ul class="detail-list">
          <li v-for="item in payload.article.highlights" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="detail-section">
        <div class="section-head">
          <h2>来源说明</h2>
          <p>保持站内可读性，同时把来源透明地交给用户。</p>
        </div>
        <div class="source-panel">
          <div>
            <span>来源机构</span>
            <p>{{ payload.article.sourceName || '已配置来源' }}</p>
          </div>
          <div>
            <span>打开方式</span>
            <p>{{ payload.article.isExternal ? '将打开外部来源页' : '站内继续打开' }}</p>
          </div>
        </div>
      </section>

      <section class="detail-section" v-if="payload.relatedArticles.length">
        <div class="section-head">
          <h2>同主题文章</h2>
          <p>如果这篇对你有帮助，可以继续往同一主题深一点读。</p>
        </div>
        <div class="detail-grid">
          <ArticleFeatureCard
            v-for="item in payload.relatedArticles"
            :key="`article-${item.id}`"
            :article="item"
            dense
            @action="openArticle(item.id)"
          />
        </div>
      </section>

      <section class="detail-section" v-if="payload.relatedBooks.length">
        <div class="section-head">
          <h2>同主题书籍</h2>
          <p>如果你想把这个主题放进更长期的阅读里，可以从这些书开始。</p>
        </div>
        <div class="detail-grid">
          <BookShelfCard
            v-for="item in payload.relatedBooks"
            :key="`book-${item.id}`"
            :book="item"
            dense
            @action="openBook(item.id)"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.content-detail-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-hero {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 22px;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(171, 193, 228, 0.22);
  background:
    radial-gradient(circle at top right, rgba(103, 177, 138, 0.14), transparent 30%),
    linear-gradient(145deg, rgba(16, 27, 48, 0.96), rgba(9, 15, 28, 0.96));
}

.detail-hero__cover {
  overflow: hidden;
  min-height: 320px;
  border-radius: 24px;
  border: 1px solid rgba(168, 189, 221, 0.18);
}

.detail-hero__body,
.detail-hero__meta,
.detail-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-hero__body {
  flex-direction: column;
}

.detail-hero__eyebrow {
  margin: 0;
  color: #8fc5c7;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.detail-hero__head h1,
.section-head h2 {
  margin: 10px 0 0;
  color: #f4fbff;
}

.detail-hero__head h1 {
  font-size: clamp(30px, 4.4vw, 46px);
  line-height: 1.08;
  font-family: var(--font-display);
}

.detail-hero__head p,
.section-head p,
.detail-note p,
.source-panel p {
  margin: 12px 0 0;
  color: #b3c6e4;
  line-height: 1.7;
}

.detail-hero__notes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-note,
.source-panel > div {
  padding: 16px;
  border-radius: 18px;
  background: rgba(14, 23, 39, 0.72);
  border: 1px solid rgba(120, 144, 181, 0.16);
}

.detail-note span,
.source-panel span {
  color: #8fc4c8;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.detail-section {
  display: grid;
  gap: 14px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(165, 189, 223, 0.18);
  background: linear-gradient(180deg, rgba(15, 24, 40, 0.92), rgba(10, 16, 30, 0.92));
}

.detail-list {
  margin: 0;
  padding-left: 20px;
  display: grid;
  gap: 10px;
  color: #eff6ff;
}

.source-panel,
.detail-grid {
  display: grid;
  gap: 14px;
}

.pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  font-size: 12px;
}

.pill-muted {
  color: #dceafb;
  background: rgba(73, 103, 143, 0.28);
}

.pill-accent {
  color: #f4fff7;
  background: rgba(111, 182, 141, 0.28);
}

.pill-soft {
  color: #fff4df;
  background: rgba(194, 164, 108, 0.22);
}

@media (max-width: 980px) {
  .detail-hero {
    grid-template-columns: 1fr;
  }

  .detail-hero__notes {
    grid-template-columns: 1fr;
  }
}
</style>
