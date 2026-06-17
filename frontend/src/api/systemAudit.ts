import request from '@/api/request'
import type { ApiResult } from '@/api/dict'
import type { PageResult } from '@/api/security'

export interface SysParam {
  id: string
  paramKey: string
  paramValue: string
  paramType: string
  paramGroup: string
  description?: string | null
  editable: number
  updatedAt?: string | null
}

export interface AuditLog {
  id: string
  bizType?: string | null
  bizId?: string | null
  target?: string | null
  operatorId?: string | null
  operatorName?: string | null
  operatorCollegeId?: string | null
  operateTime?: string | null
  comment?: string | null
  oldStatus?: string | null
  newStatus?: string | null
  operation?: string | null
  ip?: string | null
}

export interface BackupRecord {
  id: string
  backupType: string
  status: string
  scope?: string | null
  storageUri?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  operatorId?: string | null
  remark?: string | null
  errorMessage?: string | null
}

export interface AuditQuery {
  bizType?: string | null
  bizId?: string | null
  operation?: string | null
  operatorId?: string | null
  studentId?: string | null
  collegeId?: string | null
  batchNo?: string | null
  keyword?: string | null
  startTime?: string | null
  endTime?: string | null
}

export function listSystemParams(query: { group?: string | null; keyword?: string | null }) {
  return request.get<unknown, ApiResult<PageResult<SysParam>>>('/system/param', { params: cleanParams(query) })
}

export function updateSystemParam(id: string, payload: { paramValue: string; description?: string | null }) {
  return request.put<unknown, ApiResult<SysParam>>(`/system/param/${id}`, payload)
}

export function listAuditLogs(query: AuditQuery) {
  return request.get<unknown, ApiResult<PageResult<AuditLog>>>('/audit/log', {
    params: cleanParams({
      bizType: query.bizType,
      bizId: query.bizId,
      operation: query.operation,
      operatorId: query.operatorId,
      studentId: query.studentId,
      collegeId: query.collegeId,
      batchNo: query.batchNo,
      keyword: query.keyword,
      startTime: query.startTime,
      endTime: query.endTime
    })
  })
}

export function deleteAuditLog(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/audit/log/${id}`)
}

export function listBackups(status?: string | null) {
  return request.get<unknown, ApiResult<PageResult<BackupRecord>>>('/system/backup', { params: cleanParams({ status }) })
}

export function triggerBackup(payload: { backupType: string; scope?: string | null; remark?: string | null }) {
  return request.post<unknown, ApiResult<BackupRecord>>('/system/backup/trigger', payload)
}

function cleanParams(query: Record<string, unknown>) {
  const params: Record<string, string | number> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string') {
      const text = value.trim()
      if (text) params[key] = text
    } else if (typeof value === 'number') {
      params[key] = value
    }
  }
  return params
}
