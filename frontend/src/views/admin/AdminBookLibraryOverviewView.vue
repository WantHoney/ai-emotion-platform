<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getBooks, type CmsBook } from '@/api/cms'
import EmptyState from '@/components/states/EmptyState.vue'
import ErrorState from '@/components/states/ErrorState.vue'
import LoadingState from '@/components/states/LoadingState.vue'
import SmartImage from '@/components/ui/SmartImage.vue'
import { ARTICLE_CATEGORY_LABELS, ARTICLE_CATEGORY_OPTIONS } from '@/constants/contentMeta'
import { parseError, type ErrorStatePayload } from '@/utils/error'

const router = useRouter()

const loading = ref(false)
const errorState = ref<ErrorStatePayload | null>(null)
const books = ref<CmsBook[]>([])
const keyword = ref('')
const selectedCategory = ref('all')
const sourceFilter = ref<'all' | 'seed' | 'manual'>('all')
const showOnlyRecommended = ref(false)
const showOnlyEnabled = ref(false)

const filteredBooks = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return books.value.filter((book) => {
    if (selectedCategory.value !== 'all' && book.category !== selectedCategory.value) {
      return false
    }
    if (sourceFilter.value !== 'all' && (book.dataSource || 'manual') !== sourceFilter.value) {
      return false
    }
    if (showOnlyRecommended.value && !book.recommended) {
      return false
    }
    if (showOnlyEnabled.value && !book.enabled) {
      return false
    }
    if (!search) {
      return true
    }
    const haystack = [
      book.title,
      book.author,
      book.category,
      book.seedKey,
      book.coverImageUrl,
      book.purchaseUrl,
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return haystack.includes(search)
  })
})

const stats = computed(() => {
  const total = books.value.length
  const recommended = books.value.filter((book) => book.recommended).length
  const enabled = books.value.filter((book) => book.enabled).length
  const seed = books.value.filter((book) => book.dataSource === 'seed').length
  const missingCover = books.value.filter((book) => !book.coverImageUrl).length
  const missingLink = books.value.filter((book) => !book.purchaseUrl).length
  return { total, recommended, enabled, seed, missingCover, missingLink }
})

