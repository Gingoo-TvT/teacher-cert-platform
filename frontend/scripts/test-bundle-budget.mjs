import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  analyzeBundle,
  DEFAULT_BUDGETS,
  evaluateBundle,
  GZIP_LEVEL
} from './check-bundle-budget.mjs'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const nginxConfig = await readFile(path.join(frontendRoot, 'nginx.conf'), 'utf8')
const nginxGzipLevels = [...nginxConfig.matchAll(/^\s*gzip_comp_level\s+([1-9]);\s*$/gm)]
assert.equal(nginxGzipLevels.length, 1, '生产 Nginx 必须且只能配置一个 gzip_comp_level')
assert.equal(
  Number(nginxGzipLevels[0][1]),
  GZIP_LEVEL,
  '生产 Nginx 与 bundle budget 必须使用相同 gzip level'
)

const report = await analyzeBundle()
assert.deepEqual(evaluateBundle(report), [], '当前构建应满足默认体积预算')

const forcedOverBudget = evaluateBundle(report, {
  ...DEFAULT_BUDGETS,
  entryGzipBytes: 1
})
assert(
  forcedOverBudget.some((item) => item.includes('入口关键路径 gzip')),
  '人为压低入口预算时必须失败'
)

const chartsLeak = structuredClone(report)
chartsLeak.routes.login.assets.push({
  file: 'assets/charts-negative-control.js',
  rawBytes: 1,
  gzipBytes: 1
})
assert(
  evaluateBundle(chartsLeak).some((item) => item.includes('login 关键路径提前加载')),
  '图表进入登录关键路径时必须失败'
)

console.log('PASS: bundle budget positive and negative controls')
