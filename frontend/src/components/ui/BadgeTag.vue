<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    tone?: 'low' | 'medium' | 'high' | 'neutral'
    text: string
  }>(),
  {
    tone: 'neutral',
  },
)
</script>

<template>
  <span class="badge" :class="`tone-${props.tone}`">{{ text }}</span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.05em;
  border: 1px solid transparent;
}

.tone-low {
  color: #86efac;
  border-color: rgba(22, 163, 74, 0.52);
  background: rgba(22, 163, 74, 0.12);
}

.tone-medium {
  color: #fde68a;
  border-color: rgba(245, 158, 11, 0.55);
  background: rgba(245, 158, 11, 0.16);
}

.tone-high {
  color: #fca5a5;
  border-color: rgba(239, 68, 68, 0.62);
  background: rgba(239, 68, 68, 0.16);
}

.tone-neutral {
  color: #c3d3ee;
  border-color: rgba(148, 163, 184, 0.48);
  background: rgba(100, 116, 139, 0.15);
}
</style>
