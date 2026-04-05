<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getContentHub, type ContentHubPayload } from '@/api/content'
import ArchiveCalendar from '@/components/content/ArchiveCalendar.vue'
import ArticleFeatureCard from '@/components/content/ArticleFeatureCard.vue'
import BookShelfCard from '@/components/content/BookShelfCard.vue'
import ContentStateTabs from '@/components/content/ContentStateTabs.vue'
import QuoteHero from '@/components/content/QuoteHero.vue'
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
const payload = ref<ContentHubPayload | null>(null)
const archiveDrawerVisible = ref(false)

const selectedCategory = computed(() => String(route.query.category ?? ''))
const selectedDate = computed(() => String(route.query.date ?? ''))

const dailyPackage = computed(() => payload.value?.dailyPackage ?? null)
const selectedCategoryPayload = computed(() => payload.value?.categoryHighlights ?? null)
const hasArchiveDates = computed(() => (payload.value?.archiveDates?.length ?? 0) > 0)
const selectedCategoryKey = computed(
  () => selectedCategoryPayload.value?.selectedCategory || dailyPackage.value?.theme?.themeKey || 'stress',
)
const selectedCategoryLabel = computed(
  () => ARTICLE_CATEGORY_LABELS[selectedCategoryKey.value] || selectedCategoryKey.value,
)
const readingBooks = computed(() => (selectedCategoryPayload.value?.books ?? []).slice(0, 1))
const readingArticles = computed(() => {
  const items = selectedCategoryPayload.value?.articles ?? []
  return items.slice(0, readingBooks.value.length ? 2 : 3)
})
const secondaryArticles = computed(() => {
  const featuredId = dailyPackage.value?.featuredArticle?.id
  return (dailyPackage.value?.articles ?? []).filter((item) => item.id !== featuredId)
})
const secondaryBooks = computed(() => {
  const featuredId = dailyPackage.value?.featuredBook?.id
  return (dailyPackage.value?.books ?? []).filter((item) => item.id !== featuredId)
})
const supportingBooks = computed(() => secondaryBooks.value.slice(0, 1))
const supportingArticles = computed(() => secondaryArticles.value.slice(0, supportingBooks.value.length ? 2 : 3))
const hasSupportingContent = computed(() => supportingArticles.value.length > 0 || supportingBooks.value.length > 0)

const formatDateLabel = (value?: string) => {
  if (!value) return ''
  const [year, month, day] = value.split('-')
  if (!year || !month || !day) return value
  return `${year} 年 ${month} 月 ${day} 日`
}

const updateQuery = async (patch: Record<string, string | undefined>) => {
  const nextQuery = {
    ...route.query,
    ...patch,
  }
  Object.keys(nextQuery).forEach((key) => {
    if (!nextQuery[key]) {
      delete nextQuery[key]
    }
  })
  await router.push({ path: route.path, query: nextQuery })
}

