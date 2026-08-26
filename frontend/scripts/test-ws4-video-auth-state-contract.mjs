import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const readSource = (relativePath) => readFileSync(new URL(`../${relativePath}`, import.meta.url), 'utf8')

const tasks = readSource('src/views/video/components/MyTaskPanel.vue')
const manage = readSource('src/views/video/components/ManagePanel.vue')
const router = readSource('src/router/index.ts')

assert.match(tasks, /const hasLoadedSuccessfully = computed\(\(\) => Boolean\(lastSuccessfulQueryKey\.value\)\)/)
assert.match(tasks, /<n-tag v-if="hasLoadedSuccessfully"[^>]*>待评分/)
assert.match(tasks, /<n-card v-if="hasLoadedSuccessfully"[^>]*class="score-card">/)
assert.match(tasks, /<n-result v-if="initialLoadFailed"[^>]*title="评审任务加载失败"/)
assert.match(tasks, /以下仍显示上次成功加载的结果，暂不可提交评分/)

assert.match(manage, /const hasLoadedSuccessfully = computed\(\(\) => Boolean\(lastSuccessfulReviewQueryKey\.value\)\)/)
assert.match(manage, /<n-grid v-if="hasLoadedSuccessfully"[^>]*class="page-section">/)
assert.match(manage, /<n-alert v-if="reviewDataStale"/)
assert.match(manage, /:error="hasLoadedSuccessfully \? '' : loadError"/)

assert.match(
  router,
  /await userStore\.loadMe\(\)\s*if \(userStore\.mustChangePwd\) \{\s*return \{ name: 'login', query: \{ redirect: to\.fullPath \} \}\s*\}/s
)

console.log('WS-4 video/auth state contract: PASS')
