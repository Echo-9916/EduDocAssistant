import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { citationLabel } from './QaPanel'

vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
const api = vi.hoisted(() => ({
  listQaSessions: vi.fn(),
  listQaMessages: vi.fn(),
  createQaSession: vi.fn(),
  askQa: vi.fn(),
}))
vi.mock('../../api/ai', () => api)

import QaPanel from './QaPanel'
import type { PreviewBlock, QaMessageView } from '../../api/ai'

const blocks: PreviewBlock[] = [
  { chunkId: 'C1', chunkNo: 1, headingPath: null, pageNo: 3, charStart: 10, charEnd: 20, text: '证据文本内容' },
]

const msgs: QaMessageView[] = [
  { messageId: 'M1', role: 'USER', contentText: '文档讲了什么？', status: 'READY', sequenceNo: 1, citations: [] },
  {
    messageId: 'M2', role: 'ASSISTANT', contentText: '讲了向量检索。', status: 'READY', sequenceNo: 2,
    citations: [{ chunkId: 'C1', pageNo: 3, charStart: 12, charEnd: 16, quoteText: '证据文本内容片段较长截取四十字符以内测试测试测试' }],
  },
  { messageId: 'M3', role: 'ASSISTANT', contentText: '未在文档中找到依据，无法回答该问题。', status: 'REFUSED', sequenceNo: 3, citations: [] },
  { messageId: 'M4', role: 'ASSISTANT', contentText: '问答失败：模型服务失败', status: 'FAILED', sequenceNo: 4, citations: [] },
]

describe('QaPanel（C-12）', () => {
  beforeEach(() => {
    api.listQaSessions.mockResolvedValue([{ sessionId: 'S1', documentId: 'D1', title: 't', status: 'A' }])
    api.listQaMessages.mockResolvedValue(msgs)
  })

  it('citationLabel：页码+片段前 40 字', () => {
    expect(citationLabel({ chunkId: 'C1', pageNo: 3, charStart: null, charEnd: null, quoteText: '甲'.repeat(60) }, 0))
      .toBe('[1] 第3页 ' + '甲'.repeat(40))
  })

  it('三态消息流渲染 + 引用 chip 跳回调 + 拒答文案突出', async () => {
    const onHighlight = vi.fn()
    render(<QaPanel documentId="D1" blocks={blocks} onHighlight={onHighlight} allowed />)
    expect(await screen.findByText('讲了向量检索。')).toBeTruthy()
    expect(screen.getByText('文档讲了什么？')).toBeTruthy()
    expect(screen.getByText(/未在文档中找到依据/)).toBeTruthy()
    // 1106 复审 P3：FAILED 用通用「失败」标签，不误导为超时
    expect(screen.getByText('失败')).toBeTruthy()
    expect(screen.queryByText('超时')).toBeNull()

    fireEvent.click(screen.getByText(/^\[1\] 第3页/))
    expect(onHighlight).toHaveBeenCalledWith(12, 16)
  })

  it('提问禁言至响应返回并重载消息流；空问题禁用', async () => {
    let resolveAsk: (v: unknown) => void = () => {}
    api.askQa.mockReturnValue(new Promise((r) => { resolveAsk = r }))
    render(<QaPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed />
    )
    const textarea = await screen.findByPlaceholderText(/就本文档提问/)
    const sendBtn = screen.getByTestId('qa-send')
    expect((sendBtn.closest('button') as HTMLButtonElement).disabled).toBe(true)

    fireEvent.change(textarea, { target: { value: '新问题' } })
    fireEvent.click(sendBtn)
    await waitFor(() => expect(api.askQa).toHaveBeenCalledWith('S1', '新问题'))
    // 发送中禁言
    expect((textarea as HTMLTextAreaElement).disabled).toBe(true)
    resolveAsk({ sessionId: 'S1', status: 'READY', answer: 'x', citations: [], questionMessageId: '', answerMessageId: '' })
    await waitFor(() => expect(api.listQaMessages).toHaveBeenCalledTimes(2))
    expect((textarea as HTMLTextAreaElement).disabled).toBe(false)
  })

  it('无活跃会话时首次提问自动建会话', async () => {
    api.listQaSessions.mockResolvedValue([])
    api.createQaSession.mockResolvedValue({ sessionId: 'S9', documentId: 'D1', title: null, status: 'A' })
    api.askQa.mockResolvedValue({ sessionId: 'S9', status: 'REFUSED', answer: '', citations: [] })
    api.listQaMessages.mockResolvedValue([])
    render(<QaPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed />)
    const textarea = await screen.findByPlaceholderText(/就本文档提问/)
    fireEvent.change(textarea, { target: { value: '问' } })
    fireEvent.click(screen.getByTestId('qa-send'))
    await waitFor(() => expect(api.createQaSession).toHaveBeenCalledWith('D1'))
    await waitFor(() => expect(api.askQa).toHaveBeenCalledWith('S9', '问'))
  })

  it('首次提问失败也用本次新建会话回载 FAILED 消息（1106 P2-2）', async () => {
    api.listQaSessions.mockResolvedValue([])
    api.createQaSession.mockResolvedValue({ sessionId: 'S9', documentId: 'D1', title: null, status: 'A' })
    api.askQa.mockRejectedValue({ code: 50401, message: '超时' })
    render(<QaPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed />)
    const textarea = await screen.findByPlaceholderText(/就本文档提问/)
    fireEvent.change(textarea, { target: { value: '问' } })
    fireEvent.click(screen.getByTestId('qa-send'))
    await waitFor(() => expect(api.askQa).toHaveBeenCalled())
    // 失败后必须 GET 新会话消息（服务端已落 FAILED 双消息），而非沿用旧 null sessionId
    await waitFor(() => expect(api.listQaMessages).toHaveBeenCalledWith('S9'))
  })

  it('归档会话只读：输入与提问禁用，可新建会话（1106 P2-3）', async () => {
    api.listQaSessions.mockResolvedValue([
      { sessionId: 'S9', documentId: 'D1', title: '旧会话', status: 'C' },
      { sessionId: 'S1', documentId: 'D1', title: '新会话', status: 'A' },
    ])
    render(<QaPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed />)
    await screen.findByText('讲了向量检索。')
    fireEvent.mouseDown(screen.getByText('新会话（进行中）'))
    fireEvent.click(await screen.findByText('旧会话（已归档）'))
    const textarea = await screen.findByPlaceholderText(/就本文档提问/)
    await waitFor(() => expect((textarea as HTMLTextAreaElement).disabled).toBe(true))
    expect((screen.getByTestId('qa-send') as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/归档会话只读/)).toBeTruthy()
  })

  it('indexing=true 禁用提问（R3/1106 P2-1 前端门控）', async () => {
    render(<QaPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed indexing />)
    const textarea = await screen.findByPlaceholderText(/索引中/)
    fireEvent.change(textarea, { target: { value: '问' } })
    expect((screen.getByTestId('qa-send') as HTMLButtonElement).disabled).toBe(true)
  })

  it('无权限提示', () => {
    render(<QaPanel documentId="D1" blocks={blocks} onHighlight={vi.fn()} allowed={false} />)
    expect(screen.getByText(/AI_QA/)).toBeTruthy()
  })
})
