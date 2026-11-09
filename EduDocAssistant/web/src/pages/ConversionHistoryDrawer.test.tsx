import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/documents', () => ({ listDocuments: vi.fn() }))
vi.mock('../api/format', () => ({ listConversions: vi.fn() }))

import { listDocuments } from '../api/documents'
import { listConversions } from '../api/format'
import type { ConversionView } from '../api/format'
import ConversionHistoryDrawer from './ConversionHistoryDrawer'
import type { DocumentSummary } from '../api/documents'

const DOCX = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'

function doc(over: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: 'D1', title: '中期报告', docType: 'REPORT', status: 'READY', courseId: null, courseName: null,
    sizeBytes: 1, mimeType: DOCX, versionNo: 1, createTime: '', updateTime: '', ...over,
  }
}

const task = (over: Partial<ConversionView> = {}): ConversionView => ({
  id: 'T1', documentId: 'D1', targetFormat: 'PDF', status: 'SUCCEEDED', progress: 100,
  failureCode: null, failureMessage: null, downloadUrl: 'https://oss/signed',
  startedAt: '2026-11-07T01:00:00', finishedAt: '2026-11-07T01:00:20', ...over,
})

describe('ConversionHistoryDrawer（1107 P2-7）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listDocuments).mockResolvedValue({ items: [doc()], total: 1, page: 1, pageSize: 100 })
    vi.mocked(listConversions).mockResolvedValue([
      task(),
      task({ id: 'T2', status: 'FAILED', failureCode: 'ENCRYPTED', failureMessage: 'PDF 已加密', downloadUrl: null }),
    ])
  })

  it('initialDocumentId 预选文档即拉记录，成功行给签名下载', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    render(<ConversionHistoryDrawer open onClose={() => undefined} initialDocumentId="D1" />)
    await waitFor(() => expect(listConversions).toHaveBeenCalledWith('D1'))
    expect((await screen.findAllByText('中期报告')).length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('完成')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '下载' }))
    expect(openSpy).toHaveBeenCalledWith('https://oss/signed', '_blank')
  })

  it('关键字检索过滤失败记录可见原因', async () => {
    render(<ConversionHistoryDrawer open onClose={() => undefined} initialDocumentId="D1" />)
    await screen.findByText('失败')
    const search = screen.getByPlaceholderText(/检索/)
    fireEvent.change(search, { target: { value: '加密' } })
    fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' })
    await waitFor(() => expect(screen.queryByText('完成')).toBeNull())
    expect(screen.getByText(/ENCRYPTED/)).toBeTruthy()
  })

  it('未选文档不请求记录接口', async () => {
    render(<ConversionHistoryDrawer open onClose={() => undefined} />)
    expect(await screen.findByText('选择一个文档查看其转换记录')).toBeTruthy()
    expect(listConversions).not.toHaveBeenCalled()
  })
})
