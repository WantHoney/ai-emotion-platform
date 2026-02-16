<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '@/stores/auth'
import { toErrorMessage } from '@/utils/errorMessage'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const portalTab = ref<'user' | 'admin'>('user')
const userAuthTab = ref<'login' | 'register'>('login')

const userLoginForm = reactive({
  username: '',
  password: '',
})

const adminLoginForm = reactive({
  username: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const resolveRedirect = (role: 'USER' | 'ADMIN') => {
  const fallback = role === 'ADMIN' ? '/admin/analytics' : '/home'
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.trim()) {
    return redirect
  }
  return fallback
}

const goAfterAuth = async (role: 'USER' | 'ADMIN') => {
  await router.push(resolveRedirect(role))
}

const handleUserLogin = async () => {
  loading.value = true
  try {
    await authStore.login(userLoginForm.username, userLoginForm.password, 'USER')
    ElMessage.success('User login succeeded')
    await goAfterAuth('USER')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, 'User login failed'))
  } finally {
    loading.value = false
  }
}

const handleAdminLogin = async () => {
  loading.value = true
  try {
    await authStore.login(adminLoginForm.username, adminLoginForm.password, 'ADMIN')
    ElMessage.success('Admin login succeeded')
    await goAfterAuth('ADMIN')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, 'Admin login failed'))
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('Passwords are inconsistent')
    return
  }

  loading.value = true
  try {
    await authStore.register(registerForm.username, registerForm.password, registerForm.nickname)
    ElMessage.success('Registration succeeded and user is logged in')
    await goAfterAuth('USER')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, 'Registration failed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>
        <div class="login-card-header">
          <span>Account Access</span>
          <el-tag type="info">User Portal / Admin Console</el-tag>
        </div>
      </template>

      <el-tabs v-model="portalTab" stretch>
        <el-tab-pane label="User Portal" name="user">
          <el-tabs v-model="userAuthTab" stretch>
            <el-tab-pane label="Login" name="login">
              <el-form label-position="top" class="mt-16" @submit.prevent="handleUserLogin">
                <el-form-item label="Username">
                  <el-input v-model="userLoginForm.username" placeholder="Enter username" />
                </el-form-item>
                <el-form-item label="Password">
                  <el-input
                    v-model="userLoginForm.password"
                    type="password"
                    placeholder="Enter password"
                    show-password
                  />
                </el-form-item>
                <el-button type="primary" :loading="loading" @click="handleUserLogin">Login</el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="Register" name="register">
              <el-form label-position="top" class="mt-16" @submit.prevent="handleRegister">
                <el-form-item label="Username">
                  <el-input v-model="registerForm.username" placeholder="Enter username" />
                </el-form-item>
                <el-form-item label="Nickname (optional)">
                  <el-input v-model="registerForm.nickname" placeholder="Enter nickname" />
                </el-form-item>
                <el-form-item label="Password">
                  <el-input
                    v-model="registerForm.password"
                    type="password"
                    placeholder="At least 8 chars with letters and digits"
                    show-password
                  />
                </el-form-item>
                <el-form-item label="Confirm Password">
                  <el-input
                    v-model="registerForm.confirmPassword"
                    type="password"
                    placeholder="Enter password again"
                    show-password
                  />
                </el-form-item>
                <el-button type="primary" :loading="loading" @click="handleRegister">
                  Register and Login
                </el-button>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <el-tab-pane label="Admin Console" name="admin">
          <el-alert
            title="Admin accounts are seeded by backend config only. Registration is disabled."
            type="warning"
            :closable="false"
            show-icon
          />
          <el-form label-position="top" class="mt-16" @submit.prevent="handleAdminLogin">
            <el-form-item label="Admin Username">
              <el-input v-model="adminLoginForm.username" placeholder="Enter admin username" />
            </el-form-item>
            <el-form-item label="Password">
              <el-input
                v-model="adminLoginForm.password"
                type="password"
                placeholder="Enter password"
                show-password
              />
            </el-form-item>
            <el-button type="primary" :loading="loading" @click="handleAdminLogin">Admin Login</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #eff6ff 0%, #f8fafc 100%);
}

.login-card {
  width: min(520px, calc(100vw - 32px));
}

.login-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.mt-16 {
  margin-top: 16px;
}
</style>
