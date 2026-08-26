import type { Page, Request, Route } from '@playwright/test'

export interface MockUser {
  id: string
  username: string
  realName: string
  userType: string
  collegeId: string | null
  studentId: string | null
  mustChangePwd: boolean
  userManagementWritable: boolean
  roleManagementWritable: boolean
  roles: string[]
  permissions: string[]
}

export interface ApiCall {
  route: Route
  request: Request
  path: string
  url: URL
}

export type ApiHandler = (call: ApiCall) => Promise<boolean> | boolean

export function currentUser(overrides: Partial<MockUser> = {}): MockUser {
  return {
    id: 'ws6-user',
    username: 'ws6-user',
    realName: 'WS-6 测试用户',
    userType: 'STAFF',
    collegeId: 'college-1',
    studentId: null,
    mustChangePwd: false,
    userManagementWritable: false,
    roleManagementWritable: false,
    roles: ['TEST_ROLE'],
    permissions: [],
    ...overrides
  }
}

export function loginResult(user: MockUser, accessToken = 'ws6-access') {
  return { accessToken, expiresIn: 900, mustChangePwd: user.mustChangePwd, user }
}

export function pageResult<T>(records: T[]) {
  return { records, total: records.length, page: 1, size: 20 }
}

export async function ok(route: Route, data: unknown): Promise<void> {
  await route.fulfill({
    status: 200,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify({ code: 0, msg: '成功', data })
  })
}

export async function httpError(route: Route, status: number, message: string): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify({ code: status, msg: message, data: null })
  })
}

export async function installApiMock(
  page: Page,
  options: { user?: MockUser | null; handler?: ApiHandler } = {}
) {
  const unexpected: string[] = []
  const pageErrors: string[] = []
  const consoleErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('console', (message) => {
    if (message.type() !== 'error') return
    const text = message.text()
    if (text.includes('Failed to load resource') && text.includes('401')) return
    consoleErrors.push(text)
  })
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const apiPath = url.pathname.replace(/^\/api/, '')
    const call = { route, request, url, path: apiPath }
    if (options.handler && await options.handler(call)) return
    if (apiPath === '/auth/refresh' && request.method() === 'POST') {
      if (options.user) await ok(route, loginResult(options.user))
      else await httpError(route, 401, '登录已过期')
      return
    }
    if (apiPath === '/auth/me' && options.user) {
      await ok(route, options.user)
      return
    }
    if (apiPath === '/notice/unread-count') {
      await ok(route, 0)
      return
    }
    if (apiPath === '/notice') {
      await ok(route, pageResult([]))
      return
    }
    if (/^\/stats\/[^/]+$/.test(apiPath)) {
      await ok(route, {
        type: 'submission', title: 'WS-6 工作台', assessmentYear: '2026',
        denominatorRule: '测试口径', metrics: [], rows: [], details: []
      })
      return
    }
    if (/^\/dict\/[^/]+\/items$/.test(apiPath)) {
      await ok(route, [])
      return
    }
    if (['/college', '/major', '/region/children', '/subject', '/subject/recent'].includes(apiPath)) {
      await ok(route, [])
      return
    }
    if (apiPath === '/exchange/batches') {
      await ok(route, pageResult([]))
      return
    }
    unexpected.push(`${request.method()} ${apiPath}`)
    await httpError(route, 500, `未处理的 WS-6 模拟接口：${request.method()} ${apiPath}`)
  })
  return { unexpected, pageErrors, consoleErrors }
}
