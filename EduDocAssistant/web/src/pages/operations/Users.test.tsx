import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles: ['OPS'], permissions: ['ROLE_MANAGE', 'AUDIT_READ'] } as never, login: vi.fn(), logout: vi.fn() }),
}))

import OpsUsers from './Users'

describe('用户与权限（对应原型：运维 07–12）', () => {
  it('用户目录渲染世界观账号与状态标签', async () => {
    render(<OpsUsers />)
    expect(await screen.findByText('李老师')).toBeTruthy()
    expect(screen.getByText('赵工')).toBeTruthy()
    expect(screen.getByText('张同学')).toBeTruthy()
    expect(screen.getAllByText('正常')).toHaveLength(5)
    expect(screen.getByText('待激活')).toBeTruthy()
    expect(screen.getByText('已锁定')).toBeTruthy()
  })

  it('关键字搜索过滤目录', async () => {
    render(<OpsUsers />)
    await screen.findByText('李老师')
    fireEvent.change(screen.getByPlaceholderText('搜索姓名 / 工号 / 学号'), { target: { value: 'T2021043' } })
    await waitFor(() => expect(screen.queryByText('张同学')).toBeNull())
    expect(screen.getByText('王老师')).toBeTruthy()
  })

  it('权限矩阵为「教师」勾选角色管理后命中职责冲突提示', async () => {
    render(<OpsUsers />)
    fireEvent.click(await screen.findByText('角色权限矩阵'))
    // 等角色数据加载完成，矩阵出现「学生/教师/运维管理员/院系教学审核员」四列
    await screen.findByText('院系教学审核员')
    const row = (await screen.findByText('角色管理')).closest('tr')!
    await waitFor(() => expect(row.querySelectorAll('input[type="checkbox"]')).toHaveLength(4))
    fireEvent.click(row.querySelectorAll('input[type="checkbox"]')[1])
    expect(await screen.findByText(/职责冲突：「教师」同时授予「角色管理」与「作业管理」/)).toBeTruthy()
  })
})
