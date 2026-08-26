import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'

vi.mock('@/api/auth', () => ({
  getMe: vi.fn(),
  logout: vi.fn(),
  refreshToken: vi.fn()
}))

import { authGuard } from '@/router'
import { useUserStore } from '@/stores/user'

function route(fullPath: string, meta: Record<string, unknown> = {}, name = 'target') {
  return { fullPath, meta, name } as unknown as RouteLocationNormalized
}

function applySession(permissions: string[] = [], mustChangePwd = false) {
  const store = useUserStore()
  store.applyLogin({
    accessToken: 'router-token',
    expiresIn: 900,
    mustChangePwd,
    user: {
      id: 'router-user',
      username: 'router-user',
      realName: '路由测试用户',
      userType: 'STAFF',
      collegeId: 'college-1',
      studentId: null,
      mustChangePwd,
      userManagementWritable: false,
      roleManagementWritable: false,
      roles: ['TEST_ROLE'],
      permissions
    }
  })
  return store
}

describe('router authorization guard', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('authRestoreBlocked', '1')
    setActivePinia(createPinia())
  })

  it('redirects a protected route to login when no session can be restored', async () => {
    const result = await authGuard(route('/students'))

    expect(result).toEqual({ name: 'login', query: { redirect: '/students' } })
  })

  it('redirects authenticated users without route permission to forbidden', async () => {
    applySession([])

    const result = await authGuard(route('/exchange/import', { perms: ['exchange:template', 'exchange:prevalidate'] }))

    expect(result).toEqual({ name: 'forbidden', replace: true })
  })

  it('allows a route when any declared permission is present', async () => {
    applySession(['exchange:prevalidate'])

    const result = await authGuard(route('/exchange/import', { perms: ['exchange:template', 'exchange:prevalidate'] }))

    expect(result).toBe(true)
  })

  it('keeps a forced-password-change user on the login flow', async () => {
    applySession(['student:view'], true)

    const result = await authGuard(route('/students', { perms: ['student:view'] }))

    expect(result).toEqual({ name: 'login', query: { redirect: '/students' } })
  })
})
