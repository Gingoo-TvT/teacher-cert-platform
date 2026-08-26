import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { chromium } from '../node_modules/playwright/index.mjs'

const baseUrl = (process.env.WS4_GAP_BASE_URL || 'http://127.0.0.1:18099').replace(/\/$/, '')
const outDir = path.resolve(process.env.WS4_GAP_OUT_DIR || 'target/ws4-gap-runtime')
const checks = []

function apiResult(data, msg = '成功') {
  return { code: 0, msg, data }
}

function pageResult(records = [], page = 1, size = 20) {
  return { records, total: records.length, page, size }
}

function currentUser({
  username = 'ws4_gap_user',
  roles = ['SYS_ADMIN'],
  permissions = [],
  studentId = null,
  mustChangePwd = false
} = {}) {
  return {
    id: `user-${username}`,
    username,
    realName: `WS4 ${username}`,
    userType: roles.includes('STUDENT') ? 'STUDENT' : 'STAFF',
    collegeId: 'college-1',
    studentId,
    mustChangePwd,
    userManagementWritable: true,
    roleManagementWritable: true,
    roles,
    permissions
  }
}

function dictItem(typeCode, itemCode, itemValue) {
  return { id: `${typeCode}-${itemCode}`, typeCode, itemCode, itemValue, sort: 1, status: 1 }
}

function material(id, fileName, studentName) {
  return {
    id,
    studentId: `student-${id}`,
    studentNo: `NO-${id}`,
    studentName,
    collegeId: 'college-1',
    assessmentYear: '2026',
    category: 'COURSE',
    categoryLabel: '课程材料',
    fileId: `file-${id}`,
    fileName,
    fileSize: 1024,
    contentType: 'application/pdf',
    uploaderId: 'uploader-1',
    status: 'FIRST_REVIEW',
    statusLabel: '待初审',
    locked: 0
  }
}

function statsReport(title, dimensionLabel) {
  return {
    type: 'submission',
    title,
    assessmentYear: '2026',
    denominatorRule: '当前考核年度在册学生',
    metrics: [],
    rows: [{ dimension: dimensionLabel, dimensionLabel, status: 'DONE', statusLabel: '已完成', count: 1, values: {} }],
    details: []
  }
}

async function json(route, body, status = 200, contentType = 'application/json; charset=utf-8') {
  await route.fulfill({ status, contentType, body: typeof body === 'string' ? body : JSON.stringify(body) })
}

async function ok(route, data) {
  await json(route, apiResult(data))
}

async function fail(route, message) {
  await json(route, { code: 500, msg: message, data: null }, 503)
}

async function waitFor(predicate, message, timeout = 4000) {
  const startedAt = Date.now()
  while (!(await predicate())) {
    if (Date.now() - startedAt > timeout) throw new Error(message)
    await new Promise((resolve) => setTimeout(resolve, 20))
  }
}

async function commonSuccess({ route, request, path: apiPath }) {
  if (request.method() !== 'GET') return false
  const dictMatch = apiPath.match(/^\/dict\/([^/]+)\/items$/)
  if (dictMatch) {
    const type = decodeURIComponent(dictMatch[1])
    const values = {
      material_category: [dictItem(type, 'COURSE', '课程材料')],
      training_goal: [dictItem(type, 'GOAL_A', '目标A'), dictItem(type, 'GOAL_B', '目标B')],
      teaching_segment: [dictItem(type, 'SEG_A', '学段A'), dictItem(type, 'SEG_B', '学段B')],
      internship_location: [dictItem(type, 'LOC_A', '地点A'), dictItem(type, 'LOC_B', '地点B')],
      exemption_basis: [dictItem(type, 'BASIS', '测试依据')]
    }
    await ok(route, values[type] || [dictItem(type, 'DEFAULT', '默认选项')])
    return true
  }
  if (apiPath === '/college') {
    await ok(route, [{ id: 'college-1', code: 'C01', name: '测试学院', sort: 1, status: 1 }])
    return true
  }
  if (apiPath === '/major') {
    await ok(route, [{
      id: 'major-1', collegeId: 'college-1', collegeName: '测试学院', internalMajorCode: 'M01',
      internalMajorName: '测试专业', secondDisciplineCode: '0401', secondDisciplineName: '教育学',
      pilotScopeFlag: 1, yearVersion: '2026', sort: 1, status: 1, trainingGoals: []
    }])
    return true
  }
  if (apiPath === '/region/children' || apiPath === '/subject' || apiPath === '/subject/recent') {
    await ok(route, [])
    return true
  }
  if (apiPath === '/notice/unread-count') {
    await ok(route, 0)
    return true
  }
  return false
}

