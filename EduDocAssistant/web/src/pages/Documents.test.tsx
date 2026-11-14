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
  listDocumentVersions: vi.fn(),
  restoreDocumentVersion: vi.fn(),
}))
vi.mock('../api/ai', () => ({ getPreview: vi.fn() }))
vi.mock('./DocumentUploadModal', () => ({ default: () => <div data-testid="upload-modal" /> }))
vi.mock('./ConversionModal', () => ({ default: () => <div data-testid="conv-modal" /> }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: null }) }))
vi.mock('./DocumentDetailDrawer', () => ({ default: () => <div data-testid="detail-drawer" /> }))

import { listCourses, listDocumentVersions, listDocuments, reparseDocument, restoreDocument, restoreDocumentVersion } from '../api/documents'
import { getPreview } from '../api/ai'
import Documents from './Documents'
import type { DocumentSummary, DocumentVersionRow } from '../api/documents'

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

  it('READY 行预览入口用相对段 {id}/view（1106 P1-1，配合 App 路由 .../documents 解析为 .../documents/{id}/view）', async () => {
    render(<Documents role="TEACHER" />)
    fireEvent.click(await screen.findByText('预览'))
    expect(navigateMock).toHaveBeenCalledWith('D1/view')
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

  // ==================== 验收决议④：版本历史 Drawer ====================

  it('行「版本」打开 Drawer：降序列表+当前标注+签名下载（决议④）', async () => {
    vi.mocked(listDocumentVersions).mockResolvedValue([
      { versionId: 'V2', versionNo: 2, versionType: 'EDIT', createTime: '2026-11-09T10:00:00',
        changeSummary: '一键修复', downloadUrl: 'https://oss/signed-v2', current: true },
      { versionId: 'V1', versionNo: 1, versionType: 'UPLOAD', createTime: '2026-11-09T09:00:00',
        changeSummary: null, downloadUrl: null, current: false },
    ])
    render(<Documents role="STUDENT" />)
    fireEvent.click(await screen.findByText('版本', { selector: 'a' }))
    expect(await screen.findByText(/版本历史：第一课件/)).toBeTruthy()
    await waitFor(() => expect(listDocumentVersions).toHaveBeenCalledWith('D1'))
    expect(await screen.findByText('当前')).toBeTruthy()
    expect(screen.getByText('一键修复')).toBeTruthy()
    expect(screen.getByRole('button', { name: '下载' })).toBeTruthy()
    expect(screen.getByText(/恢复会复制为新版本/)).toBeTruthy()
  })

  it('历史版本可恢复：调用恢复接口并刷新版本链', async () => {
    const versions: DocumentVersionRow[] = [
      { versionId: 'V2', versionNo: 2, versionType: 'EDIT', createTime: '2026-11-09T10:00:00',
        changeSummary: '一键修复', downloadUrl: null, current: true },
      { versionId: 'V1', versionNo: 1, versionType: 'UPLOAD', createTime: '2026-11-09T09:00:00',
        changeSummary: null, downloadUrl: null, current: false },
    ]
    vi.mocked(listDocumentVersions).mockResolvedValue(versions)
    vi.mocked(restoreDocumentVersion).mockResolvedValue({ versionId: 'V3', versionNo: 3 })
    render(<Documents role="STUDENT" />)
    fireEvent.click(await screen.findByText('版本', { selector: 'a' }))
    fireEvent.click(await screen.findByText('恢复'))
    fireEvent.click(await screen.findByText('确认恢复'))
    await waitFor(() => expect(restoreDocumentVersion).toHaveBeenCalledWith('D1', 'V1'))
    await waitFor(() => expect(listDocumentVersions).toHaveBeenCalledTimes(2))
  })

  it('历史版本对比：两版文本走 preview 并渲染行级增删', async () => {
    vi.mocked(listDocumentVersions).mockResolvedValue([
      { versionId: 'V2', versionNo: 2, versionType: 'EDIT', createTime: '2026-11-09T10:00:00',
        changeSummary: null, downloadUrl: null, current: true },
      { versionId: 'V1', versionNo: 1, versionType: 'UPLOAD', createTime: '2026-11-09T09:00:00',
        changeSummary: null, downloadUrl: null, current: false },
    ])
    const preview = (text: string, versionId: string) => ({
      documentId: 'D1', versionId, versionNo: 1, vectorReady: true,
      blocks: [{ chunkId: 'C1', chunkNo: 1, headingPath: null, pageNo: 1, charStart: 0,
        charEnd: text.length - 1, text }],
    })
    vi.mocked(getPreview).mockImplementation(async (_id: string, versionId?: string) =>
      versionId === 'V1' ? preview('旧版本内容', 'V1') : preview('当前版本内容', 'V2'))
    render(<Documents role="STUDENT" />)
    fireEvent.click(await screen.findByText('版本', { selector: 'a' }))
    fireEvent.click(await screen.findByText('对比'))
    expect(await screen.findByText(/版本对比/)).toBeTruthy()
    expect(await screen.findByText(/旧版本内容/)).toBeTruthy()
    expect(await screen.findByText(/当前版本内容/)).toBeTruthy()
  })

  it('非 READY 状态行同样有版本入口；接口失败给干净 Alert', async () => {
    vi.mocked(listDocumentVersions).mockRejectedValue(new Error('boom'))
    render(<Documents role="STUDENT" />)
    fireEvent.click(await screen.findByText('版本', { selector: 'a' }))
    expect(await screen.findByText('boom')).toBeTruthy()
  })
})
