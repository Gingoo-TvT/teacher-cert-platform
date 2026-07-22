import {
  completeVideoUpload,
  initVideoUpload,
  mergeVideoUpload,
  uploadVideoChunk,
  type VideoPresignedPart,
  type VideoReview,
  type VideoUploadedPart,
  type VideoUploadInitPayload
} from '@/api/video'

const FINGERPRINT_CHUNK_SIZE = 8 * 1024 * 1024
const MULTIPART_CONCURRENCY = 5
const PRESIGN_FORBIDDEN_REFRESH_LIMIT = 2
const PRESIGN_STAGNANT_REFRESH_LIMIT = 3
const PRESIGN_EXPIRY_SKEW_MS = 5_000
let fingerprintRequestSequence = 0

interface FingerprintWorkerResponse {
  requestId: string
  ok: boolean
  fingerprint?: string
  legacyFileHash?: string
  error?: string
}

export interface VideoTransferProgress {
  completedParts: number
  totalParts: number
  completedBytes: number
  totalBytes: number
  percentage: number
}

export interface UploadVideoFileOptions {
  file: File
  studentId: string
  assessmentYear: string
  durationSeconds?: number | null
  chunkSize: number
  signal?: AbortSignal
  onInitPending?: (pending: boolean) => void
  shouldAbandon?: () => boolean
  onSession?: (uploadId: string) => void
  onProgress?: (progress: VideoTransferProgress) => void
}

export interface UploadVideoFileResult {
  instantHit: boolean
  review: VideoReview | null
  validationMessage?: string | null
}

export interface FileFingerprint {
  fingerprint: string
  legacyFileHash: string
}

export interface PresignedMultipartOptions {
  file: File
  partSize: number
  parts: VideoPresignedPart[]
  uploadedParts: VideoUploadedPart[]
  concurrency?: number
  signal?: AbortSignal
  onProgress?: (progress: VideoTransferProgress) => void
}

export interface CompletedPart {
  partNumber: number
  etag: string
}

export type PresignedUrlExpiryReason = 'local-expiry' | 'http-403'

export class PresignedUrlExpiredError extends Error {
  constructor(
    public readonly partNumber: number,
    public readonly reason: PresignedUrlExpiryReason
  ) {
    super(`第 ${partNumber} 个分片的直传地址已过期`)
    this.name = 'PresignedUrlExpiredError'
  }
}

interface PresignedRefreshState {
  forbiddenRefreshes: number
  bestCompletedParts: number
  stagnantRefreshes: number
}

export interface PresignedRefreshOptions<TSession, TResult> {
  initialSession: TSession
  signal?: AbortSignal
  upload: (session: TSession) => Promise<TResult>
  refresh: () => Promise<TSession>
  completedPartCount: (session: TSession) => number
}

export interface PresignedRefreshResult<TSession, TResult> {
  session: TSession
  result: TResult
}

export async function uploadWithPresignedRefresh<TSession, TResult>(
  options: PresignedRefreshOptions<TSession, TResult>
): Promise<PresignedRefreshResult<TSession, TResult>> {
  let session = options.initialSession
  let state: PresignedRefreshState = {
    forbiddenRefreshes: 0,
    bestCompletedParts: normalizeCompletedPartCount(options.completedPartCount(session)),
    stagnantRefreshes: 0
  }

  while (true) {
    throwIfAborted(options.signal)
    try {
      return { session, result: await options.upload(session) }
    } catch (error) {
      if (!(error instanceof PresignedUrlExpiredError)) throw error
      const reserved = reservePresignedRefresh(state, error.reason)
      if (!reserved) throw error

      throwIfAborted(options.signal)
      const refreshed = await options.refresh()
      throwIfAborted(options.signal)
      state = recordPresignedRefreshProgress(
        reserved,
        normalizeCompletedPartCount(options.completedPartCount(refreshed))
      )
      if (state.stagnantRefreshes > PRESIGN_STAGNANT_REFRESH_LIMIT) {
        throw new Error('直传地址连续刷新后上传仍无进展，请稍后重新发起上传')
      }
      session = refreshed
    }
  }
}

