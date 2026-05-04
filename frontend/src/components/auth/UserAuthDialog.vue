<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { CloseBold } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { useUserAuthStore } from '@/stores/userAuth'
import { toErrorMessage } from '@/utils/errorMessage'

type AuthTab = 'login' | 'register'

const props = withDefaults(
  defineProps<{
    open: boolean
    initialTab?: AuthTab
    redirectPath?: string
  }>(),
  {
    initialTab: 'login',
    redirectPath: '/app/home',
  },
)

const emit = defineEmits<{
  close: []
  success: [path: string]
}>()

const userAuthStore = useUserAuthStore()

const loading = ref(false)
const activeTab = ref<AuthTab>(props.initialTab)

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

const waveformBars = [34, 58, 42, 76, 48, 67, 39, 56, 31, 45, 61, 37]
const snapshotTags = ['语音切片', '状态记录', '趋势回看']

const authCopy = computed(() =>
  activeTab.value === 'login'
    ? {
        title: '密码登录',
        subtitle: '继续查看报告、趋势和个人记录',
        submit: '登录',
        switchText: '还没有账号？',
        switchAction: '去注册',
        note: '登录后即可继续上传、查看报告、追踪趋势与浏览内容专栏。',
      }
    : {
        title: '注册账号',
        subtitle: '从今天开始建立你的情绪档案',
        submit: '注册并登录',
        switchText: '已经有账号了？',
        switchAction: '去登录',
        note: '注册完成后会自动登录，你可以从上传、报告与趋势追踪开始留下第一条记录。',
      },
)

const sanitizeRedirect = (value?: string) => {
  if (typeof value === 'string' && value.startsWith('/')) {
    return value
  }
  return '/app/home'
}

const releaseBodyScroll = () => {
  if (typeof document !== 'undefined') {
    document.body.style.overflow = ''
  }
}

const syncBodyScroll = (open: boolean) => {
  if (typeof document !== 'undefined') {
    document.body.style.overflow = open ? 'hidden' : ''
  }
}

const handleClose = () => {
  emit('close')
}

const handleKeydown = (event: KeyboardEvent) => {
  if (props.open && event.key === 'Escape') {
    emit('close')
  }
}

watch(
  () => props.initialTab,
  (value) => {
    activeTab.value = value
  },
  { immediate: true },
)

watch(
  () => props.open,
  (open) => {
    syncBodyScroll(open)
    if (typeof window === 'undefined') {
      return
    }
    if (open) {
      window.addEventListener('keydown', handleKeydown)
      return
    }
    window.removeEventListener('keydown', handleKeydown)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', handleKeydown)
  }
  releaseBodyScroll()
})

const switchTab = (tab: AuthTab) => {
  activeTab.value = tab
}

