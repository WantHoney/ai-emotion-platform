<script setup lang="ts">
import { computed } from 'vue'

import type { ContentArticle } from '@/api/content'
import { ARTICLE_CATEGORY_LABELS, ARTICLE_DIFFICULTY_LABELS } from '@/constants/contentMeta'
import SmartImage from '@/components/ui/SmartImage.vue'

defineEmits<{
  action: []
}>()

const props = withDefaults(
  defineProps<{
    article: ContentArticle
    dense?: boolean
    quiet?: boolean
    variant?: 'cover' | 'text'
    actionText?: string
    showAction?: boolean
    highlightLimit?: number
  }>(),
  {
    dense: false,
    quiet: false,
    variant: 'cover',
    actionText: '查看详情',
    showAction: true,
  },
)

const visibleHighlights = computed(() => {
  const fallbackLimit = props.variant === 'text'
    ? props.dense || props.quiet
      ? 1
      : 2
    : props.dense
      ? 1
      : 2
  return props.article.highlights.slice(0, props.highlightLimit ?? fallbackLimit)
})

const compactNote = computed(() => {
  if (!(props.variant === 'text' && props.quiet)) return ''
  return props.article.recommendReason || props.article.fitFor || ''
})

const showSource = computed(() => {
  if (props.variant === 'text') {
    return !props.quiet || props.showAction
  }
  return !props.quiet
})
</script>

<template>
  <article
    class="article-card"
    :class="{
      dense,
      quiet,
      'article-card--text': variant === 'text',
      'article-card--passive': !showAction,
    }"
  >
    <div v-if="variant === 'cover'" class="article-card__cover">
      <SmartImage :src="article.coverImageUrl" :alt="article.title" kind="article" fit="cover" />
    </div>

    <div class="article-card__content">
      <div class="article-card__meta">
        <span class="pill pill-muted">
          {{ ARTICLE_CATEGORY_LABELS[article.category || ''] || '内容专栏' }}
        </span>
        <span v-if="article.difficultyTag && !dense && !quiet" class="pill pill-accent">
          {{ ARTICLE_DIFFICULTY_LABELS[article.difficultyTag] || article.difficultyTag }}
        </span>
        <span v-if="article.readingMinutes && !quiet" class="pill pill-soft">{{ article.readingMinutes }} 分钟</span>
      </div>

      <div class="article-card__head">
        <h3>{{ article.title }}</h3>
        <p>{{ article.sourceName || '推荐文章' }}</p>
      </div>

      <p v-if="article.summary" class="article-card__summary">{{ article.summary }}</p>

      <p v-if="compactNote" class="article-card__note">{{ compactNote }}</p>

      <div v-if="!dense && !quiet" class="article-card__details">
        <div v-if="article.recommendReason" class="article-card__detail">
          <span>为什么读</span>
          <p>{{ article.recommendReason }}</p>
        </div>
        <div v-if="article.fitFor" class="article-card__detail">
          <span>适合谁</span>
          <p>{{ article.fitFor }}</p>
        </div>
      </div>

      <ul v-if="visibleHighlights.length && !quiet" class="article-card__highlights">
        <li v-for="item in visibleHighlights" :key="item">{{ item }}</li>
      </ul>

      <div
        v-if="showAction || showSource"
        class="article-card__footer"
        :class="{ 'article-card__footer--solo': !showAction }"
      >
        <span v-if="showSource" class="article-card__source">来源：{{ article.sourceName || '已配置来源' }}</span>
        <el-button v-if="showAction" type="primary" plain size="small" @click="$emit('action')">{{ actionText }}</el-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.article-card {
  display: grid;
  grid-template-columns: minmax(190px, 230px) minmax(0, 1fr);
  gap: var(--content-gap-3);
  padding: 20px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-2);
  background: var(--content-surface-2);
  box-shadow: var(--content-shadow-1);
  transition:
    transform var(--content-motion-fast) var(--content-ease-standard),
    border-color var(--content-motion-fast) var(--content-ease-standard),
    box-shadow var(--content-motion-fast) var(--content-ease-standard);
}

.article-card.dense {
  grid-template-columns: minmax(144px, 168px) minmax(0, 1fr);
  gap: var(--content-gap-2);
  padding: 15px;
}

.article-card.quiet {
  padding: 16px;
  gap: var(--content-gap-2);
}

