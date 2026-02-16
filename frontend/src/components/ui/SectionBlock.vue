<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>

<template>
  <section class="section" v-motion :initial="{ opacity: 0, y: 28 }" :visibleOnce="{ opacity: 1, y: 0 }">
    <header class="section-header">
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
  border: 1px solid rgba(152, 173, 214, 0.2);
  background: linear-gradient(180deg, rgba(17, 25, 42, 0.84), rgba(10, 16, 30, 0.88));
  box-shadow: 0 22px 44px rgba(0, 0, 0, 0.26);
}

.section-header {
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0;
  color: #8ab8c1;
  letter-spacing: 0.11em;
  text-transform: uppercase;
  font-size: 12px;
}

.title {
  margin: 8px 0 0;
  color: #f8fafc;
  font-size: clamp(24px, 4.4vw, 34px);
  line-height: 1.2;
  font-family: var(--font-display);
}

.description {
  margin: 12px 0 0;
  color: #b8c7df;
  line-height: 1.68;
  font-size: 14px;
}

.section-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
