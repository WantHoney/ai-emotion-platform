<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
  headerless?: boolean
}>()
</script>

<template>
  <section class="section" v-motion :initial="{ opacity: 0, y: 28 }" :visibleOnce="{ opacity: 1, y: 0 }">
    <header v-if="!headerless" class="section-header">
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h2 class="title">{{ title }}</h2>
      <p v-if="description" class="description">{{ description }}</p>
    </header>
    <div class="section-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.section {
  padding: clamp(18px, 3.8vw, 32px);
  border-radius: 20px;
  border: 1px solid var(--user-section-border);
  background: var(--user-section-bg);
  box-shadow: var(--user-section-shadow);
}

.section-header {
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0;
  color: var(--user-eyebrow-color);
  letter-spacing: 0.11em;
  text-transform: uppercase;
  font-size: 12px;
}

.title {
  margin: 8px 0 0;
  color: var(--user-text-primary);
  font-size: clamp(24px, 4.4vw, 34px);
  line-height: 1.2;
  font-family: var(--font-display);
  overflow-wrap: anywhere;
  word-break: break-word;
  white-space: normal;
}

.description {
  margin: 12px 0 0;
  color: var(--user-text-secondary);
  line-height: 1.68;
  font-size: 14px;
}

.section-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
