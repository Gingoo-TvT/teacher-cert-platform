import request from '@/api/request'
import type { ApiResult } from '@/api/dict'

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface NotificationItem {
  id: string
  userId: string
  type: string
  title: string
  content?: string | null
  bizType?: string | null
  bizId?: string | null
  readFlag: number
  createdAt?: string | null
}

export function listNotices(read?: boolean | null, page?: number, size?: number) {
  return request.get<unknown, ApiResult<PageResult<NotificationItem>>>('/notice', {
    params: cleanParams({ read, page, size })
  })
}

export function unreadNoticeCount() {
  return request.get<unknown, ApiResult<number>>('/notice/unread-count')
}

export function markNoticeRead(id: string) {
  return request.post<unknown, ApiResult<null>>(`/notice/${id}/read`)
}

export function markAllNoticesRead() {
  return request.post<unknown, ApiResult<null>>('/notice/read-all')
}

// Phase 44e-contract（P1-1 真分页 rollout）：本地 cleanParams 原先只处理字符串（且用于 read 的三态 null/true/false），
// 现补 number（分页 page/size，剔除 NaN/Infinity）与 boolean（read 本身）两类，null/undefined 一律省略该 key。
function cleanParams(query: Record<string, unknown>) {
  const params: Record<string, string | number | boolean> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'boolean') {
      params[key] = value
    } else if (typeof value === 'string') {
      const text = value.trim()
      if (text) params[key] = text
    } else if (typeof value === 'number' && Number.isFinite(value)) {
      params[key] = value
    }
  }
  return params
}