const loadHub = async () => {
  loading.value = true
  errorState.value = null
  try {
    payload.value = await getContentHub({
      date: selectedDate.value || undefined,
      category: selectedCategory.value || undefined,
    })
  } catch (error) {
    errorState.value = parseError(error, '内容专栏加载失败')
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

const openArchiveDrawer = () => {
  archiveDrawerVisible.value = true
}

const selectArchiveDate = async (date: string) => {
  archiveDrawerVisible.value = false
  await updateQuery({ date })
}

watch(
  () => [route.query.date, route.query.category],
  () => {
    void loadHub()
  },
)

onMounted(() => {
  void loadHub()
})
</script>

<template>
  <div class="content-hub-page user-layout">
    <LoadingState v-if="loading && !payload" />
    <ErrorState
      v-else-if="errorState && !payload"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadHub"
    />
    <template v-else>
      <QuoteHero
        class="content-fade-rise"
        :theme="dailyPackage?.theme"
        :quote="dailyPackage?.quote"
        :date-label="formatDateLabel(payload?.selectedDate || payload?.todayDate)"
      >
        <template #actions>
          <el-button @click="updateQuery({ date: payload?.todayDate })">回到今天</el-button>
        </template>
      </QuoteHero>

      <section class="hub-switcher content-fade-rise">
        <div class="hub-switcher__copy">
          <p class="hub-switcher__eyebrow">先选一个更贴近你的方向</p>
          <h2>现在更需要哪一种支持？</h2>
          <p>先把今天的阅读入口定下来，再决定要不要继续往下看。</p>
        </div>
        <ContentStateTabs :model-value="selectedCategoryKey" @change="updateQuery({ category: $event })" />
      </section>

      <EmptyState
        v-if="dailyPackage && !dailyPackage.hasSchedule"
        title="该日期暂无每日内容"
        description="可以先回到今天，或者通过页面底部的往期入口切换到其他日期。"
        action-text="回到今天"
        @action="updateQuery({ date: payload?.todayDate })"
      />

      <template v-else-if="dailyPackage">
        <section class="hub-section hub-section--featured content-fade-rise">
          <div class="section-head section-head--featured">
            <h2>今天先从这里看</h2>
            <p>先读主推文章，把今天的主题落到更具体的一步；配套书适合往深里读。</p>
          </div>

          <div class="feature-stage">
            <div class="feature-stage__article">
              <div class="feature-stage__intro">
                <p class="feature-stage__eyebrow">主推文章</p>
                <h3>先看这篇</h3>
                <p>如果今天只读一个内容，先从这里开始。</p>
              </div>
              <ArticleFeatureCard
                v-if="dailyPackage.featuredArticle"
                :article="dailyPackage.featuredArticle"
                action-text="查看文章导读"
                @action="openArticle(dailyPackage.featuredArticle.id)"
              />
            </div>
            <div class="feature-stage__book">
              <div class="feature-stage__book-copy">
                <p class="feature-stage__eyebrow">配套书籍</p>
                <h3>想往深一点，再看这本</h3>
                <p>如果今天这条线索对你有帮助，它适合继续往里读。</p>
              </div>
              <BookShelfCard
                v-if="dailyPackage.featuredBook"
                :book="dailyPackage.featuredBook"
                dense
                action-text="查看书籍导读"
                @action="openBook(dailyPackage.featuredBook.id)"
              />
            </div>
          </div>
        </section>

        <section class="hub-section hub-section--reading content-fade-rise">
          <div class="section-head">
            <h2>换一条更贴近你的线索</h2>
            <p>当前选择：{{ selectedCategoryLabel }}</p>
          </div>

          <transition name="content-swap" mode="out-in">
            <div :key="selectedCategoryKey" class="detail-grid detail-grid--stream">
              <button
                v-for="item in readingArticles"
                :key="`category-article-${item.id}`"
                type="button"
                class="content-card-shell"
                @click="openArticle(item.id)"
              >
                <ArticleFeatureCard :article="item" dense quiet :show-action="false" :highlight-limit="0" />
              </button>
              <button
                v-for="item in readingBooks"
                :key="`category-book-${item.id}`"
                type="button"
                class="content-card-shell"
                @click="openBook(item.id)"
              >
                <BookShelfCard :book="item" dense quiet :show-action="false" :highlight-limit="0" />
              </button>
            </div>
          </transition>

          <div v-if="hasSupportingContent" class="reading-subsection">
            <div class="section-head section-head--quiet section-head--subtle">
              <h3>如果还想再看一条</h3>
              <p>剩下的内容收在这里，不让它们和主线抢焦点。</p>
            </div>

            <div class="detail-grid detail-grid--supporting">
              <button
                v-for="item in supportingArticles"
                :key="`secondary-article-${item.id}`"
                type="button"
                class="content-card-shell"
                @click="openArticle(item.id)"
              >
                <ArticleFeatureCard :article="item" dense quiet :show-action="false" :highlight-limit="0" />
              </button>
              <button
                v-for="item in supportingBooks"
                :key="`secondary-book-${item.id}`"
                type="button"
                class="content-card-shell"
                @click="openBook(item.id)"
              >
                <BookShelfCard :book="item" dense quiet :show-action="false" :highlight-limit="0" />
              </button>
            </div>
          </div>
        </section>

        <section
          class="hub-section hub-section--utility content-fade-rise"
          v-if="authStore.isAuthenticated && payload?.recentHistory.length"
        >
          <div class="section-head section-head--quiet">
            <h2>继续上次看到的地方</h2>
            <p>只对登录用户显示。</p>
          </div>

          <div class="history-grid">
            <button
              v-for="item in payload.recentHistory"
              :key="`${item.contentType}-${item.contentId}`"
              type="button"
              class="history-card"
              @click="item.contentType === 'ARTICLE' ? openArticle(item.contentId) : openBook(item.contentId)"
            >
              <span class="history-card__type">{{ item.contentType === 'ARTICLE' ? '文章' : '书籍' }}</span>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description || item.subtitle || '点击继续阅读' }}</p>
              <small>{{ item.subtitle || '最近看过' }}</small>
            </button>
          </div>
        </section>
      </template>

      <section v-if="hasArchiveDates" class="archive-entry content-fade-rise">
        <div class="archive-entry__copy">
          <p class="archive-entry__eyebrow">往期入口</p>
          <h2>还想回看前几天？</h2>
          <p>需要时再展开看，不让历史内容打断今天的阅读节奏。</p>
        </div>
        <el-button type="primary" plain class="archive-entry__action" @click="openArchiveDrawer">查看往期内容</el-button>
      </section>

      <el-drawer
        v-model="archiveDrawerVisible"
        title="往期内容"
        size="420px"
        :with-header="true"
        class="archive-drawer"
      >
        <div class="archive-drawer__body">
          <p class="archive-drawer__lead">这里保留按日期回看的能力，但不再占用专栏主页面的大块空间。</p>
          <ArchiveCalendar
            :dates="payload?.archiveDates ?? []"
            :selected-date="payload?.selectedDate"
            :today-date="payload?.todayDate"
            @select="selectArchiveDate"
          />
        </div>
      </el-drawer>
    </template>
  </div>
