import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/documents', () => ({
  createUploadSession: vi.fn(),
  completeUpload: vi.fn(),
}))
vi.mock('../upload/ossUpload', async (importOriginal) => {
  const real = await importOriginal<typeof import('../upload/ossUpload')>()
  return { ...real, sha256Hex: vi.fn().mockResolvedValue('HASH'), uploadToOss: vi.fn().mockResolvedValue(undefined) }
})

import { completeUpload, createUploadSession } from '../api/documents'
import { uploadToOss } from '../upload/ossUpload'
import DocumentUploadModal from './DocumentUploadModal'

const courses = [{ id: 'C1', courseCode: 'CS101', courseName: '计算机导论', termCode: '2026-FALL', memberRole: 'TEACHER' as const }]

function pdf(name = 'a.pdf'): File {
  return new File(['pdf-bytes'], name, { type: 'application/pdf' })
}

function renderModal(role: 'STUDENT' | 'TEACHER' = 'STUDENT', onUploaded = vi.fn(), onClose = vi.fn()) {
  const ui = (
    <DocumentUploadModal open role={role} courses={courses} onClose={onClose} onUploaded={onUploaded} />
  )
  const { rerender } = render(ui)
  return { onUploaded, onClose, rerender: (next?: Partial<{ open: boolean }>) => rerender(
    <DocumentUploadModal open={next?.open ?? true} role={role} courses={courses} onClose={onClose} onUploaded={onUploaded} />,
  ) }
}

function addFile(file: File) {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement
  fireEvent.change(input, { target: { files: [file] } })
}

async function chooseDocType(label: string) {
  fireEvent.mouseDown(screen.getByText('文档类型'))
  fireEvent.click(await screen.findByText(label, { selector: '.ant-select-item-option-content' }))
}

describe('DocumentUploadModal', () => {
  beforeEach(() => {
    vi.mocked(createUploadSession).mockResolvedValue({
      sessionId: 'S1', objectKey: 'k', bucket: 'b', region: 'r', endpoint: 'e',
      sts: { accessKeyId: 'AK', accessKeySecret: 'SK', securityToken: 'T', expiration: 'x' },
    })
    vi.mocked(completeUpload).mockResolvedValue({ documentId: 'D1', versionId: 'V1' })
  })
  afterEach(() => vi.clearAllMocks())

  it('拒绝非法扩展名且不发会话请求', async () => {
    renderModal()
    addFile(new File(['x'], 'note.txt', { type: 'text/plain' }))
    expect(await screen.findByText(/仅支持 Word/)).toBeTruthy()
    expect(createUploadSession).not.toHaveBeenCalled()
  })

  it('未选类型时提示且不提交', async () => {
    renderModal()
    addFile(pdf())
    fireEvent.click(screen.getByText('开始上传'))
    expect(await screen.findByText('请选择文档类型')).toBeTruthy()
    expect(createUploadSession).not.toHaveBeenCalled()
  })

  it('教师未绑课程时拦截', async () => {
    renderModal('TEACHER')
    addFile(pdf())
    await chooseDocType('课件')
    fireEvent.click(screen.getByText('开始上传'))
    expect(await screen.findByText('教师上传须绑定课程')).toBeTruthy()
    expect(createUploadSession).not.toHaveBeenCalled()
  })

  it('学生单文件全流程：会话→sha→直传→complete→onUploaded', async () => {
    const m = renderModal()
    addFile(pdf())
    await chooseDocType('论文')
    fireEvent.click(screen.getByText('开始上传'))

    await waitFor(() => expect(completeUpload).toHaveBeenCalled())
    expect(createUploadSession).toHaveBeenCalledWith({
      courseId: null, title: 'a', fileName: 'a.pdf', size: 9, mimeType: 'application/pdf', docType: 'THESIS',
    })
    expect(uploadToOss).toHaveBeenCalled()
    expect(completeUpload).toHaveBeenCalledWith('S1', { contentHash: 'HASH' })
    expect(m.onUploaded).toHaveBeenCalled()
  })

  it('全部成功后自动关闭并清空，重开为空（1104 审查 P2-2）', async () => {
    const m = renderModal()
    addFile(pdf())
    await chooseDocType('论文')
    fireEvent.click(screen.getByText('开始上传'))

    await waitFor(() => expect(m.onClose).toHaveBeenCalled())
    m.rerender()
    expect(screen.queryByText('a.pdf')).toBeNull()
    fireEvent.click(screen.getByText('开始上传'))
    expect(await screen.findByText('请先选择文件')).toBeTruthy()
    expect(createUploadSession).toHaveBeenCalledTimes(1) // 残留输入未被二次提交
  })
})
