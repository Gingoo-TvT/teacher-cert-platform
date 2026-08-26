import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  getMe,
  logout as requestLogout,
  refreshToken as requestRefreshToken,
  type CurrentUser,
  type LoginResult
} from '@/api/auth'
import { executeLogout } from '@/stores/logoutFlow'
import { SessionEpochGuard } from '@/stores/sessionEpoch'

const RESTORE_BLOCK_KEY = 'authRestoreBlocked'
const SESSION_EVENT_KEY = 'authSessionInvalidated'

export const useUserStore = defineStore('user', () => {
  const sessionEpoch = new SessionEpochGuard()
  const token = ref<string>('')
  const sessionRestoreAttempted = ref<boolean>(localStorage.getItem(RESTORE_BLOCK_KEY) === '1')
  const perms = ref<string[]>([])
  const roles = ref<string[]>([])
  const username = ref<string>(localStorage.getItem('username') || '')
  const realName = ref<string>(localStorage.getItem('realName') || '')
  const mustChangePwd = ref<boolean>(localStorage.getItem('mustChangePwd') === 'true')
  const initialized = ref<boolean>(false)
  const currentUser = ref<CurrentUser | null>(null)
  clearLegacyTokenStorage()
  window.addEventListener('storage', (event) => {
    if (event.key === SESSION_EVENT_KEY) {
      resetSessionState()
      window.location.replace('/login')
    }
  })

  function applyLogin(result: LoginResult) {
    sessionEpoch.invalidate()
    applyRefreshedSession(result)
  }

  function applyRefreshedSession(result: LoginResult) {
    token.value = result.accessToken
    sessionRestoreAttempted.value = true
    localStorage.removeItem(RESTORE_BLOCK_KEY)
    clearLegacyTokenStorage()
    applyUser(result.user)
  }

  function applyUser(user: CurrentUser) {
    currentUser.value = user
    username.value = user.username
    realName.value = user.realName
    mustChangePwd.value = Boolean(user.mustChangePwd)
    roles.value = [...user.roles]
    perms.value = [...user.permissions]
    initialized.value = true
    localStorage.setItem('username', user.username)
    localStorage.setItem('realName', user.realName)
    localStorage.setItem('mustChangePwd', String(Boolean(user.mustChangePwd)))
  }

  async function loadMe() {
    if (!token.value) {
      initialized.value = true
      return null
    }
    const expectedEpoch = sessionEpoch.capture()
    const res = await getMe()
    sessionEpoch.assertCurrent(expectedEpoch)
    applyUser(res.data)
    return res.data
  }

  async function refreshSession() {
    const expectedEpoch = sessionEpoch.capture()
    const res = await requestRefreshToken()
    sessionEpoch.assertCurrent(expectedEpoch)
    applyRefreshedSession(res.data)
    return res.data.accessToken
  }

  async function restoreSession(): Promise<boolean> {
    if (token.value) return true
    if (sessionRestoreAttempted.value) return false
    sessionRestoreAttempted.value = true
    try {
      await refreshSession()
      return true
    } catch {
      clearSession()
      return false
    }
  }

  function clearSession() {
    resetSessionState()
  }

  function resetSessionState() {
    sessionEpoch.invalidate()
    token.value = ''
    sessionRestoreAttempted.value = true
    perms.value = []
    roles.value = []
    username.value = ''
    realName.value = ''
    mustChangePwd.value = false
    initialized.value = false
    currentUser.value = null
    localStorage.setItem(RESTORE_BLOCK_KEY, '1')
    clearLegacyTokenStorage()
    localStorage.removeItem('username')
    localStorage.removeItem('realName')
    localStorage.removeItem('mustChangePwd')
  }

  async function logout() {
    try {
      await executeLogout(requestLogout, clearSession)
    } finally {
      broadcastSessionInvalidation()
    }
  }

  function clearSessionEverywhere() {
    clearSession()
    broadcastSessionInvalidation()
  }

  function broadcastSessionInvalidation() {
    localStorage.setItem(SESSION_EVENT_KEY, `${Date.now()}:${Math.random()}`)
  }

  function clearLegacyTokenStorage() {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('token')
  }

  const permSet = computed(() => new Set(perms.value))

  function hasPerm(code: string): boolean {
    return permSet.value.has(code)
  }

  function hasAnyPerm(codes: string[]): boolean {
    return codes.some((code) => hasPerm(code))
  }

  return {
    token,
    perms,
    roles,
    username,
    realName,
    mustChangePwd,
    initialized,
    currentUser,
    applyLogin,
    applyUser,
    loadMe,
    refreshSession,
    restoreSession,
    clearSession,
    clearSessionEverywhere,
    logout,
    hasPerm,
    hasAnyPerm
  }
})
