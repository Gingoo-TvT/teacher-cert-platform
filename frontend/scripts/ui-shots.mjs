#!/usr/bin/env node

import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = path.resolve(SCRIPT_DIR, '../..')
const DEFAULT_CONFIG = path.join(SCRIPT_DIR, 'ui-routes.json')
const DEFAULT_OUT = path.join(REPO_ROOT, 'docs/ui-audit/d0-before')

function printUsage() {
  console.log(`Usage:
  node frontend/scripts/ui-shots.mjs --out docs/ui-audit/d0-before

Options:
  --base-url <url>       Frontend URL (default: UI_AUDIT_BASE_URL or http://127.0.0.1:5173)
  --api-base-url <url>   API URL (default: UI_AUDIT_API_BASE_URL or <base-url>/api)
  --config <path>        Route matrix (default: frontend/scripts/ui-routes.json)
  --out <path>           Screenshot/report directory
  --role <id[,id...]>    Limit authenticated roles
  --route <id[,id...]>   Limit routes
  --breakpoint <id>      Limit breakpoints
  --dry-run              Validate and print the matrix without opening a browser
  --help                 Show this help

Passwords are read only from each role's passwordEnv or UI_AUDIT_PASSWORD.
The application must already be running; this script never starts or stops services.`)
}

function readOptions(argv) {
  const options = {
    baseUrl: process.env.UI_AUDIT_BASE_URL || 'http://127.0.0.1:5173',
    apiBaseUrl: process.env.UI_AUDIT_API_BASE_URL || '',
    configPath: DEFAULT_CONFIG,
    outDir: DEFAULT_OUT,
    roleIds: [],
    routeIds: [],
    breakpointIds: [],
    dryRun: false,
    help: false
  }
  const valueFlags = new Map([
    ['--base-url', 'baseUrl'],
    ['--api-base-url', 'apiBaseUrl'],
    ['--config', 'configPath'],
    ['--out', 'outDir'],
    ['--role', 'roleIds'],
    ['--route', 'routeIds'],
    ['--breakpoint', 'breakpointIds']
  ])
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index]
    if (flag === '--dry-run') {
      options.dryRun = true
      continue
    }
    if (flag === '--help' || flag === '-h') {
      options.help = true
      continue
    }
    const key = valueFlags.get(flag)
    if (!key) throw new Error(`Unknown option: ${flag}`)
    const value = argv[index + 1]
    if (!value || value.startsWith('--')) throw new Error(`${flag} requires a value`)
    index += 1
    if (Array.isArray(options[key])) {
      options[key].push(...value.split(',').map((item) => item.trim()).filter(Boolean))
    } else {
      options[key] = value
    }
  }
  options.configPath = path.isAbsolute(options.configPath)
    ? options.configPath
    : path.resolve(REPO_ROOT, options.configPath)
  options.outDir = path.isAbsolute(options.outDir)
    ? options.outDir
    : path.resolve(REPO_ROOT, options.outDir)
  options.baseUrl = withoutTrailingSlash(options.baseUrl)
  options.apiBaseUrl = withoutTrailingSlash(
    options.apiBaseUrl || new URL('/api', `${options.baseUrl}/`).toString()
  )
  return options
}

function withoutTrailingSlash(value) {
  return String(value).replace(/\/+$/, '')
}

function requireArray(value, label) {
  if (!Array.isArray(value) || value.length === 0) throw new Error(`${label} must be a non-empty array`)
  return value
}

function assertUniqueIds(items, label) {
  const ids = items.map((item) => item?.id)
  if (ids.some((id) => typeof id !== 'string' || !id.trim())) throw new Error(`${label} contains an invalid id`)
  if (new Set(ids).size !== ids.length) throw new Error(`${label} contains duplicate ids`)
}

