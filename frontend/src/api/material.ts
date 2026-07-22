import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'
import type { ReviewPayload } from '@/api/student'

export interface ProcessMaterial {
  id: string
  studentId: string
  studentNo?: string | null
  studentName?: string | null
  collegeId: string
  assessmentYear: string
  category: string
  categoryLabel: string
  fileId: string
  fileName: string
  fileSize?: number | null
  contentType?: string | null
  uploaderId: string
  uploadTime?: string | null
  status: string
  statusLabel: string
  locked: number
  firstReviewStatus?: string | null
  firstReviewComment?: string | null
  secondReviewStatus?: string | null
  secondReviewComment?: string | null
}

export interface MaterialQuery {
  keyword?: string | null
  status?: string | null
  collegeId?: string | null
  studentId?: string | null
  assessmentYear?: string | null
  category?: string | null
  page?: number
  size?: number
}

export interface CategoryStatus {
  category: string
  categoryLabel: string
  passed: boolean
  totalCount: number
  passedCount: number
  failedCount: number
}

export interface ProcessStatus {
  studentId: string
  assessmentYear: string
  qualified: boolean
  categories: CategoryStatus[]
}

export const MATERIAL_UPLOAD_PART_SIZE = 8 * 1024 * 1024

export interface MaterialPresignedPart {
  partNumber: number
  url: string
  expiresAt: string
}

export interface MaterialUploadedPart {
  partNumber: number
  etag: string
  size: number
}

export interface MaterialDirectUploadInitResult {
  fileId?: string | null
  uploadMode: 'PRESIGNED_MULTIPART' | 'SERVER_UPLOAD' | 'READY'
  partSize: number
  uploadedParts: MaterialUploadedPart[]
  parts: MaterialPresignedPart[]
}

export interface MaterialDirectUploadContext {
  materialId?: string | null
  studentId: string
  assessmentYear: string
  category: string
}

export function listMaterials(query: MaterialQuery = {}) {
  return request.get<unknown, ApiResult<PageResult<ProcessMaterial>>>('/material', {
    params: cleanParams(query)
  })
}

export function uploadMaterial(payload: { studentId: string; assessmentYear: string; category: string; file: File }, signal?: AbortSignal) {
  const data = new FormData()
  data.append('studentId', payload.studentId)
  data.append('assessmentYear', payload.assessmentYear)
  data.append('category', payload.category)
  data.append('file', payload.file)
  return request.post<unknown, ApiResult<string>>('/material/upload', data, { signal })
}

export function initMaterialDirectUpload(payload: MaterialDirectUploadContext & {
  fileName: string
  contentType: string
  size: number
  fileHash: string
  partSize: number
}, signal?: AbortSignal) {
  return request.post<unknown, ApiResult<MaterialDirectUploadInitResult>>('/material/upload/init', payload, { signal })
}

export function completeMaterialDirectUpload(payload: MaterialDirectUploadContext & {
  fileId: string
  parts: Array<{ partNumber: number; etag: string }>
}, signal?: AbortSignal) {
  return request.post<unknown, ApiResult<string>>('/material/upload/complete', payload, { signal })
}

export function cancelMaterialDirectUpload(fileId: string) {
  return request.delete<unknown, ApiResult<null>>(`/material/upload/${fileId}`)
}

export function replaceMaterial(id: string, file: File, signal?: AbortSignal) {
  const data = new FormData()
  data.append('file', file)
  return request.put<unknown, ApiResult<null>>(`/material/${id}/replace`, data, { signal })
}

export function deleteMaterial(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/material/${id}`)
}

export function previewMaterial(id: string) {
  return request.get<unknown, ApiResult<string>>(`/material/preview/${id}`)
}

export function submitMaterial(id: string) {
  return request.post<unknown, ApiResult<null>>(`/material/${id}/submit`, {})
}

export function firstReviewMaterial(id: string, payload: ReviewPayload) {
  return request.post<unknown, ApiResult<null>>(`/material/${id}/first-review`, payload)
}

export function secondReviewMaterial(id: string, payload: ReviewPayload) {
  return request.post<unknown, ApiResult<null>>(`/material/${id}/second-review`, payload)
}

export function getProcessStatus(studentId: string, assessmentYear: string) {
  return request.get<unknown, ApiResult<ProcessStatus>>(`/material/process-status/${studentId}`, {
    params: { year: assessmentYear }
  })
}

export function batchDownloadMaterials(query: MaterialQuery = {}) {
  // 泛型必须是 <unknown, Blob>：拦截器返回 response.data，故此函数直接 resolve 出 Blob 本身。
  return request.post<unknown, Blob>('/material/batch-download', cleanParams(query), { responseType: 'blob' })
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
