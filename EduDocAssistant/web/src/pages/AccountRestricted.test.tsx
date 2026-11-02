import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import AccountRestricted from './AccountRestricted'
import Login from './Login'
import { useAuth } from '../auth/AuthContext'
import { ApiError, post } from '../api/http'

vi.mock('../auth/AuthContext', () => ({
  useAuth: vi.fn(),
  homeOf: (roles: string[]) =>
    roles.includes('OPS') ? '/operations' : roles.includes('TEACHER') ? '/teacher' : '/student',
  landingPath: () => '/roles',
}))

vi.mock('../api/http', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/http')>()),
  post: vi.fn(),
}))

const loginMock = vi.fn()

type RestrictedState = { username?: string; accountStatus?: string; reason?: string }

function renderRestricted(state?: RestrictedState) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/account-restricted', state }]}>
      <Routes>
        <Route path="/account-restricted" element={<AccountRestricted />} />
        <Route path="/login" element={<div>登录页</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('AccountRestricted', () => {
  beforeEach(() => vi.resetAllMocks())

  it('锁定账号渲染账号状态卡、操作按钮与安全提示', () => {
    renderRestricted({ username: 'locked', accountStatus: 'L', reason: '账号受限：账号已锁定，请联系管理员' })

    expect(screen.getByText('账号暂时受限')).toBeTruthy()
    expect(screen.getByText('locked')).toBeTruthy()
    expect(screen.getByText('已锁定')).toBeTruthy()
    expect(screen.getByText('账号已锁定，请联系管理员')).toBeTruthy()
    expect(screen.getByRole('button', { name: '联系信息中心解锁' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '申请临时访问权限' })).toBeTruthy()
    expect(screen.getByText('安全提示')).toBeTruthy()
  })

  it('未激活账号不显示「已锁定」', () => {
    renderRestricted({ username: 'inactive', accountStatus: 'I', reason: '账号受限：账号未激活，请先完成激活' })

    expect(screen.getByText('未激活')).toBeTruthy()
    expect(screen.queryByText('已锁定')).toBeNull()
    expect(screen.getByText('账号未激活，请先完成激活')).toBeTruthy()
  })

  it('注销账号显示「已注销」', () => {
    renderRestricted({ username: 'gone', accountStatus: 'D', reason: '账号受限：账号已注销，无法登录' })

    expect(screen.getByText('已注销')).toBeTruthy()
    expect(screen.getByText('账号已注销，无法登录')).toBeTruthy()
  })

  it('未知状态与无 state 时回退「已受限」与通用原因', () => {
    renderRestricted({ accountStatus: 'X' })

    expect(screen.getByText('已受限')).toBeTruthy()
    expect(screen.getByText('账号状态异常，请联系管理员')).toBeTruthy()
    expect(screen.getByText('—')).toBeTruthy()
  })

  it('申请临时访问权限走公开占位接口并提示占位文案', async () => {
    vi.mocked(post).mockResolvedValue(null)
    renderRestricted({ username: 'locked', accountStatus: 'L' })

    fireEvent.click(screen.getByRole('button', { name: '申请临时访问权限' }))

    expect(await screen.findByText('该功能正在开发中')).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/auth/access-request')
  })

  it('登录账号受限跳转受限页', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: null, loading: false, login: loginMock, logout: vi.fn() })
    loginMock.mockRejectedValue(
      new ApiError(40101, '账号受限：账号已锁定，请联系管理员', undefined, { accountStatus: 'L' })
    )
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/account-restricted" element={<AccountRestricted />} />
        </Routes>
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('2023001 或教师工号'), { target: { value: 'locked' } })
    fireEvent.change(screen.getByPlaceholderText('••••••••••'), { target: { value: 'pw' } })
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(await screen.findByText('账号暂时受限')).toBeTruthy()
    expect(screen.getByText('已锁定')).toBeTruthy()
    expect(screen.getByRole('button', { name: '申请临时访问权限' })).toBeTruthy()
  })
})
