import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import type { Server } from 'node:http'
import {
  currentUser,
  httpError,
  installApiMock,
  loginResult,
  ok,
  pageResult
} from './support/mockApi'
import { startStaticServer, stopStaticServer } from './support/staticServer'

let server: Server

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

test.beforeAll(async () => {
  server = await startStaticServer()
})

test.afterAll(async () => {
  await stopStaticServer(server)
})

test('login restores a protected request through one cookie refresh', async ({ page }) => {
  const user = currentUser({ permissions: ['student:view'] })
  let loggedIn = false
  let successfulRefreshes = 0
  const studentHeaders: string[] = []
  const api = await installApiMock(page, {
    handler: async ({ route, request, path }) => {
      if (path === '/auth/refresh' && request.method() === 'POST') {
        expect(request.headers().authorization).toBeUndefined()
        if (!loggedIn) await httpError(route, 401, '未登录')
        else {
          successfulRefreshes += 1
          await ok(route, loginResult(user, 'refreshed-access'))
        }
        return true
      }
      if (path === '/auth/captcha') {
        await ok(route, { captchaId: 'captcha-1', image: captchaImage('2468') })
        return true
      }
      if (path === '/auth/login' && request.method() === 'POST') {
        loggedIn = true
        expect(request.postDataJSON()).toEqual({
          username: 'teacher01',
          password: 'Initial-Password-1!',
          captchaId: 'captcha-1',
          captchaCode: '2468'
        })
        await ok(route, loginResult(user, 'login-access'))
        return true
      }
      if (path === '/student' && request.method() === 'GET') {
        studentHeaders.push(request.headers().authorization || '')
        if (studentHeaders.length === 1) await httpError(route, 401, 'access expired')
        else await ok(route, pageResult([student('20260001', '登录链学生', '4401********1234')]))
        return true
      }
      return false
    }
  })

  await page.goto('/login')
  await expectAccessible(page, '登录页', '.login-shell')
  await page.getByLabel('账号').fill('teacher01')
  await page.getByLabel('密码').fill('Initial-Password-1!')
  await page.getByRole('textbox', { name: '验证码', exact: true }).fill('2468')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
  await page.getByRole('menuitem', { name: '基本信息', exact: true }).click()
  await page.getByRole('link', { name: '学生基本信息', exact: true }).click()
  await expect(page.getByText('登录链学生', { exact: true })).toBeVisible()

  expect(successfulRefreshes).toBe(1)
  expect(studentHeaders).toEqual(['Bearer login-access', 'Bearer refreshed-access'])
  expect(await page.evaluate(() => ({
    access: localStorage.getItem('accessToken'),
    refresh: localStorage.getItem('refreshToken'),
    legacy: localStorage.getItem('token')
  }))).toEqual({ access: null, refresh: null, legacy: null })
  await expectAccessible(page, '登录与刷新后的学生页', '.app-content-frame')
  expectClean(api)
})

test('forced initial password change clears the session and returns to login', async ({ page }) => {
  const user = currentUser({ mustChangePwd: true })
  let passwordPayload: unknown
  const api = await installApiMock(page, {
    handler: async ({ route, request, path }) => {
      if (path === '/auth/refresh') {
        await httpError(route, 401, '未登录')
        return true
      }
      if (path === '/auth/captcha') {
        await ok(route, { captchaId: 'captcha-pwd', image: captchaImage('1357') })
        return true
      }
      if (path === '/auth/login') {
        await ok(route, loginResult(user, 'must-change-access'))
        return true
      }
      if (path === '/auth/change-pwd') {
        passwordPayload = request.postDataJSON()
        await ok(route, null)
        return true
      }
      return false
    }
  })

  await page.goto('/login')
  await page.getByLabel('账号').fill('first-login')
  await page.getByLabel('密码').fill('Initial-Password-1!')
  await page.getByRole('textbox', { name: '验证码', exact: true }).fill('1357')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '修改初始密码' })
  await expect(dialog).toBeVisible()
  await dialog.evaluate(async (element) => {
    await Promise.all(element.getAnimations({ subtree: true }).map((animation) => animation.finished.catch(() => undefined)))
  })
  await expectAccessible(page, '首次改密弹窗', '.n-modal')
  await dialog.getByLabel('新密码', { exact: true }).fill('Changed-Password-2!')
  await dialog.getByLabel('确认新密码', { exact: true }).fill('Changed-Password-2!')
  await dialog.getByRole('button', { name: '确认修改' }).click()

  await expect(dialog).toBeHidden()
  await expect(page.getByText('密码已修改，请使用新密码重新登录')).toBeVisible()
  expect(passwordPayload).toEqual({ oldPassword: 'Initial-Password-1!', newPassword: 'Changed-Password-2!' })
  expect(await page.evaluate(() => localStorage.getItem('authRestoreBlocked'))).toBe('1')
  expectClean(api)
})

