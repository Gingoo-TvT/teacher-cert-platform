import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const defaultDistDir = path.join(frontendRoot, 'dist')

export const GZIP_LEVEL = 6

export const DEFAULT_BUDGETS = Object.freeze({
  entryGzipBytes: 135 * 1024,
  loginGzipBytes: 210 * 1024,
  managementGzipBytes: 430 * 1024,
  chartsGzipBytes: 190 * 1024,
  largestCriticalChunkGzipBytes: 96 * 1024,
  totalJavaScriptGzipBytes: 850 * 1024
})

const routeRoots = Object.freeze({
  entry: ['index.html'],
  login: ['index.html', 'src/views/LoginView.vue'],
  management: ['index.html', 'src/layouts/MainLayout.vue', 'src/views/student/StudentManageView.vue']
})

const chartConsumers = [
  'src/views/DashboardView.vue',
  'src/views/stats/StatsReportView.vue'
]
const chartComponent = 'src/components/ChartBox.vue'

export async function analyzeBundle({ distDir = defaultDistDir } = {}) {
  const manifestPath = path.join(distDir, '.vite', 'manifest.json')
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'))
  const assetCache = new Map()

  async function asset(file) {
    if (!assetCache.has(file)) {
      const bytes = await readFile(path.join(distDir, file))
      assetCache.set(file, {
        file,
        rawBytes: bytes.length,
        gzipBytes: gzipSync(bytes, { level: GZIP_LEVEL }).length
      })
    }
    return assetCache.get(file)
  }

  async function routeReport(roots) {
    const files = manifestClosure(manifest, roots)
    const assets = await Promise.all(files.filter((file) => file.endsWith('.js')).map(asset))
    return summarize(assets)
  }

  const routes = Object.fromEntries(
    await Promise.all(
      Object.entries(routeRoots).map(async ([name, roots]) => [name, await routeReport(roots)])
    )
  )
  const allJavaScriptFiles = (await readdir(path.join(distDir, 'assets')))
    .filter((file) => file.endsWith('.js'))
    .map((file) => `assets/${file}`)
  const allJavaScript = summarize(await Promise.all(allJavaScriptFiles.map(asset)))
  const charts = allJavaScript.assets.filter((item) => path.basename(item.file).startsWith('charts-'))
  const chartConsumerState = Object.fromEntries(chartConsumers.map((key) => [
    key,
    Boolean(manifest[key]?.dynamicImports?.includes(chartComponent))
  ]))

  return {
    routes,
    allJavaScript,
    charts,
    chartComponentPresent: Boolean(manifest[chartComponent]),
    chartConsumerState,
    monolithicNaiveChunks: allJavaScript.assets.filter((item) => /^naive-/i.test(path.basename(item.file)))
  }
}

export function evaluateBundle(report, budgets = DEFAULT_BUDGETS) {
  const errors = []
  checkLimit(errors, '入口关键路径 gzip', report.routes.entry.gzipBytes, budgets.entryGzipBytes)
  checkLimit(errors, '登录关键路径 gzip', report.routes.login.gzipBytes, budgets.loginGzipBytes)
  checkLimit(errors, '普通管理关键路径 gzip', report.routes.management.gzipBytes, budgets.managementGzipBytes)
  checkLimit(errors, '全部 JavaScript gzip', report.allJavaScript.gzipBytes, budgets.totalJavaScriptGzipBytes)

  if (report.charts.length !== 1) {
    errors.push(`应只有一个 charts chunk，实际 ${report.charts.length}`)
  } else {
    checkLimit(errors, 'charts chunk gzip', report.charts[0].gzipBytes, budgets.chartsGzipBytes)
  }
  if (!report.chartComponentPresent) {
    errors.push('构建 manifest 缺少异步 ChartBox')
  }
  for (const [consumer, deferred] of Object.entries(report.chartConsumerState)) {
    if (!deferred) errors.push(`${consumer} 未通过 dynamic import 延迟 ChartBox`)
  }
  for (const routeName of ['entry', 'login', 'management']) {
    const route = report.routes[routeName]
    const leaked = route.assets.find((item) => /(?:charts|echarts)/i.test(path.basename(item.file)))
    if (leaked) errors.push(`${routeName} 关键路径提前加载 ${leaked.file}`)
    const largest = route.assets.reduce((max, item) => Math.max(max, item.gzipBytes), 0)
    checkLimit(errors, `${routeName} 最大单 chunk gzip`, largest, budgets.largestCriticalChunkGzipBytes)
  }
  if (report.monolithicNaiveChunks.length) {
    errors.push(`检测到整库 Naive chunk：${report.monolithicNaiveChunks.map((item) => item.file).join(', ')}`)
  }
  return errors
}

export function formatBundleReport(report, budgets = DEFAULT_BUDGETS) {
  const lines = [
    metric('入口关键路径', report.routes.entry.gzipBytes, budgets.entryGzipBytes),
    metric('登录关键路径', report.routes.login.gzipBytes, budgets.loginGzipBytes),
    metric('普通管理关键路径', report.routes.management.gzipBytes, budgets.managementGzipBytes),
    metric('全部 JavaScript', report.allJavaScript.gzipBytes, budgets.totalJavaScriptGzipBytes)
  ]
  if (report.charts[0]) lines.push(metric('charts chunk', report.charts[0].gzipBytes, budgets.chartsGzipBytes))
  return lines.join('\n')
}

function manifestClosure(manifest, roots) {
  const pending = [...roots]
  const seenKeys = new Set()
  const files = new Set()
  while (pending.length) {
    const key = pending.pop()
    if (seenKeys.has(key)) continue
    const entry = manifest[key]
    if (!entry) throw new Error(`构建 manifest 缺少 ${key}`)
    seenKeys.add(key)
    if (entry.file) files.add(entry.file)
    for (const imported of entry.imports || []) pending.push(imported)
  }
  return [...files]
}

function summarize(assets) {
  return {
    assets,
    rawBytes: assets.reduce((sum, item) => sum + item.rawBytes, 0),
    gzipBytes: assets.reduce((sum, item) => sum + item.gzipBytes, 0)
  }
}

function checkLimit(errors, label, actual, limit) {
  if (actual > limit) errors.push(`${label} ${formatKiB(actual)} 超过预算 ${formatKiB(limit)}`)
}

function metric(label, actual, limit) {
  return `${label}: ${formatKiB(actual)} / ${formatKiB(limit)}`
}

function formatKiB(bytes) {
  return `${(bytes / 1024).toFixed(1)} KiB`
}

if (path.resolve(process.argv[1] || '') === fileURLToPath(import.meta.url)) {
  try {
    const report = await analyzeBundle()
    const errors = evaluateBundle(report)
    console.log(formatBundleReport(report))
    if (errors.length) {
      for (const error of errors) console.error(`BUDGET ERROR: ${error}`)
      process.exitCode = 1
    } else {
      console.log('PASS: WS-12 bundle budget')
    }
  } catch (error) {
    console.error(`BUDGET ERROR: ${error instanceof Error ? error.message : String(error)}`)
    process.exitCode = 1
  }
}
