import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
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
        meta: { title: '测试结果', perms: ['student:view', 'test:edit', 'test:import', 'test:confirm'] }
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
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    if (to.name === 'login' && userStore.token) return { name: 'dashboard' }
    return true
  }
  if (!userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!userStore.initialized) {
    try {
      await userStore.loadMe()
    } catch {
      userStore.logout()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  const perms = to.meta.perms as string[] | undefined
  if (perms?.length && !userStore.hasAnyPerm(perms)) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
