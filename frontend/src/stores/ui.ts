import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const pendingRequestCount = ref(0)

  const isGlobalLoading = computed(() => pendingRequestCount.value > 0)

  const startLoading = () => {
    pendingRequestCount.value += 1
  }

  const stopLoading = () => {
    pendingRequestCount.value = Math.max(0, pendingRequestCount.value - 1)
  }

  return {
    isGlobalLoading,
    startLoading,
    stopLoading,
  }
})