function validateConfig(config) {
  if (config?.schemaVersion !== 1) throw new Error('ui-routes schemaVersion must be 1')
  const breakpoints = requireArray(config.breakpoints, 'breakpoints')
  const publicRoutes = requireArray(config.publicRoutes, 'publicRoutes')
  const roles = requireArray(config.roles, 'roles')
  const authenticatedRoutes = requireArray(config.authenticatedRoutes, 'authenticatedRoutes')
  assertUniqueIds(breakpoints, 'breakpoints')
  assertUniqueIds(publicRoutes, 'publicRoutes')
  assertUniqueIds(roles, 'roles')
  assertUniqueIds(authenticatedRoutes, 'authenticatedRoutes')
  for (const breakpoint of breakpoints) {
    if (!Number.isInteger(breakpoint.width) || !Number.isInteger(breakpoint.height)
      || breakpoint.width <= 0 || breakpoint.height <= 0) {
      throw new Error(`Invalid viewport for breakpoint '${breakpoint.id}'`)
    }
  }
  for (const route of [...publicRoutes, ...authenticatedRoutes]) {
    if (typeof route.path !== 'string' || !route.path.startsWith('/')) {
      throw new Error(`Route '${route.id}' must use an absolute application path`)
    }
  }
  for (const role of roles) {
    if (!role.username || !role.passwordEnv) throw new Error(`Role '${role.id}' lacks username/passwordEnv`)
    const expectedRouteIds = requireArray(role.expectedRouteIds, `role '${role.id}' expectedRouteIds`)
    if (new Set(expectedRouteIds).size !== expectedRouteIds.length) {
      throw new Error(`Role '${role.id}' contains duplicate expectedRouteIds`)
    }
    const knownRouteIds = new Set(authenticatedRoutes.map((route) => route.id))
    const unknownRouteIds = expectedRouteIds.filter((routeId) => !knownRouteIds.has(routeId))
    if (unknownRouteIds.length) {
      throw new Error(`Role '${role.id}' references unknown route(s): ${unknownRouteIds.join(', ')}`)
    }
  }
  return config
}

function selectByIds(items, selectedIds, label) {
  if (selectedIds.length === 0) return items
  const selected = new Set(selectedIds)
  const missing = [...selected].filter((id) => !items.some((item) => item.id === id))
  if (missing.length) throw new Error(`Unknown ${label}: ${missing.join(', ')}`)
  return items.filter((item) => selected.has(item.id))
}

function routeAllowed(route, user) {
  if (route.studentOnly && user.userType !== 'STUDENT') return false
  const permissionAny = route.permissionAny || []
  return permissionAny.length === 0 || permissionAny.some((permission) => user.permissions.includes(permission))
}

function decodeCaptcha(image) {
  const comma = image.indexOf(',')
  if (comma < 0) throw new Error('Captcha image is not a data URI')
  const header = image.slice(0, comma)
  const payload = image.slice(comma + 1)
  const svg = header.includes(';base64')
    ? Buffer.from(payload, 'base64').toString('utf8')
    : decodeURIComponent(payload)
  const match = svg.match(/<text\b[^>]*>([^<]+)<\/text>/i)
  if (!match) throw new Error('Captcha text could not be read from the demo SVG')
  return match[1].trim()
}

async function apiCall(requestContext, apiBaseUrl, endpoint, init = {}) {
  const response = await requestContext.fetch(`${apiBaseUrl}${endpoint}`, init)
  const text = await response.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    throw new Error(`${endpoint} returned non-JSON HTTP ${response.status()}`)
  }
  if (!response.ok() || body?.code !== 0) {
    throw new Error(`${endpoint} failed: HTTP ${response.status()}, ${body?.msg || 'unknown error'}`)
  }
  return body.data
}

async function loginRole(requestContext, apiBaseUrl, role) {
  const password = process.env[role.passwordEnv] || process.env.UI_AUDIT_PASSWORD
  if (!password) {
    throw new Error(`Missing ${role.passwordEnv} (or UI_AUDIT_PASSWORD) for role '${role.id}'`)
  }
  const captcha = await apiCall(requestContext, apiBaseUrl, '/auth/captcha')
  const result = await apiCall(requestContext, apiBaseUrl, '/auth/login', {
    method: 'POST',
    data: {
      username: role.username,
      password,
      captchaId: captcha.captchaId,
      captchaCode: decodeCaptcha(captcha.image)
    }
  })
  if (result.mustChangePwd || result.user?.mustChangePwd) {
    throw new Error(`Role '${role.id}' requires an initial password change; prepare the demo account before UI audit`)
  }
  return result
}

function normalizePath(value) {
  if (value !== '/' && value.endsWith('/')) return value.slice(0, -1)
  return value
}

function screenshotRelativePath(actorId, routeId, breakpointId) {
  return path.join(actorId, `${routeId}-${breakpointId}.png`)
}

