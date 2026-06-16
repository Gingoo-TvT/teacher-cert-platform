import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'

export interface VideoTask {
  id: string
  videoReviewId: string
  reviewerId: string
  reviewerName?: string | null
  reviewerRole: string
  score?: number | null
  dimensionScores?: Record<string, number> | null
  comment?: string | null
  conclusion?: 'PASS' | 'FAIL' | null
  submitted: number
  submitTime?: string | null
}

export interface VideoReview {
  id: string
  studentId: string
  studentNo?: string | null
  studentName?: string | null
  collegeId: string
  assessmentYear: string
  videoFileId?: string | null
  videoFileName?: string | null
  fileMd5?: string | null
  durationSeconds?: number | null
  formatCheck?: string | null
  validationMessage?: string | null
  status: string
  statusLabel: string
  finalScore?: number | null
  finalConclusion?: 'PASS' | 'FAIL' | null
  arbitrateReviewer?: string | null
  arbitrateMode?: string | null
  confirmedBy?: string | null
  confirmedAt?: string | null
  locked: number
  tasks: VideoTask[]
}

export interface VideoQuery {
  keyword?: string | null
  status?: string | null
  collegeId?: string | null
  studentId?: string | null
  assessmentYear?: string | null
}

export interface VideoUploadInitPayload {
  studentId: string
  assessmentYear: string
  fileMd5: string
  fileName: string
  contentType?: string | null
  size: number
  chunkSize: number
  durationSeconds?: number | null
}

export interface VideoUploadInitResult {
  uploadId?: string | null
  instantHit: boolean
  fileId?: string | null
  reviewId?: string | null
  uploadedChunks: number[]
  status?: string | null
  validationMessage?: string | null
}

export interface VideoUploadProgress {
  uploadId: string
  status: string
  totalChunks: number
  uploadedChunks: number
  uploadedBytes: number
  uploadedChunkIndexes: number[]
  fileId?: string | null
  validationMessage?: string | null
}

export interface VideoScorePayload {
  score: number
  dimensionScores: Record<string, number>
  comment?: string | null
  conclusion: 'PASS' | 'FAIL'
}

export interface VideoPlayback {
  url: string
  expirySeconds: number
  watermarkText: string
  issuedAt: number
}

export function initVideoUpload(payload: VideoUploadInitPayload) {
  return request.post<unknown, ApiResult<VideoUploadInitResult>>('/video/upload/init', payload)
}

export function uploadVideoChunk(payload: { uploadId: string; index: number; md5: string; blob: Blob }) {
  const data = new FormData()
  data.append('uploadId', payload.uploadId)
  data.append('index', String(payload.index))
  data.append('md5', payload.md5)
  data.append('file', payload.blob, `chunk-${payload.index}`)
  return request.post<unknown, ApiResult<null>>('/video/upload/chunk', data)
}

export function mergeVideoUpload(uploadId: string, durationSeconds?: number | null) {
  return request.post<unknown, ApiResult<VideoReview>>('/video/upload/merge', { uploadId, durationSeconds })
}

export function videoUploadProgress(uploadId: string) {
  return request.get<unknown, ApiResult<VideoUploadProgress>>('/video/upload/progress', { params: { uploadId } })
}

export function listVideoReviews(query: VideoQuery = {}) {
  return request.get<unknown, ApiResult<PageResult<VideoReview>>>('/video/reviews', { params: cleanParams(query) })
}

export function getVideoReview(id: string) {
  return request.get<unknown, ApiResult<VideoReview>>(`/video/reviews/${id}`)
}

export function assignVideoReview(id: string, reviewerIds: string[]) {
  return request.post<unknown, ApiResult<null>>(`/video/reviews/${id}/assign`, { reviewerIds })
}

export function listMyVideoTasks(status?: string | null) {
  return request.get<unknown, ApiResult<PageResult<VideoTask>>>('/video/tasks/my', { params: cleanParams({ status }) })
}

export function getVideoTask(id: string) {
  return request.get<unknown, ApiResult<VideoTask>>(`/video/tasks/${id}`)
}

export function submitVideoScore(id: string, payload: VideoScorePayload) {
  return request.post<unknown, ApiResult<null>>(`/video/tasks/${id}/score`, payload)
}

export function listVideoTasks(id: string) {
  return request.get<unknown, ApiResult<VideoTask[]>>(`/video/reviews/${id}/tasks`)
}

export function thirdVideoReview(id: string, payload: VideoScorePayload & { reviewerId: string }) {
  return request.post<unknown, ApiResult<null>>(`/video/reviews/${id}/third-review`, payload)
}

export function arbitrateVideoReview(id: string, payload: { finalScore: number; conclusion: 'PASS' | 'FAIL'; comment?: string | null }) {
  return request.post<unknown, ApiResult<null>>(`/video/reviews/${id}/arbitrate`, payload)
}

export function confirmVideoReview(id: string) {
  return request.post<unknown, ApiResult<null>>(`/video/reviews/${id}/confirm`, {})
}

export function playVideoReview(id: string) {
  return request.get<unknown, ApiResult<VideoPlayback>>(`/video/reviews/${id}/play`)
}

function cleanParams(query: object) {
  const params: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
  }
  return params
}