.article-card--text {
  position: relative;
  overflow: hidden;
  grid-template-columns: 1fr;
  background:
    radial-gradient(circle at 88% 18%, rgba(106, 182, 148, 0.1), transparent 22%),
    radial-gradient(circle at 82% 78%, rgba(105, 136, 193, 0.08), transparent 24%),
    linear-gradient(180deg, rgba(16, 26, 44, 0.96), rgba(10, 17, 31, 0.94));
}

.article-card--text.dense {
  padding: 16px;
}

.article-card--text.quiet {
  min-height: 174px;
  padding: 18px;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.015), rgba(255, 255, 255, 0)),
    radial-gradient(circle at 92% 18%, rgba(131, 193, 199, 0.14), transparent 20%),
    linear-gradient(180deg, rgba(13, 21, 36, 0.94), rgba(10, 17, 31, 0.92));
  box-shadow: none;
}

.article-card__cover {
  overflow: hidden;
  border-radius: var(--content-radius-1);
  border: 1px solid var(--content-border-1);
  min-height: 196px;
  background: rgba(9, 16, 28, 0.72);
  box-shadow: var(--content-shadow-1);
}

.article-card.dense .article-card__cover {
  min-height: 152px;
}

.article-card__content,
.article-card__meta,
.article-card__details,
.article-card__footer {
  display: flex;
  gap: var(--content-gap-2);
  flex-wrap: wrap;
}

.article-card__content {
  flex-direction: column;
  min-width: 0;
}

.article-card--text .article-card__content {
  position: relative;
  z-index: 1;
  max-width: 58ch;
}

.article-card--text.quiet .article-card__content {
  max-width: 46ch;
}

.article-card__head {
  display: grid;
  gap: 6px;
}

.article-card__head h3 {
  margin: 0;
  color: #f5fbff;
  font-size: 28px;
  line-height: 1.16;
}

.article-card.dense .article-card__head h3 {
  font-size: 22px;
}

.article-card--text .article-card__head h3 {
  font-size: 30px;
}

.article-card--text.dense .article-card__head h3 {
  font-size: 22px;
}

.article-card--text.quiet .article-card__head h3 {
  font-size: 19px;
}

.article-card__head p,
.article-card__summary,
.article-card__detail p,
.article-card__source {
  margin: 0;
  color: #b2c5e3;
  line-height: 1.65;
}

.article-card__head p,
.article-card__source {
  font-size: 13px;
}

.article-card__summary {
  color: #dbe7f8;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.article-card--text .article-card__summary {
  -webkit-line-clamp: 4;
}

.article-card.dense .article-card__summary,
.article-card--text.dense .article-card__summary,
.article-card--text.quiet .article-card__summary {
  -webkit-line-clamp: 2;
}

.article-card__note {
  margin: 0;
  padding-top: 10px;
  border-top: 1px solid var(--content-border-1);
  color: #9eb8d9;
  line-height: 1.65;
}

.article-card__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.article-card__detail {
  padding: 12px 14px;
  border-radius: var(--content-radius-1);
  background: var(--content-surface-inset);
  border: 1px solid var(--content-border-1);
}

.article-card__detail span {
  display: inline-block;
  margin-bottom: 6px;
  color: #8fc3c8;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.article-card__highlights {
  margin: 0;
  padding-left: 18px;
  color: #eff6ff;
  display: grid;
  gap: 6px;
}

.article-card__footer {
  margin-top: auto;
  justify-content: space-between;
  align-items: center;
}

.article-card--text .article-card__footer {
  padding-top: 10px;
  border-top: 1px solid var(--content-border-1);
}

.article-card__footer--solo {
  justify-content: flex-start;
}

.article-card--passive {
  height: 100%;
}

.pill {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 11px;
  border-radius: var(--content-radius-pill);
  font-size: 12px;
}

.pill-muted {
  color: #dceafb;
  background: var(--content-chip-muted-surface);
}

.pill-accent {
  color: #f4fff7;
  background: var(--content-chip-accent-surface);
}

.pill-soft {
  color: #fff4df;
  background: var(--content-chip-gold-surface);
}

@media (max-width: 860px) {
  .article-card,
  .article-card.dense {
    grid-template-columns: 1fr;
  }

  .article-card__details {
    grid-template-columns: 1fr;
  }
}
</style>
