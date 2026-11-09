import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/documents', () => ({ listDocuments: vi.fn() }))
vi.mock('../api/format', () => ({ listElements: vi.fn() }))
// FormatSection 内嵌学生模板页：给其 api 一个空返回（装配测试只验证页签结构）
vi.mock('../api/template', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/template')>()
  return {
    ...actual,
    listTemplates: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 1, pageSize: 100 })),
    currentRules: vi.fn(() => Promise.resolve([])),
    provisionFromTemplate: vi.fn(),
  }
})

import { listDocuments } from '../api/documents'
import type { DocumentSummary } from '../api/documents'
import { listElements } from '../api/format'
import type { ElementView } from '../api/format'
import NormalizeSection from './NormalizeSection'
import FormatSection from './FormatSection'

const DOCX = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'

function doc(over: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: 'D1', title: '报告', docType: 'REPORT', status: 'READY', courseId: null, courseName: null,
    sizeBytes: 1, mimeType: DOCX, versionNo: 1, createTime: '', updateTime: '', ...over,
  }
}

const el = (over: Partial<ElementView> = {}): ElementView => ({
  elementKey: 'F-6', elementType: 'FIGURE', chapterNo: '2', sequenceNo: 1,
  displayLabel: '图 2-1', captionText: '图2-1 系统架构', locationJson: '{"paragraphIndex":6}', ...over,
})

describe('NormalizeSection（D-14 只读）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listDocuments).mockResolvedValue({ items: [doc()], total: 1, page: 1, pageSize: 100 })
    vi.mocked(listElements).mockResolvedValue([
      el(),
      el({ elementKey: 'C-9', elementType: 'CODE', displayLabel: null, captionText: null, sequenceNo: null }),
    ])
  })

  it('选文档后渲染编号清单、位置列与能力边界注记（P2-8）', async () => {
    render(<NormalizeSection />)
    fireEvent.mouseDown(await screen.findByRole('combobox'))
    fireEvent.click(await screen.findByText('报告', { selector: '.ant-select-item-option-content' }))
    await waitFor(() => expect(listElements).toHaveBeenCalledWith('D1'))
    expect(await screen.findByText('图 2-1')).toBeTruthy()
    expect(screen.getByText('图2-1 系统架构')).toBeTruthy()
    expect(screen.getByText('代码块')).toBeTruthy()
    expect(screen.getAllByText('第 7 段').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/增删图表后全文编号重排将随协作版本上线/)).toBeTruthy()
  })

  it('行点击直达预览页（P2-8 与 D-11 机制一致的页内替代）', async () => {
    render(<NormalizeSection />)
    fireEvent.mouseDown(await screen.findByRole('combobox'))
    fireEvent.click(await screen.findByText('报告', { selector: '.ant-select-item-option-content' }))
    fireEvent.click(await screen.findByText('图2-1 系统架构'))
    expect(navigateMock).toHaveBeenCalledWith('../documents/D1/view', { relative: 'path', state: { focus: { paragraphIndex: 6, charStart: 0, charEnd: 9 } } })
  })

  it('非 docx 文档提示无结构树', async () => {
    vi.mocked(listDocuments).mockResolvedValue({
      items: [doc({ mimeType: 'application/pdf' })], total: 1, page: 1, pageSize: 100,
    })
    vi.mocked(listElements).mockResolvedValue([])
    render(<NormalizeSection />)
    fireEvent.mouseDown(await screen.findByRole('combobox'))
    fireEvent.click(await screen.findByText('报告', { selector: '.ant-select-item-option-content' }))
    expect(await screen.findByText(/非 Word 文档暂无结构树/)).toBeTruthy()
  })

  it('打开预览走相对路由', async () => {
    render(<NormalizeSection />)
    fireEvent.mouseDown(await screen.findByRole('combobox'))
    fireEvent.click(await screen.findByText('报告', { selector: '.ant-select-item-option-content' }))
    fireEvent.click(await screen.findByRole('button', { name: '打开预览' }))
    expect(navigateMock).toHaveBeenCalledWith('../documents/D1/view', { relative: 'path' })
  })
})

describe('FormatSection 装配', () => {
  it('两个二级页签，默认课程模板', async () => {
    vi.mocked(listElements)
    render(<FormatSection />)
    expect(await screen.findByRole('tab', { name: '课程模板' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /代码与图表规范化/ })).toBeTruthy()
  })
})
