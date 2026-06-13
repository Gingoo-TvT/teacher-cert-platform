import axios, { type AxiosInstance } from 'axios'
import { useUserStore } from '@/stores/user'

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

// 请求拦截：注入 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
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
  (error) => {
    if (error.response && error.response.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default request
