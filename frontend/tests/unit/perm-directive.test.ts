import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/auth', () => ({
  getMe: vi.fn(),
  logout: vi.fn(),
  refreshToken: vi.fn()
}))

import { permDirective } from '@/directives/perm'
import { useUserStore } from '@/stores/user'

function applyPermissions(permissions: string[]) {
  useUserStore().applyLogin({
    accessToken: 'directive-token',
    expiresIn: 900,
    mustChangePwd: false,
    user: {
      id: 'directive-user',
      username: 'directive-user',
      realName: '按钮权限用户',
      userType: 'STAFF',
      collegeId: null,
      studentId: null,
      mustChangePwd: false,
      userManagementWritable: false,
      roleManagementWritable: false,
      roles: [],
      permissions
    }
  })
}

function mountDirective(code: string) {
  const parent = document.createElement('div')
  const button = document.createElement('button')
  parent.appendChild(button)
  if (typeof permDirective === 'function') throw new Error('perm directive must use object hooks')
  const mounted = permDirective.mounted
  if (typeof mounted !== 'function') throw new Error('perm directive lacks mounted hook')
  mounted(button, { value: code, oldValue: undefined, arg: undefined, modifiers: {}, instance: null, dir: permDirective }, {} as never, null)
  return { parent, button }
}

describe('v-perm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('keeps an element when the permission is granted', () => {
    applyPermissions(['cert:generate'])

    const { parent, button } = mountDirective('cert:generate')

    expect(parent.contains(button)).toBe(true)
  })

  it('removes an element when the permission is missing', () => {
    applyPermissions([])

    const { parent, button } = mountDirective('cert:generate')

    expect(parent.contains(button)).toBe(false)
  })
})
