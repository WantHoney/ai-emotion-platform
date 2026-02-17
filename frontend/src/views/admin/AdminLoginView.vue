<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAdminAuthStore } from '@/stores/adminAuth'
import { toErrorMessage } from '@/utils/errorMessage'

const adminAuthStore = useAdminAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const loginForm = reactive({
  username: '',
  password: '',
})

const resolveRedirect = () => {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.trim()) {
    return redirect
  }
  return '/admin/dashboard'
}

const handleAdminLogin = async () => {
  loading.value = true
  try {
    await adminAuthStore.login(loginForm.username, loginForm.password)
    ElMessage.success('管理员登录成功')
    await router.push(resolveRedirect())
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '管理员登录失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-login-wrap">
    <section class="admin-login-shell">
      <el-card class="admin-login-card">
        <template #header>
          <div class="header-row">
            <div>
              <p class="eyebrow">Admin Console</p>
              <h2>管理端登录</h2>
            </div>
            <el-button text @click="router.push('/app/login')">前往用户端</el-button>
          </div>
        </template>

        <el-alert
          title="管理员账号仅支持系统预置，不开放注册。"
          type="warning"
          :closable="false"
          show-icon
          class="mb-16"
        />

        <el-form label-position="top" @submit.prevent="handleAdminLogin">
          <el-form-item label="管理员用户名">
            <el-input v-model="loginForm.username" placeholder="请输入管理员用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              show-password
            />
          </el-form-item>
          <el-button type="primary" :loading="loading" @click="handleAdminLogin">登录管理端</el-button>
        </el-form>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.admin-login-wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 20px;
  background:
    radial-gradient(circle at 18% 14%, rgba(62, 116, 219, 0.22), transparent 38%),
    radial-gradient(circle at 85% 10%, rgba(48, 205, 196, 0.18), transparent 36%),
    #0b1220;
}

.admin-login-shell {
  width: min(560px, 100%);
}

.admin-login-card {
  border-radius: 16px;
  border: 1px solid rgba(71, 92, 127, 0.5);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.96), rgba(11, 18, 32, 0.96));
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-row h2 {
  margin: 4px 0 0;
  color: #e2ebfa;
}

.eyebrow {
  margin: 0;
  color: #9ab3d6;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.mb-16 {
  margin-bottom: 16px;
}
</style>
