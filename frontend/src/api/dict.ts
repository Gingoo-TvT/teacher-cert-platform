import request from '@/api/request'

export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export interface DictType {
  id: string
  typeCode: string
  typeName: string
  description?: string | null
  sort: number
  status: number
}

export interface DictItem {
  id: string
  typeCode: string
  itemCode: string
  itemValue: string
  parentCode?: string | null
  sort: number
  status: number
  yearVersion: string
  extJson?: string | null
}

export interface DictTypePayload {
  typeCode: string
  typeName: string
  description?: string | null
  sort?: number | null
  status?: number | null
}

export interface DictItemPayload {
  typeCode: string
  itemCode: string
  itemValue: string
  parentCode?: string | null
  sort?: number | null
  status?: number | null
  yearVersion?: string | null
  extJson?: string | null
}

export function listDictTypes() {
  return request.get<unknown, ApiResult<DictType[]>>('/dict/types')
}

export function listDictItems(typeCode: string, onlyEnabled = false) {
  return request.get<unknown, ApiResult<DictItem[]>>(`/dict/${encodeURIComponent(typeCode)}/items`, {
    params: { onlyEnabled }
  })
}

export function createDictType(payload: DictTypePayload) {
  return request.post<unknown, ApiResult<string>>('/dict/type', payload)
}

export function updateDictType(id: string, payload: DictTypePayload) {
  return request.put<unknown, ApiResult<null>>(`/dict/type/${id}`, payload)
}

export function deleteDictType(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/dict/type/${id}`)
}

export function createDictItem(payload: DictItemPayload) {
  return request.post<unknown, ApiResult<string>>('/dict/item', payload)
}

export function updateDictItem(id: string, payload: DictItemPayload) {
  return request.put<unknown, ApiResult<null>>(`/dict/item/${id}`, payload)
}

export function deleteDictItem(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/dict/item/${id}`)
}
