import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
let perms: string[] = ['DOCUMENT_READ', 'FORMAT_CHECK', 'TEMPLATE_READ']
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { permissions: perms } as never, login: vi.fn(), logout: vi.fn(), reload: vi.fn() }),
}))

import StudentWorkbench from './Workbench'

describe('学生工作台快捷入口（1107 P2-4）', () => {
  beforeEach(() => {
    perms = ['DOCUMENT_READ', 'FORMAT_CHECK', 'TEMPLATE_READ']
  })
  it('设计 05 四入口齐备且不出现教师/运维能力', async () => {
    render(<StudentWorkbench />)
    expect(await screen.findByText('上传文档')).toBeTruthy()
    expect(screen.getByText('按模板新建')).toBeTruthy()
    expect(screen.getByText('查看作业')).toBeTruthy()
    expect(screen.getByText('教学消息')).toBeTruthy()
    expect(screen.queryByText('模板管理')).toBeNull()
    expect(screen.queryByText('审计')).toBeNull()
  })

  it('「按模板新建」直达格式与模板页', async () => {
    render(<StudentWorkbench />)
    const cards = screen.getAllByText('按模板新建')
    const card = cards[0].closest('.ant-card')!
    fireEvent.click(card.querySelector('button')!)
    expect(navigateMock).toHaveBeenCalledWith('/student/format')
  })

  it('查看作业：缺 SUBMISSION_READ 权限时入口禁用，不再跳 403（验收反馈 2）', async () => {
    render(<StudentWorkbench />)
    const cards = screen.getAllByText('查看作业')
    const btn = cards[0].closest('.ant-card')!.querySelector('button')!
    expect(btn.hasAttribute('disabled')).toBe(true)
    fireEvent.click(screen.getByText('上传文档').closest('.ant-card')!.querySelector('button')!)
    expect(navigateMock).toHaveBeenCalledWith('/student/documents')
  })
})
