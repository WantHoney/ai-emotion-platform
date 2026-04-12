<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getContentHub, type ContentArticle, type ContentBook, type ContentHubPayload } from '@/api/content'
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
const showBackToToday = computed(
  () => Boolean(payload.value?.selectedDate && payload.value.selectedDate !== payload.value?.todayDate),
)

const currentCategoryKey = computed(
  () => selectedCategoryPayload.value?.selectedCategory || dailyPackage.value?.theme?.themeKey || 'stress',
)
const currentCategoryLabel = computed(
  () => ARTICLE_CATEGORY_LABELS[currentCategoryKey.value] || currentCategoryKey.value,
)

const categoryArticles = computed(() => selectedCategoryPayload.value?.articles ?? [])
const categoryBooks = computed(() => selectedCategoryPayload.value?.books ?? [])

const stageArticle = computed<ContentArticle | null>(() => categoryArticles.value[0] ?? dailyPackage.value?.featuredArticle ?? null)
const stageBook = computed<ContentBook | null>(() => categoryBooks.value[0] ?? dailyPackage.value?.featuredBook ?? null)
const hasStageContent = computed(() => Boolean(stageArticle.value || stageBook.value))

const supportingArticles = computed(() => {
  const activeId = stageArticle.value?.id
  const base = categoryArticles.value.filter((item) => item.id !== activeId)
  return base.slice(0, 3)
})

const supportingBooks = computed(() => {
  const activeId = stageBook.value?.id
  const base = categoryBooks.value.filter((item) => item.id !== activeId)
  return base.slice(0, 1)
})

const hasSupportingContent = computed(() => supportingArticles.value.length > 0 || supportingBooks.value.length > 0)
const recentHistoryItems = computed(() => (payload.value?.recentHistory ?? []).slice(0, 4))
const hasRecentHistory = computed(() => authStore.isAuthenticated && recentHistoryItems.value.length > 0)

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
      />

      <EmptyState
        v-if="dailyPackage && !dailyPackage.hasSchedule"
        title="这一天暂时还没有内容排期"
        description="先回到今天，或者换一天看看。"
        action-text="回到今天"
        @action="updateQuery({ date: payload?.todayDate })"
      />

      <template v-else-if="dailyPackage">
        <section class="hub-section hub-section--stage content-fade-rise">
          <div class="stage-entry">
          <div class="stage-entry__copy">
              <p class="stage-entry__eyebrow">先选方向</p>
              <h2>先选一个更贴近你的方向</h2>
              <p>不用一次看很多，先从现在最需要的支持开始，再决定要不要继续往下读。</p>
            </div>
            <el-button v-if="showBackToToday" text class="stage-entry__back" @click="updateQuery({ date: payload?.todayDate })">
              回到今天
            </el-button>
          </div>

          <ContentStateTabs :model-value="currentCategoryKey" @change="updateQuery({ category: $event })" />

          <div v-if="hasStageContent" class="stage-block">
            <div class="section-head section-head--split section-head--compact">
              <div>
                <p class="section-head__eyebrow">今日先看</p>
                <h2>先从这一组内容开始</h2>
              </div>
              <p>先看这篇文章理解当下的线索，如果想继续了解，再看旁边这本书。</p>
            </div>

            <div class="feature-stage" :class="{ 'feature-stage--single': !(stageArticle && stageBook) }">
              <ArticleFeatureCard
                v-if="stageArticle"
                class="feature-stage__article"
                :article="stageArticle"
                variant="text"
                action-text="查看文章导读"
                @action="openArticle(stageArticle.id)"
              />

              <div v-if="stageBook" class="feature-stage__book">
                <div class="feature-stage__book-copy">
                  <p class="section-head__eyebrow">配套书籍</p>
                  <h3>想读得更深，再看这本</h3>
                  <p>如果刚才那篇内容对你有帮助，这本书适合继续往下读。</p>
                </div>
                <BookShelfCard
                  :book="stageBook"
                  dense
                  action-text="查看书籍导读"
                  @action="openBook(stageBook.id)"
                />
              </div>
            </div>
          </div>
        </section>

        <section v-if="hasSupportingContent" class="hub-section hub-section--supporting content-fade-rise">
          <div class="section-head section-head--split section-head--compact section-head--quiet">
            <div>
              <p class="section-head__eyebrow">补充阅读</p>
              <h2>如果还想继续看</h2>
            </div>
            <p>如果刚才那组内容还不够，这里还有几条可以继续往下看。</p>
          </div>

          <div class="supporting-stream">
            <button
              v-for="item in supportingArticles"
              :key="`secondary-article-${item.id}`"
              type="button"
              class="content-card-shell"
              @click="openArticle(item.id)"
            >
              <ArticleFeatureCard
                :article="item"
                dense
                quiet
                variant="text"
                :show-action="false"
                :highlight-limit="0"
              />
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
        </section>

        <section v-if="hasRecentHistory" class="hub-section hub-section--utility content-fade-rise">
          <div class="section-head section-head--split section-head--compact section-head--quiet">
            <div>
              <p class="section-head__eyebrow">最近看过</p>
              <h2>接着上次看到的地方</h2>
            </div>
            <p>你最近看过的内容会放在这里，方便下次继续读。</p>
          </div>

          <div class="history-grid">
            <button
              v-for="item in recentHistoryItems"
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
          <p class="archive-entry__eyebrow">往期内容</p>
          <h2>想回看前几天的内容，再从这里展开</h2>
          <p>想回看前几天的内容，可以从这里打开。</p>
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
          <p class="archive-drawer__lead">这里保留按日期回看的能力，但不再占用专栏首页的大块空间。</p>
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

