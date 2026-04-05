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
  border: 1px solid rgba(171, 192, 228, 0.22);
  background:
    linear-gradient(135deg, rgba(17, 28, 49, 0.9), rgba(10, 16, 30, 0.92)),
    radial-gradient(circle at top left, rgba(194, 224, 174, 0.12), transparent 42%);
  transition: transform 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease;
  min-height: 196px;
}

.media-card.interactive {
  cursor: pointer;
}

.media-card.interactive:hover {
  transform: translateY(-4px);
  border-color: rgba(186, 212, 157, 0.6);
  box-shadow: 0 18px 32px rgba(0, 0, 0, 0.32);
}

.cover-wrap {
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid rgba(166, 186, 220, 0.18);
  min-height: 164px;
  background: rgba(10, 16, 28, 0.5);
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
  color: #f7fbff;
  font-size: 20px;
  line-height: 1.3;
}

.head p {
  margin: 8px 0 0;
  color: #a9c0e5;
  font-size: 13px;
  line-height: 1.6;
}

.description {
  margin: 0;
  color: #dbe7f8;
  line-height: 1.68;
}

.body {
  color: #dbe7f8;
}

.footer {
  margin-top: auto;
  color: #9cb5da;
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