const handleUserLogin = async () => {
  loading.value = true
  try {
    await userAuthStore.login(loginForm.username, loginForm.password)
    ElMessage.success('用户登录成功')
    emit('success', sanitizeRedirect(props.redirectPath))
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
    emit('success', sanitizeRedirect(props.redirectPath))
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '注册失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <transition name="auth-dialog-fade">
      <div v-if="open" class="auth-dialog" @click.self="handleClose">
        <div class="auth-dialog__backdrop"></div>

        <section class="auth-dialog__panel" role="dialog" aria-modal="true" aria-label="用户登录与注册">
          <button class="auth-dialog__close" type="button" aria-label="关闭登录弹窗" @click="handleClose">
            <el-icon><CloseBold /></el-icon>
          </button>

          <aside class="auth-dialog__visual">
            <p class="auth-dialog__visual-title">进入档案库</p>

            <div class="auth-dialog__snapshot-card" aria-hidden="true">
              <div class="auth-dialog__snapshot-head">
                <span class="auth-dialog__snapshot-label">今日切片</span>
                <span class="auth-dialog__snapshot-pill">语音样本</span>
              </div>

              <div class="auth-dialog__waveform">
                <span
                  v-for="(height, index) in waveformBars"
                  :key="index"
                  class="auth-dialog__waveform-bar"
                  :style="{ height: `${height}px` }"
                ></span>
              </div>

              <div class="auth-dialog__snapshot-body">
                <p>把今天先留住，再慢慢整理成能回看的页面。</p>

                <div class="auth-dialog__snapshot-tags">
                  <span v-for="tag in snapshotTags" :key="tag">{{ tag }}</span>
                </div>
              </div>
            </div>

            <p class="auth-dialog__visual-caption">
              每一次进入，都是把今天重新接住的起点。你可以从上传、报告和趋势里，慢慢看清自己的变化。
            </p>

            <div class="auth-dialog__visual-tags">
              <span>上传 / 录音分析</span>
              <span>报告查看</span>
              <span>趋势追踪</span>
            </div>
          </aside>

          <main class="auth-dialog__form-panel">
            <div class="auth-dialog__tabs" role="tablist" aria-label="登录注册切换">
              <button
                type="button"
                class="auth-dialog__tab"
                :class="{ 'is-active': activeTab === 'login' }"
                @click="switchTab('login')"
              >
                密码登录
              </button>
              <button
                type="button"
                class="auth-dialog__tab"
                :class="{ 'is-active': activeTab === 'register' }"
                @click="switchTab('register')"
              >
                注册账号
              </button>
            </div>

            <div class="auth-dialog__head">
              <h2>{{ authCopy.title }}</h2>
              <p>{{ authCopy.subtitle }}</p>
            </div>

            <el-form
              v-if="activeTab === 'login'"
              label-position="top"
              class="auth-dialog__form"
              @submit.prevent="handleUserLogin"
            >
              <el-form-item label="账号">
                <el-input v-model="loginForm.username" placeholder="请输入账号" autocomplete="username" />
              </el-form-item>

              <el-form-item label="密码">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  autocomplete="current-password"
                  show-password
                />
              </el-form-item>

              <div class="auth-dialog__actions">
                <button class="auth-dialog__secondary" type="button" @click="switchTab('register')">注册</button>
                <el-button type="primary" native-type="submit" :loading="loading">{{ authCopy.submit }}</el-button>
              </div>
            </el-form>

            <el-form v-else label-position="top" class="auth-dialog__form" @submit.prevent="handleRegister">
              <el-form-item label="账号">
                <el-input v-model="registerForm.username" placeholder="请输入账号" autocomplete="username" />
              </el-form-item>

              <el-form-item label="昵称（可选）">
                <el-input v-model="registerForm.nickname" placeholder="请输入昵称" autocomplete="nickname" />
              </el-form-item>

              <el-form-item label="密码">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="至少 8 位，包含字母和数字"
                  autocomplete="new-password"
                  show-password
                />
              </el-form-item>

              <el-form-item label="确认密码">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="请再次输入密码"
                  autocomplete="new-password"
                  show-password
                />
              </el-form-item>

              <div class="auth-dialog__actions">
                <button class="auth-dialog__secondary" type="button" @click="switchTab('login')">返回登录</button>
                <el-button type="primary" native-type="submit" :loading="loading">{{ authCopy.submit }}</el-button>
              </div>
            </el-form>

            <div class="auth-dialog__footer">
              <p class="auth-dialog__note">{{ authCopy.note }}</p>
              <p class="auth-dialog__switch">
                {{ authCopy.switchText }}
                <button
                  type="button"
                  class="auth-dialog__switch-button"
                  @click="switchTab(activeTab === 'login' ? 'register' : 'login')"
                >
                  {{ authCopy.switchAction }}
                </button>
              </p>
            </div>
          </main>
        </section>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.auth-dialog {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
}

.auth-dialog__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(7, 12, 20, 0.46);
  backdrop-filter: blur(8px);
}

.auth-dialog__panel {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 280px 1fr;
  width: min(820px, calc(100vw - 32px));
  height: min(720px, calc(100vh - 32px));
  border-radius: 18px;
  overflow: hidden;
  background: #f8fbff;
  box-shadow:
    0 22px 60px rgba(4, 12, 24, 0.22),
    0 2px 10px rgba(4, 12, 24, 0.08);
}

.auth-dialog__close {
  position: absolute;
  top: 18px;
  right: 18px;
  z-index: 2;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: rgba(48, 63, 84, 0.82);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition:
    transform 160ms ease,
    color 160ms ease,
    background 160ms ease;
}

.auth-dialog__close:hover {
  transform: scale(1.04);
  color: rgba(15, 28, 46, 0.98);
  background: rgba(20, 38, 61, 0.06);
}

.auth-dialog__visual,
.auth-dialog__form-panel {
  min-width: 0;
  padding: 44px 36px 34px;
}

.auth-dialog__visual {
  position: relative;
  background:
    radial-gradient(circle at 18% 18%, rgba(118, 192, 255, 0.18), transparent 36%),
    linear-gradient(180deg, #f7fbff, #eef6ff);
  border-right: 1px solid rgba(30, 60, 100, 0.08);
}

.auth-dialog__visual::before,
.auth-dialog__visual::after {
  content: '';
  position: absolute;
  width: 96px;
  height: 96px;
  border-radius: 30px;
  background: radial-gradient(circle at 50% 50%, rgba(110, 188, 255, 0.22), transparent 72%);
  pointer-events: none;
}

.auth-dialog__visual::before {
  left: -26px;
  bottom: -24px;
}

.auth-dialog__visual::after {
  right: -24px;
  bottom: -30px;
  background: radial-gradient(circle at 50% 50%, rgba(255, 182, 153, 0.2), transparent 72%);
}

.auth-dialog__visual-title {
  margin: 0;
  color: #3b4f68;
  font-size: 14px;
  font-weight: 700;
  text-align: center;
}

.auth-dialog__snapshot-card {
  margin: 30px auto 20px;
  padding: 18px 18px 16px;
  border-radius: 20px;
  background: #ffffff;
  box-shadow:
    inset 0 0 0 1px rgba(30, 60, 100, 0.08),
    0 10px 24px rgba(66, 128, 192, 0.08);
}

.auth-dialog__snapshot-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.auth-dialog__snapshot-label {
  color: #425971;
  font-size: 13px;
  font-weight: 700;
}

.auth-dialog__snapshot-pill {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(101, 198, 246, 0.12);
  color: #4da8ea;
  font-size: 11px;
  font-weight: 700;
}

.auth-dialog__waveform {
  display: flex;
  align-items: end;
  justify-content: center;
  gap: 6px;
  height: 92px;
  margin-top: 18px;
  padding: 0 4px;
}

.auth-dialog__waveform-bar {
  width: 10px;
  border-radius: 999px;
  background: linear-gradient(180deg, #7fd5f6, #4da8ea);
  box-shadow: 0 6px 16px rgba(77, 168, 234, 0.18);
}

.auth-dialog__snapshot-body {
  margin-top: 18px;
}

.auth-dialog__snapshot-body p {
  margin: 0;
  color: #5f7288;
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}

.auth-dialog__snapshot-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
}

.auth-dialog__snapshot-tags span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(17, 89, 143, 0.08);
  color: #58718d;
  font-size: 11px;
  white-space: nowrap;
}

.auth-dialog__visual-caption {
  margin: 0;
  color: #5d6f85;
  font-size: 13px;
  line-height: 1.75;
  text-align: center;
}

.auth-dialog__visual-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 22px;
}

