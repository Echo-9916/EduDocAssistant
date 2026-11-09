import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => vi.fn(),
}))
vi.mock('./api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('./auth/AuthContext', () => ({ useAuth: () => ({ user: null }) }))
vi.mock('./api/template', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/template')>()
  return {
    ...actual,
    listTemplates: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 1, pageSize: 100 })),
    currentRules: vi.fn(() => Promise.resolve([])),
    provisionFromTemplate: vi.fn(),
  }
})
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

  it('04 切片接线：format=格式与模板容器，templates=教师模板库', async () => {
    render(pageOf('STUDENT', { key: 'format', label: '格式与模板' }))
    expect(await screen.findByRole('tab', { name: '课程模板' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /代码与图表规范化/ })).toBeTruthy()
  })

  it('templates key 渲染教师模板库列表', async () => {
    render(pageOf('TEACHER', { key: 'templates', label: '课程模板' }))
    expect(await screen.findByText('新建模板')).toBeTruthy()
  })
})
