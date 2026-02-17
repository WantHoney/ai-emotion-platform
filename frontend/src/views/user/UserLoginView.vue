<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useUserAuthStore } from '@/stores/userAuth'
import { toErrorMessage } from '@/utils/errorMessage'

const userAuthStore = useUserAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const userAuthTab = ref<'login' | 'register'>('login')

const userLoginForm = reactive({
  username: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const resolveRedirect = () => {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.trim()) {
    return redirect
  }
  return '/app/home'
}

const handleUserLogin = async () => {
  loading.value = true
  try {
    await userAuthStore.login(userLoginForm.username, userLoginForm.password)
    ElMessage.success('用户登录成功')
    await router.push(resolveRedirect())
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '用户登录失败'))
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次密码输入不一致')
    return
  }

  loading.value = true
  try {
    await userAuthStore.register(registerForm.username, registerForm.password, registerForm.nickname)
    ElMessage.success('注册成功，已自动登录')
    await router.push('/app/home')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '注册失败'))
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
        <p class="eyebrow">情绪档案库</p>
        <h1>AI 语音情绪分析与心理状态预警</h1>
        <p class="summary">
          用户端面向自助评估与长期追踪，支持上传/录音分析、报告查看、趋势追踪与资源引导。
        </p>
        <ul class="story-list">
          <li>普通用户可注册并建立个人历史档案。</li>
          <li>语音分析、报告、趋势仅对用户端开放。</li>
          <li>管理员请使用独立入口登录管理控制台。</li>
        </ul>
      </aside>

      <el-card class="login-card">
        <template #header>
          <div class="login-card-header">
            <span>用户端账号入口</span>
            <el-button text type="primary" @click="router.push('/admin/login')">前往管理端登录</el-button>
          </div>
        </template>

        <el-tabs v-model="userAuthTab" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form label-position="top" class="mt-16" @submit.prevent="handleUserLogin">
              <el-form-item label="用户名">
                <el-input v-model="userLoginForm.username" placeholder="请输入用户名" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="userLoginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                />
              </el-form-item>
              <el-button type="primary" :loading="loading" @click="handleUserLogin">登录</el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form label-position="top" class="mt-16" @submit.prevent="handleRegister">
              <el-form-item label="用户名">
                <el-input v-model="registerForm.username" placeholder="请输入用户名" />
              </el-form-item>
              <el-form-item label="昵称（可选）">
                <el-input v-model="registerForm.nickname" placeholder="请输入昵称" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="至少 8 位，包含字母和数字"
                  show-password
                />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="请再次输入密码"
                  show-password
                />
              </el-form-item>
              <el-button type="primary" :loading="loading" @click="handleRegister">
                注册并登录
              </el-button>
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
