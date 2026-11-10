import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({
  get: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 1, pageSize: 10 })),
  patch: vi.fn(() => Promise.resolve({ updated: 0 })),
}))

import { get, patch } from './http'
import { listMessages, markRead, readAll, unreadCount } from './messages'

const g = vi.mocked(get)
const p = vi.mocked(patch)

describe('messages API 契约（06 切片2，过滤参数是 unread 不是 read）', () => {
  beforeEach(() => vi.clearAllMocks())

  it('列表按 type/unread/page/pageSize 传参', async () => {
    await listMessages({ type: 'SUBMISSION', unread: true, page: 2, pageSize: 20 })
    expect(g).toHaveBeenCalledWith('/messages', {
      params: { type: 'SUBMISSION', unread: true, page: 2, pageSize: 20 },
    })
    await listMessages({})
    expect(g).toHaveBeenCalledWith('/messages', { params: {} })
  })

  it('未读数走单独端点，不拉全量', async () => {
    await unreadCount()
    expect(g).toHaveBeenCalledWith('/messages/unread-count')
  })

  it('批量已读走 PATCH messages:read，body 是 {ids,read}', async () => {
    await markRead(['M1', 'M2'], true)
    expect(p).toHaveBeenCalledWith('/messages:read', { ids: ['M1', 'M2'], read: true })
    await markRead(['M1'], false)
    expect(p).toHaveBeenCalledWith('/messages:read', { ids: ['M1'], read: false })
  })

  it('全部已读走 PATCH messages:read-all，无 body', async () => {
    await readAll()
    expect(p).toHaveBeenCalledWith('/messages:read-all')
  })
})