async function inspectRoute({ browser, storageState, baseUrl, outDir, actorId, route, breakpoint }) {
  const context = await browser.newContext({
    storageState,
    viewport: { width: breakpoint.width, height: breakpoint.height }
  })
  const page = await context.newPage()
  const consoleIssues = []
  const pageErrors = []
  page.on('console', (message) => {
    if (message.type() === 'warning' || message.type() === 'error') {
      consoleIssues.push({ type: message.type(), text: message.text() })
    }
  })
  page.on('pageerror', (error) => pageErrors.push(error.message))

  const targetUrl = new URL(route.path, `${baseUrl}/`).toString()
  const relativeScreenshot = screenshotRelativePath(actorId, route.id, breakpoint.id)
  const absoluteScreenshot = path.join(outDir, relativeScreenshot)
  const result = {
    actor: actorId,
    routeId: route.id,
    routePath: route.path,
    breakpoint: breakpoint.id,
    viewport: { width: breakpoint.width, height: breakpoint.height },
    screenshot: relativeScreenshot.replaceAll('\\', '/'),
    status: 'PASS',
    failures: []
  }

  try {
    const response = await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30000 })
    await page.waitForLoadState('networkidle', { timeout: 15000 })
    await page.evaluate(async () => {
      if (document.fonts?.ready) await document.fonts.ready
      await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    })
    const actualPath = normalizePath(new URL(page.url()).pathname)
    const expectedPath = normalizePath(new URL(targetUrl).pathname)
    if (actualPath !== expectedPath) {
      result.failures.push(`redirected to ${actualPath}`)
    }
    if (response && response.status() >= 400) {
      result.failures.push(`navigation returned HTTP ${response.status()}`)
    }
    const overflow = await page.evaluate(() => {
      const root = document.scrollingElement || document.documentElement
      return { scrollWidth: root.scrollWidth, clientWidth: root.clientWidth }
    })
    result.documentWidth = overflow
    if (overflow.scrollWidth > overflow.clientWidth + 1) {
      result.failures.push(`horizontal overflow ${overflow.scrollWidth}px > ${overflow.clientWidth}px + 1px`)
    }
    const layout = await page.evaluate(() => {
      const sider = document.querySelector('.app-sider')
      if (!(sider instanceof HTMLElement)) return { siderVisible: false, siderWidth: 0 }
      const rect = sider.getBoundingClientRect()
      const style = window.getComputedStyle(sider)
      return {
        siderVisible: style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 1,
        siderWidth: Math.round(rect.width)
      }
    })
    result.layout = layout
    if (breakpoint.width <= 720 && layout.siderVisible) {
      result.failures.push(`mobile viewport retains ${layout.siderWidth}px fixed sidebar`)
    }
    await mkdir(path.dirname(absoluteScreenshot), { recursive: true })
    await page.screenshot({ path: absoluteScreenshot, fullPage: true })
    if (consoleIssues.length) result.failures.push(`${consoleIssues.length} console warning/error message(s)`)
    if (pageErrors.length) result.failures.push(`${pageErrors.length} uncaught page error(s)`)
    result.consoleIssues = [...consoleIssues]
    result.pageErrors = [...pageErrors]
  } catch (error) {
    result.failures.push(error instanceof Error ? error.message : String(error))
  } finally {
    result.status = result.failures.length ? 'FAIL' : 'PASS'
    await context.close()
  }
  return result
}

async function prepareOutputDirectory(outDir) {
  await mkdir(outDir, { recursive: true })
  const entries = await readdir(outDir)
  if (entries.length) {
    throw new Error(`Output directory must be empty to avoid stale evidence: ${outDir}`)
  }
}