async function withAuthedPage(browser, user, viewport, handler, run) {
  const context = await browser.newContext({ viewport })
  const page = await context.newPage()
  const pageErrors = []
  const consoleErrors = []
  const unhandled = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('console', (message) => {
    if (message.type() !== 'error') return
    const text = message.text()
    if (text.includes('Failed to load resource') && text.includes('503')) return
    consoleErrors.push(text)
  })

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const apiPath = url.pathname.replace(/^\/api/, '')
    const requestContext = { route, request, url, path: apiPath }
    if (apiPath === '/auth/refresh' && request.method() === 'POST') {
      await ok(route, {
        accessToken: 'ws4-gap-access', expiresIn: 900,
        mustChangePwd: Boolean(user.mustChangePwd), user
      })
      return
    }
    if (await handler(requestContext)) return
    if (apiPath === '/auth/me') {
      await ok(route, user)
      return
    }
    if (await commonSuccess(requestContext)) return
    unhandled.push(`${request.method()} ${apiPath}`)
    await fail(route, `未处理的 WS-4 gap 模拟接口：${request.method()} ${apiPath}`)
  })

  try {
    await run(page)
    assert.deepEqual(unhandled, [], `unhandled API calls: ${unhandled.join(', ')}`)
    assert.deepEqual(pageErrors, [], `page errors: ${pageErrors.join(' | ')}`)
    assert.deepEqual(consoleErrors, [], `console errors: ${consoleErrors.join(' | ')}`)
  } finally {
    await context.close()
  }
}

async function selectOption(scope, page, label, optionText) {
  const item = scope.locator('.n-form-item').filter({ hasText: label }).first()
  await item.locator('.n-select').click()
  await page.locator('.n-base-select-option:visible').filter({ hasText: optionText }).first().click()
}

async function gridColumns(locator) {
  return locator.evaluate((element) => getComputedStyle(element).gridTemplateColumns.split(/\s+/).filter(Boolean).length)
}

async function releaseResponseAndSettle(page, responsePromise, release) {
  release()
  const response = await responsePromise
  await response.finished()
  await page.evaluate(() => new Promise((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(resolve))
  }))
}

