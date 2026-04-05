<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import ErrorBoundary from '@/components/ErrorBoundary.vue'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <el-backtop :right="20" :bottom="20" />
    <ErrorBoundary>
      <router-view />
    </ErrorBoundary>
  </el-config-provider>
</template>
