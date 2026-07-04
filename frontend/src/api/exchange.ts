import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'

export interface ExchangeQuery {
  keyword?: string | null
  assessmentYear?: string | null
  collegeId?: string | null
  internalMajorCode?: string | null
  className?: string | null
  trainingGoal?: string | null
  teachingSegment?: string | null
  auditStatus?: string | null
  certStatus?: string | null
}

export interface ExchangeStandardRow {
  sequenceNo?: string | null
  schoolCode?: string | null
  schoolName?: string | null
  studentNo?: string | null
  name?: string | null
  gender?: string | null
  idCardType?: string | null
  idCardNo?: string | null
  birthDate?: string | null
  identityType?: string | null
  sourcePlace?: string | null
  secondDisciplineCode?: string | null
  secondDisciplineName?: string | null
  internalMajorCode?: string | null
  internalMajorName?: string | null
  educationLevel?: string | null
  trainingGoal?: string | null
  internshipOrgMode?: string | null
  internshipLocation?: string | null
  teachingSegment?: string | null
  teachingSubject?: string | null
  interviewOrgMode?: string | null
  certNo?: string | null
  validUntil?: string | null
  issuer?: string | null
  remark?: string | null
}

export interface ImportPreviewRow {
  rowNo: number
  row: ExchangeStandardRow
}

export interface ImportError {
  id?: string | null
  rowNo: number
  studentNo?: string | null
  studentName?: string | null
  fieldName: string
  errorValue?: string | null
  errorReason: string
  suggestion?: string | null
}

export interface PrevalidateResult {
  batchId: string
  batchNo: string
  total: number
  successCount: number
  failCount: number
  previewRows: ImportPreviewRow[]
  errors: ImportError[]
}

export interface ImportResult {
  batchId: string
  batchNo: string
  total: number
  successCount: number
  failCount: number
  status: string
  messages: string[]
}

export interface RollbackResult {
  batchId: string
  batchNo: string
  rolledBackCount: number
  conflictCount: number
  status: string
  conflicts: string[]
}

export interface ExchangeBatch {
  id: string
  batchNo: string
  type: string
  fileName?: string | null
  operateTime?: string | null
  total: number
  successCount: number
  failCount: number
  strategy?: string | null
  status: string
  remark?: string | null
}

export function downloadTemplate(query: ExchangeQuery = {}) {
  return request.get<unknown, Blob>('/exchange/template', {
    params: cleanParams(query),
    responseType: 'blob'
  })
}

export function prevalidateExchange(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<unknown, ApiResult<PrevalidateResult>>('/exchange/prevalidate', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function downloadErrorReport(batchId: string) {
  return request.get<unknown, Blob>(`/exchange/prevalidate/${batchId}/error-report`, {
    responseType: 'blob'
  })
}

export function confirmExchangeImport(batchId: string, strategy: string) {
  return request.post<unknown, ApiResult<ImportResult>>(`/exchange/import/${batchId}/confirm`, { strategy })
}

export function rollbackExchangeImport(batchId: string) {
  return request.post<unknown, ApiResult<RollbackResult>>(`/exchange/import/${batchId}/rollback`, {})
}

export function listExchangeBatches(
  type?: string | null,
  status?: string | null,
  page?: number,
  size?: number
) {
  return request.get<unknown, ApiResult<PageResult<ExchangeBatch>>>('/exchange/batches', {
    params: cleanParams({ type, status, page, size })
  })
}

export function exportExchange(type: string, query: ExchangeQuery = {}) {
  return request.post<unknown, Blob>(`/exchange/export/${type}`, cleanParams(query), {
    responseType: 'blob'
  })
}

export function exportExchangeAttachments(query: ExchangeQuery = {}) {
  return request.post<unknown, Blob>('/exchange/export/attachments', cleanParams(query), {
    responseType: 'blob'
  })
}

export function saveBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

function cleanParams(query: object) {
  const params: Record<string, string | number> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
    else if (typeof value === 'number' && Number.isFinite(value)) params[key] = value
  }
  return params
}