async function materialOrderingAndBarrier(browser) {
  const user = currentUser({ permissions: ['material:firstReview'] })
  let mode = 'success'
  let delayedCalls = 0
  let releaseDelayed
  const delayedGate = new Promise((resolve) => { releaseDelayed = resolve })

  await withAuthedPage(browser, user, { width: 1280, height: 900 }, async ({ route, request, url, path: apiPath }) => {
    if (apiPath === '/material' && request.method() === 'GET') {
      if (mode === 'error') {
        await fail(route, '材料列表刷新失败')
        return true
      }
      const keyword = url.searchParams.get('keyword') || ''
      if (keyword === 'A') {
        delayedCalls += 1
        await delayedGate
        await ok(route, pageResult([material('a', 'A-旧请求.pdf', '旧请求学生')]))
      } else if (keyword === 'B') {
        await ok(route, pageResult([material('b', 'B-最新请求.pdf', '最新请求学生')]))
      } else {
        await ok(route, pageResult([material('initial', '初始材料.pdf', '初始学生')]))
      }
      return true
    }
    return false
  }, async (page) => {
    await page.goto(`${baseUrl}/materials`, { waitUntil: 'networkidle' })
    await page.getByText('初始材料.pdf', { exact: true }).waitFor()
    const keyword = page.getByPlaceholder('文件名 / 类别 / 学生')
    await keyword.fill('A')
    await keyword.press('Enter')
    await waitFor(() => delayedCalls === 1, 'material delayed request A did not start')
    await keyword.fill('B')
    await keyword.press('Enter')
    await page.getByText('B-最新请求.pdf', { exact: true }).waitFor()
    const staleResponse = page.waitForResponse((response) => {
      const responseUrl = new URL(response.url())
      return response.request().method() === 'GET'
        && responseUrl.pathname === '/api/material'
        && responseUrl.searchParams.get('keyword') === 'A'
    })
    await releaseResponseAndSettle(page, staleResponse, releaseDelayed)
    assert.equal(await page.getByText('B-最新请求.pdf', { exact: true }).isVisible(), true)
    assert.equal(await page.getByText('A-旧请求.pdf', { exact: true }).count(), 0)
    checks.push({ id: 'material-latest-request-wins', status: 'PASS' })

    const metricsGrid = page.locator('.n-grid.page-section').first()
    assert.equal(await gridColumns(metricsGrid), 4)
    await page.setViewportSize({ width: 375, height: 812 })
    await waitFor(async () => await gridColumns(metricsGrid) === 1, 'material metrics did not reflow to one column')
    assert.equal(await gridColumns(metricsGrid), 1)
    checks.push({ id: 'd2-material-grid-real-columns', status: 'PASS' })
    await page.setViewportSize({ width: 1280, height: 900 })

    mode = 'error'
    await page.locator('.data-panel').getByRole('button', { name: '刷新', exact: true }).click()
    await page.getByText(/材料列表刷新失败/).first().waitFor()
    const reviewButton = page.getByRole('button', { name: '初审', exact: true })
    assert.equal(await reviewButton.isDisabled(), true)
    await reviewButton.click({ force: true })
    assert.equal(await page.getByText('材料初审', { exact: true }).count(), 0)
    await page.screenshot({ path: path.join(outDir, 'material-stale-barrier.png'), fullPage: true })
    checks.push({ id: 'material-stale-blocks-review', status: 'PASS' })
  })
}

