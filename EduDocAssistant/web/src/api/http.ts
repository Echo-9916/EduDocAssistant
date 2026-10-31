import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from './types'
import { getToken } from './token'

/** 业务/协议错误：携带错误码与 traceId，供上层按码跳登录/无权限/字段提示。 */
export class ApiError extends Error {
  readonly code: number
  readonly traceId?: string

  constructor(code: number, message: string, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Trace-Id'] = newTraceId()
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && typeof body.code === 'number' && body.code !== 200) {
      return Promise.reject(new ApiError(body.code, body.message, body.traceId))
    }
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const body = error.response?.data
    if (body && typeof body.code === 'number') {
      return Promise.reject(new ApiError(body.code, body.message, body.traceId))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiError(50401, '请求超时'))
    }
    return Promise.reject(new ApiError(50001, error.message))
  },
)

/** 发起请求并解包 envelope 的 data。 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await http.request<ApiResponse<T>>(config)
  return res.data.data
}

export const get = <T>(url: string, config?: AxiosRequestConfig) =>
  request<T>({ ...config, method: 'GET', url })
export const post = <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
  request<T>({ ...config, method: 'POST', url, data })
export const put = <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
  request<T>({ ...config, method: 'PUT', url, data })
export const del = <T>(url: string, config?: AxiosRequestConfig) =>
  request<T>({ ...config, method: 'DELETE', url })

/** 5xx 带 traceId，其余直接展示 message。 */
export function getErrorMessage(err: unknown): string {
  if (!(err instanceof ApiError)) {
    return err instanceof Error ? err.message : '请求失败'
  }
  if (err.code >= 50000) {
    return `服务异常，请稍后重试（traceId: ${err.traceId ?? '-'}）`
  }
  return err.message
}

export function newTraceId(): string {
  return crypto.randomUUID().replace(/-/g, '')
}

/** 幂等键：写操作首次生成，重试时复用同一 key。 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}
