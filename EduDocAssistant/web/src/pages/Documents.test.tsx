import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/documents', () => ({
  listCourses: vi.fn(),
  listDocuments: vi.fn(),
  deleteDocument: vi.fn(),
  restoreDocument: vi.fn(),
  reparseDocument: vi.fn(),
}))
vi.mock('./DocumentUploadModal', () => ({ default: () => <div data-testid="upload-modal" /> }))
vi.mock('./DocumentDetailDrawer', () => ({ default: () => <div data-testid="detail-drawer" /> }))

import { listCourses, listDocuments, reparseDocument, restoreDocument } from '../api/documents'
import Documents from './Documents'
import type { DocumentSummary } from '../api/documents'

function row(over: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: 'D1', title: '第一课件', docType: 'COURSEWARE', status: 'READY',
    courseId: 'C1', courseName: '计算机导论', sizeBytes: 2048, mimeType: 'application/pdf',
    versionNo: 1, createTime: '2026-11-03T10:00:00', updateTime: '2026-11-03T10:00:00', ...over,
  }
}

function page(items: DocumentSummary[]) {
  return { items, total: items.length, page: 1, pageSize: 20 }
}

describe('Documents', () => {
  beforeEach(() => {
    vi.mocked(listCourses).mockResolvedValue([{ id: 'C1', courseCode: 'CS101', courseName: '计算机导论', termCode: '2026-FALL', memberRole: 'TEACHER' }])
    vi.mocked(listDocuments).mockResolvedValue(page([row()]))
  })
  afterEach(() => vi.clearAllMocks())

  it('渲染列表：标题、状态文字徽标、课程列（教师）', async () => {
    render(<Documents role="TEACHER" />)
    expect(await screen.findByText('第一课件')).toBeTruthy()
    expect(screen.getByText('就绪')).toBeTruthy()
    expect(screen.getByText('计算机导论', { selector: 'td' })).toBeTruthy()
    expect(listDocuments).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 20 }))
  })

  it('学生视图不渲染课程列', async () => {
    render(<Documents role="STUDENT" />)
    await screen.findByText('第一课件')
    expect(screen.queryByText('课程', { selector: 'th' })).toBeNull()
  })

  it('回收站视图带 status=DELETED 且行操作为恢复', async () => {
    vi.mocked(listDocuments).mockResolvedValue(page([row({ status: 'DELETED', title: '已删文档' })]))
    render(<Documents role="STUDENT" />)
    fireEvent.click(await screen.findByText('回收站'))
    await waitFor(() => expect(listDocuments).toHaveBeenCalledWith(expect.objectContaining({ status: 'DELETED' })))
    vi.mocked(restoreDocument).mockResolvedValue(undefined)
    fireEvent.click(await screen.findByText('恢复'))
    await waitFor(() => expect(restoreDocument).toHaveBeenCalledWith('D1'))
  })

  it('FAILED 行提供重新解析', async () => {
    vi.mocked(listDocuments).mockResolvedValue(page([row({ status: 'FAILED', title: '坏文档' })]))
    vi.mocked(reparseDocument).mockResolvedValue({ taskId: 'T1' })
    render(<Documents role="STUDENT" />)
    fireEvent.click(await screen.findByText('重新解析'))
    await waitFor(() => expect(reparseDocument).toHaveBeenCalledWith('D1'))
    expect(await screen.findByText('解析失败')).toBeTruthy()
  })

  it('READY 行提供预览入口（C-10）', async () => {
    render(<Documents role="TEACHER" />)
    fireEvent.click(await screen.findByText('预览'))
    expect(navigateMock).toHaveBeenCalledWith('documents/D1/view')
  })

  it('状态筛选透传 status 且重置页码（1104 审查 P2-1）', async () => {
    render(<Documents role="STUDENT" />)
    await screen.findByText('第一课件')
    fireEvent.mouseDown(screen.getByText('按状态筛选'))
    fireEvent.click(await screen.findByText('解析中', { selector: '.ant-select-item-option-content' }))
    await waitFor(() =>
      expect(listDocuments).toHaveBeenCalledWith(expect.objectContaining({ status: 'PARSING', page: 1 })),
    )
  })

  it('存在 PARSING 行时轮询，全部终态后停止', async () => {
    // fake timers 下不用 RTL 的 findBy/waitFor（内部计时器同被 mock 会死锁），只断言调用计数
    vi.useFakeTimers()
    try {
      vi.mocked(listDocuments).mockResolvedValue(page([row({ status: 'PARSING' })]))
      render(<Documents role="STUDENT" />)
      await vi.advanceTimersByTimeAsync(0)
      const initial = vi.mocked(listDocuments).mock.calls.length
      expect(initial).toBe(1)
      await vi.advanceTimersByTimeAsync(3100)
      expect(vi.mocked(listDocuments).mock.calls.length).toBeGreaterThan(initial)
      // 全部转 READY 后停止轮询
      vi.mocked(listDocuments).mockResolvedValue(page([row({ status: 'READY' })]))
      await vi.advanceTimersByTimeAsync(3100)
      const settled = vi.mocked(listDocuments).mock.calls.length
      await vi.advanceTimersByTimeAsync(9500)
      expect(vi.mocked(listDocuments).mock.calls.length).toBe(settled)
    } finally {
      vi.useRealTimers()
    }
  })
})
