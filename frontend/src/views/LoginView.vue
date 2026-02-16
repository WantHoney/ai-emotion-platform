<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import type { AuthRole } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { toErrorMessage } from '@/utils/errorMessage'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const activeTab = ref<'login' | 'register'>('login')
const loginRole = ref<AuthRole>('USER')

const loginForm = reactive({
  username: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const goAfterAuth = async () => {
  const redirect = String(route.query.redirect ?? '/home')
  await router.push(redirect)
}

const handleLogin = async () => {
  loading.value = true
  try {
    await authStore.login(loginForm.username, loginForm.password, loginRole.value)
    ElMessage.success(loginRole.value === 'ADMIN' ? '运营端登录成功' : '用户登录成功')
    await goAfterAuth()
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '登录失败'))
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
    await authStore.register(registerForm.username, registerForm.password, registerForm.nickname)
    ElMessage.success('注册成功，已自动登录')
    await goAfterAuth()
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '注册失败'))
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
          <span>登录 / 注册</span>
          <el-tag type="info">用户端与运营端分离</el-tag>
        </div>
      </template>

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-alert
            title="运营端请切换到“运营端登录”，默认账号 operator / operator123。"
            type="info"
            :closable="false"
            show-icon
          />
          <el-form label-position="top" class="mt-16" @submit.prevent="handleLogin">
            <el-form-item label="登录身份">
              <el-radio-group v-model="loginRole">
                <el-radio-button label="USER">用户端</el-radio-button>
                <el-radio-button label="ADMIN">运营端</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" show-password />
            </el-form-item>
            <el-button type="primary" :loading="loading" @click="handleLogin">立即登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form label-position="top" @submit.prevent="handleRegister">
            <el-form-item label="用户名">
              <el-input v-model="registerForm.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="昵称（选填）">
              <el-input v-model="registerForm.nickname" placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" type="password" placeholder="请输入密码" show-password />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" show-password />
            </el-form-item>
            <el-button type="primary" :loading="loading" @click="handleRegister">注册并登录</el-button>
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
  width: 460px;
}

.login-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mt-16 {
  margin-top: 16px;
}
</style>
