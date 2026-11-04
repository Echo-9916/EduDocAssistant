import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

vi.mock('./api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('./api/documents', () => ({
  listCourses: vi.fn().mockResolvedValue([]),
  listDocuments: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 }),
  deleteDocument: vi.fn(),
  restoreDocument: vi.fn(),
  reparseDocument: vi.fn(),
}))

import { pageOf } from './App'

describe('pageOf（B-12 接线）', () => {
  it('documents key 渲染真实页面而非占位', async () => {
    render(pageOf('STUDENT', { key: 'documents', label: '我的文档' }))
    expect(await screen.findByText('上传文档')).toBeTruthy()
    expect(screen.queryByText('我的文档', { selector: '.ant-result-title' })).toBeNull()
  })

  it('未接线 key 仍走 SectionPlaceholder', () => {
    render(pageOf('TEACHER', { key: 'ai', label: '智能处理' }))
    expect(screen.getByText('智能处理')).toBeTruthy()
  })
})
