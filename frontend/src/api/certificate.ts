import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'

export interface Certificate {
  id: string
  studentId: string
  collegeId: string
  assessmentYear: string
  certNo?: string | null
  studentNo?: string | null
  studentName?: string | null
  idCardType?: string | null
  idCardNo?: string | null
  educationLevel?: string | null
  trainingGoal?: string | null
  teachingSegment?: string | null
  teachingSubjectCode?: string | null
  teachingSubjectName?: string | null
  issuer?: string | null
  issueDate?: string | null
  validUntil?: string | null
  status: string
  statusLabel: string
  voidReason?: string | null
  reissueOriginCertNo?: string | null
  correctionReason?: string | null
  locked: number
}

export interface CertificateQuery {
  keyword?: string | null
  studentId?: string | null
  collegeId?: string | null
  assessmentYear?: string | null
  status?: string | null
}

export interface CertificatePrecheck {
  studentId: string
  assessmentYear: string
  passed: boolean
  missingItems: string[]
}

export interface CertificateGeneratePayload {
  studentId: string
  assessmentYear: string
  reissueOriginCertNo?: string | null
}

export interface CertificateIssuePayload {
  issuer: string
  issueDate: string
}

export interface CertificateCorrectPayload {
  certNo?: string | null
  validUntil?: string | null
  teachingSubjectCode?: string | null
  teachingSubjectName?: string | null
  teachingSegment?: string | null
  trainingGoal?: string | null
  reason: string
}

export function listCertificates(query: CertificateQuery = {}) {
  return request.get<unknown, ApiResult<PageResult<Certificate>>>('/cert', {
    params: cleanParams(query)
  })
}

export function getCertificate(id: string) {
  return request.get<unknown, ApiResult<Certificate>>(`/cert/${id}`)
}

export function precheckCertificate(studentId: string, assessmentYear: string) {
  return request.get<unknown, ApiResult<CertificatePrecheck>>(`/cert/precheck/${studentId}`, {
    params: { year: assessmentYear }
  })
}

export function generateCertificate(payload: CertificateGeneratePayload) {
  return request.post<unknown, ApiResult<Certificate>>('/cert/generate', payload)
}

export function issueCertificate(id: string, payload: CertificateIssuePayload) {
  return request.post<unknown, ApiResult<Certificate>>(`/cert/${id}/issue`, payload)
}

export function markCertificateExported(id: string) {
  return request.post<unknown, ApiResult<Certificate>>(`/cert/${id}/export`, {})
}

export function archiveCertificate(id: string) {
  return request.post<unknown, ApiResult<Certificate>>(`/cert/${id}/archive`, {})
}

export function voidCertificate(id: string, reason: string) {
  return request.post<unknown, ApiResult<Certificate>>(`/cert/${id}/void`, { reason })
}

export function reissueCertificate(id: string) {
  return request.post<unknown, ApiResult<Certificate>>(`/cert/${id}/reissue`, {})
}

export function correctCertificate(id: string, payload: CertificateCorrectPayload) {
  return request.put<unknown, ApiResult<Certificate>>(`/cert/${id}/correct`, payload)
}

function cleanParams(query: object) {
  const params: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
  }
  return params
}