test('RBAC hides unavailable menus, blocks direct access, and keeps identity masked', async ({ page }) => {
  const user = currentUser({ permissions: ['student:view'] })
  const fullIdentity = '440101199901011234'
  const maskedIdentity = '4401********1234'
  const api = await installApiMock(page, {
    user,
    handler: async ({ route, request, path }) => {
      if (path === '/student' && request.method() === 'GET') {
        await ok(route, pageResult([student('20260002', '脱敏学生', maskedIdentity)]))
        return true
      }
      return false
    }
  })

  await page.goto('/students')
  await expect(page.getByText('脱敏学生', { exact: true })).toBeVisible()
  await expect(page.getByText(maskedIdentity, { exact: true })).toBeVisible()
  await expect(page.getByText(fullIdentity, { exact: true })).toHaveCount(0)
  await expect(page.getByText('账号权限', { exact: true })).toHaveCount(0)
  await page.goto('/system/security')
  await expect(page).toHaveURL(/\/forbidden$/)
  await expect(page.locator('.app-content-frame').getByText('无权访问', { exact: true })).toBeVisible()
  expectClean(api)
})

test('import prevalidation shows row outcomes and confirms the selected strategy', async ({ page }) => {
  const user = currentUser({ permissions: ['exchange:template', 'exchange:prevalidate', 'exchange:import'] })
  let confirmPayload: unknown
  const uppercaseFieldProbe: { captured?: { contentType: string; body: Buffer } } = {}
  const api = await installApiMock(page, {
    user,
    handler: async ({ route, request, path, url }) => {
      if (path === '/exchange/prevalidate' && url.searchParams.get('ws6FieldCase') === 'upper') {
        const body = request.postDataBuffer()
        if (!body) throw new Error('uppercase multipart probe body missing')
        uppercaseFieldProbe.captured = {
          contentType: request.headers()['content-type'] || '',
          body
        }
        await ok(route, null)
        return true
      }
      if (path === '/exchange/prevalidate' && request.method() === 'POST') {
        const contentType = request.headers()['content-type'] || ''
        const body = request.postDataBuffer()
        if (!body) throw new Error('multipart request body missing')
        assertImportFileMultipart(contentType, body)
        await ok(route, {
          batchId: 'batch-1', batchNo: 'IMP-20260811-001', total: 2, successCount: 1, failCount: 1,
          previewRows: [{ rowNo: 2, row: { studentNo: '20260003', name: '预校验通过学生', idCardNo: '4401********5678' } }],
          errors: [{ rowNo: 3, studentNo: '20260004', studentName: '异常学生', fieldName: '学号', errorValue: '', errorReason: '学号不能为空', suggestion: '补充学号' }]
        })
        return true
      }
      if (path === '/exchange/import/batch-1/confirm' && request.method() === 'POST') {
        confirmPayload = request.postDataJSON()
        await ok(route, {
          batchId: 'batch-1', batchNo: 'IMP-20260811-001', total: 2,
          successCount: 1, failCount: 1, status: 'IMPORTED', messages: []
        })
        return true
      }
      return false
    }
  })

  await page.goto('/exchange/import')
  await page.evaluate(async ({ mimeType }) => {
    const form = new FormData()
    form.append('FILE', new File(['WS6-XLSX-FIXTURE'], 'students.xlsx', { type: mimeType }))
    const response = await fetch('/api/exchange/prevalidate?ws6FieldCase=upper', { method: 'POST', body: form })
    if (!response.ok) throw new Error(`uppercase multipart probe failed: ${response.status}`)
  }, { mimeType: XLSX_MIME })
  const capturedUppercaseField = uppercaseFieldProbe.captured
  if (!capturedUppercaseField) throw new Error('uppercase multipart probe was not captured')
  expect(() => assertImportFileMultipart(
    capturedUppercaseField.contentType,
    capturedUppercaseField.body
  )).toThrow('unexpected multipart field name: FILE')

  await page.locator('input[type="file"]').setInputFiles({
    name: 'students.xlsx',
    mimeType: XLSX_MIME,
    buffer: Buffer.from('WS6-XLSX-FIXTURE')
  })
  await page.getByRole('button', { name: '预校验', exact: true }).click()
  await expect(page.getByText('预校验通过学生', { exact: true })).toBeVisible()
  await page.locator('.n-tabs-tab').filter({ hasText: '异常明细' }).click()
  await expect(page.getByText('学号不能为空', { exact: true })).toBeVisible()
  const strategyField = page.locator('.filter-field').filter({ has: page.getByText('策略', { exact: true }) })
  await strategyField.locator('.n-select').click()
  await page.locator('.n-base-select-option').filter({ hasText: '覆盖' }).click()
  await expect(strategyField).toContainText('覆盖')
  await page.getByRole('button').filter({ hasText: '确认导入' }).click()
  await expect(page.getByText('导入完成：成功 1，失败 1')).toBeVisible()

  expect(confirmPayload).toEqual({ strategy: 'OVERWRITE' })
  expectClean(api)
})

