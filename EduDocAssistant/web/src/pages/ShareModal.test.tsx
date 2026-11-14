import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/shares', () => ({
  createShare: vi.fn(),
  listShares: vi.fn(),
  revokeShare: vi.fn(),
}))

import { createShare, listShares } from '../api/shares'
import ShareModal from './ShareModal'

const row = {
  id: 'S1', token: 'tok1', path: '/s/tok1', permission: 'VIEW' as const,
  status: 'A' as const, expiresAt: '2026-11-21T10:00:00Z', createTime: '2026-11-14T10:00:00Z',
}

describe('共享弹窗', () => {
  beforeEach(() => {
    vi.mocked(listShares).mockResolvedValue([row])
  })
  afterEach(() => vi.clearAllMocks())

  it('列出已有链接与状态，创建后展示可复制链接', async () => {
    vi.mocked(createShare).mockResolvedValue({
      shareId: 'S2', token: 'tok-new', path: '/s/tok-new', permission: 'COMMENT',
      expiresAt: '2026-11-21T10:00:00Z',
    })
    render(<ShareModal open documentId="D1" documentTitle="报告" onClose={() => undefined} />)

    expect(await screen.findByText('有效')).toBeTruthy()
    expect(listShares).toHaveBeenCalledWith('D1')

    fireEvent.click(screen.getByText('生成共享链接'))

    await waitFor(() => expect(createShare).toHaveBeenCalledWith('D1', { expiresInDays: 7, permission: 'VIEW' }))
    expect(await screen.findByText('链接已生成')).toBeTruthy()
    expect((screen.getByDisplayValue(/\/s\/tok-new/) as HTMLInputElement).value)
      .toBe(`${window.location.origin}/s/tok-new`)
  })

  it('展示撤销后的状态', async () => {
    vi.mocked(listShares).mockResolvedValue([{ ...row, status: 'R' }])
    render(<ShareModal open documentId="D1" onClose={() => undefined} />)
    expect(await screen.findByText('已撤销')).toBeTruthy()
    expect(screen.queryByText('撤销', { selector: 'button' })).toBeNull()
  })
})