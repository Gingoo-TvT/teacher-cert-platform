import request from '@/api/request'
import type { ApiResult } from '@/api/dict'

export interface TeachingSubject {
  id: string
  segmentCode: string
  categoryNode?: string | null
  subjectCode: string
  subjectName: string
  isCategory: number
  selectable: boolean
  keyword?: string | null
  yearVersion: string
  status: number
}

export interface SubjectQuery {
  segment?: string | null
  keyword?: string | null
  category?: string | null
  yearVersion?: string | null
}

export interface SubjectSelectPayload {
  segmentCode: string
  subjectCode: string
  yearVersion?: string | null
}

export interface SubjectImportError {
  rowNo: number
  field: string
  errorValue?: string | null
  reason: string
}

export interface SubjectImportResult {
  total: number
  successCount: number
  failCount: number
  errors: SubjectImportError[]
}

export function listSubjects(query: SubjectQuery = {}) {
  return request.get<unknown, ApiResult<TeachingSubject[]>>('/subject', {
    params: cleanParams({ ...query })
  })
}

export function importSubjects(file: File, yearVersion?: string | null) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<unknown, ApiResult<SubjectImportResult>>('/subject/import', formData, {
    params: cleanParams({ yearVersion })
  })
}

export function validateSubject(payload: SubjectSelectPayload) {
  return request.post<unknown, ApiResult<null>>('/subject/validate', payload)
}

export function recordRecentSubject(payload: SubjectSelectPayload) {
  return request.post<unknown, ApiResult<null>>('/subject/recent', payload)
}

export function listRecentSubjects(segment: string, yearVersion?: string | null) {
  return request.get<unknown, ApiResult<TeachingSubject[]>>('/subject/recent', {
    params: cleanParams({ segment, yearVersion })
  })
}

function cleanParams(query: Record<string, unknown>) {
  const params: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    const text = typeof value === 'string' ? value.trim() : ''
    if (text) params[key] = text
  }
  return params
}
