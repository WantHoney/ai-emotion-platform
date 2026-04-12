<script setup lang="ts">
import { computed } from 'vue'

import { CONTENT_STATE_TABS } from '@/constants/contentMeta'

defineEmits<{
  change: [value: string]
}>()

const props = defineProps<{
  modelValue: string
}>()

const activeTab = computed(
  () => CONTENT_STATE_TABS.find((item) => item.value === props.modelValue) ?? CONTENT_STATE_TABS[0],
)
</script>

<template>
  <div class="state-tabs">
    <div class="state-tabs__rail">
      <button
        v-for="item in CONTENT_STATE_TABS"
        :key="item.value"
        type="button"
        class="state-tab"
        :class="{ active: item.value === modelValue }"
        @click="$emit('change', item.value)"
      >
        <span class="state-tab__label">{{ item.label }}</span>
      </button>
    </div>
    <p class="state-tabs__focus">
      <span>{{ activeTab.label }}</span>
      {{ activeTab.description }}
    </p>
  </div>
</template>

<style scoped>
.state-tabs {
  display: grid;
  gap: 10px;
}

.state-tabs__rail {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: var(--content-gap-2);
  width: 100%;
}

.state-tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  width: 100%;
  padding: 0 16px;
  border-radius: var(--content-radius-pill);
  border: 1px solid var(--content-border-1);
  background: var(--content-surface-1);
  color: #d8e7fb;
  cursor: pointer;
  transition:
    transform var(--content-motion-fast) var(--content-ease-standard),
    border-color var(--content-motion-fast) var(--content-ease-standard),
    box-shadow var(--content-motion-fast) var(--content-ease-standard),
    background var(--content-motion-fast) var(--content-ease-standard);
  text-align: left;
}

.state-tab:hover {
  transform: translateY(-2px);
  border-color: var(--content-border-3);
  box-shadow: var(--content-shadow-1);
}

.state-tab.active {
  border-color: var(--content-border-3);
  background: var(--content-surface-2);
  box-shadow: var(--content-shadow-2);
}

.state-tab__label {
  color: #f4fbff;
  font-size: 16px;
  font-weight: 700;
}

.state-tabs__focus {
  margin: 0;
  min-width: 0;
  color: #9fb4d6;
  line-height: 1.7;
  font-size: 14px;
  text-align: left;
}

.state-tabs__focus span {
  margin-right: 8px;
  color: #f4fbff;
  font-weight: 700;
}

@media (max-width: 980px) {
  .state-tabs__rail {
    display: flex;
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 2px;
  }
}
</style>
