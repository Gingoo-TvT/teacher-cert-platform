import request from '@/api/request'
import type { ApiResult, DictItem } from '@/api/dict'
import type { PageResult } from '@/api/security'
import type { ReviewPayload } from '@/api/student'

export interface ExemptionMaterial {
  id: string
  exemptionRequestId: string
  fileId: string
  fileName: string
  fileSize?: number | null
  contentType?: string | null
  uploaderId: string
  uploadTime?: string | null
}

export interface ExemptionRequest {
  id: string
  studentId: string
  studentNo?: string | null
  studentName?: string | null
  collegeId: string
  assessmentYear: string
  teachingSegment: string
  teachingSegmentLabel: string
  subject: string
  subjectLabel: string
  basis: string
  basisLabel: string
  remark?: string | null
  finalStatus: string
  statusLabel: string
  includedInExam: number
  locked: number
  firstReviewStatus?: string | null
  firstReviewComment?: string | null
  secondReviewStatus?: string | null
  secondReviewComment?: string | null
  firstReviewTime?: string | null
  secondReviewTime?: string | null
  materials: ExemptionMaterial[]
}

export interface ExemptionQuery {
  keyword?: string | null
  status?: string | null
  collegeId?: string | null
  studentId?: string | null
  assessmentYear?: string | null
  teachingSegment?: string | null
  subject?: string | null
}

export interface ExemptionApplyItem {
  subject: string
  basis: string
  remark?: string | null
}

export interface ExemptionApplyPayload {
  studentId: string
  assessmentYear: string
  teachingSegment: string
  items: ExemptionApplyItem[]
}

export interface ExamSubject {
  subject: string
  subjectLabel: string
  exempted: boolean
  includedInExam: boolean
}

export function listExemptions(query: ExemptionQuery = {}) {
  return request.get<unknown, ApiResult<PageResult<ExemptionRequest>>>('/exemption', {
    params: cleanParams(query)
  })
}

export function getExemptionSubjects(segment?: string | null) {
  return request.get<unknown, ApiResult<DictItem[]>>('/exemption/subjects', {
    params: cleanParams({ segment })
  })
}

export function applyExemption(payload: ExemptionApplyPayload) {
  return request.post<unknown, ApiResult<string[]>>('/exemption', payload)
}

export function updateExemption(id: string, payload: { basis: string; remark?: string | null }) {
  return request.put<unknown, ApiResult<null>>(`/exemption/${id}`, payload)
}

export function uploadExemptionMaterial(id: string, file: File) {
  const data = new FormData()
  data.append('file', file)
  return request.post<unknown, ApiResult<null>>(`/exemption/${id}/materials`, data)
}

export function replaceExemptionMaterial(materialId: string, file: File) {
  const data = new FormData()
  data.append('file', file)
  return request.put<unknown, ApiResult<null>>(`/exemption/materials/${materialId}`, data)
}

export function deleteExemptionMaterial(materialId: string) {
  return request.delete<unknown, ApiResult<null>>(`/exemption/materials/${materialId}`)
}

export function previewExemptionMaterial(materialId: string) {
  return request.get<unknown, ApiResult<string>>(`/exemption/materials/${materialId}/preview`)
}

export function submitExemption(id: string) {
  return request.post<unknown, ApiResult<null>>(`/exemption/${id}/submit`, {})
}

export function firstReviewExemption(id: string, payload: ReviewPayload) {
  return request.post<unknown, ApiResult<null>>(`/exemption/${id}/first-review`, payload)
}

export function secondReviewExemption(id: string, payload: ReviewPayload) {
  return request.post<unknown, ApiResult<null>>(`/exemption/${id}/second-review`, payload)
}

export function getExamSubjects(studentId: string, assessmentYear: string, teachingSegment: string) {
  return request.get<unknown, ApiResult<ExamSubject[]>>(`/exemption/exam-subjects/${studentId}`, {
    params: { year: assessmentYear, segment: teachingSegment }
  })
}

function cleanParams(query: object) {
  const params: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
  }
  return params
}
