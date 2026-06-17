import request from '@/api/request'
import type { ApiResult } from '@/api/dict'

export interface StatsQuery {
  assessmentYear?: string | null
  collegeId?: string | null
  internalMajorCode?: string | null
  className?: string | null
  teachingSegment?: string | null
  teachingSubjectCode?: string | null
  status?: string | null
  keyword?: string | null
}

export interface StatsMetric {
  label: string
  value: string
  unit?: string | null
}

export interface StatsRow {
  dimension?: string | null
  dimensionLabel?: string | null
  status?: string | null
  statusLabel?: string | null
  count: number
  values: Record<string, string>
}

export interface StatsDetail {
  studentId?: string | null
  studentNo?: string | null
  studentName?: string | null
  collegeId?: string | null
  collegeName?: string | null
  fieldName?: string | null
  errorReason?: string | null
  values: Record<string, string>
}

export interface StatsReport {
  type: string
  title: string
  assessmentYear: string
  denominatorRule: string
  metrics: StatsMetric[]
  rows: StatsRow[]
  details: StatsDetail[]
}

export function getStatsReport(type: string, query: StatsQuery = {}) {
  return request.get<unknown, ApiResult<StatsReport>>(`/stats/${type}`, { params: cleanParams(query) })
}

export function exportStatsReport(type: string, query: StatsQuery = {}) {
  return request.post<unknown, Blob>(`/stats/${type}/export`, cleanParams(query), { responseType: 'blob' })
}

export function saveStatsBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

function cleanParams(query: StatsQuery) {
  const params: Record<string, string> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string' && value.trim()) params[key] = value.trim()
  }
  return params
}
