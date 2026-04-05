<script setup lang="ts">
import type { ContentQuote, ContentTheme } from '@/api/content'
import { ARTICLE_CATEGORY_LABELS } from '@/constants/contentMeta'

defineProps<{
  theme?: ContentTheme | null
  quote?: ContentQuote | null
  dateLabel?: string
  compact?: boolean
}>()
</script>

<template>
  <section class="quote-hero" :class="{ compact }">
    <div class="quote-hero__head">
      <div>
        <p class="quote-hero__eyebrow">今日主轴</p>
        <div class="quote-hero__chips">
          <span v-if="theme?.themeKey" class="quote-hero__chip">
            {{ ARTICLE_CATEGORY_LABELS[theme.themeKey] || theme.themeKey }}
          </span>
          <span v-if="dateLabel" class="quote-hero__chip quote-hero__chip-muted">{{ dateLabel }}</span>
        </div>
      </div>
      <div v-if="$slots.actions" class="quote-hero__actions">
        <slot name="actions" />
      </div>
    </div>

    <div class="quote-hero__body">
      <div class="quote-hero__theme">
        <p class="quote-hero__label">今日主题</p>
        <h2>{{ theme?.themeTitle || '今天先照顾自己。' }}</h2>
        <p>{{ theme?.themeSubtitle || '给自己一个更稳的入口，再继续往前走。' }}</p>
      </div>

      <div class="quote-hero__quote">
        <p class="quote-hero__label">今日语录</p>
        <blockquote>
          {{ quote?.content || '允许自己慢一点，不是退步，而是在给情绪留出被看见的时间。' }}
        </blockquote>
        <p class="quote-hero__author">{{ quote?.author || 'AI Emotion 编辑部' }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.quote-hero {
  display: grid;
  gap: var(--content-gap-4);
  padding: 30px;
  border-radius: var(--content-radius-3);
  border: 1px solid var(--content-border-2);
  background: var(--content-surface-3);
  box-shadow: var(--content-shadow-3);
}

.quote-hero.compact {
  padding: 24px;
  gap: var(--content-gap-3);
}

.quote-hero__head,
.quote-hero__actions,
.quote-hero__chips {
  display: flex;
  align-items: center;
  gap: var(--content-gap-2);
  flex-wrap: wrap;
}

.quote-hero__head {
  justify-content: space-between;
}

.quote-hero__eyebrow,
.quote-hero__label {
  margin: 0;
  color: #8dc5c8;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.quote-hero__chip {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: var(--content-radius-pill);
  color: #f4f9ff;
  background: var(--content-chip-gold-surface);
  border: 1px solid var(--content-border-3);
  font-size: 12px;
}

.quote-hero__chip-muted {
  background: var(--content-chip-muted-surface);
  border-color: var(--content-border-1);
  color: #cfe1f9;
}

.quote-hero__body {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(320px, 0.82fr);
  gap: var(--content-gap-4);
  align-items: start;
}

.quote-hero__theme {
  max-width: 62ch;
}

.quote-hero__theme h2,
.quote-hero__quote blockquote {
  margin: 12px 0 0;
  color: #f6fbff;
  font-family: var(--font-display);
}

.quote-hero__theme h2 {
  font-size: clamp(34px, 4.8vw, 54px);
  line-height: 1.08;
}

.quote-hero__theme p:last-child {
  margin: 14px 0 0;
  color: #a9c0e3;
  line-height: 1.7;
  font-size: 15px;
}

.quote-hero__quote {
  padding: 18px 20px;
  border-radius: var(--content-radius-2);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-inset);
  box-shadow: var(--content-shadow-1);
}

.quote-hero__quote blockquote {
  font-size: clamp(22px, 2.7vw, 32px);
  line-height: 1.34;
}

.quote-hero__author {
  margin: 14px 0 0;
  color: #b8cae7;
}

@media (max-width: 900px) {
  .quote-hero__body {
    grid-template-columns: 1fr;
  }
}
</style>
