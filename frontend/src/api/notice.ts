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

export function listNotices(read?: boolean | null) {
  return request.get<unknown, ApiResult<PageResult<NotificationItem>>>('/notice', {
    params: read === null || typeof read === 'undefined' ? {} : { read }
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