.hub-section {
  display: grid;
  gap: var(--content-gap-3);
  padding: 24px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  box-shadow: var(--content-shadow-1);
}

.hub-section--stage {
  background: var(--content-surface-2);
}

.hub-section--supporting {
  background: var(--content-surface-1);
}

.hub-section--utility {
  background: var(--content-surface-inset);
  box-shadow: none;
}

.stage-entry {
  display: flex;
  justify-content: space-between;
  gap: var(--content-gap-3);
  align-items: start;
}

.stage-entry__copy {
  display: grid;
  gap: 8px;
  max-width: 620px;
}

.stage-entry__eyebrow,
.section-head__eyebrow,
.archive-entry__eyebrow {
  margin: 0;
  color: #8fc3c8;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.stage-entry__copy h2,
.section-head h2,
.archive-entry__copy h2 {
  margin: 0;
  color: #f4fbff;
  font-family: var(--font-display);
}

.stage-entry__copy h2 {
  font-size: 28px;
  line-height: 1.12;
}

.stage-entry__copy p:last-child,
.section-head p,
.feature-stage__book-copy p:last-child,
.archive-entry__copy p,
.archive-drawer__lead {
  margin: 0;
  color: #a9c0e3;
  line-height: 1.7;
}

.stage-entry__back {
  padding-left: 0;
}

.stage-block {
  display: grid;
  gap: var(--content-gap-3);
}

.section-head {
  display: grid;
  gap: 8px;
}

.section-head--split {
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.82fr);
  gap: var(--content-gap-3);
  align-items: end;
}

.section-head--compact {
  grid-template-columns: minmax(0, 780px);
}

.section-head h2 {
  font-size: 30px;
  line-height: 1.12;
}

.section-head--quiet p {
  color: #96afd0;
}

.feature-stage {
  display: grid;
  grid-template-columns: minmax(0, 1.32fr) minmax(300px, 0.88fr);
  gap: var(--content-gap-3);
  align-items: stretch;
}

.feature-stage--single {
  grid-template-columns: 1fr;
}

.feature-stage__article {
  height: 100%;
}

.feature-stage__article :deep(.article-card--text .article-card__content) {
  margin-left: 24px;
  max-width: 54ch;
}

.feature-stage__book {
  display: grid;
  gap: var(--content-gap-2);
  padding: 18px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-inset);
  box-shadow: var(--content-shadow-1);
}

.feature-stage__book-copy {
  display: grid;
  gap: 8px;
}

.feature-stage__book-copy h3 {
  margin: 0;
  color: #f4fbff;
  font-family: var(--font-display);
  font-size: 22px;
  line-height: 1.18;
}

.supporting-stream,
.history-grid {
  display: grid;
  gap: var(--content-gap-2);
}

.supporting-stream {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.history-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.content-card-shell {
  display: block;
  width: 100%;
  height: 100%;
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
  background: var(--content-surface-inset);
  box-shadow: none;
}

.archive-entry__copy {
  display: grid;
  gap: 6px;
}

.archive-entry__action {
  align-self: flex-start;
  width: fit-content;
}

.archive-drawer__body {
  display: grid;
  gap: var(--content-gap-3);
}

.content-fade-rise {
  animation: contentFadeRise var(--content-motion-base) var(--content-ease-standard);
}

@keyframes contentFadeRise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1100px) {
  .feature-stage {
    grid-template-columns: 1fr;
  }

  .feature-stage__article :deep(.article-card--text .article-card__content) {
    margin-left: 12px;
  }
}

@media (max-width: 980px) {
  .stage-entry,
  .supporting-stream,
  .history-grid,
  .archive-entry {
    grid-template-columns: 1fr;
  }

  .stage-entry,
  .archive-entry {
    display: grid;
  }

  .feature-stage__article :deep(.article-card--text .article-card__content) {
    margin-left: 0;
    max-width: 58ch;
  }
}
</style>
