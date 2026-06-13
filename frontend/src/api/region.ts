import request from '@/api/request'
import type { ApiResult } from '@/api/dict'

export interface RegionNode {
  id: string
  code: string
  name: string
  parentCode?: string | null
  level: number
  sort: number
  status: number
  leaf: boolean
}

export interface RegionPath {
  code: string
  fullName: string
  nodes: RegionNode[]
}

export function listRegionChildren(parent?: string | null) {
  return request.get<unknown, ApiResult<RegionNode[]>>('/region/children', {
    params: parent ? { parent } : {}
  })
}

export function getRegionPath(code: string) {
  return request.get<unknown, ApiResult<RegionPath>>('/region/path', {
    params: { code }
  })
}
