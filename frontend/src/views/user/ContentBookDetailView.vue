<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getContentBookDetail, postContentHistory, type BookDetailPayload } from '@/api/content'
import ArticleFeatureCard from '@/components/content/ArticleFeatureCard.vue'
import BookShelfCard from '@/components/content/BookShelfCard.vue'
import SmartImage from '@/components/ui/SmartImage.vue'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import { ARTICLE_CATEGORY_LABELS } from '@/constants/contentMeta'
import { useUserAuthStore } from '@/stores/userAuth'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const authStore = useUserAuthStore()

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const payload = ref<BookDetailPayload | null>(null)

const loadDetail = async () => {
  loading.value = true
  errorState.value = null
  try {
    payload.value = await getContentBookDetail(route.params.id as string)
    if (authStore.isAuthenticated) {
      void postContentHistory('VIEW', 'BOOK', route.params.id as string).catch(() => undefined)
    }
  } catch (error) {
    errorState.value = parseError(error, '书籍导读加载失败')
  } finally {
    loading.value = false
  }
}

const openBook = async (id: number) => {
  await router.push(`/app/content/books/${id}`)
}

const openArticle = async (id: number) => {
  await router.push(`/app/content/articles/${id}`)
}

const openPurchase = () => {
  const book = payload.value?.book
  if (!book?.purchaseUrl) return
  if (authStore.isAuthenticated) {
    void postContentHistory('OUTBOUND', 'BOOK', book.id).catch(() => undefined)
  }
  window.open(book.purchaseUrl, '_blank', 'noopener,noreferrer')
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
      title="书籍不存在"
      description="未找到对应书籍内容。"
      action-text="返回专栏"
      @action="router.push('/app/content')"
    />
    <template v-else>
      <section class="detail-hero">
        <div class="detail-hero__shelf">
          <div class="detail-hero__cover">
            <SmartImage :src="payload.book.coverImageUrl" :alt="payload.book.title" kind="book" fit="cover" />
          </div>
        </div>

        <div class="detail-hero__body">
          <div class="detail-hero__meta">
            <span v-if="payload.book.category" class="pill pill-muted">
              {{ ARTICLE_CATEGORY_LABELS[payload.book.category] || payload.book.category }}
            </span>
            <span class="pill pill-soft">书籍推荐</span>
          </div>

          <div class="detail-hero__head">
            <p class="detail-hero__eyebrow">站内导读</p>
            <h1>{{ payload.book.title }}</h1>
            <p>{{ payload.book.description }}</p>
          </div>

          <div class="detail-hero__notes">
            <div class="detail-note">
              <span>作者</span>
              <p>{{ payload.book.author || '待补充作者信息' }}</p>
            </div>
            <div class="detail-note">
              <span>推荐理由</span>
              <p>{{ payload.book.recommendReason || '适合放进更长期的主题阅读里。' }}</p>
            </div>
            <div class="detail-note">
              <span>适合谁</span>
              <p>{{ payload.book.fitFor || '适合想把这个主题读得更深一点的人。' }}</p>
            </div>
          </div>

          <div class="detail-hero__actions">
            <el-button @click="router.push('/app/content')">返回专栏</el-button>
            <el-button type="primary" :disabled="!payload.book.purchaseUrl" @click="openPurchase">
              前往了解更多
            </el-button>
          </div>
        </div>
      </section>

      <section class="detail-section">
        <div class="section-head">
          <h2>为什么这本书值得现在读</h2>
          <p>书籍是长期陪伴，不是短平快信息，所以这里更强调读它能带来的持续价值。</p>
        </div>
        <ul class="detail-list">
          <li v-for="item in payload.book.highlights" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="detail-section" v-if="payload.relatedBooks.length">
        <div class="section-head">
          <h2>同主题书架</h2>
          <p>继续沿着同一主题读下去，会比临时刷一堆碎片内容更稳。</p>
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

      <section class="detail-section" v-if="payload.relatedArticles.length">
        <div class="section-head">
          <h2>搭配阅读文章</h2>
          <p>先用文章快速抓住重点，再回到书里做更完整的理解。</p>
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
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 24px;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(171, 193, 228, 0.22);
  background:
    radial-gradient(circle at 12% 8%, rgba(193, 164, 109, 0.15), transparent 30%),
    linear-gradient(145deg, rgba(16, 27, 48, 0.96), rgba(9, 15, 28, 0.96));
}

.detail-hero__shelf {
  padding: 18px;
  border-radius: 26px;
  background: linear-gradient(180deg, rgba(44, 63, 95, 0.66), rgba(13, 22, 38, 0.96));
  border: 1px solid rgba(176, 194, 226, 0.16);
}

.detail-hero__cover {
  overflow: hidden;
  min-height: 360px;
  border-radius: 18px;
  box-shadow: 0 18px 32px rgba(3, 9, 20, 0.34);
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
  color: #e2c08b;
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
.detail-note p {
  margin: 12px 0 0;
  color: #b3c6e4;
  line-height: 1.7;
}

.detail-hero__notes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.detail-note {
  padding: 16px;
  border-radius: 18px;
  background: rgba(14, 23, 39, 0.72);
  border: 1px solid rgba(120, 144, 181, 0.16);
}

.detail-note span {
  color: #e4c28f;
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