export async function uploadVideoFile(options: UploadVideoFileOptions): Promise<UploadVideoFileResult> {
  if (options.file.size <= 0) throw new Error('不能上传空视频文件')
  if (!Number.isSafeInteger(options.chunkSize) || options.chunkSize <= 0) {
    throw new Error('视频分片大小无效')
  }
  // 后端沿用 fileMd5 字段名；实际值是 Worker 生成的 SHA-256 分片树指纹。
  throwIfAborted(options.signal)
  const fileFingerprint = await fingerprintVideoWithLegacy(options.file, options.signal)
  const fileMd5 = fileFingerprint.fingerprint
  const initPayload: VideoUploadInitPayload = {
    studentId: options.studentId,
    assessmentYear: options.assessmentYear,
    fileMd5,
    legacyFileHash: fileFingerprint.legacyFileHash,
    fileName: options.file.name,
    contentType: options.file.type || 'video/mp4',
    size: options.file.size,
    chunkSize: options.chunkSize,
    durationSeconds: options.durationSeconds
  }
  let response
  options.onInitPending?.(true)
  try {
    response = await initVideoUpload(initPayload, options.signal)
  } catch (error) {
    if (!options.shouldAbandon?.()) throw error
    try {
      response = await initVideoUpload(initPayload)
    } catch (recoveryError) {
      const detail = recoveryError instanceof Error ? `：${recoveryError.message}` : ''
      throw new Error(`无法确认服务端上传会话是否已取消，请重试${detail}`)
    }
  } finally {
    options.onInitPending?.(false)
  }
  let session = response.data
  if (session.instantHit) {
    options.onProgress?.(completeProgress(options.file.size))
    return {
      instantHit: true,
      review: null,
      validationMessage: session.validationMessage
    }
  }

  let uploadId = session.uploadId
  if (!uploadId) throw new Error('服务端未返回上传会话，请重新选择文件后再试')
  options.onSession?.(uploadId)

  let review: VideoReview
  if (session.uploadMode === 'PRESIGNED_MULTIPART') {
    const transferred = await uploadWithPresignedRefresh({
      initialSession: session,
      signal: options.signal,
      upload: (current) => uploadPresignedMultipart({
        file: options.file,
        partSize: current.partSize,
        parts: current.parts ?? [],
        uploadedParts: current.uploadedParts ?? [],
        concurrency: MULTIPART_CONCURRENCY,
        signal: options.signal,
        onProgress: options.onProgress
      }),
      refresh: async () => {
        const refreshed = (await initVideoUpload(initPayload, options.signal)).data
        if (refreshed.instantHit || refreshed.uploadMode !== 'PRESIGNED_MULTIPART' || !refreshed.uploadId) {
          throw new Error('刷新直传地址时上传会话发生变化，请重新选择文件')
        }
        uploadId = refreshed.uploadId
        options.onSession?.(uploadId)
        return refreshed
      },
      completedPartCount: (current) => current.uploadedParts?.length ?? 0
    })
    session = transferred.session
    if (!session.uploadId) throw new Error('服务端未返回上传会话，请重新选择文件后再试')
    uploadId = session.uploadId
    const completed = await completeVideoUpload({
      uploadId,
      durationSeconds: options.durationSeconds,
      parts: transferred.result
    }, options.signal)
    review = completed.data
  } else if (session.uploadMode === 'SERVER_CHUNK') {
    review = await uploadThroughServer({
      file: options.file,
      uploadId,
      chunkSize: options.chunkSize,
      uploadedChunks: session.uploadedChunks ?? [],
      durationSeconds: options.durationSeconds,
      signal: options.signal,
      onProgress: options.onProgress
    })
  } else {
    throw new Error('服务端返回了不支持的上传模式，请刷新页面后重试')
  }

  options.onProgress?.(completeProgress(options.file.size))
  return {
    instantHit: false,
    review,
    validationMessage: review.validationMessage
  }
}

