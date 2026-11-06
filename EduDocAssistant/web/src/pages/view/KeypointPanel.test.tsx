import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { parseKeypoints } from './KeypointPanel'

vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
const api = vi.hoisted(() => ({
  createKeypointTask: vi.fn(),
  getAiTask: vi.fn(),
  cancelAiTask: vi.fn(),
  saveTaskId: vi.fn(),
  loadTaskId: vi.fn(() => null as string | null),
  clearTaskId: vi.fn(),
}))
vi.mock('../../api/ai', () => api)

import KeypointPanel from './KeypointPanel'
import type { AiTaskView, PreviewBlock } from '../../api/ai'

const blocks: PreviewBlock[] = [
  { chunkId: 'C1', chunkNo: 1, headingPath: null, pageNo: 1, charStart: 0, charEnd: 4, text: 'hello' },
  { chunkId: 'C2', chunkNo: 2, headingPath: null, pageNo: 2, charStart: 5, charEnd: 9, text: 'world' },
]

const task = (json: unknown): AiTaskView => ({
  taskId: 'T1', documentId: 'D1', taskType: 'KEYPOINT', status: 'SUCCEEDED', progress: 100,
  errorCode: null,
  result: {
    resultId: 'R1', resultType: 'KEYPOINT', contentText: '清单', structuredJson: JSON.stringify(json),
    modelName: 'K', promptVersion: 'v1', inputTokens: null, outputTokens: null, citations: [],
  },
})

describe('parseKeypoints（C-05 契约）', () => {
  it('容忍空/坏 JSON/非对象条目', () => {
    expect(parseKeypoints(null).keypoints).toEqual([])
    expect(parseKeypoints('not json').keypoints).toEqual([])
    expect(parseKeypoints('{"keypoints":["x",{"text":"甲","chunkId":"C1"}]}').keypoints)
      .toEqual([{ text: '甲', chunkId: 'C1', charStart: null }])
    expect(parseKeypoints('{"keypoints":[],"underMin":true}').underMin).toBe(true)
  })
})

describe('KeypointPanel', () => {
  beforeEach(() => { api.loadTaskId.mockReturnValue(null) })

  it('渲染要点、underMin 警示与定位回调', async () => {
    api.loadTaskId.mockReturnValue('T1')
    api.getAiTask.mockResolvedValue(task({
      keypoints: [
        { text: '要点甲', chunkId: 'C1', charStart: 0 },
        { text: '要点乙', chunkId: 'C2', charStart: 5 },
        { text: '孤儿要点', chunkId: 'GONE', charStart: 99 },
      ],
      underMin: true,
    }))
    const onHighlight = vi.fn()
    render(<KeypointPanel documentId="D1" blocks={blocks} onHighlight={onHighlight} allowed />)
    expect((await screen.findByTestId('kp-0')).textContent).toContain('要点甲')
    expect(screen.getByText(/少于 5 条/)).toBeTruthy()
    expect(screen.getByText('引用缺失')).toBeTruthy()

    const locs = await screen.findAllByText(/定\s*位/); fireEvent.click(locs[1])
    expect(onHighlight).toHaveBeenCalledWith(5, 9)
  })

  it('勾选后复制选中要点', async () => {
    api.loadTaskId.mockReturnValue('T1')
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } })
    api.getAiTask.mockResolvedValue(task({ keypoints: [{ text: '要点甲', chunkId: 'C1', charStart: 0 }] }))
    render(<KeypointPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed />
    )
    const cb = await screen.findByRole('checkbox')
    fireEvent.click(cb)
    fireEvent.click(screen.getByText(/复制选中/))
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('1. 要点甲'))
    vi.unstubAllGlobals()
  })
})
