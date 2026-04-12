<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getContentBookDetail, postContentHistory, type BookDetailPayload } from '@/api/content'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import SmartImage from '@/components/ui/SmartImage.vue'
import { ARTICLE_CATEGORY_LABELS } from '@/constants/contentMeta'
import { useUserAuthStore } from '@/stores/userAuth'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const authStore = useUserAuthStore()

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const payload = ref<BookDetailPayload | null>(null)

const previewArticle = computed(() => payload.value?.relatedArticles?.[0] ?? null)
const heroHighlights = computed(() => (payload.value?.book.highlights ?? []).slice(0, 2))

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
      title="这本书暂时找不到了"
      description="可以先回内容专栏看看别的推荐入口。"
      action-text="返回专栏"
      @action="router.push('/app/content')"
    />
    <template v-else>
      <section class="book-decision">
        <div class="book-decision__visual">
          <div class="book-decision__cover">
            <SmartImage :src="payload.book.coverImageUrl" :alt="payload.book.title" kind="book" fit="cover" />
          </div>
        </div>

        <div class="book-decision__content">
          <div class="book-decision__meta">
            <span v-if="payload.book.category" class="pill pill-muted">
              {{ ARTICLE_CATEGORY_LABELS[payload.book.category] || payload.book.category }}
            </span>
            <span class="pill pill-soft">书籍导读</span>
          </div>

          <p class="book-decision__eyebrow">站内导读</p>
          <h1>{{ payload.book.title }}</h1>
          <p class="book-decision__author">{{ payload.book.author || '推荐阅读' }}</p>
          <p class="book-decision__summary">
            {{
              payload.book.description ||
              '先用这一页判断这本书值不值得你现在继续读，再决定要不要跳到外部了解更多。'
            }}
          </p>

          <div class="book-decision__notes">
            <div class="decision-note">
              <span>这本适合你，如果</span>
              <p>{{ payload.book.fitFor || '你想把眼前的问题读得更深一点，而不是只停在短内容里。' }}</p>
            </div>
            <div class="decision-note">
              <span>你会先得到什么</span>
              <p>{{ payload.book.recommendReason || '它会帮你把眼前的困扰放进更完整的理解里。' }}</p>
            </div>
          </div>

          <ul v-if="heroHighlights.length" class="book-decision__list">
            <li v-for="item in heroHighlights" :key="item">{{ item }}</li>
          </ul>

          <div class="book-decision__actions">
            <el-button @click="router.push('/app/content')">返回专栏</el-button>
            <el-button type="primary" size="large" :disabled="!payload.book.purchaseUrl" @click="openPurchase">
              前往了解更多
            </el-button>
          </div>

          <p class="book-decision__hint">先用这一页判断值不值得，再决定是否跳到外部继续看。</p>
        </div>
      </section>

      <section v-if="previewArticle" class="paired-reading">
        <div class="paired-reading__head">
          <p class="paired-reading__eyebrow">搭配文章</p>
          <h2>如果想先抓住重点，先看这篇</h2>
          <p>先用一篇更轻的内容把主线看清楚，再决定要不要继续往外部了解更多。</p>
        </div>

        <div class="paired-reading__card">
          <article class="paired-reading__article">
            <div class="paired-reading__meta">
              <span class="pill pill-muted">
                {{ ARTICLE_CATEGORY_LABELS[previewArticle.category || ''] || '推荐文章' }}
              </span>
              <span v-if="previewArticle.readingMinutes" class="pill pill-soft">
                {{ previewArticle.readingMinutes }} 分钟
              </span>
            </div>

            <h3>{{ previewArticle.title }}</h3>
            <p class="paired-reading__source">{{ previewArticle.sourceName || '推荐文章' }}</p>
            <p class="paired-reading__summary">
              {{
                previewArticle.summary ||
                previewArticle.recommendReason ||
                '先把重点抓住，再决定下一步。'
              }}
            </p>

            <div class="paired-reading__footer">
              <span class="paired-reading__hint">{{ previewArticle.fitFor || '适合先看一篇更轻内容的人。' }}</span>
              <el-button type="primary" plain @click="openArticle(previewArticle.id)">先看文章导读</el-button>
            </div>
          </article>
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
  max-width: 1020px;
  margin: 0 auto;
}

