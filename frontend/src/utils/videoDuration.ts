export function detectVideoDurationSeconds(file: File): Promise<number> {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')
    const url = URL.createObjectURL(file)
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(url)
      const duration = Math.round(video.duration || 0)
      if (Number.isFinite(duration) && duration > 0) resolve(duration)
      else reject(new Error('duration unavailable'))
    }
    video.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('duration unavailable'))
    }
    video.src = url
  })
}

export function formatVideoDuration(seconds?: number | null) {
  const value = Number(seconds || 0)
  if (!Number.isFinite(value) || value <= 0) return '-'
  const mins = Math.floor(value / 60)
  const secs = value % 60
  return `${mins}:${String(secs).padStart(2, '0')}`
}