export function fingerprintVideo(blob: Blob, signal?: AbortSignal): Promise<string> {
  return fingerprintVideoWithLegacy(blob, signal).then((result) => result.fingerprint)
}

export function fingerprintVideoWithLegacy(blob: Blob, signal?: AbortSignal): Promise<FileFingerprint> {
  const requestId = `${Date.now()}-${fingerprintRequestSequence += 1}`
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(abortError())
      return
    }
    const worker = new Worker(new URL('../workers/videoFingerprint.worker.ts', import.meta.url), { type: 'module' })
    const finish = () => {
      signal?.removeEventListener('abort', onAbort)
      worker.terminate()
    }
    const onAbort = () => {
      finish()
      reject(abortError())
    }
    signal?.addEventListener('abort', onAbort, { once: true })

    worker.onmessage = (event: MessageEvent<unknown>) => {
      if (!isFingerprintResponse(event.data) || event.data.requestId !== requestId) return
      finish()
      if (event.data.ok && event.data.fingerprint && event.data.legacyFileHash) {
        resolve({ fingerprint: event.data.fingerprint, legacyFileHash: event.data.legacyFileHash })
      } else {
        reject(new Error(event.data.error || '视频指纹计算失败'))
      }
    }
    worker.onerror = (event) => {
      finish()
      reject(new Error(event.message || '视频指纹 Worker 运行失败'))
    }
    worker.postMessage({ requestId, blob, chunkSize: FINGERPRINT_CHUNK_SIZE })
  })
}

export const fingerprintFile = fingerprintVideo

export async function uploadPresignedMultipart(options: PresignedMultipartOptions): Promise<CompletedPart[]> {
  const { file, partSize } = options
  throwIfAborted(options.signal)
  if (!Number.isSafeInteger(partSize) || partSize <= 0) {
    throw new Error('服务端返回的分片大小无效，请重新发起上传')
  }
  if (file.size <= 0) throw new Error('不能上传空视频文件')

  const totalParts = Math.ceil(file.size / partSize)
  const completed = collectUploadedParts(options.uploadedParts, totalParts, file.size, partSize)
  const urls = collectPresignedParts(options.parts, totalParts)
  const pending: VideoPresignedPart[] = []
  for (let partNumber = 1; partNumber <= totalParts; partNumber += 1) {
    if (completed.has(partNumber)) continue
    const descriptor = urls.get(partNumber)
    if (!descriptor) throw new Error(`服务端未返回第 ${partNumber} 个分片的上传地址，请重新发起上传`)
    pending.push(descriptor)
  }

  reportMultipartProgress(completed, totalParts, file.size, partSize, options.onProgress)
  const concurrency = normalizeConcurrency(options.concurrency, pending.length)
  const controller = new AbortController()
  const abortFromOutside = () => controller.abort(options.signal?.reason)
  options.signal?.addEventListener('abort', abortFromOutside, { once: true })
  const state: { cursor: number; error: Error | null } = { cursor: 0, error: null }

  const uploadNext = async () => {
    while (!state.error) {
      const current = state.cursor
      state.cursor += 1
      if (current >= pending.length) return
      const descriptor = pending[current]
      try {
        if (isExpiring(descriptor.expiresAt)) {
          throw new PresignedUrlExpiredError(descriptor.partNumber, 'local-expiry')
        }
        const start = (descriptor.partNumber - 1) * partSize
        const end = Math.min(start + partSize, file.size)
        const response = await fetch(descriptor.url, {
          method: 'PUT',
          body: file.slice(start, end),
          credentials: 'omit',
          signal: controller.signal
        })
        if (!response.ok) {
          if (response.status === 403) {
            throw new PresignedUrlExpiredError(descriptor.partNumber, 'http-403')
          }
          throw new Error(`第 ${descriptor.partNumber} 个分片上传失败（HTTP ${response.status}）`)
        }
        const etag = response.headers.get('etag')?.trim()
        if (!etag) {
          throw new Error(`第 ${descriptor.partNumber} 个分片未返回 ETag，请检查对象存储 CORS 响应头配置`)
        }
        completed.set(descriptor.partNumber, etag)
        reportMultipartProgress(completed, totalParts, file.size, partSize, options.onProgress)
      } catch (error) {
        if (!state.error) {
          state.error = partUploadError(error, descriptor.partNumber)
          controller.abort()
        }
      }
    }
  }

  try {
    await Promise.all(Array.from({ length: concurrency }, uploadNext))
  } finally {
    options.signal?.removeEventListener('abort', abortFromOutside)
  }
  if (state.error) throw state.error
  if (completed.size !== totalParts) throw new Error('分片上传结果不完整，请重新发起上传')
  return Array.from(completed, ([partNumber, etag]) => ({ partNumber, etag }))
    .sort((left, right) => left.partNumber - right.partNumber)
}

