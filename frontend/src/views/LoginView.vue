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
    <div class="login-bg"></div>
    <section class="login-shell">
      <aside class="login-story">
        <p class="eyebrow">Emotion Atlas</p>
        <h1>AI Voice Emotion Analysis and Mental Risk Warning</h1>
        <p class="summary">
          User portal focuses on self-service assessment and longitudinal tracking.
          Admin console is for governance, warning handling, and content operations.
        </p>
        <ul class="story-list">
          <li>Users can register, upload or record voice, and view personal reports.</li>
          <li>Admin accounts are seeded by system configuration only.</li>
          <li>Roles are strictly isolated at route and API levels.</li>
        </ul>
      </aside>

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
    </section>
  </div>
</template>

<style scoped>
.login-wrap {
  position: relative;
  min-height: 100vh;
  padding: 24px;
  display: grid;
  place-items: center;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 14%, rgba(194, 163, 109, 0.26), transparent 36%),
    radial-gradient(circle at 80% 10%, rgba(76, 168, 188, 0.22), transparent 34%),
    radial-gradient(circle at 56% 90%, rgba(95, 117, 169, 0.2), transparent 38%),
    #070c18;
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(1120px, 100%);
  display: grid;
  gap: 20px;
  grid-template-columns: 1.1fr 0.9fr;
  align-items: stretch;
}

.login-story {
  border: 1px solid rgba(139, 161, 204, 0.28);
  border-radius: 22px;
  background: linear-gradient(150deg, rgba(12, 20, 35, 0.92), rgba(9, 15, 28, 0.88));
  padding: clamp(22px, 5vw, 42px);
  box-shadow: 0 26px 48px rgba(1, 7, 21, 0.42);
}

.eyebrow {
  margin: 0;
  color: #9cb7dd;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-size: 12px;
}

.login-story h1 {
  margin: 12px 0 0;
  color: #f7fbff;
  line-height: 1.12;
  font-size: clamp(30px, 4.2vw, 50px);
  font-family: var(--font-display);
}

.summary {
  margin: 14px 0 0;
  color: #bfd0ec;
  line-height: 1.8;
  font-size: 14px;
  max-width: 560px;
}

.story-list {
  margin: 18px 0 0;
  padding-left: 20px;
  color: #dce8fa;
  line-height: 1.8;
  font-size: 14px;
}

.login-card {
  width: 100%;
  border-radius: 22px;
  border: 1px solid rgba(145, 170, 211, 0.28);
  background: linear-gradient(180deg, rgba(14, 23, 39, 0.94), rgba(10, 17, 31, 0.96));
  box-shadow: 0 24px 44px rgba(2, 8, 22, 0.46);
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

@media (max-width: 980px) {
  .login-wrap {
    padding: 14px;
  }

  .login-shell {
    grid-template-columns: 1fr;
  }

  .login-story {
    padding: 22px;
  }
}
</style>
