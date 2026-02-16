import { ElMessage } from 'element-plus'
import { createRouter, createWebHistory } from 'vue-router'

import MainLayout from '@/layouts/MainLayout.vue'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: 'Login / Register' },
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
          meta: {
            public: true,
            title: 'Home',
            description: 'Immersive content portal for emotion analysis and support',
            breadcrumb: ['Workspace', 'Home'],
            hidePageHeader: true,
          },
        },
        {
          path: '/content',
          name: 'content',
          component: () => import('@/views/ContentView.vue'),
          meta: {
            public: true,
            title: 'Content Atlas',
            description: 'Curated articles, books and quotes',
            breadcrumb: ['Workspace', 'Content Atlas'],
            hidePageHeader: true,
          },
        },
        {
          path: '/upload',
          name: 'upload',
          component: () => import('@/views/UploadView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'USER',
            title: 'Upload Audio',
            description: 'Upload audio and create analysis task',
            breadcrumb: ['Workspace', 'Upload Audio'],
            hidePageHeader: true,
          },
        },
        {
          path: '/tasks',
          name: 'tasks',
          component: () => import('@/views/TasksView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'USER',
            title: 'Tasks',
            description: 'Monitor task status and processing pipeline',
            breadcrumb: ['Workspace', 'Tasks'],
          },
        },
        {
          path: '/tasks/:id',
          name: 'taskDetail',
          component: () => import('@/views/TaskView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'USER',
            title: 'Task Detail',
            description: 'Task result, risk score and suggestion detail',
            breadcrumb: ['Workspace', 'Tasks', 'Task Detail'],
          },
        },
        {
          path: '/reports',
          name: 'reports',
          component: () => import('@/views/ReportsView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'USER',
            title: 'Reports',
            description: 'Browse and filter emotional analysis reports',
            breadcrumb: ['Workspace', 'Reports'],
            hidePageHeader: true,
          },
        },
        {
          path: '/trends',
          name: 'trends',
          component: () => import('@/views/TrendsView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'USER',
            title: 'Trends',
            description: 'Timeline and score trend of personal reports',
            breadcrumb: ['Workspace', 'Trends'],
          },
        },
        {
          path: '/reports/:id',
          name: 'reportDetail',
          component: () => import('@/views/ReportView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'USER',
            title: 'Report Detail',
            description: 'Composed emotion, risk level and recommendations',
            breadcrumb: ['Workspace', 'Reports', 'Report Detail'],
            hidePageHeader: true,
          },
        },
        {
          path: '/psy-centers',
          name: 'psyCenters',
          component: () => import('@/views/PsyCentersView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'USER',
            title: 'Psy Centers',
            description: 'Find psychology and support centers',
            breadcrumb: ['Workspace', 'Psy Centers'],
          },
        },
        {
          path: '/profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'USER',
            title: 'Profile',
            description: 'Personal account and activity summary',
            breadcrumb: ['Workspace', 'Profile'],
            hidePageHeader: true,
          },
        },
        {
          path: '/system',
          name: 'system',
          component: () => import('@/views/SystemView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'ADMIN',
            title: 'System Status',
            description: 'Service health, latency and runtime metrics',
            breadcrumb: ['Admin', 'System Status'],
          },
        },
        {
          path: '/admin/models',
          name: 'adminModels',
          component: () => import('@/views/AdminModelsView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'ADMIN',
            title: 'Model Governance',
            description: 'Version registry and model switch management',
            breadcrumb: ['Admin', 'Model Governance'],
          },
        },
        {
          path: '/admin/rules',
          name: 'adminRules',
          component: () => import('@/views/AdminRulesView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'ADMIN',
            title: 'Warning Rules',
            description: 'Thresholds and trigger rule management',
            breadcrumb: ['Admin', 'Warning Rules'],
          },
        },
        {
          path: '/admin/warnings',
          name: 'adminWarnings',
          component: () => import('@/views/AdminWarningsView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'ADMIN',
            title: 'Warning Desk',
            description: 'Warning event triage and disposal',
            breadcrumb: ['Admin', 'Warning Desk'],
          },
        },
        {
          path: '/admin/content',
          name: 'adminContent',
          component: () => import('@/views/AdminContentView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'ADMIN',
            title: 'Content Operations',
            description: 'Manage banners, quotes, articles and books',
            breadcrumb: ['Admin', 'Content Operations'],
          },
        },
        {
          path: '/admin/analytics',
          name: 'adminAnalytics',
          component: () => import('@/views/AdminAnalyticsView.vue'),
          meta: {
            requiresAuth: true,
            requiresRole: 'ADMIN',
            title: 'Analytics',
            description: 'Daily active users, uploads, reports and warnings',
            breadcrumb: ['Admin', 'Analytics'],
          },
        },
        {
          path: '/about',
          name: 'about',
          component: () => import('@/views/AboutView.vue'),
          meta: {
            public: true,
            title: 'About',
            description: 'Project context and architecture',
            breadcrumb: ['Workspace', 'About'],
          },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore(pinia)
  const defaultPath = authStore.userRole === 'ADMIN' ? '/admin/analytics' : '/home'

  if (to.meta.public) {
    if (to.name === 'login' && authStore.isAuthenticated) {
      return defaultPath
    }
    return true
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    ElMessage.warning('Please login first.')
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  const requiredRole = to.meta.requiresRole
  if (requiredRole && authStore.userRole !== requiredRole) {
    ElMessage.error('Current account has no permission to access this page.')
    return defaultPath
  }

  return true
})

export default router
