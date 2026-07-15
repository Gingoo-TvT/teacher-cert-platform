import request from '@/api/request'
import type { ApiResult } from '@/api/dict'

export interface Captcha {
  captchaId: string
  image: string
}

export interface CurrentUser {
  id: string
  username: string
  realName: string
  userType: string
  collegeId?: string | null
  studentId?: string | null
  mustChangePwd: boolean
  userManagementWritable: boolean
  roles: string[]
  permissions: string[]
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  mustChangePwd: boolean
  user: CurrentUser
}

export interface LoginPayload {
  username: string
  password: string
  captchaId: string
  captchaCode: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export function getCaptcha() {
  return request.get<unknown, ApiResult<Captcha>>('/auth/captcha')
}

export function login(payload: LoginPayload) {
  return request.post<unknown, ApiResult<LoginResult>>('/auth/login', payload, { skipAuthRefresh: true })
}

export function refreshToken(refreshToken: string) {
  return request.post<unknown, ApiResult<LoginResult>>('/auth/refresh', { refreshToken }, { skipAuthRefresh: true })
}

export function logout() {
  return request.post<unknown, ApiResult<null>>('/auth/logout')
}

export function changePassword(payload: ChangePasswordPayload) {
  return request.post<unknown, ApiResult<null>>('/auth/change-pwd', payload, { skipAuthRefresh: true })
}

export function getMe() {
  return request.get<unknown, ApiResult<CurrentUser>>('/auth/me')
}
