import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/shares', () => ({
  createShare: vi.fn(),
  listShares: vi.fn(),
  revokeShare: vi.fn(),
}))
vi.mock('../api/collaboration', () => ({
  listCollaborators: vi.fn(),
  listCollaboratorCandidates: vi.fn(),
  addCollaborator: vi.fn(),
  removeCollaborator: vi.fn(),
}))

import { createShare, listShares } from '../api/shares'
import {
  addCollaborator,
  listCollaboratorCandidates,
  listCollaborators,
} from '../api/collaboration'
import ShareModal from './ShareModal'

const row = {
  id: 'S1', token: 'tok1', path: '/s/tok1', permission: 'VIEW' as const,
  status: 'A' as const, expiresAt: '2026-11-21T10:00:00Z', createTime: '2026-11-14T10:00:00Z',
}

describe('共享弹窗', () => {
  beforeEach(() => {
    vi.mocked(listShares).mockResolvedValue([row])
    vi.mocked(listCollaborators).mockResolvedValue([])
    vi.mocked(listCollaboratorCandidates).mockResolvedValue([])
    vi.mocked(addCollaborator).mockResolvedValue({
      id: 'M1', userId: 'U2', username: '2023001', displayName: '陈同学',
      permission: 'COMMENT', joinedAt: null,
    })
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

    fireEvent.click(screen.getByText('生成只读链接'))

    await waitFor(() => expect(createShare).toHaveBeenCalledWith('D1', { expiresInDays: 7, permission: 'VIEW' }))
    expect(await screen.findByText('链接已生成')).toBeTruthy()
    expect((screen.getByDisplayValue(/\/s\/tok-new/) as HTMLInputElement).value)
      .toBe(`${window.location.origin}/s/tok-new`)
  })

  it('未关联课程时禁用课程成员邀请并说明原因', async () => {
    render(<ShareModal open documentId="D1" onClose={() => undefined} />)
    expect((await screen.findAllByText(/未关联课程/)).length).toBeGreaterThan(0)
    expect(listCollaboratorCandidates).not.toHaveBeenCalled()
  })

  it('关联课程时加载教师/学生候选并展示现有协作者', async () => {
    vi.mocked(listCollaboratorCandidates).mockResolvedValue([{
      userId: 'U2', username: '2023001', displayName: '陈同学', role: 'STUDENT', groupName: '课程组 3',
    }])
    vi.mocked(listCollaborators).mockResolvedValue([{
      id: 'M1', userId: 'U3', username: 'teacher', displayName: '王老师',
      permission: 'COMMENT', joinedAt: null,
    }])
    render(<ShareModal open documentId="D1" courseId="C1" onClose={() => undefined} />)
    await waitFor(() => expect(listCollaboratorCandidates).toHaveBeenCalledWith('D1'))
    expect(await screen.findByText('王老师')).toBeTruthy()
    expect(screen.getByText('邀请协作者（可批注）')).toBeTruthy()
  })

  it('展示撤销后的状态', async () => {
    vi.mocked(listShares).mockResolvedValue([{ ...row, status: 'R' }])
    render(<ShareModal open documentId="D1" onClose={() => undefined} />)
    expect(await screen.findByText('已撤销')).toBeTruthy()
    expect(screen.queryByText('撤销', { selector: 'button' })).toBeNull()
  })
})