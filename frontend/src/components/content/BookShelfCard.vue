<script setup lang="ts">
import { computed } from 'vue'

import SmartImage from '@/components/ui/SmartImage.vue'
import type { ContentBook } from '@/api/content'
import { ARTICLE_CATEGORY_LABELS } from '@/constants/contentMeta'

defineEmits<{
  action: []
}>()

const props = withDefaults(
  defineProps<{
    book: ContentBook
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
  props.book.highlights.slice(0, props.highlightLimit ?? (props.dense ? 1 : 2)),
)
</script>

<template>
  <article class="book-card" :class="{ dense, quiet, 'book-card--passive': !showAction }">
    <div class="book-card__frame">
      <div class="book-card__cover">
        <SmartImage :src="book.coverImageUrl" :alt="book.title" kind="book" fit="cover" />
      </div>
    </div>

    <div class="book-card__body">
      <div class="book-card__meta">
        <span v-if="book.category" class="pill pill-muted">
          {{ ARTICLE_CATEGORY_LABELS[book.category] || book.category }}
        </span>
        <span v-if="!dense && !quiet" class="pill pill-book">书籍推荐</span>
      </div>

      <div class="book-card__head">
        <h3>{{ book.title }}</h3>
        <p>{{ book.author || '推荐阅读' }}</p>
      </div>

      <p v-if="book.description" class="book-card__description">{{ book.description }}</p>

      <div v-if="!dense && !quiet" class="book-card__notes">
        <div v-if="book.recommendReason" class="book-card__note">
          <span>推荐理由</span>
          <p>{{ book.recommendReason }}</p>
        </div>
        <div v-if="book.fitFor" class="book-card__note">
          <span>适合谁</span>
          <p>{{ book.fitFor }}</p>
        </div>
      </div>

      <ul v-if="visibleHighlights.length && !quiet" class="book-card__highlights">
        <li v-for="item in visibleHighlights" :key="item">{{ item }}</li>
      </ul>

      <div
        v-if="showAction || !quiet"
        class="book-card__footer"
        :class="{ 'book-card__footer--solo': !showAction || quiet }"
      >
        <span v-if="!quiet" class="book-card__hint">先看站内导读，再决定是否继续外跳。</span>
        <el-button v-if="showAction" type="primary" plain size="small" @click="$emit('action')">{{ actionText }}</el-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.book-card {
  display: grid;
  grid-template-columns: minmax(138px, 168px) minmax(0, 1fr);
  gap: var(--content-gap-4);
  padding: 20px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-2);
  background: var(--content-surface-2);
  box-shadow: var(--content-shadow-1);
}

.book-card.dense {
  grid-template-columns: minmax(122px, 144px) minmax(0, 1fr);
  gap: var(--content-gap-3);
  padding: 15px;
}

.book-card.quiet {
  gap: var(--content-gap-2);
  padding: 16px;
}

.book-card__frame {
  display: grid;
  gap: 10px;
  padding: 12px;
  border-radius: var(--content-radius-1);
  background: var(--content-surface-inset);
  border: 1px solid var(--content-border-1);
}

.book-card__frame::after {
  content: '';
  display: block;
  height: 10px;
  border-radius: var(--content-radius-pill);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
}

.book-card__cover {
  overflow: hidden;
  border-radius: var(--content-radius-1);
  min-height: 220px;
  box-shadow: var(--content-shadow-1);
}

.book-card.dense .book-card__cover {
  min-height: 180px;
}

.book-card.quiet .book-card__cover {
  min-height: 156px;
}

.book-card__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.book-card__meta,
.book-card__footer {
  display: flex;
  align-items: center;
  gap: var(--content-gap-2);
  flex-wrap: wrap;
}

.book-card__head h3 {
  margin: 0;
  color: #f7fbff;
  font-size: 26px;
  line-height: 1.2;
}

.book-card__head {
  display: grid;
  gap: 6px;
}

.book-card.dense .book-card__head h3 {
  font-size: 20px;
}

.book-card.quiet .book-card__head h3 {
  font-size: 19px;
}

.book-card__head p,
.book-card__description,
.book-card__note p,
.book-card__hint {
  margin: 0;
  color: #b4c6e3;
  line-height: 1.65;
}

.book-card__description {
  color: #dbe7f9;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.book-card.dense .book-card__description {
  -webkit-line-clamp: 2;
}

.book-card.quiet .book-card__description {
  -webkit-line-clamp: 2;
}

.book-card__notes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.book-card__note {
  padding: 12px 14px;
  border-radius: var(--content-radius-1);
  background: var(--content-surface-inset);
  border: 1px solid var(--content-border-1);
}

.book-card__note span {
  display: inline-block;
  margin-bottom: 6px;
  color: #e4c28f;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.book-card__highlights {
  margin: 0;
  padding-left: 18px;
  color: #eff6ff;
  display: grid;
  gap: 6px;
}

.book-card__footer {
  margin-top: auto;
  justify-content: space-between;
}

.book-card__footer--solo {
  justify-content: flex-start;
}

.book-card--passive {
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

.pill-book {
  color: #fff5df;
  background: var(--content-chip-gold-surface);
}

@media (max-width: 860px) {
  .book-card,
  .book-card.dense {
    grid-template-columns: 1fr;
  }

  .book-card__notes {
    grid-template-columns: 1fr;
  }
}
</style>
