<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(async () => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/app/home'
  const tab = route.query.tab === 'register' ? 'register' : 'login'

  await router.replace({
    path: '/app/home',
    query: {
      auth: '1',
      redirect,
      tab,
    },
  })
})
</script>

<template>
  <div class="user-login-redirect" aria-hidden="true"></div>
</template>

<style scoped>
.user-login-redirect {
  display: none;
}
</style>
