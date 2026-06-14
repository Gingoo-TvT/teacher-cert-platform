import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMe, refreshToken as requestRefreshToken, type CurrentUser, type LoginResult } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('accessToken') || localStorage.getItem('token') || '')
  const refreshToken = ref<string>(localStorage.getItem('refreshToken') || '')
  const perms = ref<string[]>([])
  const roles = ref<string[]>([])
  const username = ref<string>(localStorage.getItem('username') || '')
  const realName = ref<string>(localStorage.getItem('realName') || '')
  const mustChangePwd = ref<boolean>(localStorage.getItem('mustChangePwd') === 'true')
  const initialized = ref<boolean>(false)
  const currentUser = ref<CurrentUser | null>(null)

  function applyLogin(result: LoginResult) {
    token.value = result.accessToken
    refreshToken.value = result.refreshToken
    localStorage.setItem('accessToken', result.accessToken)
    localStorage.setItem('refreshToken', result.refreshToken)
    localStorage.removeItem('token')
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
    const res = await getMe()
    applyUser(res.data)
    return res.data
  }

  async function refreshSession() {
    if (!refreshToken.value) throw new Error('refresh token missing')
    const res = await requestRefreshToken(refreshToken.value)
    applyLogin(res.data)
    return res.data.accessToken
  }

  function clearSession() {
    token.value = ''
    refreshToken.value = ''
    perms.value = []
    roles.value = []
    username.value = ''
    realName.value = ''
    mustChangePwd.value = false
    initialized.value = false
    currentUser.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('realName')
    localStorage.removeItem('mustChangePwd')
  }

  function logout() {
    clearSession()
  }

  function hasPerm(code: string): boolean {
    return perms.value.includes(code)
  }

  function hasAnyPerm(codes: string[]): boolean {
    return codes.some((code) => hasPerm(code))
  }

  return {
    token,
    refreshToken,
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
    clearSession,
    logout,
    hasPerm,
    hasAnyPerm
  }
})
