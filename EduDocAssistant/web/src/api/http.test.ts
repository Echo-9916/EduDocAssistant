import { describe, expect, it } from 'vitest'
import { ApiError, getErrorMessage, newIdempotencyKey, newTraceId, resolveIdempotencyKey } from './http'

describe('ApiError', () => {
  it('携带 code 与 traceId', () => {
    const e = new ApiError(50001, '内部错误', 't1')
    expect(e).toBeInstanceOf(Error)
    expect(e.name).toBe('ApiError')
    expect(e.code).toBe(50001)
    expect(e.traceId).toBe('t1')
  })
})

describe('getErrorMessage', () => {
  it('5xx 带 traceId', () => {
    expect(getErrorMessage(new ApiError(50001, '内部错误', 't1'))).toContain('traceId: t1')
  })

  it('非 5xx 直接展示 message', () => {
    expect(getErrorMessage(new ApiError(40001, '字段非法'))).toBe('字段非法')
  })

  it('非 ApiError 退回 Error.message 或兜底文案', () => {
    expect(getErrorMessage(new Error('网络错误'))).toBe('网络错误')
    expect(getErrorMessage('未知')).toBe('请求失败')
  })
})

describe('traceId 与幂等键', () => {
  it('newTraceId 为 32 位小写 hex', () => {
    expect(newTraceId()).toMatch(/^[0-9a-f]{32}$/)
  })

  it('newIdempotencyKey 为 UUID', () => {
    expect(newIdempotencyKey()).toMatch(/^[0-9a-f-]{36}$/)
  })
})

describe('resolveIdempotencyKey', () => {
  it('写请求无已有键时生成 UUID', () => {
    expect(resolveIdempotencyKey('POST')).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('写请求已有键时复用', () => {
    expect(resolveIdempotencyKey('POST', 'fixed-key')).toBe('fixed-key')
  })

  it('读请求不生成', () => {
    expect(resolveIdempotencyKey('GET')).toBeUndefined()
  })
})
