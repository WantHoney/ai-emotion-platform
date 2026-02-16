<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()
</script>

<template>
  <section class="page-shell">
    <header v-if="title" class="page-header">
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
</style>
