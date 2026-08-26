import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const frontendRoot = fileURLToPath(new URL('../', import.meta.url))
const repoRoot = fileURLToPath(new URL('../../', import.meta.url))
const readFrontend = (path) => readFileSync(new URL(path, `file:///${frontendRoot.replaceAll('\\', '/')}/`), 'utf8')
const readRepo = (path) => readFileSync(new URL(path, `file:///${repoRoot.replaceAll('\\', '/')}/`), 'utf8')

const nginx = readFrontend('nginx.conf')
const compose = readRepo('docker-compose.yml')
const envExample = readRepo('.env.example')
const dockerfile = readFrontend('Dockerfile')
const match = nginx.match(/add_header Content-Security-Policy "([^"]+)" always;/)
assert.ok(match, 'production nginx must emit a CSP header')

const directives = new Map(
  match[1].split(';').map((entry) => entry.trim()).filter(Boolean).map((entry) => {
    const [name, ...sources] = entry.split(/\s+/)
    return [name, sources]
  })
)
const sources = (name) => directives.get(name) || []

assert.deepEqual(sources('default-src'), ["'self'"])
assert.deepEqual(sources('base-uri'), ["'self'"])
assert.deepEqual(sources('script-src'), ["'self'"])
assert.deepEqual(sources('object-src'), ["'self'"])
assert.deepEqual(sources('frame-src'), ["'self'"])
assert.deepEqual(sources('img-src'), ["'self'", 'data:'])
assert.deepEqual(sources('media-src'), ["'self'", 'blob:'])
assert.deepEqual(sources('worker-src'), ["'self'"])
assert.deepEqual(sources('connect-src'), ["'self'", '${MINIO_PUBLIC_ENDPOINT}'])
assert.ok(sources('style-src').includes("'unsafe-inline'"), 'dynamic component styles need the sole inline exception')
assert.ok(!sources('script-src').includes("'unsafe-inline'"))
assert.ok(!match[1].includes("'unsafe-eval'"))
assert.ok(!match[1].split(/\s+/).includes('*'))
assert.equal((match[1].match(/\$\{MINIO_PUBLIC_ENDPOINT\}/g) || []).length, 1)

assert.match(
  compose,
  /frontend:[\s\S]*?MINIO_PUBLIC_ENDPOINT: \$\{MINIO_PUBLIC_ENDPOINT:\?MINIO_PUBLIC_ENDPOINT must be set\}/
)
assert.match(envExample, /^MINIO_PUBLIC_ENDPOINT=https:\/\/[^\s/]+\/?$/m)

const normalizedCompose = compose.replaceAll('\r\n', '\n')
const serviceBlock = (name) => {
  const marker = `  ${name}:\n`
  const start = normalizedCompose.indexOf(marker)
  assert.notEqual(start, -1, `compose service ${name} must exist`)
  const tail = normalizedCompose.slice(start + marker.length)
  const nextService = tail.search(/^  [a-zA-Z0-9_-]+:\n/m)
  return nextService === -1 ? tail : tail.slice(0, nextService)
}

const backend = serviceBlock('backend')
const minio = serviceBlock('minio')
const frontend = serviceBlock('frontend')
const portEntries = (block, serviceName) => {
  const marker = '    ports:\n'
  const start = block.indexOf(marker)
  assert.notEqual(start, -1, `${serviceName} must declare ports`)
  const tail = block.slice(start + marker.length)
  const nextKey = tail.search(/^    [a-zA-Z0-9_-]+:/m)
  const body = nextKey === -1 ? tail : tail.slice(0, nextKey)
  return body.split('\n').map((line) => line.trim()).filter(Boolean)
}

assert.doesNotMatch(backend, /^    ports:/m, 'backend must only be reachable through the internal proxy network')
assert.deepEqual(portEntries(minio, 'minio'), [
  '- "127.0.0.1:${MINIO_API_PORT:-9000}:9000"',
  '- "127.0.0.1:${MINIO_CONSOLE_PORT:-9001}:9001"'
])
assert.match(frontend, /FRONTEND_HTTPS_PORT: \$\{FRONTEND_HTTPS_PORT:-443\}/)
assert.deepEqual(portEntries(frontend, 'frontend'), [
  '- "${FRONTEND_PORT:-80}:8080"',
  '- "${FRONTEND_HTTPS_PORT:-443}:8443"'
])
assert.doesNotMatch(normalizedCompose, /\$\{BACKEND_PORT(?::-[^}]*)?\}/)
assert.doesNotMatch(envExample, /^BACKEND_PORT=/m)
assert.match(envExample, /^FRONTEND_HTTPS_PORT=443$/m)
assert.match(dockerfile, /^ENV FRONTEND_HTTPS_PORT=443$/m)
assert.match(
  nginx,
  /return 301 https:\/\/\$\{TLS_SERVER_NAME\}:\$\{FRONTEND_HTTPS_PORT\}\$request_uri;/
)
assert.doesNotMatch(nginx, /return 301 https:\/\/\$\{TLS_SERVER_NAME\}\$request_uri;/)

const renderRedirect = (port) => nginx
  .replaceAll('${TLS_SERVER_NAME}', 'ws05.test')
  .replaceAll('${FRONTEND_HTTPS_PORT}', port)
  .replaceAll('$request_uri', '/business')
assert.match(renderRedirect('443'), /return 301 https:\/\/ws05\.test:443\/business;/)
assert.match(renderRedirect('18443'), /return 301 https:\/\/ws05\.test:18443\/business;/)

console.log('WS-5 edge contract: PASS')
