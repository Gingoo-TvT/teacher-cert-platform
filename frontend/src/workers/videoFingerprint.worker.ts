interface FingerprintRequest {
  requestId: string
  blob: Blob
  chunkSize: number
}

interface FingerprintSuccess {
  requestId: string
  ok: true
  fingerprint: string
  legacyFileHash: string
}

interface FingerprintFailure {
  requestId: string
  ok: false
  error: string
}

interface WorkerScope {
  onmessage: ((event: MessageEvent<FingerprintRequest>) => void) | null
  postMessage(message: FingerprintSuccess | FingerprintFailure): void
}

const scope = globalThis as unknown as WorkerScope
const FORMAT_MARKER = new TextEncoder().encode('teacher-cert-file-sha256-tree-v1\0')
const DIGEST_BYTES = 32

scope.onmessage = (event) => {
  const { requestId, blob, chunkSize } = event.data
  void fingerprint(blob, chunkSize)
    .then((value) => scope.postMessage({ requestId, ok: true, ...value }))
    .catch((error: unknown) => scope.postMessage({
      requestId,
      ok: false,
      error: error instanceof Error ? error.message : '视频指纹计算失败'
    }))
}

async function fingerprint(blob: Blob, chunkSize: number): Promise<{ fingerprint: string; legacyFileHash: string }> {
  if (!Number.isSafeInteger(chunkSize) || chunkSize <= 0) {
    throw new Error('视频指纹分片大小无效')
  }
  if (!globalThis.crypto?.subtle) {
    throw new Error('当前浏览器环境不支持安全哈希，请使用 HTTPS 或 localhost 访问')
  }

  // 一次只读取一个 8 MiB 左右的叶片；根摘要只拼接每片的 32-byte 摘要。
  const chunkDigests: Uint8Array[] = []
  let legacyHash = 0
  for (let offset = 0; offset < blob.size; offset += chunkSize) {
    const chunk = await blob.slice(offset, Math.min(offset + chunkSize, blob.size)).arrayBuffer()
    for (const byte of new Uint8Array(chunk)) legacyHash = (legacyHash * 31 + byte) >>> 0
    chunkDigests.push(new Uint8Array(await crypto.subtle.digest('SHA-256', chunk)))
  }

  const metadataBytes = 24
  const rootInput = new Uint8Array(FORMAT_MARKER.byteLength + metadataBytes + chunkDigests.length * DIGEST_BYTES)
  rootInput.set(FORMAT_MARKER)
  const metadata = new DataView(rootInput.buffer, FORMAT_MARKER.byteLength, metadataBytes)
  metadata.setBigUint64(0, BigInt(blob.size), false)
  metadata.setBigUint64(8, BigInt(chunkSize), false)
  metadata.setBigUint64(16, BigInt(chunkDigests.length), false)

  let cursor = FORMAT_MARKER.byteLength + metadataBytes
  for (const digest of chunkDigests) {
    rootInput.set(digest, cursor)
    cursor += digest.byteLength
  }
  return {
    fingerprint: toHex(await crypto.subtle.digest('SHA-256', rootInput)),
    legacyFileHash: legacyHash.toString(16).padStart(8, '0')
  }
}

function toHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

export {}
