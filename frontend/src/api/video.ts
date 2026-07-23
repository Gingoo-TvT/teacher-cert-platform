import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'

/**
 * 视频分片大小：8 MiB。≥ MinIO 服务端合并 composeObject 要求的 5 MiB 部件下限，
 * 使多分片上传走服务端合并快路径（P1-2 阶段1），避免逐片经应用拉回重传的慢路径。
 */
export const VIDEO_UPLOAD_CHUNK_SIZE = 8 * 1024 * 1024

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
  page?: number
  size?: number
}

export interface VideoUploadInitPayload {
  studentId: string
  assessmentYear: string
  fileMd5: string
  legacyFileHash?: string | null
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
  uploadMode: VideoUploadMode
  partSize: number
  uploadedParts: VideoUploadedPart[]
  parts: VideoPresignedPart[]
  status?: string | null
  validationMessage?: string | null
}

export type VideoUploadMode = 'PRESIGNED_MULTIPART' | 'SERVER_CHUNK' | 'FAST_HIT'

export interface VideoUploadedPart {
  partNumber: number
  etag: string
  size: number
}

export interface VideoPresignedPart {
  partNumber: number
  url: string
  expiresAt: string
}

export interface VideoUploadCompletedPart {
  partNumber: number
  etag: string
}

export interface VideoUploadCompletePayload {
  uploadId: string
  durationSeconds?: number | null
  parts: VideoUploadCompletedPart[]
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

export interface ReviewerGroupMember {
  id: string
  reviewerUserId: string
  reviewerName?: string | null
  workNo?: string | null
}

export interface ReviewerGroup {
  id: string
  collegeId: string
  name: string
  status: 'ENABLED' | 'DISABLED'
  memberCount: number
  members: ReviewerGroupMember[]
}

export interface ReviewerGroupPayload {
  name: string
  status?: 'ENABLED' | 'DISABLED'
}

export interface ReviewerCandidate {
  id: string
  realName: string
  workNo?: string | null
}

export function initVideoUpload(payload: VideoUploadInitPayload, signal?: AbortSignal) {
  return request.post<unknown, ApiResult<VideoUploadInitResult>>('/video/upload/init', payload, { signal })
}

export function uploadVideoChunk(payload: { uploadId: string; index: number; md5: string; blob: Blob; signal?: AbortSignal }) {
  const data = new FormData()
  data.append('uploadId', payload.uploadId)
  data.append('index', String(payload.index))
  data.append('md5', payload.md5)
  data.append('file', payload.blob, `chunk-${payload.index}`)
  return request.post<unknown, ApiResult<null>>('/video/upload/chunk', data, { signal: payload.signal })
}

export function mergeVideoUpload(uploadId: string, durationSeconds?: number | null, signal?: AbortSignal) {
  return request.post<unknown, ApiResult<VideoReview>>('/video/upload/merge', { uploadId, durationSeconds }, { signal })
}

export function completeVideoUpload(payload: VideoUploadCompletePayload, signal?: AbortSignal) {
  return request.post<unknown, ApiResult<VideoReview>>('/video/upload/complete', payload, { signal })
}

export function cancelVideoUpload(uploadId: string) {
  return request.delete<unknown, ApiResult<null>>(`/video/upload/${encodeURIComponent(uploadId)}`)
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

export function assignVideoReviewGroup(id: string, groupId: string) {
  return request.post<unknown, ApiResult<null>>(`/video/reviews/${id}/assign`, { groupId })
}

export function listReviewerGroups() {
  return request.get<unknown, ApiResult<ReviewerGroup[]>>('/video/reviewer-groups')
}

export function listReviewerCandidates() {
  return request.get<unknown, ApiResult<ReviewerCandidate[]>>('/video/reviewer-candidates')
}

export function createReviewerGroup(payload: ReviewerGroupPayload) {
  return request.post<unknown, ApiResult<ReviewerGroup>>('/video/reviewer-groups', payload)
}

export function updateReviewerGroup(id: string, payload: ReviewerGroupPayload) {
  return request.put<unknown, ApiResult<ReviewerGroup>>(`/video/reviewer-groups/${id}`, payload)
}

export function deleteReviewerGroup(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/video/reviewer-groups/${id}`)
}

export function addReviewerGroupMember(id: string, reviewerUserId: string) {
  return request.post<unknown, ApiResult<ReviewerGroup>>(`/video/reviewer-groups/${id}/members`, { reviewerUserId })
}

export function removeReviewerGroupMember(id: string, memberId: string) {
  return request.delete<unknown, ApiResult<null>>(`/video/reviewer-groups/${id}/members/${memberId}`)
}

export function listMyVideoTasks(status?: string | null, page?: number, size?: number) {
  return request.get<unknown, ApiResult<PageResult<VideoTask>>>('/video/tasks/my', { params: cleanParams({ status, page, size }) })
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

export function returnVideoReview(id: string, comment: string) {
  return request.post<unknown, ApiResult<null>>(`/video/reviews/${id}/return`, { comment })
}

export function playVideoReview(id: string) {
  return request.get<unknown, ApiResult<VideoPlayback>>(`/video/reviews/${id}/play`)
}

function cleanParams(query: object) {
  const params: Record<string, string | number> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string') {
      const text = value.trim()
      if (text) params[key] = text
    } else if (typeof value === 'number' && Number.isFinite(value)) {
      params[key] = value
    }
  }
  return params
}