async function statsOrderingAndExportBinding(browser) {
  const user = currentUser({ permissions: ['stats:view'] })
  let delayedCalls = 0
  let exportCalls = 0
  let exportBody = null
  let releaseDelayed
  const delayedGate = new Promise((resolve) => { releaseDelayed = resolve })

  await withAuthedPage(browser, user, { width: 1280, height: 900 }, async ({ route, request, url, path: apiPath }) => {
    if (apiPath === '/stats/submission' && request.method() === 'GET') {
      const keyword = url.searchParams.get('keyword') || ''
      if (keyword === 'A') {
        delayedCalls += 1
        await delayedGate
        await ok(route, statsReport('A-旧报表', 'A-旧维度'))
      } else if (keyword === 'B') {
        await ok(route, statsReport('B-最新报表', 'B-最新维度'))
      } else {
        await ok(route, statsReport('初始报表', '初始维度'))
      }
      return true
    }
    if (apiPath === '/stats/submission/export' && request.method() === 'POST') {
      exportCalls += 1
      exportBody = JSON.parse(request.postData() || '{}')
      await json(route, 'xlsx', 200, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
      return true
    }
    return false
  }, async (page) => {
    await page.goto(`${baseUrl}/stats`, { waitUntil: 'networkidle' })
    await page.getByText('初始报表', { exact: true }).waitFor()
    const keyword = page.getByPlaceholder('学号/姓名/批次/证书编号')
    await keyword.fill('A')
    await keyword.press('Enter')
    await waitFor(() => delayedCalls === 1, 'stats delayed request A did not start')
    await keyword.fill('B')
    await keyword.press('Enter')
    await page.getByText('B-最新报表', { exact: true }).waitFor()
    const staleResponse = page.waitForResponse((response) => {
      const responseUrl = new URL(response.url())
      return response.request().method() === 'GET'
        && responseUrl.pathname === '/api/stats/submission'
        && responseUrl.searchParams.get('keyword') === 'A'
    })
    await releaseResponseAndSettle(page, staleResponse, releaseDelayed)
    assert.equal(await page.getByText('B-最新报表', { exact: true }).isVisible(), true)
    assert.equal(await page.getByText('A-旧报表', { exact: true }).count(), 0)
    checks.push({ id: 'stats-latest-request-wins', status: 'PASS' })

    const exportButton = page.getByRole('button', { name: '导出 Excel', exact: true })
    await exportButton.click()
    await waitFor(() => exportCalls === 1, 'stats export did not start')
    assert.equal(exportBody?.keyword, 'B')
    checks.push({ id: 'stats-export-uses-loaded-snapshot', status: 'PASS' })

    await keyword.fill('C')
    assert.equal(await exportButton.isDisabled(), true)
    await exportButton.click({ force: true })
    await page.waitForTimeout(80)
    assert.equal(exportCalls, 1)
    checks.push({ id: 'stats-stale-query-blocks-export', status: 'PASS' })
  })
}

async function trainingCascadeBarrier(browser) {
  const user = currentUser({ permissions: ['student:view', 'training:edit'] })
  let delayedCalls = 0
  let mode = 'ordering'
  let writeCalls = 0
  let releaseDelayed
  const delayedGate = new Promise((resolve) => { releaseDelayed = resolve })

  await withAuthedPage(browser, user, { width: 1280, height: 900 }, async ({ route, request, url, path: apiPath }) => {
    if (apiPath === '/training' && request.method() === 'GET') {
      await ok(route, pageResult([]))
      return true
    }
    if (apiPath === '/training/options' && request.method() === 'GET') {
      const goal = url.searchParams.get('goal') || ''
      const segment = url.searchParams.get('segment') || ''
      if (mode === 'error' && goal === 'GOAL_A') {
        await fail(route, '培养联动模拟失败')
        return true
      }
      if (goal === 'GOAL_A' && !segment) {
        delayedCalls += 1
        await delayedGate
        await ok(route, {
          trainingGoal: goal, defaultSegment: 'SEG_A', allowedSegments: ['SEG_A'],
          defaultInternshipLocation: 'LOC_A', allowedInternshipLocations: ['LOC_A'], subjects: []
        })
        return true
      }
      await ok(route, {
        trainingGoal: goal, defaultSegment: goal === 'GOAL_B' ? 'SEG_B' : 'SEG_A',
        allowedSegments: [goal === 'GOAL_B' ? 'SEG_B' : 'SEG_A'],
        defaultInternshipLocation: goal === 'GOAL_B' ? 'LOC_B' : 'LOC_A',
        allowedInternshipLocations: [goal === 'GOAL_B' ? 'LOC_B' : 'LOC_A'], subjects: []
      })
      return true
    }
    if (apiPath === '/training' && request.method() === 'POST') {
      writeCalls += 1
      await ok(route, 'training-id')
      return true
    }
    return false
  }, async (page) => {
    await page.goto(`${baseUrl}/training`, { waitUntil: 'networkidle' })
    await page.getByRole('button', { name: '新增培养信息', exact: true }).click()
    const drawer = page.locator('.n-drawer:visible').filter({ hasText: '新增专业培养信息' }).last()
    await drawer.waitFor()
    await selectOption(drawer, page, '培养目标', '目标A')
    await waitFor(() => delayedCalls === 1, 'training delayed goal A did not start')
    await selectOption(drawer, page, '培养目标', '目标B')
    await drawer.getByText('学段B', { exact: true }).waitFor()
    const staleResponse = page.waitForResponse((response) => {
      const responseUrl = new URL(response.url())
      return response.request().method() === 'GET'
        && responseUrl.pathname === '/api/training/options'
        && responseUrl.searchParams.get('goal') === 'GOAL_A'
        && !responseUrl.searchParams.has('segment')
    })
    await releaseResponseAndSettle(page, staleResponse, releaseDelayed)
    assert.equal(await drawer.getByText('学段B', { exact: true }).isVisible(), true)
    assert.equal(await drawer.getByText('学段A', { exact: true }).count(), 0)
    checks.push({ id: 'training-cascade-latest-request-wins', status: 'PASS' })

    mode = 'error'
    await selectOption(drawer, page, '培养目标', '目标A')
    await drawer.getByText(/培养联动模拟失败/).first().waitFor()
    const saveButton = drawer.getByRole('button', { name: '保存', exact: true })
    assert.equal(await saveButton.isDisabled(), true)
    await saveButton.click({ force: true })
    await page.waitForTimeout(80)
    assert.equal(writeCalls, 0)
    await page.screenshot({ path: path.join(outDir, 'training-cascade-error.png'), fullPage: true })
    checks.push({ id: 'training-cascade-error-blocks-save', status: 'PASS' })
  })
}

async function exemptionCascadeBarrier(browser) {
  const user = currentUser({
    username: 'ws4_exemption_student', roles: ['STUDENT'], permissions: ['exemption:apply'], studentId: 'student-exemption'
  })
  let delayedCalls = 0
  let mode = 'ordering'
  let writeCalls = 0
  let releaseDelayed
  const delayedGate = new Promise((resolve) => { releaseDelayed = resolve })

  await withAuthedPage(browser, user, { width: 1280, height: 900 }, async ({ route, request, url, path: apiPath }) => {
    if (apiPath === '/exemption' && request.method() === 'GET') {
      await ok(route, pageResult([]))
      return true
    }
    if (apiPath === '/exemption/subjects' && request.method() === 'GET') {
      const segment = url.searchParams.get('segment') || ''
      if (!segment) {
        await ok(route, [])
        return true
      }
      if (mode === 'error' && segment === 'SEG_A') {
        await fail(route, '免考科目模拟失败')
        return true
      }
      if (segment === 'SEG_A') {
        delayedCalls += 1
        await delayedGate
        await ok(route, [dictItem('exemption_subject', 'SUBJECT_A', '科目A')])
        return true
      }
      await ok(route, [dictItem('exemption_subject', 'SUBJECT_B', '科目B')])
      return true
    }
    if (apiPath === '/exemption' && request.method() === 'POST') {
      writeCalls += 1
      await ok(route, [])
      return true
    }
    return false
  }, async (page) => {
    await page.goto(`${baseUrl}/exemptions`, { waitUntil: 'networkidle' })
    await page.getByRole('button', { name: '免考申请', exact: true }).click()
    const drawer = page.locator('.n-drawer:visible').filter({ hasText: '免考申请' }).last()
    await drawer.waitFor()
    await selectOption(drawer, page, '任教学段', '学段A')
    await waitFor(() => delayedCalls === 1, 'exemption delayed segment A did not start')
    await selectOption(drawer, page, '任教学段', '学段B')
    const addButton = drawer.getByRole('button', { name: '添加科目', exact: true })
    await waitFor(async () => await addButton.isEnabled(), 'exemption segment B did not become fresh')
    const staleResponse = page.waitForResponse((response) => {
      const responseUrl = new URL(response.url())
      return response.request().method() === 'GET'
        && responseUrl.pathname === '/api/exemption/subjects'
        && responseUrl.searchParams.get('segment') === 'SEG_A'
    })
    await releaseResponseAndSettle(page, staleResponse, releaseDelayed)
    await addButton.click()
    const subjectSelect = drawer.locator('.subject-row__controls .n-select').first()
    await subjectSelect.click()
    const visibleSubjectOptions = page.locator('.n-base-select-option:visible')
    await waitFor(async () => await visibleSubjectOptions.count() > 0, 'exemption subject options did not open')
    assert.deepEqual(await visibleSubjectOptions.allTextContents(), ['科目B'])
    await page.keyboard.press('Escape')
    checks.push({ id: 'exemption-cascade-latest-request-wins', status: 'PASS' })

    mode = 'error'
    await selectOption(drawer, page, '任教学段', '学段A')
    await drawer.getByText(/免考科目模拟失败/).first().waitFor()
    assert.equal(await addButton.isDisabled(), true)
    const saveButton = drawer.getByRole('button', { name: '保存', exact: true })
    assert.equal(await saveButton.isDisabled(), true)
    await saveButton.click({ force: true })
    await page.waitForTimeout(80)
    assert.equal(writeCalls, 0)
    await page.screenshot({ path: path.join(outDir, 'exemption-cascade-error.png'), fullPage: true })
    checks.push({ id: 'exemption-cascade-error-blocks-save', status: 'PASS' })
  })
}

async function videoInitialFailureSemantics(browser) {
  const reviewer = currentUser({ username: 'ws4_reviewer', roles: ['REVIEW_TEACHER'], permissions: ['video:score'] })
  let taskMode = 'error'
  await withAuthedPage(browser, reviewer, { width: 1280, height: 900 }, async ({ route, request, path: apiPath }) => {
    if (apiPath === '/video/tasks/my' && request.method() === 'GET') {
      if (taskMode === 'error') await fail(route, '评审任务首次加载失败')
      else await ok(route, pageResult([]))
      return true
    }
    return false
  }, async (page) => {
    await page.goto(`${baseUrl}/videos`, { waitUntil: 'networkidle' })
    await page.getByText('评审任务加载失败', { exact: true }).waitFor()
    assert.equal(await page.getByText('待评分 0', { exact: true }).count(), 0)
    assert.equal(await page.getByText('已提交 0', { exact: true }).count(), 0)
    assert.equal(await page.getByText('请选择左侧任务', { exact: true }).count(), 0)
    checks.push({ id: 'video-task-first-error-hides-false-zero', status: 'PASS' })

    taskMode = 'success'
    await page.getByRole('button', { name: '重试', exact: true }).click()
    await page.getByText('暂无评审任务', { exact: true }).waitFor()
    assert.equal(await page.getByText('待评分 0', { exact: true }).count(), 1)
    assert.equal(await page.getByText('已提交 0', { exact: true }).count(), 1)
    assert.equal(await page.getByText('请选择左侧任务', { exact: true }).count(), 1)
    checks.push({ id: 'video-task-success-empty-shows-real-zero', status: 'PASS' })
  })

  const manager = currentUser({ permissions: ['video:assign', 'video:confirm'] })
  let manageMode = 'error'
  await withAuthedPage(browser, manager, { width: 1280, height: 900 }, async ({ route, request, path: apiPath }) => {
    if (apiPath === '/video/reviews' && request.method() === 'GET') {
      if (manageMode === 'error') await fail(route, '视频评审列表加载失败')
      else await ok(route, pageResult([]))
      return true
    }
    if (request.method() === 'GET' && (apiPath === '/video/reviewer-candidates' || apiPath === '/video/reviewer-groups')) {
      await ok(route, [])
      return true
    }
    return false
  }, async (page) => {
    await page.goto(`${baseUrl}/videos`, { waitUntil: 'networkidle' })
    await page.locator('.data-panel').getByText('视频评审列表加载失败', { exact: true }).waitFor()
    for (const label of ['视频总数', '待评审', '评审中', '需复评', '已退回']) {
      assert.equal(await page.getByText(label, { exact: true }).count(), 0)
    }
    checks.push({ id: 'video-manage-first-error-hides-false-zero', status: 'PASS' })

    manageMode = 'success'
    await page.getByRole('button', { name: '重试', exact: true }).click()
    await page.getByText('视频总数', { exact: true }).waitFor()
    await page.getByText('暂无视频评审记录', { exact: true }).waitFor()
    checks.push({ id: 'video-manage-success-empty-shows-real-zero', status: 'PASS' })
  })
}

async function coldStartMustChangePwd(browser) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const page = await context.newPage()
  let businessCalls = 0
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const apiPath = new URL(request.url()).pathname.replace(/^\/api/, '')
    if (apiPath === '/auth/refresh' && request.method() === 'POST') {
      const user = currentUser({ permissions: ['student:view'], mustChangePwd: true })
      await ok(route, { accessToken: 'ws4-cold-token', expiresIn: 900, mustChangePwd: true, user })
      return
    }
    if (apiPath === '/auth/me') {
      await ok(route, currentUser({ permissions: ['student:view'], mustChangePwd: true }))
      return
    }
    if (apiPath === '/auth/captcha') {
      await ok(route, { captchaId: 'captcha', image: 'data:image/svg+xml,%3Csvg%3E%3C/svg%3E' })
      return
    }
    businessCalls += 1
    await fail(route, 'cold start must not mount business page')
  })
  try {
    await page.goto(`${baseUrl}/students`, { waitUntil: 'networkidle' })
    await page.waitForURL(/\/login(?:\?|$)/)
    assert.equal(await page.getByRole('heading', { name: '学生基本信息', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: '登录', exact: true }).isVisible(), true)
    assert.equal(businessCalls, 0)
    await page.screenshot({ path: path.join(outDir, 'cold-start-must-change-login.png'), fullPage: true })
    checks.push({ id: 'cold-start-must-change-redirects-before-mount', status: 'PASS' })
  } finally {
    await context.close()
  }
}

