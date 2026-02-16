<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue'

import { toErrorMessage } from '@/utils/errorMessage'

const hasError = ref(false)
const errorText = ref('Page rendering failed. Please refresh and retry.')

onErrorCaptured((error) => {
  hasError.value = true
  errorText.value = toErrorMessage(error, 'Page rendering failed. Please refresh and retry.')
  return false
})
</script>

<template>
  <el-result v-if="hasError" icon="error" title="Page Error" :sub-title="errorText" />
  <slot v-else />
</template>