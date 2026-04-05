<script setup lang="ts">
import { computed } from 'vue'

import SmartImage from '@/components/ui/SmartImage.vue'
import type { ContentArticle } from '@/api/content'
import { ARTICLE_CATEGORY_LABELS, ARTICLE_DIFFICULTY_LABELS } from '@/constants/contentMeta'

defineEmits<{
  action: []
}>()

const props = withDefaults(
  defineProps<{
    article: ContentArticle
    dense?: boolean
    quiet?: boolean
    actionText?: string
    showAction?: boolean
    highlightLimit?: number
  }>(),
  {
    dense: false,
    quiet: false,
    actionText: '查看详情',
    showAction: true,
  },
)

const visibleHighlights = computed(() =>
  props.article.highlights.slice(0, props.highlightLimit ?? (props.dense ? 1 : 2)),
)
</script>

<template>
  <article class="article-card" :class="{ dense, quiet, 'article-card--passive': !showAction }">
    <div class="article-card__cover">
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
        v-if="showAction || !quiet"
        class="article-card__footer"
        :class="{ 'article-card__footer--solo': !showAction || quiet }"
      >
        <span v-if="!quiet" class="article-card__source">来源：{{ article.sourceName || '已配置来源' }}</span>
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
  gap: var(--content-gap-2);
  padding: 16px;
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

.article-card.quiet .article-card__cover {
  min-height: 136px;
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

.article-card__head {
  display: grid;
  gap: 6px;
}

.article-card__head h3 {
  margin: 0;
  color: #f5fbff;
  font-size: 28px;
  line-height: 1.18;
}

.article-card.dense .article-card__head h3 {
  font-size: 22px;
}

.article-card.quiet .article-card__head h3 {
  font-size: 20px;
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

.article-card.dense .article-card__summary {
  -webkit-line-clamp: 2;
}

.article-card.quiet .article-card__summary {
  -webkit-line-clamp: 2;
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
