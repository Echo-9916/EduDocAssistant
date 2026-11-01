import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import AccountRestricted from './AccountRestricted'
import Login from './Login'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/http'

vi.mock('../auth/AuthContext', () => ({
  useAuth: vi.fn(),
  homeOf: (roles: string[]) =>
    roles.includes('OPS') ? '/operations' : roles.includes('TEACHER') ? '/teacher' : '/student',
  landingPath: () => '/roles',
}))

const loginMock = vi.fn()

function renderRestricted(state?: { username?: string }) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/account-restricted', state }]}>
      <Routes>
        <Route path="/account-restricted" element={<AccountRestricted />} />
        <Route path="/operations/users" element={<div>角色与权限管理</div>} />
        <Route path="/login" element={<div>登录页</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('AccountRestricted', () => {
  beforeEach(() => vi.resetAllMocks())

  it('渲染账号状态卡、操作按钮与安全提示', () => {
    renderRestricted({ username: 'locked' })

    expect(screen.getByText('账号暂时受限')).toBeTruthy()
    expect(screen.getByText('locked')).toBeTruthy()
    expect(screen.getByText('已锁定')).toBeTruthy()
    expect(screen.getByText('多次连续密码错误')).toBeTruthy()
    expect(screen.getByRole('button', { name: '联系信息中心解锁' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '申请临时访问权限' })).toBeTruthy()
    expect(screen.getByText('安全提示')).toBeTruthy()
  })

  it('申请临时访问权限跳角色与权限管理', () => {
    renderRestricted()

    fireEvent.click(screen.getByRole('button', { name: '申请临时访问权限' }))

    expect(screen.getByText('角色与权限管理')).toBeTruthy()
  })

  it('登录账号受限跳转受限页', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null, loading: false, login: loginMock, logout: vi.fn() })
    loginMock.mockRejectedValue(new ApiError(40101, '账号受限，请联系管理员'))
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/account-restricted" element={<AccountRestricted />} />
          <Route path="/operations/users" element={<div>角色与权限管理</div>} />
        </Routes>
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('2023001 或教师工号'), { target: { value: 'locked' } })
    fireEvent.change(screen.getByPlaceholderText('••••••••••'), { target: { value: 'pw' } })
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(await screen.findByText('账号暂时受限')).toBeTruthy()
    expect(screen.getByRole('button', { name: '申请临时访问权限' })).toBeTruthy()
  })
})
