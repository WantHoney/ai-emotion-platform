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
  color: #2d8a4f;
  border-color: rgba(22, 163, 74, 0.42);
  background: rgba(22, 163, 74, 0.1);
}

.tone-medium {
  color: #a96a18;
  border-color: rgba(245, 158, 11, 0.44);
  background: rgba(245, 158, 11, 0.14);
}

.tone-high {
  color: #c44848;
  border-color: rgba(239, 68, 68, 0.5);
  background: rgba(239, 68, 68, 0.14);
}

.tone-neutral {
  color: var(--content-text-muted);
  border-color: rgba(148, 163, 184, 0.42);
  background: rgba(100, 116, 139, 0.12);
}
</style>
