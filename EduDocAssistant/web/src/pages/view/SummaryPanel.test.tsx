import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'

vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
const api = vi.hoisted(() => ({
  createSummaryTask: vi.fn(),
  getAiTask: vi.fn(),
  cancelAiTask: vi.fn(),
  fetchExportBlob: vi.fn(),
  saveTaskId: vi.fn(),
  loadTaskId: vi.fn(() => null as string | null),
  clearTaskId: vi.fn(),
}))
vi.mock('../../api/ai', () => api)

import SummaryPanel from './SummaryPanel'
import type { AiTaskView, PreviewBlock } from '../../api/ai'

const blocks: PreviewBlock[] = [
  { chunkId: 'C1', chunkNo: 1, headingPath: null, pageNo: 1, charStart: 0, charEnd: 9, text: '第一段原文' },
  { chunkId: 'C2', chunkNo: 2, headingPath: null, pageNo: 1, charStart: 10, charEnd: 19, text: '第二段原文' },
]

const succeeded: AiTaskView = {
  taskId: 'T1', documentId: 'D1', taskType: 'SUMMARY', status: 'SUCCEEDED', progress: 100,
  errorCode: null,
  result: {
    resultId: 'R1', resultType: 'SUMMARY',
    contentText: '结论一\n结论二',
    structuredJson: JSON.stringify({
      segments: [
        { text: '结论一关于第一段', chunkIds: ['C1'] },
        { text: '结论二关于第二段', chunkIds: ['C2', 'GONE'] },
      ],
      groupCount: 1,
    }),
    modelName: 'K', promptVersion: 'v1', inputTokens: 1, outputTokens: 2,
    citations: [],
  },
}

describe('SummaryPanel（C-11）', () => {
  beforeEach(() => {
    api.loadTaskId.mockReturnValue(null)
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
    Object.defineProperty(URL, 'createObjectURL', { value: vi.fn(() => 'blob:x'), writable: true, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), writable: true, configurable: true })
  })
  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('无权限时置灰说明，不渲染生成入口', () => {
    render(<SummaryPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed={false} />)
    expect(screen.getByText(/AI_SUMMARY/)).toBeTruthy()
    expect(screen.queryByText('生成摘要')).toBeNull()
  })

  it('生成→轮询→分组渲染→点定位回调高亮→导出 docx 走后端端点', async () => {
    const onHighlight = vi.fn()
    api.createSummaryTask.mockResolvedValue({ taskId: 'T1', status: 'QUEUED' })
    api.getAiTask.mockResolvedValue(succeeded)
    render(<SummaryPanel documentId="D1" blocks={blocks} onHighlight={onHighlight} allowed />)

    fireEvent.click(screen.getByText('生成摘要'))
    await waitFor(() => expect(api.createSummaryTask).toHaveBeenCalledWith('D1'))
    expect(api.saveTaskId).toHaveBeenCalledWith('D1', 'SUMMARY', 'T1')
    expect(await screen.findByTestId('segment-0')).toBeTruthy()
    expect(api.clearTaskId).toHaveBeenCalledWith('D1', 'SUMMARY')

    fireEvent.click(within(screen.getByTestId('segment-0')).getByText(/定\s*位/))
    expect(onHighlight).toHaveBeenLastCalledWith(0, 9)

    api.fetchExportBlob.mockResolvedValue(new Blob(['PK']))
    fireEvent.click(screen.getByText('导出 .docx'))
    await waitFor(() => expect(api.fetchExportBlob).toHaveBeenCalledWith('R1', 'docx'))
    expect(URL.createObjectURL).toHaveBeenCalled()
  })

  it('挂载时从 localStorage 恢复未终态任务续轮询', async () => {
    api.loadTaskId.mockReturnValue('T-OLD')
    api.getAiTask.mockResolvedValue(succeeded)
    render(<SummaryPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed />)
    await waitFor(() => expect(api.getAiTask).toHaveBeenCalledWith('T-OLD'))
    expect(await screen.findByTestId('segment-1')).toBeTruthy()
  })
})
