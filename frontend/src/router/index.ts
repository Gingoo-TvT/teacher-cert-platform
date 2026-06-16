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
