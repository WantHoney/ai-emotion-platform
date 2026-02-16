<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue'

import { toErrorMessage } from '@/utils/errorMessage'

const hasError = ref(false)
const errorText = ref('页面渲染异常，请刷新重试。')

onErrorCaptured((error) => {
  hasError.value = true
  errorText.value = toErrorMessage(error, '页面渲染异常，请刷新重试。')
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