</template>

<style scoped>
.content-hub-page {
  display: flex;
  flex-direction: column;
  gap: var(--content-gap-3);
}

.hub-switcher {
  display: grid;
  grid-template-columns: minmax(220px, 300px) minmax(0, 1fr);
  gap: var(--content-gap-3);
  align-items: end;
  padding: 0 4px;
}

.hub-switcher__copy {
  display: grid;
  gap: 6px;
}

.hub-switcher__eyebrow {
  margin: 0;
  color: #8fc3c8;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.hub-switcher__copy h2,
.hub-switcher__copy p {
  margin: 0;
}

.hub-switcher__copy h2 {
  color: #f4fbff;
  font-size: 22px;
}

.hub-switcher__copy p {
  color: #99b0d1;
  line-height: 1.7;
}

.hub-section {
  display: grid;
  gap: var(--content-gap-3);
  padding: 24px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
  box-shadow: var(--content-shadow-1);
}

.hub-section--featured {
  gap: var(--content-gap-4);
  box-shadow: var(--content-shadow-2);
}

.hub-section--reading {
  background: var(--content-surface-2);
}

.hub-section--utility {
  gap: var(--content-gap-2);
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.section-head h2,
.section-head h3,
.section-head p {
  margin: 0;
}

.section-head h2 {
  color: #f4fbff;
  font-size: 24px;
}

.section-head h3 {
  color: #eef6ff;
  font-size: 18px;
}

.section-head p {
  margin-top: 8px;
  color: #aac0e2;
  line-height: 1.7;
}

.section-head--quiet h2 {
  font-size: 22px;
}

.section-head--quiet p {
  color: #96afd0;
}

.section-head--subtle h3 {
  font-size: 17px;
}

.feature-stage,
.detail-grid,
.history-grid {
  display: grid;
  gap: var(--content-gap-3);
}

.detail-grid {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.feature-stage {
  grid-template-columns: minmax(0, 2.08fr) minmax(300px, 0.92fr);
  align-items: start;
}

.detail-grid--stream {
  align-items: stretch;
}

.detail-grid--supporting {
  gap: var(--content-gap-2);
}

.feature-stage__article,
.feature-stage__book,
.feature-stage__intro,
.feature-stage__book-copy {
  display: grid;
}

.feature-stage__article,
.feature-stage__book {
  gap: var(--content-gap-2);
}

.feature-stage__book {
  padding: 18px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-inset);
  box-shadow: var(--content-shadow-1);
}

.feature-stage__intro,
.feature-stage__book-copy {
  gap: 8px;
}

.feature-stage__eyebrow {
  margin: 0;
  color: #8dc5c8;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.feature-stage__intro h3,
.feature-stage__book-copy h3 {
  margin: 0;
  color: #f4fbff;
  font-family: var(--font-display);
}

.feature-stage__intro h3 {
  font-size: 28px;
}

.feature-stage__book-copy h3 {
  font-size: 22px;
}

.feature-stage__intro p:last-child,
.feature-stage__book-copy p:last-child {
  margin: 0;
  color: #aac0e2;
  line-height: 1.7;
}

.reading-subsection {
  display: grid;
  gap: var(--content-gap-2);
  padding-top: 20px;
  border-top: 1px solid var(--content-border-1);
}

.history-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.content-card-shell {
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.content-card-shell :deep(.article-card),
.content-card-shell :deep(.book-card) {
  height: 100%;
}

.content-card-shell:hover :deep(.article-card),
.content-card-shell:hover :deep(.book-card) {
  transform: translateY(-2px);
  border-color: var(--content-border-3);
  box-shadow: var(--content-shadow-2);
}

.history-card {
  display: flex;
  flex-direction: column;
  gap: var(--content-gap-2);
  align-items: flex-start;
  padding: 18px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
  box-shadow: var(--content-shadow-1);
  color: #eef5ff;
  text-align: left;
  cursor: pointer;
  transition:
    transform var(--content-motion-fast) var(--content-ease-standard),
    border-color var(--content-motion-fast) var(--content-ease-standard),
    box-shadow var(--content-motion-fast) var(--content-ease-standard);
}

.history-card:hover {
  transform: translateY(-2px);
  border-color: var(--content-border-3);
  box-shadow: var(--content-shadow-2);
}

.history-card__type {
  display: inline-flex;
  min-height: 28px;
  padding: 0 10px;
  align-items: center;
  border-radius: var(--content-radius-pill);
  color: #fff4df;
  background: var(--content-chip-gold-surface);
  font-size: 12px;
}

.history-card strong,
.history-card p,
.history-card small {
  margin: 0;
}

.history-card p {
  color: #c9d8ef;
  line-height: 1.6;
}

.history-card small {
  color: #8fa8cf;
}

.archive-entry {
  display: flex;
  justify-content: space-between;
  gap: var(--content-gap-3);
  align-items: center;
  padding: 20px 24px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: linear-gradient(180deg, rgba(12, 20, 35, 0.9), rgba(10, 18, 31, 0.78));
  box-shadow: var(--content-shadow-1);
}

.archive-entry__copy {
  display: grid;
  gap: 6px;
}

.archive-entry__eyebrow,
.archive-drawer__lead {
  margin: 0;
  color: #9ab6db;
  line-height: 1.7;
}

.archive-entry__eyebrow {
  color: #8fc3c8;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.archive-entry__copy h2,
.archive-entry__copy p {
  margin: 0;
}

.archive-entry__copy h2 {
  color: #f4fbff;
  font-size: 22px;
}

.archive-entry__copy p {
  color: #aac0e2;
  line-height: 1.7;
}

.archive-entry__action {
  align-self: flex-start;
  width: fit-content;
}

.archive-drawer__body {
  display: grid;
  gap: var(--content-gap-3);
}

.content-swap-enter-active,
.content-swap-leave-active {
  transition:
    opacity var(--content-motion-base) var(--content-ease-standard),
    transform var(--content-motion-base) var(--content-ease-standard);
}

.content-swap-enter-from,
.content-swap-leave-to {
  opacity: 0;
  transform: translateY(14px);
}

@media (max-width: 980px) {
  .hub-switcher,
  .feature-stage,
  .detail-grid,
  .history-grid {
    grid-template-columns: 1fr;
  }

  .archive-entry {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
