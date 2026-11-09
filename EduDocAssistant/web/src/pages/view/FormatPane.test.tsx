// 格式视图 v3：后端字节流 → Blob URL → iframe 内嵌（不再触碰 OSS 签名链，杜绝弹下载）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'

vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/documents', () => ({ listDocumentVersions: vi.fn(), fetchVersionFile: vi.fn() }))
vi.mock('../../api/format', () => ({
  listConversions: vi.fn(), createConversion: vi.fn(), getConversion: vi.fn(), fetchConversionPreview: vi.fn(),
}))

import { fetchVersionFile, listDocumentVersions } from '../../api/documents'
import { createConversion, fetchConversionPreview, getConversion, listConversions } from '../../api/format'
import type { ConversionView } from '../../api/format'
import FormatPane from './FormatPane'

const DOCX = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'

const conv = (over: Partial<ConversionView> = {}): ConversionView => ({
  id: 'T1', documentId: 'D1', targetFormat: 'PDF', status: 'SUCCEEDED', progress: 100,
  failureCode: null, failureMessage: null, downloadUrl: 'https://oss/attach.pdf', previewUrl: null,
  startedAt: '2026-11-09T10:00:10', finishedAt: '2026-11-09T10:00:20', ...over,
})

beforeEach(() => {
  vi.clearAllMocks()
  // jsdom 无 ObjectURL 实现：桩造固定 blob 地址
  ;(URL as unknown as { createObjectURL: unknown }).createObjectURL = vi.fn(() => 'blob:fake-pdf')
  ;(URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = vi.fn()
  vi.mocked(listDocumentVersions).mockResolvedValue([{
    versionId: 'V2', versionNo: 2, versionType: 'EDIT', createTime: 't',
    changeSummary: null, downloadUrl: 'https://oss/signed.docx', current: true,
  }])
  vi.mocked(fetchConversionPreview).mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }))
  vi.mocked(fetchVersionFile).mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }))
})
afterEach(() => vi.unstubAllGlobals())

describe('FormatPane（格式视图 v3：blob 内嵌）', () => {
  it('docx：复用新鲜 SUCCEEDED 产物走 preview 字节流，iframe 用 blob URL 不触发新转换', async () => {
    vi.mocked(listConversions).mockResolvedValue([conv({ finishedAt: '2026-11-09T10:00:20' })])
    render(<FormatPane documentId="D1" mimeType={DOCX} documentUpdateTime="2026-11-09T09:30:00" />)
    const frame = await screen.findByTitle('版式预览')
    expect(frame.getAttribute('src')).toBe('blob:fake-pdf')
    expect(fetchConversionPreview).toHaveBeenCalledWith('T1')
    expect(createConversion).not.toHaveBeenCalled()
  })

  it('docx 产物过期：自动转换并轮询，成功后仍走字节流内嵌', async () => {
    vi.mocked(listConversions).mockResolvedValue([conv({ finishedAt: '2026-11-09T08:00:00' })])
    vi.mocked(createConversion).mockResolvedValue(conv({ status: 'QUEUED' }))
    vi.mocked(getConversion).mockResolvedValue(conv({ status: 'SUCCEEDED' }))
    render(<FormatPane documentId="D1" mimeType={DOCX} documentUpdateTime="2026-11-09T09:30:00" />)
    await waitFor(() => expect(createConversion).toHaveBeenCalledWith('D1', 'PDF'))
    await waitFor(() => expect(getConversion).toHaveBeenCalledWith('T1'), { timeout: 4000 })
    await waitFor(() => expect(fetchConversionPreview).toHaveBeenCalledWith('T1'))
    expect(screen.getByTitle('版式预览')).toBeTruthy()
  })

  it('转换 FAILED：展示失败码与原因', async () => {
    vi.mocked(listConversions).mockResolvedValue([])
    vi.mocked(createConversion).mockResolvedValue(
      conv({ status: 'FAILED', failureCode: 'ENCRYPTED', failureMessage: '文档已加密', downloadUrl: null }))
    render(<FormatPane documentId="D1" mimeType={DOCX} documentUpdateTime={null} />)
    expect(await screen.findByText(/ENCRYPTED 文档已加密/)).toBeTruthy()
  })

  it('PDF 原件：走版本文件字节流端点（fetchVersionFile），不碰转换链', async () => {
    render(<FormatPane documentId="D1" mimeType="application/pdf" />)
    expect((await screen.findByTitle('版式预览')).getAttribute('src')).toBe('blob:fake-pdf')
    expect(fetchVersionFile).toHaveBeenCalledWith('D1', 'V2')
    expect(listConversions).not.toHaveBeenCalled()
  })

  it('其他类型回落提示；PDF 原件无版本行给干净错误', async () => {
    const { rerender } = render(<FormatPane documentId="D1" mimeType="text/plain" />)
    expect(await screen.findByText(/无版式还原预览，请切回文本视图/)).toBeTruthy()
    vi.mocked(listDocumentVersions).mockResolvedValue([])
    rerender(<FormatPane documentId="D1" mimeType="application/pdf" />)
    expect(await screen.findByText('格式视图不可用')).toBeTruthy()
    expect(await screen.findByText(/没有可读的源文件/)).toBeTruthy()
  })
})
