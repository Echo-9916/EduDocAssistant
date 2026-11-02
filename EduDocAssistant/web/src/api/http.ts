import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from './types'
import { clearToken, getToken } from './token'

/** 业务/协议错误：携带错误码、traceId 与结构化 data（如 accountStatus），供上层按稳定字段分支。 */
export class ApiError extends Error {
  readonly code: number
  readonly traceId?: string
  readonly data?: unknown

  constructor(code: number, message: string, traceId?: string, data?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
    this.data = data
  }
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

const WRITE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Trace-Id'] = newTraceId()
  const idempotencyKey = resolveIdempotencyKey(
    config.method ?? 'get',
    config.headers['Idempotency-Key'] as string | undefined,
  )
  if (idempotencyKey) {
    config.headers['Idempotency-Key'] = idempotencyKey
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && typeof body.code === 'number' && body.code !== 200) {
      return Promise.reject(new ApiError(body.code, body.message, body.traceId, body.data))
    }
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const body = error.response?.data
    if (body && typeof body.code === 'number') {
      if ((body.code === 40101 || body.code === 40102)
          && !error.config?.url?.startsWith('/auth/login')) {
        clearToken()
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.replace(`/login?redirect=${redirect}`)
      }
      return Promise.reject(new ApiError(body.code, body.message, body.traceId, body.data))
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

/** 写请求需要幂等键：调用方已给（重试复用）则保留，否则生成；读请求返回 undefined。 */
export function resolveIdempotencyKey(method: string, existing?: string): string | undefined {
  if (!WRITE_METHODS.has(method.toUpperCase())) {
    return undefined
  }
  return existing || newIdempotencyKey()
}
