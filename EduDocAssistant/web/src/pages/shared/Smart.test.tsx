import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/documents', () => ({ listDocuments: vi.fn() }))

import { listDocuments } from '../../api/documents'
import type { DocumentSummary } from '../../api/documents'
import Smart from './Smart'

function doc(over: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: 'D1', title: 'Web开发课程设计报告', docType: 'HOMEWORK', status: 'READY',
    courseId: 'C1', courseName: 'Web开发技术', sizeBytes: 2048, mimeType: 'application/pdf',
    versionNo: 3, createTime: '2026-11-03T10:00:00', updateTime: '2026-11-03T10:00:00', ...over,
  }
}

describe('智能处理入口页（学生 06-10 / 教师 05-08）', () => {
  beforeEach(() => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [doc()], total: 1, page: 1, pageSize: 100 })
  })
  afterEach(() => vi.clearAllMocks())

  it('只查 READY 文档并渲染列表，页头不再挂演示标记', async () => {
    render(<Smart role="STUDENT" />)
    expect(await screen.findByText('Web开发课程设计报告')).toBeTruthy()
    expect(listDocuments).toHaveBeenCalledWith({ status: 'READY', pageSize: 100 })
    expect(screen.queryByText(/演示数据/)).toBeNull()
  })

  it('点击文档跳转 /documents/:id/view（按角色前缀）', async () => {
    render(<Smart role="STUDENT" />)
    fireEvent.click(await screen.findByText('打开'))
    expect(navigateMock).toHaveBeenCalledWith('/student/documents/D1/view')
  })

  it('教师端同样按角色前缀跳转', async () => {
    render(<Smart role="TEACHER" />)
    fireEvent.click(await screen.findByText('打开'))
    expect(navigateMock).toHaveBeenCalledWith('/teacher/documents/D1/view')
  })

  it('没有就绪文档时给上传指引', async () => {
    vi.mocked(listDocuments).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 })
    render(<Smart role="STUDENT" />)
    expect(await screen.findByText(/暂无已解析完成的文档/)).toBeTruthy()
  })
})