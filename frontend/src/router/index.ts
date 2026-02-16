import { ElMessage } from 'element-plus'
import { createRouter, createWebHistory } from 'vue-router'

import MainLayout from '@/layouts/MainLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { pinia } from '@/stores'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录 / 注册' },
    },
    {
      path: '/',
      component: MainLayout,
      redirect: '/home',
      children: [
        {
          path: '/home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { public: true, title: '产品首页', description: '聚合 Banner、推荐内容与自助练习入口。', breadcrumb: ['工作台', '首页'] },
        },
        {
          path: '/upload',
          name: 'upload',
          component: () => import('@/views/UploadView.vue'),
          meta: { requiresAuth: true, title: '上传音频', description: '上传音频并一键创建情绪分析任务。', breadcrumb: ['工作台', '上传'] },
        },
        {
          path: '/tasks',
          name: 'tasks',
          component: () => import('@/views/TasksView.vue'),
          meta: { requiresAuth: true, title: '任务中心', description: '查看分析任务状态、重试和追踪链路。', breadcrumb: ['工作台', '任务'] },
        },
        {
          path: '/tasks/:id',
          name: 'taskDetail',
          component: () => import('@/views/TaskView.vue'),
          meta: { requiresAuth: true, title: '任务详情', description: '查看任务结果与运行指标。', breadcrumb: ['工作台', '任务', '详情'] },
        },
        {
          path: '/reports',
          name: 'reports',
          component: () => import('@/views/ReportsView.vue'),
          meta: { requiresAuth: true, title: '报告中心', description: '管理情绪报告并进行筛选导出。', breadcrumb: ['工作台', '报告'] },
        },
        {
          path: '/reports/:id',
          name: 'reportDetail',
          component: () => import('@/views/ReportView.vue'),
          meta: { requiresAuth: true, title: '报告详情', description: '查看报告指标并导出结果。', breadcrumb: ['工作台', '报告', '详情'] },
        },
        {
          path: '/psy-centers',
          name: 'psyCenters',
          component: () => import('@/views/PsyCentersView.vue'),
          meta: { requiresAuth: true, title: '心理中心', description: '按城市或附近位置检索心理咨询与服务资源。', breadcrumb: ['工作台', '心理中心'] },
        },
        {
          path: '/system',
          name: 'system',
          component: () => import('@/views/SystemView.vue'),
          meta: { requiresAuth: true, requiresRole: 'ADMIN', title: '系统状态', description: '观察服务健康、延迟和运行负载。', breadcrumb: ['工作台', '系统'] },
        },
        {
          path: '/about',
          name: 'about',
          component: () => import('@/views/AboutView.vue'),
          meta: { public: true, title: '关于系统', description: '了解平台定位与功能概览。', breadcrumb: ['工作台', '关于'] },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore(pinia)

  if (to.meta.public) {
    if (to.name === 'login' && authStore.isAuthenticated) {
      return '/home'
    }
    return true
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    ElMessage.warning('请先登录后再使用该功能。')
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  const requiredRole = to.meta.requiresRole
  if (requiredRole && authStore.userRole !== requiredRole) {
    ElMessage.error('当前账号无权限访问该页面，请使用运营账号登录。')
    return '/home'
  }

  return true
})

export default router
