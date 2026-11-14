import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/documents', () => ({ listDocuments: vi.fn() }))
vi.mock('../../api/collaboration', () => ({
  addCollaborator: vi.fn(),
  deleteAnnotation: vi.fn(),
  heartbeatPresence: vi.fn().mockResolvedValue(undefined),
  listAnnotations: vi.fn(),
  listCollaborators: vi.fn(),
  listPresence: vi.fn(),
  removeCollaborator: vi.fn(),
  resolveAnnotation: vi.fn(),
}))

import { listDocuments } from '../../api/documents'
import {
  addCollaborator,
  listAnnotations,
  listCollaborators,
  listPresence,
  resolveAnnotation,
} from '../../api/collaboration'
import Collab from './Collab'

const doc = {
  id: 'D1', title: '课程设计报告', docType: 'REPORT' as const, status: 'READY' as const,
  courseId: 'C1', courseName: 'Web开发技术', sizeBytes: 1024, mimeType: 'text/plain',
  versionNo: 2, createTime: '2026-11-14T10:00:00Z', updateTime: '2026-11-14T10:00:00Z',
}

const annotation = {
  id: 'A1', documentId: 'D1', documentVersionId: 'V1', authorId: 'U1',
  authorName: '李老师', authorAccount: 'teacher', charStart: 12, charEnd: 30,
  content: '补充权限校验流程', status: 'O' as const, resolvedBy: null, resolvedByName: null,
  resolvedTime: null, createTime: '2026-11-14T10:00:00Z', canDelete: true, canResolve: true,
}

const collaborator = {
  id: 'M1', userId: 'U2', username: '2023001', displayName: '陈同学',
  permission: 'COMMENT' as const, joinedAt: '2026-11-14T10:00:00Z',
}

describe('协作与版本（批注/协作者/在线已接真）', () => {
  beforeEach(() => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [doc], total: 1, page: 1, pageSize: 100 })
    vi.mocked(listAnnotations).mockResolvedValue([annotation])
    vi.mocked(listPresence).mockResolvedValue([
      { userId: 'U1', displayName: '张同学', role: 'STUDENT', lastSeen: '2026-11-14T10:00:00Z' },
      { userId: 'U2', displayName: '李老师', role: 'TEACHER', lastSeen: '2026-11-14T10:00:00Z' },
    ])
    vi.mocked(listCollaborators).mockResolvedValue([collaborator])
  })
  afterEach(() => vi.clearAllMocks())

  it('在线成员与批注来自真实接口，3 秒轮询刷新', async () => {
    render(<Collab role="STUDENT" />)
    expect(await screen.findByText('协作与版本')).toBeTruthy()
    expect(await screen.findByText(/2 人在线/)).toBeTruthy()
    expect(await screen.findByText('补充权限校验流程')).toBeTruthy()
    expect(screen.getByText('字符 12-30')).toBeTruthy()
    await waitFor(() => expect(listAnnotations).toHaveBeenCalledWith('D1'))
    expect(listPresence).toHaveBeenCalledWith('D1')
  })

  it('批注可标记解决（走真实接口）', async () => {
    vi.mocked(resolveAnnotation).mockResolvedValue({ ...annotation, status: 'R' })
    render(<Collab role="STUDENT" />)
    fireEvent.click(await screen.findByText('标记解决'))
    await waitFor(() => expect(resolveAnnotation).toHaveBeenCalledWith('A1', true))
    expect(await screen.findByText('批注已标记为已解决')).toBeTruthy()
  })

  it('版本记录仍为演示数据：恢复生成新版本且不覆盖历史', async () => {
    render(<Collab role="STUDENT" />)
    fireEvent.click(await screen.findByRole('tab', { name: '版本记录' }))
    expect(await screen.findByText('v6')).toBeTruthy()
    const restore = screen
      .getAllByText('恢复')
      .map((el) => el.closest('button'))
      .find((b) => b && !b.disabled)!
    fireEvent.click(restore)
    fireEvent.click(await screen.findByText('确认恢复'))
    expect(await screen.findByText('v7')).toBeTruthy()
  })

  it('共享设置：协作者列表接真，添加协作者调用接口', async () => {
    vi.mocked(addCollaborator).mockResolvedValue({
      id: 'M2', userId: 'U3', username: '2023002', displayName: '刘同学',
      permission: 'COMMENT', joinedAt: '2026-11-14T10:00:00Z',
    })
    render(<Collab role="STUDENT" />)
    fireEvent.click(await screen.findByRole('tab', { name: '共享设置' }))
    expect(await screen.findByText('2023001')).toBeTruthy()
    expect(screen.getAllByText('陈同学').length).toBeGreaterThan(0)

    fireEvent.click(screen.getByText('添加协作者'))
    fireEvent.change(await screen.findByPlaceholderText('如 student 或 2023001'),
      { target: { value: '2023002' } })
    fireEvent.click(screen.getByRole('button', { name: /^添\s加$/ }))

    await waitFor(() => expect(addCollaborator).toHaveBeenCalledWith('D1',
      { username: '2023002', permission: 'COMMENT' }))
  })
})