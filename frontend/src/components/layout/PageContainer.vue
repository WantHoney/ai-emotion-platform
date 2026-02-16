<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
  variant?: 'user' | 'admin'
  hideHeader?: boolean
}>()
</script>

<template>
  <section class="page-shell" :class="variant ?? 'admin'">
    <header v-if="title && !hideHeader" class="page-header">
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-description">{{ description }}</p>
    </header>

    <div class="page-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.page-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-shell.user {
  max-width: min(1320px, calc(100vw - 34px));
  padding-top: 24px;
}

.page-shell.admin {
  max-width: var(--content-max-width);
}
</style>
