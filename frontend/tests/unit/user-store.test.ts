import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SessionChangedError } from '@/stores/sessionEpoch'

const authMocks = vi.hoisted(() => ({
  getMe: vi.fn(),
  logout: vi.fn(),
  refreshToken: vi.fn()
}))

vi.mock('@/api/auth', () => ({
  getMe: authMocks.getMe,
  logout: authMocks.logout,
  refreshToken: authMocks.refreshToken
}))

import { useUserStore } from '@/stores/user'

function loginResult(overrides: Record<string, unknown> = {}) {
  const user = {
    id: 'user-1',
    username: 'teacher01',
    realName: '测试教师',
    userType: 'STAFF',
    collegeId: 'college-1',
    studentId: null,
    mustChangePwd: false,
    userManagementWritable: false,
    roleManagementWritable: false,
    roles: ['REVIEW_TEACHER'],
    permissions: ['video:score'],
    ...(overrides.user as object | undefined)
  }
  return {
    accessToken: 'memory-access-token',
    expiresIn: 900,
    mustChangePwd: user.mustChangePwd,
    user,
    ...overrides
  }
}

describe('user store session lifecycle', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('keeps the access token in memory and removes legacy token storage', () => {
    localStorage.setItem('accessToken', 'legacy-access')
    localStorage.setItem('refreshToken', 'legacy-refresh')
    localStorage.setItem('token', 'legacy-token')
    const store = useUserStore()

    store.applyLogin(loginResult())

    expect(store.token).toBe('memory-access-token')
    expect(store.hasPerm('video:score')).toBe(true)
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('username')).toBe('teacher01')
  })

  it('clears all in-memory identity state and blocks implicit restore', () => {
    const store = useUserStore()
    store.applyLogin(loginResult())

    store.clearSession()

    expect(store.token).toBe('')
    expect(store.perms).toEqual([])
    expect(store.roles).toEqual([])
    expect(store.currentUser).toBeNull()
    expect(localStorage.getItem('authRestoreBlocked')).toBe('1')
    expect(localStorage.getItem('username')).toBeNull()
  })

  it('rejects a refresh response that arrives after the session was cleared', async () => {
    let resolveRefresh!: (value: unknown) => void
    authMocks.refreshToken.mockReturnValue(new Promise((resolve) => { resolveRefresh = resolve }))
    const store = useUserStore()

    const pending = store.refreshSession()
    store.clearSession()
    resolveRefresh({ data: loginResult({ accessToken: 'late-access' }) })

    await expect(pending).rejects.toBeInstanceOf(SessionChangedError)
    expect(store.token).toBe('')
  })

  it('attempts cookie session restoration at most once after a failure', async () => {
    authMocks.refreshToken.mockRejectedValue(new Error('refresh unavailable'))
    const store = useUserStore()

    await expect(store.restoreSession()).resolves.toBe(false)
    await expect(store.restoreSession()).resolves.toBe(false)

    expect(authMocks.refreshToken).toHaveBeenCalledTimes(1)
  })

  it('restores a cookie session through the real store refresh path', async () => {
    authMocks.refreshToken.mockResolvedValue({ data: loginResult({ accessToken: 'restored-access' }) })
    const store = useUserStore()

    await expect(store.restoreSession()).resolves.toBe(true)

    expect(authMocks.refreshToken).toHaveBeenCalledTimes(1)
    expect(store.token).toBe('restored-access')
    expect(store.currentUser?.id).toBe('user-1')
    expect(store.username).toBe('teacher01')
    expect(store.roles).toEqual(['REVIEW_TEACHER'])
    expect(store.perms).toEqual(['video:score'])
  })

  it('clears local state and broadcasts invalidation when remote logout fails', async () => {
    authMocks.logout.mockRejectedValue(new Error('network unavailable'))
    const store = useUserStore()
    store.applyLogin(loginResult())

    await expect(store.logout()).rejects.toThrow('network unavailable')

    expect(store.token).toBe('')
    expect(localStorage.getItem('authSessionInvalidated')).toMatch(/^\d+:/)
  })
})
