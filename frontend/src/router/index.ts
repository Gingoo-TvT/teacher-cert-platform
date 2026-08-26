import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { SessionChangedError } from '@/stores/sessionEpoch'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'system/dicts',
        name: 'dictManage',
        component: () => import('@/views/system/DictManageView.vue'),
        meta: { title: '字典管理', perms: ['dict:manage'] }
      },
      {
        path: 'system/regions',
        name: 'regionManage',
        component: () => import('@/views/system/RegionManageView.vue'),
        meta: { title: '行政区划', perms: ['region:manage'] }
      },
      {
        path: 'system/subjects',
        name: 'subjectManage',
        component: () => import('@/views/system/SubjectManageView.vue'),
        meta: { title: '任教学科库', perms: ['subject:manage'] }
      },
      {
        path: 'system/organizations',
        name: 'organizationManage',
        component: () => import('@/views/system/OrganizationManageView.vue'),
        meta: { title: '组织与专业', perms: ['college:manage', 'major:manage'] }
      },
      {
        path: 'system/security',
        name: 'securityManage',
        component: () => import('@/views/system/SecurityManageView.vue'),
        meta: { title: '账号权限', perms: ['system:user:manage', 'system:role:manage', 'system:perm:manage'] }
      },
      {
        path: 'system/audit',
        name: 'systemAudit',
        component: () => import('@/views/system/SystemAuditView.vue'),
        meta: { title: '参数审计备份', perms: ['system:param:manage', 'audit:view', 'system:backup'] }
      },
      {
        path: 'students',
        name: 'studentManage',
        component: () => import('@/views/student/StudentManageView.vue'),
        meta: { title: '学生基本信息', perms: ['student:view'] }
      },
      {
        path: 'student/self',
        name: 'studentSelf',
        component: () => import('@/views/student/StudentSelfView.vue'),
        meta: { title: '本人基本信息', perms: ['student:confirm'] }
      },
      {
        path: 'training',
        name: 'trainingManage',
        component: () => import('@/views/training/TrainingManageView.vue'),
        meta: { title: '专业培养信息', perms: ['student:view', 'training:confirm'] }
      },
      {
        path: 'materials',
        name: 'materialManage',
        component: () => import('@/views/material/MaterialManageView.vue'),
        meta: { title: '过程性材料', perms: ['material:upload', 'material:firstReview', 'material:secondReview', 'material:batchDownload'] }
      },
      {
        path: 'exemptions',
        name: 'exemptionManage',
        component: () => import('@/views/exemption/ExemptionManageView.vue'),
        meta: { title: '免考管理', perms: ['exemption:apply', 'exemption:firstReview', 'exemption:secondReview'] }
      },
      {
        path: 'videos',
        name: 'videoReview',
        component: () => import('@/views/video/VideoReviewView.vue'),
        meta: { title: '视频评审', perms: ['video:upload', 'video:score', 'video:assign', 'video:arbitrate', 'video:confirm', 'video:play'] }
      },
      {
        path: 'tests',
        name: 'testResultManage',
        component: () => import('@/views/test-result/TestResultManageView.vue'),
        meta: { title: '测试结果', perms: ['student:view', 'test:import', 'test:confirm'] }
      },
      {
        path: 'certificates',
        name: 'certificateManage',
        component: () => import('@/views/certificate/CertificateManageView.vue'),
        meta: { title: '证书管理', perms: ['cert:view', 'cert:generate', 'cert:correct', 'cert:void', 'cert:reissue'] }
      },
      {
        path: 'certificate-issue',
        name: 'certificateIssue',
        component: () => import('@/views/certificate/CertificateIssueView.vue'),
        meta: { title: '证书签发', perms: ['cert:issue'] }
      },
      {
        path: 'exchange/import',
        name: 'exchangeImport',
        component: () => import('@/views/exchange/ExchangeImportView.vue'),
        meta: { title: '导入预校验', perms: ['exchange:template', 'exchange:prevalidate', 'exchange:import'] }
      },
      {
        path: 'exchange/export',
        name: 'exchangeExport',
        component: () => import('@/views/exchange/ExchangeExportView.vue'),
        meta: { title: '导出中心', perms: ['exchange:export:standard', 'exchange:export:full'] }
      },
      {
        path: 'stats',
        name: 'statsReport',
        component: () => import('@/views/stats/StatsReportView.vue'),
        meta: { title: '统计报表', perms: ['stats:view'] }
      },
      {
        path: 'notice',
        name: 'noticeCenter',
        component: () => import('@/views/notice/NoticeCenterView.vue'),
        meta: { title: '通知中心', perms: ['notice:view'] }
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('@/views/error/Forbidden.vue'),
        meta: { title: '无权访问' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { public: true, title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export async function authGuard(to: RouteLocationNormalized) {
  const userStore = useUserStore()
  if (to.meta.public) {
    if (to.name === 'login' && !userStore.token) {
      await userStore.restoreSession()
    }
    if (to.name === 'login' && userStore.token) {
      if (userStore.mustChangePwd) {
        userStore.clearSession()
        return true
      }
      return { name: 'dashboard' }
    }
    return true
  }
  if (!userStore.token) {
    const restored = await userStore.restoreSession()
    if (!restored) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  if (userStore.mustChangePwd) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!userStore.initialized) {
    try {
      await userStore.loadMe()
      if (userStore.mustChangePwd) {
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    } catch (error) {
      if (error instanceof SessionChangedError) {
        return userStore.token ? true : { name: 'login', query: { redirect: to.fullPath } }
      }
      userStore.clearSession()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  const perms = to.meta.perms as string[] | undefined
  if (perms?.length && !userStore.hasAnyPerm(perms)) {
    return { name: 'forbidden', replace: true }
  }
  return true
}

router.beforeEach(authGuard)

router.afterEach((to) => {
  const title = String(to.meta.title || '平台')
  document.title = `${title} · 师范生考核与证书管理平台`
})

export default router