async function representativeNavigationAndDrawer(browser) {
  const user = currentUser({ permissions: ['student:view', 'student:edit'] })
  await withAuthedPage(browser, user, { width: 375, height: 812 }, async ({ route, request, path: apiPath }) => {
    if (apiPath === '/student' && request.method() === 'GET') {
      await ok(route, pageResult([]))
      return true
    }
    return false
  }, async (page) => {
    await page.goto(`${baseUrl}/students`, { waitUntil: 'networkidle' })
    const menuButton = page.getByRole('button', { name: '打开导航菜单', exact: true })
    await menuButton.click()
    const mobileDrawer = page.locator('.n-drawer:visible').filter({ hasText: '教师证书平台' }).last()
    await mobileDrawer.waitFor()
    assert.equal(await mobileDrawer.getByText('学生基本信息', { exact: true }).isVisible(), true)
    await page.keyboard.press('Escape')
    await mobileDrawer.waitFor({ state: 'hidden' })
    checks.push({ id: 'd1-mobile-navigation-opens', status: 'PASS' })

    await page.getByRole('button', { name: '新增学生', exact: true }).click()
    const drawer = page.locator('.n-drawer:visible').filter({ hasText: '新增学生' }).last()
    await drawer.waitFor()
    await waitFor(async () => {
      const currentBox = await drawer.boundingBox()
      return Boolean(currentBox && currentBox.x >= 0 && currentBox.x + currentBox.width <= 376)
    }, 'student drawer did not finish entering the viewport')
    const box = await drawer.boundingBox()
    assert(
      box && box.x >= 0 && box.y >= 0 && box.x + box.width <= 376 && box.y + box.height <= 813,
      `student drawer escaped viewport: ${JSON.stringify(box)}`
    )
    assert.equal(await drawer.getByRole('button', { name: '取消', exact: true }).isVisible(), true)
    assert.equal(await drawer.getByRole('button', { name: '保存', exact: true }).isVisible(), true)
    await page.screenshot({ path: path.join(outDir, 'd1-mobile-student-drawer.png'), fullPage: true })
    checks.push({ id: 'd1-mobile-drawer-contained', status: 'PASS' })
  })
}