.book-decision,
.paired-reading {
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(171, 193, 228, 0.2);
  background:
    radial-gradient(circle at top right, rgba(103, 177, 138, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(15, 24, 40, 0.94), rgba(10, 16, 30, 0.94));
}

.book-decision {
  grid-template-columns: 320px minmax(0, 1fr);
  align-items: start;
  gap: 28px;
}

.book-decision__visual {
  display: flex;
  justify-content: center;
}

.book-decision__cover {
  width: 100%;
  max-width: 292px;
  aspect-ratio: 0.72;
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid rgba(176, 194, 226, 0.22);
  background: rgba(14, 22, 38, 0.76);
  box-shadow: 0 20px 36px rgba(4, 11, 22, 0.34);
}

.book-decision__content {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.book-decision__meta,
.book-decision__actions,
.paired-reading__meta,
.paired-reading__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.book-decision__eyebrow,
.paired-reading__eyebrow {
  margin: 0;
  color: #e2c08b;
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.book-decision h1,
.paired-reading h2,
.paired-reading h3 {
  margin: 0;
  color: #f4fbff;
}

.book-decision h1 {
  font-size: clamp(36px, 4vw, 50px);
  line-height: 1.04;
  font-family: var(--font-display);
}

.paired-reading h2 {
  font-size: clamp(28px, 3vw, 34px);
  line-height: 1.12;
  font-family: var(--font-display);
}

.paired-reading h3 {
  font-size: 28px;
  line-height: 1.14;
}

.book-decision__author {
  margin: 0;
  color: #dfe9f8;
  font-size: 16px;
  font-weight: 600;
}

.book-decision__summary,
.decision-note p,
.book-decision__hint,
.paired-reading__head p,
.paired-reading__source,
.paired-reading__summary,
.paired-reading__hint {
  margin: 0;
  color: #b6c8e5;
  line-height: 1.7;
}

.book-decision__summary,
.paired-reading__summary {
  color: #dde8f8;
}

.book-decision__notes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.decision-note {
  padding: 16px;
  border-radius: 18px;
  background: rgba(14, 23, 39, 0.72);
  border: 1px solid rgba(120, 144, 181, 0.16);
}

.decision-note span {
  display: inline-block;
  margin-bottom: 8px;
  color: #e4c28f;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.book-decision__list {
  margin: 0;
  padding-left: 20px;
  display: grid;
  gap: 8px;
  color: #eff6ff;
}

.paired-reading__head {
  display: grid;
  gap: 8px;
}

.paired-reading__card {
  display: grid;
  gap: 18px;
  align-items: stretch;
}

.paired-reading__article {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(165, 189, 223, 0.18);
  background:
    radial-gradient(circle at top right, rgba(103, 177, 138, 0.1), transparent 32%),
    linear-gradient(180deg, rgba(16, 27, 45, 0.96), rgba(10, 16, 30, 0.94));
}

.paired-reading__article {
  display: grid;
  gap: 14px;
}

.paired-reading__source {
  color: #d6e2f3;
  font-size: 14px;
  font-weight: 600;
}

.paired-reading__footer {
  align-items: center;
  justify-content: space-between;
}

.paired-reading__hint {
  flex: 1;
  min-width: 220px;
}

.pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.pill-muted {
  background: rgba(120, 150, 202, 0.18);
  color: #d7e4f7;
}

.pill-soft {
  background: rgba(221, 191, 127, 0.18);
  color: #f2d9a5;
}

@media (max-width: 980px) {
  .book-decision,
  .book-decision__notes,
  .paired-reading__card {
    grid-template-columns: 1fr;
  }

  .book-decision__visual {
    justify-content: flex-start;
  }

  .book-decision__cover {
    max-width: 240px;
  }
}
</style>
