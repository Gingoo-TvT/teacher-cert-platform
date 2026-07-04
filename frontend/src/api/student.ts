import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'

export interface Student {
  id: string
  studentNo: string
  name: string
  gender: string
  idCardType: string
  idCardNo: string
  birthDate: string
  identityType: string
  sourceProvince?: string | null
  sourceCity?: string | null
  sourceCounty?: string | null
  sourceFull?: string | null
  collegeId: string
  grade?: string | null
  className?: string | null
  status: string
  statusLabel: string
  locked: number
  firstReviewComment?: string | null
  secondReviewComment?: string | null
}

export interface StudentPayload {
  studentNo: string
  name: string
  gender: string
  idCardType: string
  idCardNo: string
  birthDate: string
  identityType: string
  sourceProvince?: string | null
  sourceCity?: string | null
  sourceCounty?: string | null
  sourceFull?: string | null
  collegeId: string
  grade?: string | null
  className?: string | null
}

export interface ReviewPayload {
  action: 'PASS' | 'REJECT' | 'FAIL'
  comment?: string | null
}

export function listStudents(
  query: {
    keyword?: string
    status?: string | null
    collegeId?: string | null
    grade?: string | null
    page?: number
    size?: number
  } = {}
) {
  return request.get<unknown, ApiResult<PageResult<Student>>>('/student', { params: cleanParams(query) })
}

export function getStudent(id: string) {
  return request.get<unknown, ApiResult<Student>>(`/student/${id}`)
}

export function createStudent(payload: StudentPayload) {
  return request.post<unknown, ApiResult<string>>('/student', payload)
}

export function batchCreateStudents(payload: StudentPayload[]) {
  return request.post<unknown, ApiResult<string[]>>('/student/batch', payload)
}

export function updateStudent(id: string, payload: StudentPayload) {
  return request.put<unknown, ApiResult<null>>(`/student/${id}`, payload)
}

export function deleteStudent(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/student/${id}`)
}

export function confirmStudent(payload: StudentPayload) {
  return request.post<unknown, ApiResult<Student>>('/student/confirm', payload)
}

export function submitStudent(id: string) {
  return request.post<unknown, ApiResult<null>>(`/student/${id}/submit`, {})
}

export function firstReviewStudent(id: string, payload: ReviewPayload) {
  return request.post<unknown, ApiResult<null>>(`/student/${id}/first-review`, payload)
}

export function secondReviewStudent(id: string, payload: ReviewPayload) {
  return request.post<unknown, ApiResult<null>>(`/student/${id}/second-review`, payload)
}

function cleanParams(query: Record<string, unknown>) {
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
