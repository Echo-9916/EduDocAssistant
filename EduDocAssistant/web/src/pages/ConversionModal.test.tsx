import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/format', () => ({
  createConversion: vi.fn(),
  getConversion: vi.fn(),
  listConversions: vi.fn(),
}))

import { createConversion, getConversion } from '../api/format'
import type { ConversionView } from '../api/format'
import ConversionModal, { directionOf } from './ConversionModal'
import type { DocumentSummary } from '../api/documents'

const DOCX = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'

function doc(over: Partial<DocumentSummary>): DocumentSummary {
  return {
    id: 'D1', title: '报告', docType: 'REPORT', status: 'READY', courseId: null, courseName: null,
    sizeBytes: 10, mimeType: DOCX, versionNo: 1, createTime: '', updateTime: '', ...over,
  }
}

function task(over: Partial<ConversionView> = {}): ConversionView {
  return {
    id: 'T1', documentId: 'D1', targetFormat: 'PDF', status: 'QUEUED', progress: 0,
    failureCode: null, failureMessage: null, downloadUrl: null, startedAt: null, finishedAt: null, ...over,
  }
}

describe('directionOf（mime→方向自动匹配）', () => {
  it('docx→PDF、pdf→DOCX、其他无方向', () => {
    expect(directionOf(doc({ mimeType: DOCX }))).toBe('PDF')
    expect(directionOf(doc({ mimeType: 'application/pdf' }))).toBe('DOCX')
    expect(directionOf(doc({ mimeType: 'text/plain' }))).toBeNull()
  })
})

describe('ConversionModal（D-13）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getConversion).mockResolvedValue(task({ status: 'RUNNING' }))
  })

  it('批量循环创建：混合选择按各自方向，不可转换项跳过提示', async () => {
    vi.mocked(createConversion)
      .mockResolvedValueOnce(task({ id: 'T1', documentId: 'D1' }))
      .mockResolvedValueOnce(task({ id: 'T2', documentId: 'D2', targetFormat: 'DOCX' }))
    render(<ConversionModal open docs={[
      doc({ id: 'D1', mimeType: DOCX }),
      doc({ id: 'D2', mimeType: 'application/pdf', title: '课件' }),
      doc({ id: 'D3', mimeType: 'text/plain', title: '坏文件' }),
    ]} onClose={() => undefined} />)

    fireEvent.click(await screen.findByRole('button', { name: /开始转换/ }))
    await waitFor(() => expect(createConversion).toHaveBeenCalledTimes(2))
    expect(createConversion).toHaveBeenNthCalledWith(1, 'D1', 'PDF')
    expect(createConversion).toHaveBeenNthCalledWith(2, 'D2', 'DOCX')
    expect(await screen.findByText(/个文件不是 Word\/PDF，已跳过/)).toBeTruthy()
    // 任务行出现（排队/转换中态）
    expect(screen.getAllByText('报告').length).toBeGreaterThanOrEqual(1)
  })

  it('SUCCEEDED 任务给签名直链下载', async () => {
    vi.mocked(createConversion).mockResolvedValue(task({ status: 'SUCCEEDED', downloadUrl: 'https://oss/signed' }))
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    render(<ConversionModal open docs={[doc({})]} onClose={() => undefined} />)
    fireEvent.click(await screen.findByRole('button', { name: /开始转换/ }))
    fireEvent.click(await screen.findByRole('button', { name: '下载' }))
    expect(openSpy).toHaveBeenCalledWith('https://oss/signed', '_blank')
  })

  it('FAILED 显示失败码与原因', async () => {
    vi.mocked(createConversion).mockResolvedValue(task({ status: 'FAILED', failureCode: 'ENCRYPTED', failureMessage: 'PDF 已加密' }))
    render(<ConversionModal open docs={[doc({})]} onClose={() => undefined} />)
    fireEvent.click(await screen.findByRole('button', { name: /开始转换/ }))
    expect(await screen.findByText(/ENCRYPTED PDF 已加密/)).toBeTruthy()
    expect(screen.getByText('失败')).toBeTruthy()
  })

  it('排队任务 2s 轮询刷新状态', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout'] })
    vi.mocked(createConversion).mockResolvedValue(task({ status: 'QUEUED' }))
    vi.mocked(getConversion).mockResolvedValue(task({ status: 'SUCCEEDED', downloadUrl: 'u' }))
    render(<ConversionModal open docs={[doc({})]} onClose={() => undefined} />)
    fireEvent.click(screen.getByRole('button', { name: /开始转换/ }))
    await vi.advanceTimersByTimeAsync(2100)
    expect(getConversion).toHaveBeenCalledWith('T1')
    await vi.advanceTimersByTimeAsync(10)
    expect(screen.getByText('完成')).toBeTruthy()
    vi.useRealTimers()
  })

  it('无转换方向的单选也不能开始（按钮禁用）', async () => {
    render(<ConversionModal open docs={[doc({ mimeType: 'text/plain' })]} onClose={() => undefined} />)
    const button = await screen.findByRole('button', { name: /开始转换/ })
    expect(button.hasAttribute('disabled')).toBe(true)
  })

  /** 1108 复审 P2-7 残余：显式目标格式——不符者跳过且按钮可用性联动 */
  it('目标格式手选 PDF：仅 docx 源创建，pdf 源计入跳过；全 pdf 时按钮禁用', async () => {
    vi.mocked(createConversion).mockResolvedValue(task({ id: 'T1' }))
    render(<ConversionModal open docs={[
      doc({ id: 'D1', mimeType: DOCX }),
      doc({ id: 'D2', mimeType: 'application/pdf', title: '课件' }),
    ]} onClose={() => undefined} />)
    fireEvent.mouseDown((await screen.findByTestId('target-choice')).querySelector('.ant-select-selector') as Element)
    fireEvent.click(await screen.findByText('全部转 PDF', { selector: '.ant-select-item-option-content' }))
    fireEvent.click(screen.getByRole('button', { name: /开始转换/ }))
    await waitFor(() => expect(createConversion).toHaveBeenCalledTimes(1))
    expect(createConversion).toHaveBeenCalledWith('D1', 'PDF')
    expect(await screen.findByText(/与所选目标格式不符/)).toBeTruthy()
  })
})