test('review teacher loads a video task and submits one score', async ({ page }) => {
  const user = currentUser({ roles: ['REVIEW_TEACHER'], permissions: ['video:score'] })
  let submitted = false
  let scorePayload: unknown
  const api = await installApiMock(page, {
    user,
    handler: async ({ route, request, path }) => {
      if (path === '/video/tasks/my' && request.method() === 'GET') {
        await ok(route, pageResult([{
          id: 'task-1', videoReviewId: 'review-1', reviewerId: user.id, reviewerName: user.realName,
          reviewerRole: 'REVIEWER', score: submitted ? 88 : null,
          dimensionScores: submitted ? { CLASSROOM: 90 } : {}, comment: submitted ? '整体表现良好' : '',
          conclusion: submitted ? 'PASS' : null, submitted: submitted ? 1 : 0, submitTime: '2026-08-11T10:00:00'
        }]))
        return true
      }
      if (path === '/dict/video_score_dimension/items') {
        await ok(route, [{ id: 'dim-1', typeCode: 'video_score_dimension', itemCode: 'CLASSROOM', itemValue: '课堂表现', sort: 1, status: 1 }])
        return true
      }
      if (path === '/video/reviews/review-1') {
        await ok(route, {
          id: 'review-1', studentId: 'student-1', studentNo: '20260005', studentName: '视频学生',
          collegeId: 'college-1', assessmentYear: '2026', videoFileId: 'file-1', videoFileName: 'lesson.mp4',
          durationSeconds: 600, formatCheck: 'PASS', validationMessage: null, status: 'SCORING', statusLabel: '评分中',
          finalScore: null, finalConclusion: null, locked: 0, tasks: []
        })
        return true
      }
      if (path === '/video/tasks/task-1/score' && request.method() === 'POST') {
        scorePayload = request.postDataJSON()
        submitted = true
        await ok(route, null)
        return true
      }
      return false
    }
  })

  await page.goto('/videos')
  await expect(page.getByText('视频学生', { exact: true })).toBeVisible()
  await page.getByLabel('课堂表现得分').fill('90')
  await page.getByLabel('总分').fill('88')
  await page.getByLabel('评审意见').fill('整体表现良好')
  await page.getByRole('button', { name: '提交评分' }).click()
  await expect(page.getByText('已提交', { exact: true }).first()).toBeVisible()

  expect(scorePayload).toEqual({ score: 88, conclusion: 'PASS', comment: '整体表现良好', dimensionScores: { CLASSROOM: 90 } })
  expectClean(api)
})

