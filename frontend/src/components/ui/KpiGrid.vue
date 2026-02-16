<script setup lang="ts">
export interface KpiItem {
  label: string
  value: string | number
  helper?: string
}

defineProps<{
  items: KpiItem[]
}>()
</script>

<template>
  <div class="kpi-grid">
    <article v-for="item in items" :key="item.label" class="kpi-card" v-motion :initial="{ opacity: 0, y: 24 }" :visibleOnce="{ opacity: 1, y: 0 }">
      <p class="label">{{ item.label }}</p>
      <h3 class="value">{{ item.value }}</h3>
      <p v-if="item.helper" class="helper">{{ item.helper }}</p>
    </article>
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.kpi-card {
  border-radius: 14px;
  border: 1px solid rgba(170, 185, 216, 0.28);
  background: linear-gradient(180deg, rgba(20, 31, 52, 0.84), rgba(13, 21, 36, 0.9));
  padding: 14px;
}

.label {
  margin: 0;
  color: #9eb3d7;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.value {
  margin: 8px 0 0;
  color: #f8fafc;
  font-size: 30px;
  line-height: 1;
  font-family: var(--font-display);
}

.helper {
  margin: 10px 0 0;
  color: #bfd1ee;
  font-size: 12px;
}

@media (max-width: 960px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
