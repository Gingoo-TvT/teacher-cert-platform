import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'
import type { ExamSubject } from '@/api/exemption'

export interface AbilityTestResult {
  id?: string | null
  studentId: string
  studentNo?: string | null
  studentName?: string | null
  collegeId?: string | null
  assessmentYear: string
  teachingSegment?: string | null
  examOrgMode?: string | null
  examOrgModeLabel?: string | null
  examSubjects: ExamSubject[]
  score?: string | null
  conclusion: string
  conclusionLabel: string
  confirmStatus: string
  confirmStatusLabel: string
  locked: number
  validForCertificate: boolean
  exemptionRelation?: string | null
}

export interface AbilityTestPayload {
  studentId: string
  assessmentYear: string
  teachingSegment?: string | null
  examOrgMode: string
  score?: string | null
  conclusion: string
}

export interface AbilityTestQuery {
  keyword?: string | null
  studentId?: string | null
  collegeId?: string | null
  assessmentYear?: string | null
  conclusion?: string | null
  confirmStatus?: string | null
}

export interface AbilityTestValidity {
  studentId: string
  assessmentYear: string
  conclusion?: string | null
  validForCertificate: boolean
  message?: string | null
}

export function listAbilityTests(query: AbilityTestQuery = {}) {
  return request.get<unknown, ApiResult<PageResult<AbilityTestResult>>>('/test', {
    params: cleanParams(query)
  })
}

export function getAbilityTest(studentId: string, assessmentYear: string, teachingSegment?: string | null) {
  return request.get<unknown, ApiResult<AbilityTestResult>>(`/test/${studentId}`, {
    params: cleanParams({ year: assessmentYear, segment: teachingSegment })
  })
}

export function importAbilityTests(rows: AbilityTestPayload[]) {
  return request.post<unknown, ApiResult<string[]>>('/test/import', { rows })
}

export function importAbilityTestFile(file: File) {
  const data = new FormData()
  data.append('file', file)
  return request.post<unknown, ApiResult<string[]>>('/test/import-file', data)
}

export function confirmAbilityTest(id: string) {
  return request.post<unknown, ApiResult<null>>(`/test/${id}/confirm`, {})
}

export function getAbilityTestValidity(studentId: string, assessmentYear: string) {
  return request.get<unknown, ApiResult<AbilityTestValidity>>(`/test/${studentId}/validity`, {
    params: { year: assessmentYear }
  })
}

function cleanParams(query: object) {
  const params: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
  }
  return params
}
