<script setup lang="ts">
export interface KpiItem {
  label: string
  value: string | number
  helper?: string
}

defineProps<{
  items: KpiItem[]
}>()

const toText = (value: string | number) => String(value ?? '')
const isLongValue = (value: string | number) => toText(value).length >= 10
</script>

<template>
  <div class="kpi-grid">
    <article v-for="item in items" :key="item.label" class="kpi-card" v-motion :initial="{ opacity: 0, y: 24 }" :visibleOnce="{ opacity: 1, y: 0 }">
      <p class="label">{{ item.label }}</p>
      <h3 class="value" :class="{ 'value--long': isLongValue(item.value) }">{{ item.value }}</h3>
      <p v-if="item.helper" class="helper">{{ item.helper }}</p>
    </article>
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
}

.kpi-card {
  border-radius: 14px;
  border: 1px solid rgba(170, 185, 216, 0.28);
  background: linear-gradient(180deg, rgba(20, 31, 52, 0.84), rgba(13, 21, 36, 0.9));
  padding: 14px;
  min-width: 0;
  overflow: hidden;
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
  font-size: clamp(24px, 2.1vw, 36px);
  line-height: 1.15;
  font-family: var(--font-display);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.value--long {
  font-size: clamp(18px, 1.5vw, 28px);
  line-height: 1.2;
  white-space: normal;
  overflow-wrap: break-word;
  word-break: break-word;
}

.helper {
  margin: 10px 0 0;
  color: #bfd1ee;
  font-size: 12px;
  overflow-wrap: break-word;
  word-break: break-word;
}
</style>
