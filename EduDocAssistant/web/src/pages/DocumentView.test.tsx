import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
const api = vi.hoisted(() => ({
  getPreview: vi.fn(),
  createSummaryTask: vi.fn(),
  getAiTask: vi.fn(),
  cancelAiTask: vi.fn(),
  fetchExportBlob: vi.fn(),
  saveTaskId: vi.fn(),
  loadTaskId: vi.fn((): string | null => null),
  clearTaskId: vi.fn(),
  listQaSessions: vi.fn().mockResolvedValue([]),
  listQaMessages: vi.fn().mockResolvedValue([]),
  createQaSession: vi.fn(),
  askQa: vi.fn(),
  createKeypointTask: vi.fn(),
  polishParagraphs: vi.fn(),
  polishDecision: vi.fn(),
}))
vi.mock('../api/ai', () => api)
const getDocument = vi.hoisted(() => vi.fn())
vi.mock('../api/documents', () => ({ getDocument }))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { userId: 'U1', username: 't', roles: ['TEACHER'], permissions: [
      'DOCUMENT_READ', 'AI_SUMMARY', 'AI_KEYPOINT', 'AI_QA', 'AI_POLISH',
    ], courseScope: [] },
    loading: false, login: vi.fn(), logout: vi.fn(),
  }),
}))

import DocumentView from './DocumentView'
import type { Preview } from '../api/ai'

const preview: Preview = {
  documentId: 'D1', versionId: 'V1', versionNo: 1, vectorReady: true,
  blocks: [
    { chunkId: 'C1', chunkNo: 1, headingPath: '第一章', pageNo: 1, charStart: 0, charEnd: 4, text: 'hello' },
    { chunkId: 'C2', chunkNo: 2, headingPath: null, pageNo: 2, charStart: 5, charEnd: 9, text: '\nworld' },
  ],
}

const docDetail = {
  id: 'D1', title: '课件', docType: 'COURSEWARE' as const, status: 'READY' as const,
  courseId: null, courseName: null, sizeBytes: 1, mimeType: null, versionNo: 1,
  createTime: '', updateTime: '', currentVersionId: 'V1', contentHash: null,
  chunkCount: 2, deletedAt: null, purgeAt: null,
}

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/teacher/documents/D1/view']}>
      <Routes>
        <Route path="/teacher/documents/:docId/view" element={<DocumentView />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('DocumentView（C-10）', () => {
  beforeEach(() => {
    api.getPreview.mockResolvedValue(preview)
    getDocument.mockResolvedValue(docDetail)
    api.loadTaskId.mockReturnValue(null)
  })

  it('按块序渲染正文与页分隔、标题路径徽标', async () => {
    renderView()
    const b1 = await screen.findByTestId('block-1')
    const b2 = screen.getByTestId('block-2')
    expect(b1.textContent).toContain('hello')
    expect(b2.textContent).toContain('world')
    expect(b1.compareDocumentPosition(b2) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.getByText('— 第 1 页 —')).toBeTruthy()
    expect(screen.getByText('— 第 2 页 —')).toBeTruthy()
    expect(screen.getByText('第一章')).toBeTruthy()
  })

  it('摘要句「定位」点击 → 正文出现 <mark> 高亮（引用回跳贯通）', async () => {
    api.loadTaskId.mockReturnValue('T1')
    api.getAiTask.mockResolvedValue({
      taskId: 'T1', documentId: 'D1', taskType: 'SUMMARY', status: 'SUCCEEDED', progress: 100,
      errorCode: null,
      result: {
        resultId: 'R1', resultType: 'SUMMARY', contentText: 'x',
        structuredJson: JSON.stringify({ segments: [{ text: '摘要句', chunkIds: ['C2'] }] }),
        modelName: 'K', promptVersion: 'v1', inputTokens: null, outputTokens: null, citations: [],
      },
    })
    renderView()
    // 右侧切到摘要 Tab（默认即摘要页），点块引用 Tag 触发 onHighlight(5,9)
    const tag = await screen.findByText('#2')
    tag.click()
    await waitFor(() => expect(document.querySelectorAll('mark').length).toBeGreaterThan(0))
    expect(api.getAiTask).toHaveBeenCalledWith('T1')
  })

  it('vectorReady=false：显示索引横幅且禁用生成入口（1106 P2-1）', async () => {
    api.getPreview.mockResolvedValue({ ...preview, vectorReady: false })
    renderView()
    expect((await screen.findAllByText(/新版索引中/)).length).toBeGreaterThanOrEqual(1)
    expect((screen.getByTestId('gen-摘要') as HTMLButtonElement).disabled).toBe(true)
  })

  it('vectorReady false→true 自动恢复：持续轮询并解禁入口（1106 复审 P1）', async () => {
    api.getPreview
      .mockResolvedValueOnce({ ...preview, vectorReady: false })
      .mockResolvedValue({ ...preview, vectorReady: true })
    renderView()
    expect((await screen.findAllByText(/新版索引中/)).length).toBeGreaterThanOrEqual(1)
    expect((screen.getByTestId('gen-摘要') as HTMLButtonElement).disabled).toBe(true)
    await vi.waitFor(() => {
      expect((screen.getByTestId('gen-摘要') as HTMLButtonElement).disabled).toBe(false)
    }, { timeout: 4500, interval: 100 })
    expect(api.getPreview.mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('PARSING 文档轮询预览直至 READY（R3 新版索引中提示）', async () => {
    getDocument.mockResolvedValue({ ...docDetail, status: 'PARSING' })
    renderView()
    expect(await screen.findByText(/索引重建中/)).toBeTruthy()
    expect((await screen.findAllByText(/新版索引中/)).length).toBeGreaterThanOrEqual(1)
    getDocument.mockResolvedValue(docDetail)
    await vi.waitFor(() => expect(getDocument.mock.calls.length).toBeGreaterThanOrEqual(2),
      { timeout: 4000, interval: 100 })
  })
})