async function run() {
  const options = readOptions(process.argv.slice(2))
  if (options.help) {
    printUsage()
    return
  }
  const config = validateConfig(JSON.parse(await readFile(options.configPath, 'utf8')))
  const roles = selectByIds(config.roles, options.roleIds, 'role')
  const breakpoints = selectByIds(config.breakpoints, options.breakpointIds, 'breakpoint')
  const allRoutes = [...config.publicRoutes, ...config.authenticatedRoutes]
  const selectedRoutes = selectByIds(allRoutes, options.routeIds, 'route')
  const publicRoutes = selectedRoutes.filter((route) => config.publicRoutes.some((item) => item.id === route.id))
  const authenticatedRoutes = selectedRoutes.filter((route) => config.authenticatedRoutes.some((item) => item.id === route.id))

  if (options.dryRun) {
    console.log(JSON.stringify({
      config: options.configPath,
      out: options.outDir,
      baseUrl: options.baseUrl,
      apiBaseUrl: options.apiBaseUrl,
      breakpoints: breakpoints.map((item) => item.id),
      publicRoutes: publicRoutes.map((item) => item.id),
      roles: roles.map((item) => ({
        id: item.id,
        expectedRoutes: item.expectedRouteIds.filter((routeId) => authenticatedRoutes.some((route) => route.id === routeId))
      })),
      authenticatedRoutes: authenticatedRoutes.map((item) => item.id)
    }, null, 2))
    return
  }

  let chromium
  let playwrightRequest
  try {
    ;({ chromium, request: playwrightRequest } = await import('playwright'))
  } catch {
    throw new Error('Playwright is not installed; run npm --prefix frontend install before this audit')
  }

  await prepareOutputDirectory(options.outDir)
  const report = {
    schemaVersion: 1,
    startedAt: new Date().toISOString(),
    baseUrl: options.baseUrl,
    apiBaseUrl: options.apiBaseUrl,
    config: path.relative(REPO_ROOT, options.configPath).replaceAll('\\', '/'),
    results: [],
    roleSummaries: [],
    failures: []
  }
  const browser = await chromium.launch({ headless: true })
  try {
    for (const route of publicRoutes) {
      for (const breakpoint of breakpoints) {
        report.results.push(await inspectRoute({
          browser,
          storageState: undefined,
          baseUrl: options.baseUrl,
          outDir: options.outDir,
          actorId: 'public',
          route,
          breakpoint
        }))
      }
    }

    for (const role of roles) {
      const authContext = await playwrightRequest.newContext()
      try {
        const loginResult = await loginRole(authContext, options.apiBaseUrl, role)
        const storageState = await authContext.storageState()
        const expectedRoutes = authenticatedRoutes.filter((route) => role.expectedRouteIds.includes(route.id))
        const permittedRoutes = authenticatedRoutes.filter((route) => routeAllowed(route, loginResult.user))
        const expectedIds = new Set(expectedRoutes.map((route) => route.id))
        const permittedIds = new Set(permittedRoutes.map((route) => route.id))
        const missingRoutes = [...expectedIds].filter((routeId) => !permittedIds.has(routeId))
        const unexpectedRoutes = [...permittedIds].filter((routeId) => !expectedIds.has(routeId))
        const matrixIssues = [
          ...(missingRoutes.length ? [`missing expected route permission: ${missingRoutes.join(', ')}`] : []),
          ...(unexpectedRoutes.length ? [`unexpected route permission: ${unexpectedRoutes.join(', ')}`] : [])
        ]
        report.roleSummaries.push({
          id: role.id,
          username: role.username,
          userType: loginResult.user.userType,
          roles: loginResult.user.roles,
          permissionCount: loginResult.user.permissions.length,
          expectedRouteCount: expectedRoutes.length,
          permittedRouteCount: permittedRoutes.length,
          matrixIssues,
          status: matrixIssues.length ? 'FAIL' : 'PASS'
        })
        for (const issue of matrixIssues) report.failures.push(`role ${role.id}: ${issue}`)
        for (const route of expectedRoutes) {
          for (const breakpoint of breakpoints) {
            report.results.push(await inspectRoute({
              browser,
              storageState,
              baseUrl: options.baseUrl,
              outDir: options.outDir,
              actorId: role.id,
              route,
              breakpoint
            }))
          }
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error)
        report.roleSummaries.push({ id: role.id, username: role.username, status: 'FAIL', error: message })
        report.failures.push(`role ${role.id}: ${message}`)
      } finally {
        await authContext.dispose()
      }
    }
  } finally {
    await browser.close()
  }

  report.failures.push(...report.results
    .filter((result) => result.status === 'FAIL')
    .map((result) => `${result.actor}/${result.routeId}/${result.breakpoint}: ${result.failures.join('; ')}`))
  report.completedAt = new Date().toISOString()
  report.status = report.failures.length ? 'FAIL' : 'PASS'
  report.summary = {
    checks: report.results.length,
    passed: report.results.filter((result) => result.status === 'PASS').length,
    failed: report.results.filter((result) => result.status === 'FAIL').length
  }
  const reportPath = path.join(options.outDir, 'report.json')
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
  console.log(`${report.status}: ${report.summary.passed}/${report.summary.checks} page-breakpoint checks passed`)
  console.log(`Report: ${reportPath}`)
  if (report.status !== 'PASS') process.exitCode = 1
}

run().catch((error) => {
  console.error(`UI audit failed: ${error instanceof Error ? error.message : String(error)}`)
  process.exitCode = 1
})