const categoryStats = computed(() => {
  const counts = new Map<string, number>()
  for (const book of books.value) {
    const key = book.category || 'uncategorized'
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return [
    { value: 'all', label: '全部', count: books.value.length },
    ...ARTICLE_CATEGORY_OPTIONS.map((option) => ({
      value: option.value,
      label: option.label,
      count: counts.get(option.value) || 0,
    })),
  ]
})

const loadBooks = async () => {
  loading.value = true
  errorState.value = null
  try {
    books.value = await getBooks()
  } catch (error) {
    errorState.value = parseError(error, '书籍总览加载失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  keyword.value = ''
  selectedCategory.value = 'all'
  sourceFilter.value = 'all'
  showOnlyRecommended.value = false
  showOnlyEnabled.value = false
}

const goBooksManage = async () => {
  await router.push('/admin/content/books')
}

const openUserDetail = (id: number) => {
  window.open(`/app/content/books/${id}`, '_blank', 'noopener,noreferrer')
}

const openDouban = (url?: string) => {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

onMounted(() => {
  void loadBooks()
})
</script>

<template>
  <div class="book-overview-page">
    <section class="overview-hero">
      <div>
        <p class="overview-hero__eyebrow">管理端巡检</p>
        <h1>书籍总览</h1>
        <p class="overview-hero__summary">
          用来集中检查书名、作者、封面、链接和分类，方便内容运营快速巡检并补齐缺失信息。
        </p>
      </div>

      <div class="overview-hero__actions">
        <el-button @click="loadBooks">刷新</el-button>
        <el-button type="primary" @click="goBooksManage">前往书籍管理</el-button>
      </div>
    </section>

    <section class="overview-stats">
      <div class="stat-card">
        <span>总书数</span>
        <strong>{{ stats.total }}</strong>
      </div>
      <div class="stat-card">
        <span>推荐书</span>
        <strong>{{ stats.recommended }}</strong>
      </div>
      <div class="stat-card">
        <span>启用中</span>
        <strong>{{ stats.enabled }}</strong>
      </div>
      <div class="stat-card">
        <span>种子书</span>
        <strong>{{ stats.seed }}</strong>
      </div>
      <div class="stat-card" :class="{ warning: stats.missingCover > 0 }">
        <span>缺封面</span>
        <strong>{{ stats.missingCover }}</strong>
      </div>
      <div class="stat-card" :class="{ warning: stats.missingLink > 0 }">
        <span>缺链接</span>
        <strong>{{ stats.missingLink }}</strong>
      </div>
    </section>

    <section class="overview-toolbar">
      <div class="overview-toolbar__main">
        <el-input v-model="keyword" clearable placeholder="搜书名 / 作者 / seedKey / 封面路径 / 豆瓣链接" />
        <el-select v-model="selectedCategory" class="overview-toolbar__select">
          <el-option
            v-for="item in categoryStats"
            :key="item.value"
            :label="`${item.label} · ${item.count}`"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="sourceFilter" class="overview-toolbar__select narrow">
          <el-option label="全部来源" value="all" />
          <el-option label="种子" value="seed" />
          <el-option label="人工" value="manual" />
        </el-select>
        <el-checkbox v-model="showOnlyRecommended">只看推荐</el-checkbox>
        <el-checkbox v-model="showOnlyEnabled">只看启用</el-checkbox>
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <div class="overview-toolbar__chips">
        <button
          v-for="item in categoryStats"
          :key="item.value"
          class="overview-chip"
          :class="{ active: selectedCategory === item.value }"
          @click="selectedCategory = item.value"
        >
          {{ item.label }} {{ item.count }}
        </button>
      </div>
    </section>

    <LoadingState v-if="loading && !books.length" />
    <ErrorState
      v-else-if="errorState && !books.length"
      :title="errorState.title"
      :detail="errorState.detail"
      :trace-id="errorState.traceId"
      @retry="loadBooks"
    />
    <EmptyState
      v-else-if="!filteredBooks.length"
      title="当前筛选下没有书"
      description="可以放宽筛选条件，或者直接去书籍管理页继续维护。"
      action-text="重置筛选"
      @action="resetFilters"
    />

    <section v-else class="overview-grid">
      <article v-for="book in filteredBooks" :key="book.id" class="overview-card">
        <div class="overview-card__cover">
          <SmartImage :src="book.coverImageUrl" :alt="book.title" kind="book" fit="contain" />
        </div>

        <div class="overview-card__body">
          <div class="overview-card__meta">
            <span v-if="book.category" class="overview-pill">
              {{ ARTICLE_CATEGORY_LABELS[book.category] || book.category }}
            </span>
            <span v-if="book.recommended" class="overview-pill overview-pill--soft">推荐</span>
            <span class="overview-pill overview-pill--ghost">排序 {{ book.sortOrder ?? '-' }}</span>
            <span class="overview-pill overview-pill--ghost">{{ book.dataSource || 'manual' }}</span>
          </div>

          <h2>{{ book.title }}</h2>
          <p class="overview-card__author">{{ book.author || '未知作者' }}</p>
          <p class="overview-card__desc">{{ book.description || '暂无简介' }}</p>

          <dl class="overview-card__facts">
            <div>
              <dt>seedKey</dt>
              <dd>{{ book.seedKey || '-' }}</dd>
            </div>
            <div>
              <dt>封面路径</dt>
              <dd>{{ book.coverImageUrl || '-' }}</dd>
            </div>
            <div>
              <dt>豆瓣链接</dt>
              <dd class="overview-card__link">{{ book.purchaseUrl || '-' }}</dd>
            </div>
          </dl>

          <div class="overview-card__actions">
            <el-button type="primary" @click="openUserDetail(book.id)">看用户导读页</el-button>
            <el-button plain @click="goBooksManage">去书籍管理</el-button>
            <el-button plain :disabled="!book.purchaseUrl" @click="openDouban(book.purchaseUrl)">打开豆瓣</el-button>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.book-overview-page {
  display: grid;
  gap: 18px;
}

.overview-hero,
.overview-stats,
.overview-toolbar {
  border-radius: 24px;
  border: 1px solid rgba(165, 189, 223, 0.18);
  background:
    radial-gradient(circle at top right, rgba(103, 177, 138, 0.1), transparent 32%),
    linear-gradient(180deg, rgba(16, 27, 45, 0.96), rgba(10, 16, 30, 0.94));
  padding: 22px 24px;
}

.overview-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-end;
}

.overview-hero__eyebrow {
  margin: 0 0 8px;
  color: #8fd4ff;
  font-size: 13px;
  font-weight: 700;
}

.overview-hero h1 {
  margin: 0;
  color: #f4fbff;
  font-size: clamp(34px, 4vw, 46px);
  line-height: 1.06;
  font-family: var(--font-display);
}

.overview-hero__summary {
  margin: 10px 0 0;
  color: #b8cae7;
  line-height: 1.7;
}

.overview-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(11, 19, 33, 0.7);
  border: 1px solid rgba(120, 144, 181, 0.14);
  display: grid;
  gap: 8px;
}

.stat-card span {
  color: #9fb4d8;
  font-size: 13px;
}

.stat-card strong {
  color: #f4fbff;
  font-size: 30px;
  line-height: 1;
}

.stat-card.warning strong {
  color: #ffd497;
}

.overview-toolbar {
  display: grid;
  gap: 14px;
}

.overview-toolbar__main {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.overview-toolbar__select {
  width: 220px;
}

.overview-toolbar__select.narrow {
  width: 150px;
}

.overview-toolbar__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.overview-chip {
  border: 1px solid rgba(157, 180, 214, 0.18);
  background: rgba(14, 23, 39, 0.82);
  color: #d9e7f7;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.overview-chip.active {
  border-color: rgba(201, 174, 130, 0.85);
  background: rgba(201, 174, 130, 0.16);
  color: #fff4df;
}

.overview-grid {
  display: grid;
  gap: 16px;
}

.overview-card {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
  padding: 18px;
  border-radius: 24px;
  border: 1px solid rgba(165, 189, 223, 0.18);
  background:
    radial-gradient(circle at top right, rgba(103, 177, 138, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(16, 27, 45, 0.96), rgba(10, 16, 30, 0.94));
}

.overview-card__cover {
  width: 160px;
  aspect-ratio: 0.72;
  overflow: hidden;
  border-radius: 20px;
  border: 1px solid rgba(167, 190, 224, 0.2);
  background: rgba(13, 21, 35, 0.82);
}

.overview-card__body {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.overview-card__meta,
.overview-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview-card h2 {
  margin: 0;
  color: #f4fbff;
  font-size: 28px;
  line-height: 1.14;
  font-family: var(--font-display);
}

.overview-card__author,
.overview-card__desc {
  margin: 0;
  color: #dbe8fa;
  line-height: 1.7;
}

.overview-card__author {
  color: #9fb4d8;
  font-weight: 600;
}

.overview-card__facts {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(9, 16, 28, 0.58);
  border: 1px solid rgba(120, 144, 181, 0.14);
}

.overview-card__facts div {
  display: grid;
  gap: 4px;
}

.overview-card__facts dt {
  color: #8fa7cc;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.overview-card__facts dd {
  margin: 0;
  color: #eff6ff;
  word-break: break-all;
}

.overview-card__link {
  color: #8fd4ff;
}

.overview-pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(120, 150, 202, 0.16);
  color: #d8e5f9;
  font-size: 12px;
  font-weight: 700;
}

.overview-pill--soft {
  background: rgba(201, 174, 130, 0.16);
  color: #f2d6a1;
}

.overview-pill--ghost {
  background: rgba(15, 23, 37, 0.78);
  color: #aabfdf;
}

@media (max-width: 1200px) {
  .overview-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .overview-hero,
  .overview-card {
    grid-template-columns: 1fr;
  }

  .overview-card__cover {
    width: min(220px, 100%);
  }
}

@media (max-width: 680px) {
  .overview-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