.auth-dialog__visual-tags span {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(110, 176, 235, 0.1);
  color: #58718d;
  font-size: 12px;
  white-space: nowrap;
}

.auth-dialog__form-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: #ffffff;
  overflow-y: auto;
}

.auth-dialog__tabs {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(30, 60, 100, 0.08);
}

.auth-dialog__tab {
  position: relative;
  border: none;
  background: transparent;
  color: #7b8da5;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: color 160ms ease;
}

.auth-dialog__tab:hover {
  color: #4d6580;
}

.auth-dialog__tab.is-active {
  color: #4da8ea;
}

.auth-dialog__tab.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -18px;
  height: 2px;
  border-radius: 999px;
  background: #65c6f6;
}

.auth-dialog__head {
  margin-top: 26px;
}

.auth-dialog__head h2 {
  margin: 0;
  color: #223448;
  font-size: 30px;
  line-height: 1.1;
  font-family: var(--font-display);
}

.auth-dialog__head p {
  margin: 10px 0 0;
  color: #6f8095;
  font-size: 14px;
  line-height: 1.7;
}

.auth-dialog__form {
  margin-top: 22px;
}

:deep(.auth-dialog__form .el-form-item) {
  margin-bottom: 14px;
}

:deep(.auth-dialog__form .el-form-item__label) {
  padding-bottom: 6px;
  color: #73849a;
  font-size: 12px;
  font-weight: 700;
}

