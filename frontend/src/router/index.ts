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
