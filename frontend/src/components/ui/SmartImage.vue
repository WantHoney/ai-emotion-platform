<script setup lang="ts">
import { ref, watch } from 'vue'

import { fallbackImageFor, resolveImageUrl, type MediaImageKind } from '@/utils/contentMedia'

const props = withDefaults(
  defineProps<{
    src?: string | null
    alt: string
    kind: MediaImageKind
    fit?: 'cover' | 'contain'
    loading?: 'lazy' | 'eager'
  }>(),
  {
    src: '',
    fit: 'cover',
    loading: 'lazy',
  },
)

const currentSrc = ref(resolveImageUrl(props.src, props.kind))

watch(
  () => [props.src, props.kind],
  () => {
    currentSrc.value = resolveImageUrl(props.src, props.kind)
  },
)

const handleError = () => {
  const fallback = fallbackImageFor(props.kind)
  if (currentSrc.value !== fallback) {
    currentSrc.value = fallback
  }
}
</script>

<template>
  <img
    class="smart-image"
    :src="currentSrc"
    :alt="alt"
    :loading="loading"
    :style="{ objectFit: fit }"
    @error="handleError"
  />
</template>

<style scoped>
.smart-image {
  width: 100%;
  height: 100%;
  display: block;
  background:
    linear-gradient(180deg, rgba(19, 31, 52, 0.72), rgba(10, 18, 30, 0.86)),
    radial-gradient(circle at top left, rgba(120, 167, 210, 0.24), transparent 44%);
}
</style>
