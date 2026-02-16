<script setup lang="ts">
import ErrorBoundary from '@/components/ErrorBoundary.vue'
</script>

<template>
  <el-config-provider>
    <el-backtop :right="20" :bottom="20" />
    <ErrorBoundary>
      <router-view />
    </ErrorBoundary>
  </el-config-provider>
</template>
