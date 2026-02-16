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
  border: 1px solid rgba(180, 194, 226, 0.24);
  background:
    linear-gradient(120deg, rgba(17, 29, 50, 0.82), rgba(11, 18, 32, 0.86)),
    radial-gradient(circle at 0% 0%, rgba(198, 170, 118, 0.1), transparent 40%);
  padding: 16px;
  transition: transform 0.26s ease, border-color 0.26s ease, box-shadow 0.26s ease;
}

.interactive {
  cursor: pointer;
}

.interactive:hover {
  transform: translateY(-4px);
  border-color: rgba(205, 176, 124, 0.76);
  box-shadow: 0 18px 30px rgba(0, 0, 0, 0.34);
}

.head h3 {
  margin: 0;
  color: #f8fafc;
  font-size: 17px;
}

.head p {
  margin: 8px 0 0;
  color: #b8c7df;
  line-height: 1.6;
  font-size: 13px;
}

.body {
  margin-top: 10px;
  color: #dce6f6;
}

.footer {
  margin-top: 14px;
}
</style>
