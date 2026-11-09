import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))

import { get, post } from './http'
import {
  clearCheckId,
  createConversion,
  createFormatCheck,
  fixFormatIssues,
  getConversion,
  getFormatCheck,
  listConversions,
  listElements,
  listFormatIssues,
  loadCheckId,
  parseLocation,
  saveCheckId,
} from './format'

describe('format api（D-08 端点封装）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(get).mockResolvedValue({})
    vi.mocked(post).mockResolvedValue({})
  })

  it('校验任务三端点路径', async () => {
    await createFormatCheck('D1')
    expect(post).toHaveBeenNthCalledWith(1, '/documents/D1/format-checks')
    await getFormatCheck('C1')
    expect(get).toHaveBeenNthCalledWith(1, '/format-checks/C1')
    await listFormatIssues('C1', { severity: 'BLOCKER', page: 2, pageSize: 50 })
    expect(get).toHaveBeenNthCalledWith(2, '/format-checks/C1/issues', {
      params: { severity: 'BLOCKER', page: 2, pageSize: 50 },
    })
  })

  it('修复/元素/转换路径', async () => {
    await fixFormatIssues('C1', ['I1', 'I2'])
    expect(post).toHaveBeenNthCalledWith(1, '/format-checks/C1/fix', { issueIds: ['I1', 'I2'] })
    await listElements('D1')
    expect(get).toHaveBeenNthCalledWith(1, '/documents/D1/elements')
    await createConversion('D1', 'PDF')
    expect(post).toHaveBeenNthCalledWith(2, '/conversions', { documentId: 'D1', targetFormat: 'PDF' })
    await getConversion('T1')
    expect(get).toHaveBeenNthCalledWith(2, '/conversions/T1')
    await listConversions('D1')
    expect(get).toHaveBeenNthCalledWith(3, '/conversions', { params: { documentId: 'D1' } })
  })

  it('轮询断点 key 存取清', () => {
    localStorage.clear()
    expect(loadCheckId('D1')).toBeNull()
    saveCheckId('D1', 'C9')
    expect(loadCheckId('D1')).toBe('C9')
    clearCheckId('D1')
    expect(loadCheckId('D1')).toBeNull()
  })

  it('parseLocation 容忍坏值', () => {
    expect(parseLocation(null)).toBeNull()
    expect(parseLocation('not-json')).toBeNull()
    expect(parseLocation('{"page":1}')).toBeNull()
    expect(parseLocation('{"paragraphIndex":3,"charStart":0,"charEnd":9}'))
      .toEqual({ paragraphIndex: 3, charStart: 0, charEnd: 9 })
  })
})
