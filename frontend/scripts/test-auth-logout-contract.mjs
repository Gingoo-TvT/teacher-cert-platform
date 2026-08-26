import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const frontendRoot = fileURLToPath(new URL('../', import.meta.url))
const readSource = (path) => readFileSync(new URL(path, `file:///${frontendRoot.replaceAll('\\', '/')}/`), 'utf8')

const userStore = readSource('src/stores/user.ts')
const request = readSource('src/api/request.ts')
const authApi = readSource('src/api/auth.ts')
const router = readSource('src/router/index.ts')
const layout = readSource('src/layouts/MainLayout.vue')
const { executeLogout } = await import(new URL('../src/stores/logoutFlow.js', import.meta.url))
const { SessionChangedError, SessionEpochGuard } = await import(
  new URL('../src/stores/sessionEpoch.js', import.meta.url)
)

assert.match(userStore, /logout as requestLogout/)
assert.match(userStore, /import \{ executeLogout \} from '@\/stores\/logoutFlow'/)
assert.match(userStore, /const sessionEpoch = new SessionEpochGuard\(\)/)
assert.match(userStore, /const token = ref<string>\(''\)/)
assert.doesNotMatch(userStore, /const refreshToken = ref/)
assert.doesNotMatch(userStore, /localStorage\.(?:getItem|setItem)\(['"](?:accessToken|refreshToken|token)['"]/)
assert.match(userStore, /function restoreSession\(\): Promise<boolean>/)
assert.match(userStore, /window\.addEventListener\('storage'/)
assert.match(
  userStore,
  /event\.key === SESSION_EVENT_KEY[\s\S]*?resetSessionState\(\)[\s\S]*?window\.location\.replace\('\/login'\)/
)
assert.doesNotMatch(userStore, /window\.location\.pathname\s*!==\s*['"]\/login['"]/)
assert.match(userStore, /async function logout[\s\S]*?finally[\s\S]*?broadcastSessionInvalidation\(\)/)
assert.doesNotMatch(authApi, /refreshToken:\s*string/)
assert.match(authApi, /function refreshToken\(\)/)
assert.match(authApi, /skipAuthHeader:\s*true/)
const changePasswordApi = authApi.match(
  /export function changePassword[\s\S]*?(?=\n\})\n\}/
)?.[0]
assert.ok(changePasswordApi)
assert.doesNotMatch(changePasswordApi, /skipAuthRefresh/)
assert.match(request, /withCredentials:\s*true/)
assert.match(request, /const token = useUserStore\(\)\.token/)
assert.doesNotMatch(request, /localStorage/)
assert.match(userStore, /function applyLogin[\s\S]*?sessionEpoch\.invalidate\(\)[\s\S]*?applyRefreshedSession/s)
const refreshedSession = userStore.match(
  /function applyRefreshedSession[\s\S]*?(?=\n  function applyUser)/
)?.[0]
assert.ok(refreshedSession)
assert.doesNotMatch(refreshedSession, /invalidate/)
assert.match(
  userStore,
  /const expectedEpoch = sessionEpoch\.capture\(\)[\s\S]*?requestRefreshToken[\s\S]*?sessionEpoch\.assertCurrent\(expectedEpoch\)[\s\S]*?applyRefreshedSession/s
)
assert.match(userStore, /function clearSession\(\)\s*\{\s*resetSessionState\(\)\s*\}/s)
assert.match(userStore, /function resetSessionState\(\)\s*\{\s*sessionEpoch\.invalidate\(\)/s)
assert.match(
  userStore,
  /async function logout\(\)[\s\S]*?await executeLogout\(requestLogout, clearSession\)[\s\S]*?finally[\s\S]*?broadcastSessionInvalidation\(\)/s
)
assert.doesNotMatch(request, /userStore\.logout\(\)/)
assert.match(request, /userStore\.clearSession\(\)/)
assert.match(request, /refreshError instanceof SessionChangedError/)
assert.match(router, /!userStore\.token[\s\S]*?userStore\.restoreSession\(\)/)
assert.match(router, /error instanceof SessionChangedError/)
assert.match(
  layout,
  /async function onUserMenu[\s\S]*?try\s*\{\s*await userStore\.logout\(\)\s*\}\s*catch\s*\{[\s\S]*?message\.warning\('本地会话已清除，但服务端退出结果未确认，请稍后重试'\)[\s\S]*?\}\s*finally\s*\{\s*await router\.push\('\/login'\)/s
)

let successRemoteCalls = 0
let successClearCalls = 0
await executeLogout(
  async () => {
    successRemoteCalls += 1
  },
  () => {
    successClearCalls += 1
  }
)
assert.equal(successRemoteCalls, 1)
assert.equal(successClearCalls, 1)

const failedSession = {
  accessToken: 'old-access-token',
  currentUser: { id: '42', username: 'test_student' }
}
const remoteLogoutError = new Error('remote logout failed')
let failedRemoteCalls = 0
let failedClearCalls = 0
let callerWarning = ''
let propagatedError
try {
  await executeLogout(
    async () => {
      failedRemoteCalls += 1
      throw remoteLogoutError
    },
    () => {
      failedClearCalls += 1
      failedSession.accessToken = ''
      failedSession.currentUser = null
    }
  )
} catch (error) {
  propagatedError = error
  callerWarning = '本地会话已清除，但服务端退出结果未确认，请稍后重试'
}
assert.equal(failedRemoteCalls, 1)
assert.equal(failedClearCalls, 1)
assert.deepEqual(failedSession, { accessToken: '', currentUser: null })
assert.strictEqual(propagatedError, remoteLogoutError)
assert.equal(callerWarning, '本地会话已清除，但服务端退出结果未确认，请稍后重试')

const normalRefresh = new SessionEpochGuard()
const pendingLoadEpoch = normalRefresh.capture()
const pendingRefreshEpoch = normalRefresh.capture()
normalRefresh.assertCurrent(pendingRefreshEpoch)
normalRefresh.assertCurrent(pendingLoadEpoch)

const logoutRace = new SessionEpochGuard()
let releaseRefresh
const refreshResponse = new Promise((resolve) => {
  releaseRefresh = resolve
})
const lateRefresh = (async () => {
  const expectedEpoch = logoutRace.capture()
  await refreshResponse
  logoutRace.assertCurrent(expectedEpoch)
})()
logoutRace.invalidate()
releaseRefresh()
await assert.rejects(lateRefresh, SessionChangedError)

const tabA = new SessionEpochGuard()
const tabB = new SessionEpochGuard()
let releaseTabARefresh
const tabARefreshResponse = new Promise((resolve) => {
  releaseTabARefresh = resolve
})
const tabALateRefresh = (async () => {
  const expectedEpoch = tabA.capture()
  await tabARefreshResponse
  tabA.assertCurrent(expectedEpoch)
})()
tabB.invalidate()
// B 标签页广播 SESSION_EVENT 后，A 标签页的 storage handler 会重置状态并推进自己的 epoch。
tabA.invalidate()
releaseTabARefresh()
await assert.rejects(tabALateRefresh, SessionChangedError)

console.log('auth logout contract: PASS')
