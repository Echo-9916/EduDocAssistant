import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import RoleEntry from './RoleEntry'
import { useAuth } from './AuthContext'
import type { UserPrincipal } from '../api/types'

vi.mock('./AuthContext', () => ({
  useAuth: vi.fn(),
  homeOf: () => '/student',
}))

const multiRoleUser: UserPrincipal = {
  userId: 'u1',
  username: 'alice',
  roles: ['STUDENT', 'TEACHER'],
  permissions: [],
  courseScope: [],
}

const logoutMock = vi.fn()

function renderRoleEntry(user: UserPrincipal | null) {
  vi.mocked(useAuth).mockReturnValue({ user, loading: false, login: vi.fn(), logout: logoutMock })
  return render(
    <MemoryRouter initialEntries={['/roles']}>
      <Routes>
        <Route path="/roles" element={<RoleEntry />} />
        <Route path="/login" element={<div>登录页</div>} />
        <Route path="/student" element={<div>学生工作台</div>} />
        <Route path="/teacher" element={<div>教师工作台</div>} />
        <Route path="/operations" element={<div>运维工作台</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('RoleEntry', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('未登录回登录页', () => {
    renderRoleEntry(null)

    expect(screen.getByText('登录页')).toBeTruthy()
  })

  it('渲染身份卡片、课程列表、进入工作台/切换账号与角色演示入口', () => {
    renderRoleEntry(multiRoleUser)

    expect(screen.getByText('身份认证成功')).toBeTruthy()
    expect(screen.getByText('alice')).toBeTruthy()
    expect(screen.getByText(/学号：u1/)).toBeTruthy()
    expect(screen.getByText('我的课程')).toBeTruthy()
    expect(screen.getByText('Web开发技术')).toBeTruthy()
    expect(screen.getByText('Oracle 数据库应用')).toBeTruthy()
    expect(screen.getByText('JavaEE 程序设计')).toBeTruthy()
    expect(screen.getByRole('button', { name: '进入工作台' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '切换账号' })).toBeTruthy()
    expect(screen.getByRole('button', { name: /学\s*生/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /教\s*师/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: '教学管理' })).toBeTruthy()
  })

  it('进入工作台跳转当前账号工作台', () => {
    renderRoleEntry(multiRoleUser)

    fireEvent.click(screen.getByRole('button', { name: '进入工作台' }))

    expect(screen.getByText('学生工作台')).toBeTruthy()
  })

  it('演示入口进入对应角色路线', () => {
    renderRoleEntry(multiRoleUser)

    fireEvent.click(screen.getByRole('button', { name: /教\s*师/ }))

    expect(screen.getByText('教师工作台')).toBeTruthy()
  })

  it('切换账号登出并回登录页', async () => {
    logoutMock.mockResolvedValue(undefined)
    renderRoleEntry(multiRoleUser)

    fireEvent.click(screen.getByRole('button', { name: '切换账号' }))

    expect(await screen.findByText('登录页')).toBeTruthy()
    expect(logoutMock).toHaveBeenCalled()
  })
})
