import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../../api/http', () => {
  class ApiError extends Error {
    readonly code: number
    constructor(code: number, message: string) {
      super(message)
      this.code = code
    }
  }
  return { ApiError, getErrorMessage: (e: Error) => e?.message ?? 'err' }
})
vi.mock('../../api/format', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/format')>()
  return {
    ...actual,
    createFormatCheck: vi.fn(),
    getFormatCheck: vi.fn(),
    listFormatIssues: vi.fn(),
    fixFormatIssues: vi.fn(),
    saveCheckId: vi.fn(),
    loadCheckId: vi.fn(() => null as string | null),
    clearCheckId: vi.fn(),
  }
})

import { ApiError } from '../../api/http'
import {
  clearCheckId,
  createFormatCheck,
  fixFormatIssues,
  getFormatCheck,
  loadCheckId,
  saveCheckId,
} from '../../api/format'
import type { CheckView, IssueView } from '../../api/format'
import FormatPanel, { findAbsRange } from './FormatPanel'

const check = (over: Partial<CheckView> = {}): CheckView => ({
  id: 'C1', documentId: 'D1', documentVersionId: 'V1', templateVersionId: 'TV1',
  status: 'PASSED', totalIssueCount: 1, blockingIssueCount: 0, resultSummary: '校验通过，存在 1 个非阻断问题',
  ...over,
})

const issue = (over: Partial<IssueView> = {}): IssueView => ({
  id: 'I1', ruleId: 'R1', issueType: 'SIZE', locationType: 'PARAGRAPH',
  locationRef: '{"paragraphIndex":4,"charStart":0,"charEnd":9}',
  expectedValue: '16.0pt', actualValue: '14.0pt', severity: 'WARNING', autoFixable: 'Y',
  fixStatus: 'OPEN', guidance: '将该段落字号设置为 16.0pt', ...over,
})

const blocks = [
  { chunkId: 'B1', chunkNo: 1, headingPath: null, pageNo: 1, charStart: 0, charEnd: 20, text: '前言部分文字内容' },
  { chunkId: 'B2', chunkNo: 2, headingPath: null, pageNo: 1, charStart: 20, charEnd: 40, text: '图2-1 系统架构图 说明' },
]

function renderPanel(allowed = true, canEdit = true, ready = true, onHighlight = vi.fn()) {
  render(<FormatPanel documentId="D1" documentReady={ready} allowed={allowed} canEdit={canEdit}
    blocks={blocks} onHighlight={onHighlight} />)
  return onHighlight
}

describe('findAbsRange（P2-5 块内绝对偏移检索）', () => {
  it('命中块内文本返回 charStart+local 区间，未命中返回 null', () => {
    expect(findAbsRange(blocks, '系统架构图')).toEqual({ start: 25, end: 30 })
    expect(findAbsRange(blocks, '不存在')).toBeNull()
  })
})