:deep(.auth-dialog__form .el-input__wrapper) {
  min-height: 52px;
  border-radius: 12px;
  background: #fbfdff;
  box-shadow: inset 0 0 0 1px rgba(56, 85, 122, 0.12);
  transition: box-shadow 160ms ease;
}

:deep(.auth-dialog__form .el-input__wrapper.is-focus) {
  box-shadow:
    inset 0 0 0 1px rgba(94, 179, 236, 0.72),
    0 0 0 4px rgba(101, 198, 246, 0.12);
}

:deep(.auth-dialog__form .el-input__inner) {
  color: #223448;
  font-size: 14px;
}

:deep(.auth-dialog__form .el-input__inner::placeholder) {
  color: #99a8ba;
}

.auth-dialog__actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 12px;
  margin-top: 10px;
}

.auth-dialog__secondary {
  min-height: 50px;
  border-radius: 12px;
  border: 1px solid rgba(56, 85, 122, 0.12);
  background: #ffffff;
  color: #3f546d;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    transform 160ms ease;
}

.auth-dialog__secondary:hover {
  transform: translateY(-1px);
  border-color: rgba(94, 179, 236, 0.34);
}

:deep(.auth-dialog__actions .el-button--primary) {
  min-height: 50px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 700;
  --el-button-bg-color: #6ac6ef;
  --el-button-border-color: #6ac6ef;
  --el-button-hover-bg-color: #7bd0f4;
  --el-button-hover-border-color: #7bd0f4;
  --el-button-active-bg-color: #5bb6df;
  --el-button-active-border-color: #5bb6df;
  --el-button-disabled-bg-color: #b1dcec;
  --el-button-disabled-border-color: #b1dcec;
  box-shadow: none;
}

.auth-dialog__footer {
  margin-top: 18px;
  display: grid;
  gap: 10px;
}

.auth-dialog__note,
.auth-dialog__switch {
  margin: 0;
  color: #7c8da2;
  font-size: 12px;
  line-height: 1.7;
}

.auth-dialog__switch-button {
  margin-left: 6px;
  border: none;
  background: transparent;
  color: #4da8ea;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.auth-dialog-fade-enter-active,
.auth-dialog-fade-leave-active {
  transition: opacity 220ms ease;
}

.auth-dialog-fade-enter-active .auth-dialog__panel,
.auth-dialog-fade-leave-active .auth-dialog__panel {
  transition:
    transform 220ms ease,
    opacity 220ms ease;
}

.auth-dialog-fade-enter-from,
.auth-dialog-fade-leave-to {
  opacity: 0;
}

.auth-dialog-fade-enter-from .auth-dialog__panel,
.auth-dialog-fade-leave-to .auth-dialog__panel {
  transform: translateY(10px) scale(0.985);
  opacity: 0;
}

@media (max-width: 760px) {
  .auth-dialog {
    padding: 14px;
  }

  .auth-dialog__panel {
    grid-template-columns: 1fr;
    width: min(560px, calc(100vw - 20px));
    height: auto;
    max-height: calc(100vh - 20px);
    overflow-y: auto;
  }

  .auth-dialog__visual,
  .auth-dialog__form-panel {
    padding: 30px 22px 24px;
  }

  .auth-dialog__visual {
    border-right: none;
    border-bottom: 1px solid rgba(30, 60, 100, 0.08);
  }

  .auth-dialog__form-panel {
    overflow-y: visible;
  }

  .auth-dialog__head h2 {
    font-size: 26px;
  }
}
</style>
