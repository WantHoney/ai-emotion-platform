<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

defineProps<{
  title: string
  detail?: string
  traceId?: string
}>()

const emit = defineEmits<{
  retry: []
}>()

const expanded = ref(false)

const copyTraceId = async (traceId: string) => {
  await navigator.clipboard.writeText(traceId)
  ElMessage.success('Trace ID copied')
}
</script>

<template>
  <div class="state-card">
    <h3>{{ title }}</h3>

    <el-button link type="danger" @click="expanded = !expanded">
      {{ expanded ? 'Hide error detail' : 'Show error detail' }}
    </el-button>
    <el-alert v-if="expanded && detail" :title="detail" type="error" :closable="false" show-icon class="mt-8" />

    <div class="trace" v-if="traceId">
      <span>Trace ID: {{ traceId }}</span>
      <el-button text type="primary" @click="copyTraceId(traceId)">Copy</el-button>
    </div>

    <el-button type="danger" plain @click="emit('retry')">Retry</el-button>
  </div>
</template>

<style scoped>
.state-card {
  border: 1px solid rgba(248, 113, 113, 0.55);
  border-radius: var(--radius-lg);
  background: rgba(79, 23, 32, 0.34);
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

h3 {
  margin: 0;
  font-size: 18px;
}

.mt-8 {
  margin-top: 8px;
  width: 100%;
}

.trace {
  font-size: 13px;
  color: #fecaca;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>