describe('FormatPanel（D-11/D-12）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    // clearAllMocks 不重置 mockReturnValue——恢复用例注入的断点必须每例归零，防跨例泄漏
    vi.mocked(loadCheckId).mockReturnValue(null)
    vi.mocked(createFormatCheck).mockResolvedValue(check({ status: 'QUEUED' }))
    vi.mocked(getFormatCheck).mockResolvedValue(check())
  })

  it('发起校验：入队→断点保存→轮询→终态保留断点（验收 M-07-2 修正）', async () => {
    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /开始校验/ }))
    await waitFor(() => expect(createFormatCheck).toHaveBeenCalledWith('D1'))
    expect(saveCheckId).toHaveBeenCalledWith('D1', 'C1')
    await waitFor(() => expect(getFormatCheck).toHaveBeenCalledWith('C1'))
    // 终态不再清断点：刷新后可恢复上次结果；仅接口失败时才清
    await waitFor(() => expect(clearCheckId).not.toHaveBeenCalled())
    expect(saveCheckId).toHaveBeenLastCalledWith('D1', 'C1')
  })

  it('刷新恢复：mount 时按 localStorage 断点续查终态', async () => {
    vi.mocked(loadCheckId).mockReturnValue('C7')
    renderPanel()
    await waitFor(() => expect(getFormatCheck).toHaveBeenCalledWith('C7'))
    expect(await screen.findByText(/校验通过/)).toBeTruthy()
  })

  it('FAILED 结论横幅显示阻断计数', async () => {
    vi.mocked(getFormatCheck).mockResolvedValue(
      check({ status: 'FAILED', totalIssueCount: 3, blockingIssueCount: 1, resultSummary: null }))
    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /开始校验/ }))
    expect(await screen.findByText(/阻断 1 项，共 3 项/)).toBeTruthy()
  })

  it('J1 非 docx：ERROR 直接展示原因，不轮询', async () => {
    vi.mocked(createFormatCheck).mockResolvedValue(
      check({ status: 'ERROR', resultSummary: '当前版本不是 Word(.docx) 文档，暂不支持排版格式校验（PDF 请提交原件并按模板自查）' }))
    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /开始校验/ }))
    expect(await screen.findByText(/暂不支持排版格式校验/)).toBeTruthy()
    expect(getFormatCheck).not.toHaveBeenCalled()
  })

  it('文档未就绪时禁止发起', async () => {
    renderPanel(true, true, false)
    fireEvent.click(await screen.findByRole('button', { name: /开始校验/ }))
    expect(createFormatCheck).not.toHaveBeenCalled()
    expect(screen.getByText('文档解析就绪后可校验')).toBeTruthy()
  })

  it('题注问题点击定位：块内检索命中走 highlightRange 精确高亮（P2-5）', async () => {
    const listIssues = await import('../../api/format')
    vi.mocked(listIssues.listFormatIssues as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [issue({ issueType: 'CAPTION', actualValue: '图2-1 系统架构图 说明', autoFixable: 'N', guidance: '按章节编号' })],
      total: 1, page: 1, pageSize: 200,
    })
    const onHighlight = renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /开始校验/ }))
    fireEvent.click(await screen.findByRole('button', { name: /第 5 段/ }))
    expect(onHighlight).toHaveBeenCalledWith(20, 33)
  })

  it('字体问题无文本锚点：退化近似滚动并明示（P2-5）', async () => {
    const listIssues = await import('../../api/format')
    vi.mocked(listIssues.listFormatIssues as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [issue()], total: 1, page: 1, pageSize: 200,
    })
    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /开始校验/ }))
    fireEvent.click(await screen.findByRole('button', { name: /第 5 段/ }))
    expect(await screen.findByText('无法定位第 5 段（文档较短或结构差异），请人工浏览')).toBeTruthy()
  })

  it('一键修复：弹窗含完整影响面（选中/未选可修复/不可修复），提交仅选中项', async () => {
    const listIssues = await import('../../api/format')
    vi.mocked(listIssues.listFormatIssues as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [
        issue({ id: 'I1' }),
        issue({ id: 'I2' }),
        issue({ id: 'I9', autoFixable: 'N', guidance: '页面布局→页边距人工调整' }),
      ],
      total: 3, page: 1, pageSize: 200,
    })
    vi.mocked(fixFormatIssues).mockResolvedValue({
      newVersionId: 'V2', newVersionNo: 2, recheckId: 'C2', fixedIssueIds: ['I1'], skippedIssueIds: [],
    })
    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /开始校验/ }))
    await screen.findAllByText('16.0pt')
    const checkboxes = await waitFor(() => {
      const cbs = document.querySelectorAll('.ant-table-tbody input[type="checkbox"]')
      expect(cbs.length).toBeGreaterThan(1)
      return cbs
    })
    fireEvent.click(checkboxes[0]) // 第一行 I1
    fireEvent.click(screen.getByRole('button', { name: /一键修复/ }))
    expect(await screen.findByText(/本次将修复/)).toBeTruthy()
    expect(screen.getByText(/不可自动修复（1）/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /确认修复/ }))
    await waitFor(() => expect(fixFormatIssues).toHaveBeenCalledWith('C1', ['I1']))
    expect(await screen.findByText(/已生成新版本 v2，修复 1 项/)).toBeTruthy()
    // 修复成功自动跟踪复审任务（verifyAgain 伪代码）
    expect(saveCheckId).toHaveBeenCalledWith('D1', 'C2')
  })

  it('修复遇 40901：走冲突重查路径而非泛化报错（P2-6）', async () => {
    const listIssues = await import('../../api/format')
    vi.mocked(listIssues.listFormatIssues as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [issue()], total: 1, page: 1, pageSize: 200,
    })
    vi.mocked(fixFormatIssues).mockRejectedValue(new ApiError(40901, '文档已被并发修改'))
    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /开始校验/ }))
    await screen.findAllByText('16.0pt')
    const cb = await waitFor(() => {
      const el = document.querySelector('.ant-table-tbody input[type="checkbox"]') as HTMLInputElement
      expect(el).toBeTruthy()
      return el
    })
    fireEvent.click(cb)
    fireEvent.click(screen.getByRole('button', { name: /一键修复/ }))
    fireEvent.click(screen.getByRole('button', { name: /确认修复/ }))
    expect(await screen.findByText(/文档已被并发修改，本次修复未生效/)).toBeTruthy()
    await waitFor(() => expect(getFormatCheck).toHaveBeenCalledTimes(2)) // 旧 checkId 重查
  })
})
