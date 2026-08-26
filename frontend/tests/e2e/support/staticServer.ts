import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { createServer, type Server } from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const FRONTEND_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const DIST_ROOT = path.join(FRONTEND_ROOT, 'dist')
const PORT = 18106

const contentTypes: Record<string, string> = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2'
}

export async function startStaticServer(): Promise<Server> {
  await requireFile(path.join(DIST_ROOT, 'index.html'))
  const server = createServer(async (request, response) => {
    try {
      const pathname = decodeURIComponent(new URL(request.url || '/', `http://127.0.0.1:${PORT}`).pathname)
      const requested = path.resolve(DIST_ROOT, `.${pathname}`)
      const file = requested.startsWith(`${DIST_ROOT}${path.sep}`) && await isFile(requested)
        ? requested
        : path.join(DIST_ROOT, 'index.html')
      response.statusCode = 200
      response.setHeader('Content-Type', contentTypes[path.extname(file)] || 'application/octet-stream')
      response.setHeader('Cache-Control', 'no-store')
      createReadStream(file).pipe(response)
    } catch (error) {
      response.statusCode = 500
      response.end(error instanceof Error ? error.message : String(error))
    }
  })
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject)
    server.listen(PORT, '127.0.0.1', () => resolve())
  })
  return server
}

export async function stopStaticServer(server: Server): Promise<void> {
  await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
}

async function isFile(file: string): Promise<boolean> {
  try {
    return (await stat(file)).isFile()
  } catch {
    return false
  }
}

async function requireFile(file: string): Promise<void> {
  if (!await isFile(file)) throw new Error(`Missing frontend build output: ${file}. Run npm run build first.`)
}
