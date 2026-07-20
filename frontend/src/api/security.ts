import request from '@/api/request'
import type { ApiResult } from '@/api/dict'

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface Role {
  id: string
  code: string
  name: string
  description?: string | null
  sort: number
  status: number
}

export interface User {
  id: string
  username: string
  realName: string
  workNo?: string | null
  email?: string | null
  phone?: string | null
  status: string
  userType: string
  collegeId?: string | null
  collegeName?: string | null
  studentId?: string | null
  lastLoginAt?: string | null
  mustChangePwd: number
  roles: Role[]
  dataScopeCollegeIds: string[]
  dataScopeMajorIds: string[]
}

export interface Permission {
  id: string
  code: string
  name: string
  type: string
  parentId?: string | null
  path?: string | null
  sort: number
  status: number
  scopeType?: RolePermissionScopeType | null
  children: Permission[]
}

export const ROLE_PERMISSION_SCOPE_TYPES = [
  'SYSTEM',
  'SCHOOL',
  'LOGIN_ALL',
  'COLLEGE',
  'SELF',
  'ASSIGNED',
  'NONE'
] as const

export type RolePermissionScopeType = (typeof ROLE_PERMISSION_SCOPE_TYPES)[number]

export interface UserPayload {
  username: string
  realName: string
  workNo?: string | null
  email?: string | null
  phone?: string | null
  status: string
  userType: string
  collegeId?: string | null
  studentId?: string | null
  roleIds: string[]
}

export interface RolePayload {
  code: string
  name: string
  description?: string | null
  sort?: number | null
  status?: number | null
}

export interface RolePermissionItem {
  permissionId: string
  scopeType: RolePermissionScopeType
}

export function isRolePermissionScopeType(value: unknown): value is RolePermissionScopeType {
  return typeof value === 'string' && ROLE_PERMISSION_SCOPE_TYPES.some((scope) => scope === value)
}

export interface UserDataScopePayload {
  collegeIds: string[]
  majorIds: string[]
}

export function listUsers(
  query: {
    keyword?: string | null
    status?: string | null
    collegeId?: string | null
    page?: number
    size?: number
  } = {}
) {
  return request.get<unknown, ApiResult<PageResult<User>>>('/system/user', { params: cleanParams(query) })
}

export function createUser(payload: UserPayload) {
  return request.post<unknown, ApiResult<string>>('/system/user', payload)
}

export function updateUser(id: string, payload: UserPayload) {
  return request.put<unknown, ApiResult<null>>(`/system/user/${id}`, payload)
}

export function deleteUser(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/system/user/${id}`)
}

export function resetUserPassword(id: string) {
  return request.put<unknown, ApiResult<string | null>>(`/system/user/${id}/reset-pwd`)
}

export function assignUserRoles(id: string, roleIds: string[]) {
  return request.put<unknown, ApiResult<null>>(`/system/user/${id}/roles`, { roleIds })
}

export function assignUserDataScope(id: string, payload: UserDataScopePayload) {
  return request.put<unknown, ApiResult<null>>(`/system/user/${id}/data-scope`, payload)
}

export function listRoles(keyword?: string | null) {
  return request.get<unknown, ApiResult<Role[]>>('/system/role', { params: cleanParams({ keyword }) })
}

export function createRole(payload: RolePayload) {
  return request.post<unknown, ApiResult<string>>('/system/role', payload)
}

export function updateRole(id: string, payload: RolePayload) {
  return request.put<unknown, ApiResult<null>>(`/system/role/${id}`, payload)
}

export function deleteRole(id: string) {
  return request.delete<unknown, ApiResult<null>>(`/system/role/${id}`)
}

export function rolePermissions(id: string) {
  return request.get<unknown, ApiResult<Permission[]>>(`/system/role/${id}/permissions`)
}

export function assignRolePermissions(id: string, permissions: RolePermissionItem[]) {
  return request.put<unknown, ApiResult<null>>(`/system/role/${id}/permissions`, { permissions })
}

export function permissionTree() {
  return request.get<unknown, ApiResult<Permission[]>>('/system/permission/tree')
}

function cleanParams(query: Record<string, unknown>) {
  const params: Record<string, string | number> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string') {
      const text = value.trim()
      if (text) params[key] = text
    } else if (typeof value === 'number' && Number.isFinite(value)) {
      params[key] = value
    }
  }
  return params
}