async function publicNotFound(browser) {
  const context = await browser.newContext({ viewport: { width: 768, height: 1024 } })
  const page = await context.newPage()
  try {
    await page.goto(`${baseUrl}/ws4-gap-not-found`, { waitUntil: 'networkidle' })
    await page.getByText('页面未找到', { exact: true }).waitFor()
    assert.equal(await page.title(), '页面不存在 · 师范生考核与证书管理平台')
    checks.push({ id: 'd4-public-not-found-facade', status: 'PASS' })
  } finally {
    await context.close()
  }
}

await mkdir(outDir, { recursive: true })
const browser = await chromium.launch({ headless: true })
let failure = null
try {
  await materialOrderingAndBarrier(browser)
  await statsOrderingAndExportBinding(browser)
  await trainingCascadeBarrier(browser)
  await exemptionCascadeBarrier(browser)
  await videoInitialFailureSemantics(browser)
  await coldStartMustChangePwd(browser)
  await representativeNavigationAndDrawer(browser)
  await publicNotFound(browser)
} catch (error) {
  failure = error instanceof Error ? error.stack || error.message : String(error)
} finally {
  await browser.close()
  await writeFile(path.join(outDir, 'report.json'), `${JSON.stringify({
    generatedAt: new Date().toISOString(),
    baseUrl,
    status: failure ? 'FAIL' : 'PASS',
    checks,
    failure
  }, null, 2)}\n`, 'utf8')
}

if (failure) {
  console.error(failure)
  process.exit(1)
}
console.log(`PASS ${checks.length}/${checks.length}: WS-4 independent-review gap checks`)
