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
        meta: { title: '字典管理' }
      },
      {
        path: 'system/regions',
        name: 'regionManage',
        component: () => import('@/views/system/RegionManageView.vue'),
        meta: { title: '行政区划' }
      },
      {
        path: 'system/subjects',
        name: 'subjectManage',
        component: () => import('@/views/system/SubjectManageView.vue'),
        meta: { title: '任教学科库' }
      },
      {
        path: 'system/organizations',
        name: 'organizationManage',
        component: () => import('@/views/system/OrganizationManageView.vue'),
        meta: { title: '组织与专业' }
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

// 登录守卫（Phase 2 接入菜单/按钮权限）
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.public) return true
  if (!userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
