import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { useUserStore } from '@/stores/user'
import { SessionChangedError } from '@/stores/sessionEpoch'

declare module 'axios' {
  interface AxiosRequestConfig {
    skipAuthRefresh?: boolean
    skipAuthHeader?: boolean
    retried?: boolean
  }
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000,
  withCredentials: true
})

let refreshPromise: Promise<string> | null = null

// 请求拦截：注入 token
request.interceptors.request.use((config) => {
  const token = useUserStore().token
  if (token && !config.skipAuthHeader) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：解包统一 Result，处理 401
request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && typeof data.code !== 'undefined' && data.code !== 0) {
      return Promise.reject(new Error(data.msg || '请求失败'))
    }
    return data
  },
  async (error: AxiosError) => {
    const response = error.response
    const config = error.config as InternalAxiosRequestConfig | undefined
    if (response?.status === 401 && config && !config.skipAuthRefresh && !config.retried) {
      const userStore = useUserStore()
      try {
        config.retried = true
        refreshPromise = refreshPromise || userStore.refreshSession()
        const token = await refreshPromise
        refreshPromise = null
        config.headers.Authorization = `Bearer ${token}`
        return request(config)
      } catch (refreshError) {
        refreshPromise = null
        if (refreshError instanceof SessionChangedError) {
          return Promise.reject(refreshError)
        }
        userStore.clearSession()
      }
      window.location.href = '/login'
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    // 非 2xx 但响应体仍是统一 Result（如 P1-10 后未知异常返回 HTTP 500 + {code,msg}）：
    // 与成功分支 code!==0 一致地提取 msg，保证友好提示不因状态码变化而丢失。
    const data = response?.data as { code?: number; msg?: string } | undefined
    if (data && typeof data.code !== 'undefined' && data.code !== 0) {
      return Promise.reject(new Error(data.msg || '请求失败'))
    }
    return Promise.reject(error)
  }
)

export default request
