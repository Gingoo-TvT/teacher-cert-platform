import assert from 'node:assert/strict'
import { LatestRequestGuard } from '../src/views/dashboardLatestRequest.js'

const guard = new LatestRequestGuard()
const first = guard.begin()
const second = guard.begin()

assert.equal(guard.isCurrent(first), false)
assert.equal(guard.isCurrent(second), true)

const responseOrder = []
let releaseFirst
let releaseSecond
const firstResponse = new Promise((resolve) => {
  releaseFirst = resolve
})
const secondResponse = new Promise((resolve) => {
  releaseSecond = resolve
})

const firstLoad = (async () => {
  await firstResponse
  if (guard.isCurrent(first)) responseOrder.push('first')
})()
const secondLoad = (async () => {
  await secondResponse
  if (guard.isCurrent(second)) responseOrder.push('second')
})()

releaseSecond()
await secondLoad
releaseFirst()
await firstLoad

assert.deepEqual(responseOrder, ['second'])
console.log('dashboard latest request contract: PASS')
