import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
const saveDocumentBlocks = vi.hoisted(() => vi.fn())
const exportDocumentDocx = vi.hoisted(() => vi.fn())
const uploadDocumentImage = vi.hoisted(() => vi.fn())
vi.mock('../api/documents', () => ({
  getDocument, saveDocumentBlocks, exportDocumentDocx, uploadDocumentImage,
  getDocumentImage: vi.fn(), reindexDocument: vi.fn(),
}))
vi.mock('../api/collaboration', () => ({
  listAnnotations: vi.fn().mockResolvedValue([]),
  listPresence: vi.fn().mockResolvedValue([]),
  heartbeatPresence: vi.fn().mockResolvedValue(undefined),
  createAnnotation: vi.fn(),
  deleteAnnotation: vi.fn(),
  resolveAnnotation: vi.fn(),
  listCollaborators: vi.fn().mockResolvedValue([]),
  addCollaborator: vi.fn(),
  removeCollaborator: vi.fn(),
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { userId: 'U1', username: 't', roles: ['TEACHER'], permissions: [
      'DOCUMENT_READ', 'DOCUMENT_EDIT', 'AI_SUMMARY', 'AI_KEYPOINT', 'AI_QA', 'AI_POLISH',
    ], courseScope: [] },
    loading: false, login: vi.fn(), logout: vi.fn(),
  }),
}))

import DocumentView from './DocumentView'
import type { Preview } from '../api/ai'

const preview: Preview = {
  documentId: 'D1', versionId: 'V1', versionNo: 1, structure: [], vectorReady: true,
  blocks: [
    { chunkId: 'C1', chunkNo: 1, headingPath: '第一章', pageNo: 1, charStart: 0, charEnd: 4, text: 'hello' },
    { chunkId: 'C2', chunkNo: 2, headingPath: null, pageNo: 2, charStart: 5, charEnd: 10, text: '\nworld' },
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
    saveDocumentBlocks.mockResolvedValue({ versionId: 'V2', versionNo: 2 })
    exportDocumentDocx.mockResolvedValue(new Blob(['docx']))
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

  it('有结构层时渲染标题/表格，且块容器 id 与 testid 不变', async () => {
    api.getPreview.mockResolvedValue({
      ...preview,
      structure: [
        { no: 1, kind: 'HEADING', level: 2, text: '第一章', anchor: 0 },
        { no: 2, kind: 'HEADING', level: 2, start: 0, end: 4 },
        { no: 3, kind: 'TABLE_CELL', tableNo: 1, row: 1, col: 1, start: 5, end: 10 },
      ],
    })
    renderView()
    const b1 = await screen.findByTestId('block-1')
    const headings = [...b1.querySelectorAll('h2')].map((h) => h.textContent)
    expect(headings.some((t) => t?.includes('第一章'))).toBe(true)
    expect(headings.some((t) => t?.includes('hello'))).toBe(true)
    expect(screen.getByTestId('block-2').querySelector('td')?.textContent).toContain('world')
  })

  it('润色选择按结构块：一个正文段一个勾选单位，标题/代码不出现勾选框', async () => {
    api.getPreview.mockResolvedValue({
      ...preview,
      structure: [
        { no: 1, kind: 'HEADING', level: 1, start: 0, end: 4 },
        { no: 2, kind: 'PARAGRAPH', start: 5, end: 10 },
      ],
    })
    api.polishParagraphs.mockResolvedValue([])
    renderView()
    await screen.findByTestId('block-1')
    const boxes = screen.getAllByRole('checkbox')
    expect(boxes).toHaveLength(1) // 只有正文段可勾选
    fireEvent.click(boxes[0])
    fireEvent.click(screen.getByText('润色'))
    expect(await screen.findByText(/已选 1 段/)).toBeTruthy()
    fireEvent.click(screen.getByTestId('run-polish'))
    await waitFor(() => expect(api.polishParagraphs).toHaveBeenCalledWith('D1',
      [expect.objectContaining({ text: 'world' })]))
  })

  it('协作文档只保留批注/问答/格式校验，隐藏编辑、导出、摘要、要点、润色', async () => {
    getDocument.mockResolvedValue({ ...docDetail, relation: 'COLLAB' })
    renderView()
    await screen.findByTestId('block-1')
    expect(screen.getByRole('tab', { name: '批注' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '问答' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '格式校验' })).toBeTruthy()
    expect(screen.queryByRole('tab', { name: '摘要' })).toBeNull()
    expect(screen.queryByRole('tab', { name: '要点' })).toBeNull()
    expect(screen.queryByRole('tab', { name: '润色' })).toBeNull()
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull()
    expect(screen.queryByText('导出 Word')).toBeNull()
  })

  it('批注页点击结构块即可选中，标题显示一级标题而不是正文', async () => {
    api.getPreview.mockResolvedValue({
      ...preview,
      structure: [
        { no: 1, kind: 'HEADING', level: 1, text: '第一章', anchor: 0 },
        { no: 2, kind: 'PARAGRAPH', start: 1, end: 4 },
      ],
    })
    renderView()
    await screen.findByTestId('block-1')
    fireEvent.click(screen.getByRole('tab', { name: '批注' }))
    fireEvent.click(document.querySelector('.doc-structure h1') as HTMLElement)
    expect(await screen.findByText(/一级标题 · 第一章/)).toBeTruthy()
  })

  it('块编辑：编辑 → + 正文 → 填内容 → 保存调用 saveDocumentBlocks', async () => {
    renderView()
    await screen.findByTestId('block-1')
    fireEvent.click(screen.getByRole('button', { name: /编\s*辑/ }))
    const texts = screen.getAllByPlaceholderText('内容…')
    expect(texts.length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: /添加块/ }))
    fireEvent.click(screen.getByRole('menuitem', { name: '正文' }))
    const afterAdd = screen.getAllByPlaceholderText('内容…')
    fireEvent.change(afterAdd[afterAdd.length - 1], { target: { value: '新段落' } })
    fireEvent.click(screen.getByRole('button', { name: '保存为新版本' }))
    await waitFor(() => expect(saveDocumentBlocks).toHaveBeenCalledTimes(1))
    const [id, body] = saveDocumentBlocks.mock.calls[0]
    expect(id).toBe('D1')
    expect(body.baseVersionId).toBe('V1')
    expect(body.blocks.some((b: { text: string }) => b.text === '新段落')).toBe(true)
  })

  it('+ 下拉包含「图片」选项，选择后出现图片上传入口', async () => {
    renderView()
    await screen.findByTestId('block-1')
    fireEvent.click(screen.getByRole('button', { name: /编\s*辑/ }))
    fireEvent.click(screen.getByRole('button', { name: /添加块/ }))
    expect(screen.getByRole('menuitem', { name: '图片' })).toBeTruthy()
    fireEvent.click(screen.getByRole('menuitem', { name: '图片' }))
    expect(await screen.findByText('尚未上传图片')).toBeTruthy()
    expect(screen.getByRole('button', { name: '上传图片' })).toBeTruthy()
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
    const tag = await screen.findByText('块 2')
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
