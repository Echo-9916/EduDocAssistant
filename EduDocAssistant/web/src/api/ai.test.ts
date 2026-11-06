import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('./token', () => ({ getToken: vi.fn(() => 'TOK') }))

import { get, post } from './http'
import {
  askQa,
  cancelAiTask,
  clearTaskId,
  createKeypointTask,
  createQaSession,
  createSummaryTask,
  fetchExportBlob,
  getAiTask,
  getPreview,
  listQaMessages,
  listQaSessions,
  loadTaskId,
  polishDecision,
  polishParagraphs,
  saveTaskId,
} from './ai'

describe('ai api（C-10 八端点封装）', () => {
  beforeEach(() => vi.clearAllMocks())

  it('预览与任务端点路径', async () => {
    vi.mocked(get).mockResolvedValue({})
    vi.mocked(post).mockResolvedValue({})
    await getPreview('D1')
    expect(get).toHaveBeenNthCalledWith(1, '/documents/D1/preview', { params: {} })
    await getPreview('D1', 'V1')
    expect(get).toHaveBeenNthCalledWith(2, '/documents/D1/preview', { params: { versionId: 'V1' } })
    await createSummaryTask('D1')
    expect(post).toHaveBeenNthCalledWith(1, '/documents/D1/summaries')
    await createKeypointTask('D1')
    expect(post).toHaveBeenNthCalledWith(2, '/documents/D1/keypoints')
    await getAiTask('T1')
    expect(get).toHaveBeenNthCalledWith(3, '/ai-tasks/T1')
    await cancelAiTask('T1')
    expect(post).toHaveBeenNthCalledWith(3, '/ai-tasks/T1/cancel')
  })

  it('问答端点携带 body 与 query', async () => {
    vi.mocked(get).mockResolvedValue([])
    vi.mocked(post).mockResolvedValue({})
    await createQaSession('D1', 't')
    expect(post).toHaveBeenNthCalledWith(1, '/qa/sessions', { documentId: 'D1', title: 't' })
    await listQaSessions('D1')
    expect(get).toHaveBeenNthCalledWith(1, '/qa/sessions', { params: { documentId: 'D1' } })
    await askQa('S1', '问题')
    expect(post).toHaveBeenNthCalledWith(2, '/qa/sessions/S1/messages', { question: '问题' })
    await listQaMessages('S1')
    expect(get).toHaveBeenNthCalledWith(2, '/qa/sessions/S1/messages')
  })

  it('润色端点：段落数组与决策 body（含 charStart 消歧）', async () => {
    vi.mocked(post).mockResolvedValue([])
    await polishParagraphs('D1', [{ chunkId: 'C1', text: '甲' }, { text: '乙' }])
    expect(post).toHaveBeenNthCalledWith(1, '/documents/D1/polish', {
      paragraphs: [{ chunkId: 'C1', text: '甲' }, { text: '乙' }],
    })
    await polishDecision('P1', { decision: 'PARTIAL', acceptedText: '丙', charStart: 12 })
    expect(post).toHaveBeenNthCalledWith(2, '/polish/P1/decision', {
      decision: 'PARTIAL', acceptedText: '丙', charStart: 12,
    })
    await polishDecision('P1', { decision: 'REVERT' })
    expect(post).toHaveBeenNthCalledWith(3, '/polish/P1/decision', { decision: 'REVERT' })
  })

  it('fetchExportBlob 带 Authorization 头且非 2xx 抛错', async () => {
    const blob = new Blob(['x'])
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, blob: () => blob })
    vi.stubGlobal('fetch', fetchMock)
    await expect(fetchExportBlob('R1', 'docx')).resolves.toBe(blob)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/ai-results/R1/export?format=docx')
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer TOK')

    fetchMock.mockResolvedValue({ ok: false, status: 404 })
    await expect(fetchExportBlob('R1', 'txt')).rejects.toThrow('HTTP 404')
    vi.unstubAllGlobals()
  })

  it('任务断点恢复 localStorage 键按文档+类型隔离', () => {
    saveTaskId('D1', 'SUMMARY', 'T1')
    expect(loadTaskId('D1', 'SUMMARY')).toBe('T1')
    expect(loadTaskId('D2', 'SUMMARY')).toBeNull()
    clearTaskId('D1', 'SUMMARY')
    expect(loadTaskId('D1', 'SUMMARY')).toBeNull()
  })
})
