import { ElMessage } from 'element-plus'
import { createRouter, createWebHistory } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import UserLayout from '@/layouts/UserLayout.vue'
import { pinia } from '@/stores'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useUserAuthStore } from '@/stores/userAuth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/app/home',
    },
    {
      path: '/app/login',
      name: 'userLogin',
      component: () => import('@/views/user/UserLoginView.vue'),
      meta: { publicUser: true, title: '用户登录 / 注册' },
    },
    {
      path: '/app',
      component: UserLayout,
      redirect: '/app/home',
      children: [
        {
          path: 'home',
          name: 'userHome',
          component: () => import('@/views/user/HomeView.vue'),
          meta: {
            publicUser: true,
            title: '首页',
            description: '从这里开始上传语音、查看报告和支持内容',
            breadcrumb: ['用户端', '首页'],
            hidePageHeader: true,
          },
        },
        {
          path: 'content',
          name: 'userContent',
          component: () => import('@/views/user/ContentView.vue'),
          meta: {
            publicUser: true,
            title: '内容专栏',
            description: '看看今天适合读的语录、文章和书',
            breadcrumb: ['用户端', '内容专栏'],
            hidePageHeader: true,
          },
        },
        {
          path: 'content/articles/:id',
          name: 'userContentArticleDetail',
          component: () => import('@/views/user/ContentArticleDetailView.vue'),
          meta: {
            publicUser: true,
            title: '文章导读',
            description: '先看导读，再决定要不要继续阅读原文',
            breadcrumb: ['用户端', '内容专栏', '文章导读'],
            hidePageHeader: true,
          },
        },
        {
          path: 'content/books/:id',
          name: 'userContentBookDetail',
          component: () => import('@/views/user/ContentBookDetailView.vue'),
          meta: {
            publicUser: true,
            title: '书籍导读',
            description: '先看推荐理由，再决定要不要继续了解这本书',
            breadcrumb: ['用户端', '内容专栏', '书籍导读'],
            hidePageHeader: true,
          },
        },
        {
          path: 'about',
          name: 'userAbout',
          redirect: '/app/profile',
          meta: {
            requiresUserAuth: true,
            title: '关于',
            description: '已整合进个人中心',
            breadcrumb: ['用户端', '个人中心'],
          },
        },
        {
          path: 'upload',
          name: 'userUpload',
          component: () => import('@/views/user/UploadView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '语音上传',
            description: '上传或录制语音，开始这次分析',
            breadcrumb: ['用户端', '语音上传'],
            hidePageHeader: true,
          },
        },
        {
          path: 'tasks',
          name: 'userTasks',
          component: () => import('@/views/user/TasksView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '任务中心',
            description: '查看任务进度和当前状态',
            breadcrumb: ['用户端', '任务中心'],
          },
        },
        {
          path: 'tasks/:id',
          name: 'userTaskDetail',
          component: () => import('@/views/user/TaskView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '任务详情',
            description: '查看这次分析的进度、结果和建议',
            breadcrumb: ['用户端', '任务中心', '任务详情'],
          },
        },
        {
          path: 'tasks/:id/timeline',
          name: 'userTaskTimeline',
          component: () => import('@/views/user/TaskTimelineView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '任务时间线',
            description: '按时间查看任务处理进度',
            breadcrumb: ['用户端', '任务中心', '任务时间线'],
          },
        },
        {
          path: 'reports',
          name: 'userReports',
          component: () => import('@/views/user/ReportsView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '报告中心',
            description: '查看历史报告并按条件筛选',
            breadcrumb: ['用户端', '报告中心'],
            hidePageHeader: true,
          },
        },
        {
          path: 'reports/:id',
          name: 'userReportDetail',
          component: () => import('@/views/user/ReportView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '报告详情',
            description: '查看这次分析的结果和建议',
            breadcrumb: ['用户端', '报告中心', '报告详情'],
            hidePageHeader: true,
          },
        },
        {
          path: 'trends',
          name: 'userTrends',
          component: () => import('@/views/user/TrendsView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '趋势分析',
            description: '看看这段时间的情绪变化',
            breadcrumb: ['用户端', '趋势分析'],
          },
        },
        {
          path: 'psy-centers',
          name: 'userPsyCenters',
          component: () => import('@/views/user/PsyCentersView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '心理中心',
            description: '查找可联系的心理咨询与支持机构',
            breadcrumb: ['用户端', '心理中心'],
          },
        },
        {
          path: 'profile',
          name: 'userProfile',
          component: () => import('@/views/user/ProfileView.vue'),
          meta: {
            requiresUserAuth: true,
            title: '个人中心',
            description: '查看账号信息、报告概览和使用说明',
            breadcrumb: ['用户端', '个人中心'],
            hidePageHeader: true,
          },
        },
      ],
    },
    {
      path: '/admin/login',
      name: 'adminLogin',
      component: () => import('@/views/admin/AdminLoginView.vue'),
      meta: { publicAdmin: true, title: '管理员登录' },
    },
    {
      path: '/admin',
      component: AdminLayout,
      redirect: '/admin/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'adminDashboard',
          component: () => import('@/views/admin/AdminAnalyticsView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '管理看板',
            description: '上传、报告、预警与质量统计',
            breadcrumb: ['管理端', '看板'],
          },
        },
        {
          path: 'content/banners',
          name: 'adminBanners',
          component: () => import('@/views/admin/AdminBannersView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '轮播图管理',
            description: '轮播图内容管理（新增、查询、编辑、删除）',
            breadcrumb: ['管理端', '内容运营', '轮播图管理'],
          },
        },
        {
          path: 'content/quotes',
          name: 'adminQuotes',
          component: () => import('@/views/admin/AdminQuotesView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '语录管理',
            description: '语录内容管理（新增、查询、编辑、删除）',
            breadcrumb: ['管理端', '内容运营', '语录管理'],
          },
        },
        {
          path: 'content/articles',
          name: 'adminArticles',
          component: () => import('@/views/admin/AdminArticlesView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '文章管理',
            description: '文章内容管理（新增、查询、编辑、删除）',
            breadcrumb: ['管理端', '内容运营', '文章管理'],
          },
        },
        {
          path: 'content/books',
          name: 'adminBooks',
          component: () => import('@/views/admin/AdminBooksView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '书籍管理',
            description: '书籍内容管理（新增、查询、编辑、删除）',
            breadcrumb: ['管理端', '内容运营', '书籍管理'],
          },
        },
        {
          path: 'content/books-overview',
          name: 'adminBookLibraryOverview',
          component: () => import('@/views/admin/AdminBookLibraryOverviewView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '书籍总览',
            description: '书库巡检与封面链接核对',
            breadcrumb: ['管理端', '内容运营', '书籍总览'],
            hidePageHeader: true,
          },
        },
        {
          path: 'content/schedules',
          name: 'adminContentSchedules',
          component: () => import('@/views/admin/AdminContentSchedulesView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '每日排期',
            description: '按日期配置主题、语录、文章与书籍组合',
            breadcrumb: ['管理端', '内容运营', '每日排期'],
          },
        },
        {
          path: 'psy-centers',
          name: 'adminPsyCenters',
          component: () => import('@/views/admin/AdminPsyCentersView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '心理中心管理',
            description: '心理中心资源管理（按城市编码维护）',
            breadcrumb: ['管理端', '资源管理', '心理中心管理'],
          },
        },
        {
          path: 'warnings',
          name: 'adminWarnings',
          component: () => import('@/views/admin/AdminWarningsView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '预警处置台',
            description: '预警事件列表与处置流转',
            breadcrumb: ['管理端', '预警处置台'],
          },
        },
        {
          path: 'inspect/tasks/:id',
          name: 'adminTaskInspect',
          component: () => import('@/views/user/TaskView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '任务详情（管理查看）',
            description: '以管理视角查看关联任务详情',
            breadcrumb: ['管理端', '预警处置台', '关联任务'],
          },
        },
        {
          path: 'inspect/reports/:id',
          name: 'adminReportInspect',
          component: () => import('@/views/user/ReportView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '报告详情（管理查看）',
            description: '以管理视角查看关联报告详情',
            breadcrumb: ['管理端', '预警处置台', '关联报告'],
            hidePageHeader: true,
          },
        },
        {
          path: 'settings',
          name: 'adminSettings',
          component: () => import('@/views/admin/AdminSettingsView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '系统设置',
            description: '预警阈值、规则参数与模型信息',
            breadcrumb: ['管理端', '系统设置'],
          },
        },
        {
          path: 'models',
          name: 'adminModels',
          component: () => import('@/views/admin/AdminModelsView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '模型治理',
            description: '版本注册与模型切换管理',
            breadcrumb: ['管理端', '模型治理'],
          },
        },
        {
          path: 'rules',
          name: 'adminRules',
          component: () => import('@/views/admin/AdminRulesView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '预警规则',
            description: '阈值与触发规则管理',
            breadcrumb: ['管理端', '预警规则'],
          },
        },
        {
          path: 'system',
          name: 'adminSystem',
          component: () => import('@/views/admin/SystemView.vue'),
          meta: {
            requiresAdminAuth: true,
            title: '系统状态',
            description: '服务健康、延迟与运行指标',
            breadcrumb: ['管理端', '系统状态'],
          },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/app/home',
    },
  ],
})

router.beforeEach((to) => {
  const userAuthStore = useUserAuthStore(pinia)
  const adminAuthStore = useAdminAuthStore(pinia)

  if (to.meta.publicUser) {
    if (to.name === 'userLogin' && userAuthStore.isAuthenticated) {
      return '/app/home'
    }
    return true
  }

  if (to.meta.publicAdmin) {
    if (to.name === 'adminLogin' && adminAuthStore.isAuthenticated) {
      return '/admin/dashboard'
    }
    return true
  }

  if (to.meta.requiresUserAuth && !userAuthStore.isAuthenticated) {
    ElMessage.warning('请先登录用户账号。')
    return {
      path: '/app/login',
      query: { redirect: to.fullPath },
    }
  }

  if (to.meta.requiresAdminAuth && !adminAuthStore.isAuthenticated) {
    ElMessage.warning('请先登录管理员账号。')
    return {
      path: '/admin/login',
      query: { redirect: to.fullPath },
    }
  }

  return true
})

export default router
