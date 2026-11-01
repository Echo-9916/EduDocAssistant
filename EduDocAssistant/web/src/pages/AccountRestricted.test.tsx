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
  landingPath: (roles: string[]) => (roles.length > 1 ? '/roles' : '/student'),
}))

const loginMock = vi.fn()

function renderRestricted() {
  return render(
    <MemoryRouter initialEntries={['/account-restricted']}>
      <Routes>
        <Route path="/account-restricted" element={<AccountRestricted />} />
        <Route path="/login" element={<div>登录页</div>} />
        <Route path="/access-request" element={<div>权限申请页</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('AccountRestricted', () => {
  beforeEach(() => vi.resetAllMocks())

  it('渲染账号状态、原因、安全提示与操作按钮', () => {
    renderRestricted()

    expect(screen.getByText('账号受限')).toBeTruthy()
    expect(screen.getByText('账号状态：受限')).toBeTruthy()
    expect(screen.getByText(/锁定、未激活或已注销/)).toBeTruthy()
    expect(screen.getByText('安全提示')).toBeTruthy()
    expect(screen.getByRole('button', { name: '申请临时访问权限' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '返回登录' })).toBeTruthy()
  })

  it('申请入口跳权限申请页', () => {
    renderRestricted()

    fireEvent.click(screen.getByRole('button', { name: '申请临时访问权限' }))

    expect(screen.getByText('权限申请页')).toBeTruthy()
  })

  it('返回登录', () => {
    renderRestricted()

    fireEvent.click(screen.getByRole('button', { name: '返回登录' }))

    expect(screen.getByText('登录页')).toBeTruthy()
  })

  it('登录账号受限跳转受限页', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null, loading: false, login: loginMock, logout: vi.fn() })
    loginMock.mockRejectedValue(new ApiError(40101, '账号受限，请联系管理员'))
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/account-restricted" element={<AccountRestricted />} />
          <Route path="/access-request" element={<div>权限申请页</div>} />
        </Routes>
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'locked' } })
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'pw' } })
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(await screen.findByText('账号受限')).toBeTruthy()
    expect(screen.getByRole('button', { name: '申请临时访问权限' })).toBeTruthy()
  })
})
