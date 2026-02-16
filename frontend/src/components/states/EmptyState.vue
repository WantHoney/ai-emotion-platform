<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'

defineProps<{
  title: string
  description: string
  actionText: string
}>()

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <div class="state-card state-center">
    <el-icon :size="34" class="state-icon"><Document /></el-icon>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <el-button type="primary" @click="emit('action')">{{ actionText }}</el-button>
  </div>
</template>

<style scoped>
.state-card {
  border: 1px dashed rgba(145, 169, 212, 0.48);
  border-radius: var(--radius-lg);
  background: rgba(15, 26, 44, 0.62);
  padding: 24px;
}

.state-center {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.state-icon {
  color: #8ab8c1;
}

h3 {
  margin: 0;
  font-size: 18px;
  color: #eff5ff;
}

p {
  margin: 0;
  color: #b4c9ec;
}
</style>