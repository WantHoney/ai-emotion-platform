<script setup lang="ts">
defineProps<{
  title?: string
  subtitle?: string
  interactive?: boolean
}>()
</script>

<template>
  <article class="lore-card" :class="{ interactive }">
    <header v-if="title || subtitle" class="head">
      <h3 v-if="title">{{ title }}</h3>
      <p v-if="subtitle">{{ subtitle }}</p>
    </header>
    <div class="body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="footer">
      <slot name="footer" />
    </footer>
  </article>
</template>

<style scoped>
.lore-card {
  border-radius: 16px;
  border: 1px solid var(--user-card-border);
  background: var(--user-card-bg);
  padding: 16px;
  transition: transform 0.26s ease, border-color 0.26s ease, box-shadow 0.26s ease;
}

.interactive {
  cursor: pointer;
}

.interactive:hover {
  transform: translateY(-4px);
  border-color: var(--user-border-accent-strong);
  box-shadow: var(--user-card-hover-shadow);
}

.head h3 {
  margin: 0;
  color: var(--user-text-primary);
  font-size: 17px;
}

.head p {
  margin: 8px 0 0;
  color: var(--user-text-secondary);
  line-height: 1.6;
  font-size: 13px;
}

.body {
  margin-top: 10px;
  color: var(--user-card-text);
}

.footer {
  margin-top: 14px;
}
</style>