async function expectAccessible(page: Page, label: string, scope: string) {
  const result = await new AxeBuilder({ page })
    .include(scope)
    // Naive UI currently renders decorative NIcon internals with role="img"; keep the
    // product-content baseline focused on authored controls and text until that upstream boundary is replaced.
    .exclude('.n-icon')
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const blocking = result.violations
    .filter((violation) => violation.impact === 'critical' || violation.impact === 'serious')
    .map((violation) => ({
      id: violation.id,
      impact: violation.impact,
      nodes: violation.nodes.map((node) => ({
        target: node.target,
        checks: node.any.map((check) => ({ message: check.message, data: check.data }))
      }))
    }))
  expect(blocking, `${label} 存在 critical/serious axe 违规`).toEqual([])
}

function expectClean(api: Awaited<ReturnType<typeof installApiMock>>) {
  expect(api.unexpected, '存在未声明的 API 调用').toEqual([])
  expect(api.pageErrors, '浏览器页面异常').toEqual([])
  expect(api.consoleErrors, '浏览器 console error').toEqual([])
}

function assertImportFileMultipart(contentType: string, body: Buffer) {
  expect(contentType).toMatch(/^multipart\/form-data(?:;|$)/i)
  const boundaryMatch = contentType.match(/(?:^|;)\s*boundary=(?:"([^"]+)"|([^;\s]+))/i)
  const boundary = boundaryMatch?.[1] ?? boundaryMatch?.[2]
  if (!boundary) throw new Error(`multipart boundary missing: ${contentType}`)

  const opening = Buffer.from(`--${boundary}\r\n`)
  expect(body.subarray(0, opening.length)).toEqual(opening)
  const headerEnd = body.indexOf(Buffer.from('\r\n\r\n'), opening.length)
  if (headerEnd < 0) throw new Error('multipart file headers missing')
  assertImportFilePartHeaders(body.subarray(opening.length, headerEnd).toString('latin1'))

  const contentStart = headerEnd + 4
  const contentEnd = body.indexOf(Buffer.from(`\r\n--${boundary}--`), contentStart)
  if (contentEnd < 0) throw new Error('multipart closing boundary missing')
  expect(body.subarray(contentStart, contentEnd)).toEqual(Buffer.from('WS6-XLSX-FIXTURE'))
}

function assertImportFilePartHeaders(partHeaders: string) {
  const lines = partHeaders.split('\r\n')
  const disposition = lines.find((line) => /^content-disposition\s*:/i.test(line))
  if (!disposition) throw new Error('multipart content-disposition missing')

  const dispositionValue = disposition.slice(disposition.indexOf(':') + 1)
  const fieldName = quotedDispositionParameter(dispositionValue, 'name')
  const filename = quotedDispositionParameter(dispositionValue, 'filename')
  if (fieldName !== 'file') throw new Error(`unexpected multipart field name: ${fieldName ?? '<missing>'}`)
  if (filename !== 'students.xlsx') throw new Error(`unexpected multipart filename: ${filename ?? '<missing>'}`)

  const contentTypeHeader = lines.find((line) => /^content-type\s*:/i.test(line))
  if (!contentTypeHeader) throw new Error('multipart file content-type missing')
  const contentType = contentTypeHeader.slice(contentTypeHeader.indexOf(':') + 1).trim()
  if (contentType.toLowerCase() !== XLSX_MIME) {
    throw new Error(`unexpected multipart content-type: ${contentType}`)
  }
}

function quotedDispositionParameter(disposition: string, parameterName: 'name' | 'filename') {
  return disposition.match(new RegExp(`(?:^|;)\\s*${parameterName}\\s*=\\s*"([^"]*)"`, 'i'))?.[1]
}

function captchaImage(code: string) {
  return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg"><text>${code}</text></svg>`)}`
}

function student(studentNo: string, name: string, idCardNo: string) {
  return {
    id: `student-${studentNo}`, studentNo, name, gender: '1', idCardType: 'ID_CARD', idCardNo,
    birthDate: '2000-01-01', identityType: 'NORMAL', sourceProvince: '44', sourceCity: '4401',
    sourceDistrict: '440106', sourceFull: '广东省广州市', collegeId: 'college-1', internalMajorCode: '040101',
    grade: '2026', className: '1班', assessmentYear: '2026', status: 'DRAFT', statusLabel: '草稿', locked: 0
  }
}