interface ServerUploadOptions {
  file: File
  uploadId: string
  chunkSize: number
  uploadedChunks: number[]
  durationSeconds?: number | null
  signal?: AbortSignal
  onProgress?: (progress: VideoTransferProgress) => void
}

async function uploadThroughServer(options: ServerUploadOptions): Promise<VideoReview> {
  throwIfAborted(options.signal)
  if (!Number.isSafeInteger(options.chunkSize) || options.chunkSize <= 0) {
    throw new Error('视频分片大小无效')
  }
  const totalChunks = Math.ceil(options.file.size / options.chunkSize)
  const completed = new Set(options.uploadedChunks.filter((index) => Number.isInteger(index) && index >= 0 && index < totalChunks))
  reportServerProgress(completed, totalChunks, options.file.size, options.chunkSize, options.onProgress)

  for (let index = 0; index < totalChunks; index += 1) {
    throwIfAborted(options.signal)
    if (completed.has(index)) continue
    const start = index * options.chunkSize
    const blob = options.file.slice(start, Math.min(start + options.chunkSize, options.file.size))
    await uploadVideoChunk({
      uploadId: options.uploadId,
      index,
      md5: await sha256(blob),
      blob,
      signal: options.signal
    })
    completed.add(index)
    reportServerProgress(completed, totalChunks, options.file.size, options.chunkSize, options.onProgress)
  }
  const merged = await mergeVideoUpload(options.uploadId, options.durationSeconds, options.signal)
  return merged.data
}

function collectUploadedParts(parts: VideoUploadedPart[], totalParts: number, fileSize: number, partSize: number): Map<number, string> {
  const completed = new Map<number, string>()
  for (const part of parts) {
    if (!Number.isInteger(part.partNumber) || part.partNumber < 1 || part.partNumber > totalParts) {
      throw new Error('服务端返回了无效的已上传分片序号，请重新发起上传')
    }
    if (completed.has(part.partNumber)) throw new Error(`服务端重复返回第 ${part.partNumber} 个已上传分片`)
    const expectedSize = partByteLength(part.partNumber, fileSize, partSize)
    if (part.size !== expectedSize) throw new Error(`第 ${part.partNumber} 个已上传分片大小不一致，请重新发起上传`)
    const etag = part.etag.trim()
    if (!etag) throw new Error(`第 ${part.partNumber} 个已上传分片缺少 ETag，请重新发起上传`)
    completed.set(part.partNumber, etag)
  }
  return completed
}

function collectPresignedParts(parts: VideoPresignedPart[], totalParts: number): Map<number, VideoPresignedPart> {
  const result = new Map<number, VideoPresignedPart>()
  for (const part of parts) {
    if (!Number.isInteger(part.partNumber) || part.partNumber < 1 || part.partNumber > totalParts || !part.url.trim()) {
      throw new Error('服务端返回了无效的分片上传地址，请重新发起上传')
    }
    if (result.has(part.partNumber)) throw new Error(`服务端重复返回第 ${part.partNumber} 个分片上传地址`)
    result.set(part.partNumber, part)
  }
  return result
}

function reportMultipartProgress(
  completed: ReadonlyMap<number, string>,
  totalParts: number,
  fileSize: number,
  partSize: number,
  callback?: (progress: VideoTransferProgress) => void
) {
  let completedBytes = 0
  for (const partNumber of completed.keys()) completedBytes += partByteLength(partNumber, fileSize, partSize)
  callback?.(buildProgress(completed.size, totalParts, completedBytes, fileSize))
}

