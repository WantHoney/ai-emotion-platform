<script setup lang="ts">
import SmartImage from './SmartImage.vue'
import type { MediaImageKind } from '@/utils/contentMedia'

defineEmits<{
  click: []
}>()

withDefaults(
  defineProps<{
    imageUrl?: string | null
    imageAlt: string
    imageKind: MediaImageKind
    title: string
    subtitle?: string
    description?: string
    interactive?: boolean
    imageFit?: 'cover' | 'contain'
  }>(),
  {
    imageUrl: '',
    subtitle: '',
    description: '',
    interactive: false,
    imageFit: 'cover',
  },
)
</script>

<template>
  <article class="media-card" :class="{ interactive }" @click="$emit('click')">
    <div class="cover-wrap">
      <SmartImage :src="imageUrl" :alt="imageAlt" :kind="imageKind" :fit="imageFit" />
    </div>
    <div class="content">
      <div class="meta" v-if="$slots.meta">
        <slot name="meta" />
      </div>
      <header class="head">
        <h3>{{ title }}</h3>
        <p v-if="subtitle">{{ subtitle }}</p>
      </header>
      <p v-if="description" class="description">{{ description }}</p>
      <div v-if="$slots.default" class="body">
        <slot />
      </div>
      <footer v-if="$slots.footer" class="footer">
        <slot name="footer" />
      </footer>
    </div>
  </article>
</template>

<style scoped>
.media-card {
  display: grid;
  grid-template-columns: minmax(116px, 144px) minmax(0, 1fr);
  gap: 16px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid var(--content-border-1);
  background: var(--content-card-bg-highlight);
  transition: transform 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease;
  min-height: 196px;
}

.media-card.interactive {
  cursor: pointer;
}

.media-card.interactive:hover {
  transform: translateY(-4px);
  border-color: var(--content-border-3);
  box-shadow: var(--content-shadow-2);
}

.cover-wrap {
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid var(--content-border-1);
  min-height: 164px;
  background: var(--content-cover-bg);
}

.content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.head h3 {
  margin: 0;
  color: var(--content-text-primary);
  font-size: 20px;
  line-height: 1.3;
}

.head p {
  margin: 8px 0 0;
  color: var(--content-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.description {
  margin: 0;
  color: var(--content-text-secondary);
  line-height: 1.68;
}

.body {
  color: var(--content-text-secondary);
}

.footer {
  margin-top: auto;
  color: var(--content-text-muted);
  font-size: 13px;
}

@media (max-width: 720px) {
  .media-card {
    grid-template-columns: 1fr;
  }

  .cover-wrap {
    min-height: 180px;
  }
}
</style>
