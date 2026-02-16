<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'

const hasError = ref(false)
const errorText = ref('页面渲染异常，请刷新重试。')

onErrorCaptured((error) => {
  hasError.value = true
  errorText.value = error instanceof Error ? error.message : String(error)
  return false
})
</script>

<template>
  <el-result
    v-if="hasError"
    icon="error"
    title="页面异常"
    :sub-title="errorText"
  />
  <slot v-else />
</template>