function reportServerProgress(
  completed: ReadonlySet<number>,
  totalParts: number,
  fileSize: number,
  partSize: number,
  callback?: (progress: VideoTransferProgress) => void
) {
  let completedBytes = 0
  for (const index of completed) completedBytes += partByteLength(index + 1, fileSize, partSize)
  callback?.(buildProgress(completed.size, totalParts, completedBytes, fileSize))
}

function partByteLength(partNumber: number, fileSize: number, partSize: number): number {
  const start = (partNumber - 1) * partSize
  return Math.max(0, Math.min(partSize, fileSize - start))
}

function buildProgress(completedParts: number, totalParts: number, completedBytes: number, totalBytes: number): VideoTransferProgress {
  const percentage = totalBytes > 0 ? Math.min(100, Math.round((completedBytes / totalBytes) * 100)) : 100
  return { completedParts, totalParts, completedBytes, totalBytes, percentage }
}

function completeProgress(totalBytes: number): VideoTransferProgress {
  return buildProgress(1, 1, totalBytes, totalBytes)
}

function normalizeConcurrency(value: number | undefined, pendingParts: number): number {
  const requested = value ?? MULTIPART_CONCURRENCY
  if (!Number.isSafeInteger(requested) || requested <= 0) throw new Error('并发上传数量无效')
  return Math.max(1, Math.min(requested, Math.max(1, pendingParts)))
}

function reservePresignedRefresh(
  state: PresignedRefreshState,
  reason: PresignedUrlExpiryReason
): PresignedRefreshState | null {
  if (reason !== 'http-403') return { ...state }
  if (state.forbiddenRefreshes >= PRESIGN_FORBIDDEN_REFRESH_LIMIT) return null
  return { ...state, forbiddenRefreshes: state.forbiddenRefreshes + 1 }
}

function recordPresignedRefreshProgress(state: PresignedRefreshState, completedParts: number): PresignedRefreshState {
  if (completedParts > state.bestCompletedParts) {
    return { ...state, bestCompletedParts: completedParts, stagnantRefreshes: 0 }
  }
  return { ...state, stagnantRefreshes: state.stagnantRefreshes + 1 }
}

function normalizeCompletedPartCount(value: number): number {
  return Number.isSafeInteger(value) && value > 0 ? value : 0
}

async function sha256(blob: Blob): Promise<string> {
  if (!globalThis.crypto?.subtle) {
    throw new Error('当前浏览器环境不支持安全哈希，请使用 HTTPS 或 localhost 访问')
  }
  const digest = await crypto.subtle.digest('SHA-256', await blob.arrayBuffer())
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

function isFingerprintResponse(value: unknown): value is FingerprintWorkerResponse {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.requestId === 'string' && typeof candidate.ok === 'boolean'
}

function partUploadError(error: unknown, partNumber: number): Error {
  if (error instanceof PresignedUrlExpiredError || isAbortError(error)) return error as Error
  const prefix = `第 ${partNumber} 个分片上传失败`
  if (!(error instanceof Error) || !error.message) return new Error(prefix)
  return error.message.startsWith(`第 ${partNumber} 个分片`) ? error : new Error(`${prefix}：${error.message}`)
}

export function isAbortError(error: unknown): boolean {
  if (!(error instanceof Error)) return false
  const candidate = error as Error & { code?: string }
  return candidate.name === 'AbortError' || candidate.name === 'CanceledError' || candidate.code === 'ERR_CANCELED'
}

function isExpiring(expiresAt: string): boolean {
  const timestamp = Date.parse(expiresAt)
  return Number.isFinite(timestamp) && timestamp <= Date.now() + PRESIGN_EXPIRY_SKEW_MS
}

function throwIfAborted(signal?: AbortSignal) {
  if (signal?.aborted) throw abortError()
}

function abortError(): DOMException {
  return new DOMException('操作已取消', 'AbortError')
}
