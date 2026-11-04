import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/documents', () => ({
  getDocument: vi.fn(),
  reparseDocument: vi.fn(),
  deleteDocument: vi.fn(),
}))

import { getDocument, reparseDocument } from '../api/documents'
import DocumentDetailDrawer from './DocumentDetailDrawer'

const detail = {
  id: 'D1', title: '第一课件', docType: 'COURSEWARE' as const, status: 'READY' as const,
  courseId: 'C1', courseName: '计算机导论', sizeBytes: 2048, mimeType: 'application/pdf',
  versionNo: 1, createTime: '2026-11-03T10:00:00', updateTime: '2026-11-03T10:00:00',
  currentVersionId: 'V1', contentHash: '01cb9afc77df8fbc4bf8364e7f89dc8fde0c554f802481c0e344aa1823698a36',
  chunkCount: 3, deletedAt: null, purgeAt: null,
}

describe('DocumentDetailDrawer', () => {
  beforeEach(() => {
    vi.mocked(getDocument).mockResolvedValue(detail)
    vi.mocked(reparseDocument).mockResolvedValue({ taskId: 'T1' })
  })
  afterEach(() => vi.clearAllMocks())

  it('展示元数据、版本 hash 前 8 位与分块数', async () => {
    render(<DocumentDetailDrawer docId="D1" onClose={vi.fn()} onChanged={vi.fn()} />)
    expect(await screen.findByText('第一课件')).toBeTruthy()
    expect(screen.getByText(/01cb9afc…/)).toBeTruthy()
    expect(screen.getByText('3')).toBeTruthy()
    expect(screen.getByText('计算机导论')).toBeTruthy()
  })

  it('READY 态提供重新解析并回调 onChanged', async () => {
    const onChanged = vi.fn()
    const onClose = vi.fn()
    render(<DocumentDetailDrawer docId="D1" onClose={onClose} onChanged={onChanged} />)
    fireEvent.click(await screen.findByText('重新解析'))
    await waitFor(() => expect(reparseDocument).toHaveBeenCalledWith('D1'))
    expect(onChanged).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('docId 为空不请求', () => {
    render(<DocumentDetailDrawer docId={null} onClose={vi.fn()} onChanged={vi.fn()} />)
    expect(getDocument).not.toHaveBeenCalled()
  })
})
