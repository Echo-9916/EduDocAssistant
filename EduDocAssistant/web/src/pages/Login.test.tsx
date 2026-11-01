import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import Login from './Login'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/http'
import type { UserPrincipal } from '../api/types'

vi.mock('../auth/AuthContext', () => ({
  useAuth: vi.fn(),
  homeOf: (roles: string[]) =>
    roles.includes('OPS') ? '/operations' : roles.includes('TEACHER') ? '/teacher' : '/student',
  landingPath: () => '/roles',
}))

const alice: UserPrincipal = {
  userId: 'u1',
  username: 'alice',
  roles: ['STUDENT'],
  permissions: [],
  courseScope: [],
}

const loginMock = vi.fn()

function renderLogin(opts?: { user?: UserPrincipal | null; initialEntries?: string[] }) {
  vi.mocked(useAuth).mockReturnValue({
    user: opts?.user ?? null,
    loading: false,
    login: loginMock,
    logout: vi.fn(),
  })
  return render(
    <MemoryRouter initialEntries={opts?.initialEntries ?? ['/login']}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/roles" element={<div>身份认证成功页</div>} />
        <Route path="/student" element={<div>学生工作台</div>} />
        <Route path="/teacher" element={<div>教师工作台</div>} />
        <Route path="/account-restricted" element={<div>账号受限页</div>} />
        <Route path="/forgot-password" element={<div>找回密码页</div>} />
      </Routes>
    </MemoryRouter>
  )
}

function fillAndSubmit(username: string, password: string) {
  fireEvent.change(screen.getByPlaceholderText('2023001 或教师工号'), { target: { value: username } })
  fireEvent.change(screen.getByPlaceholderText('••••••••••'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }))
}

describe('Login', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('渲染品牌、字段、密码框、记住设备、SSO 入口与忘记密码链接', () => {
    renderLogin()

    expect(screen.getByText('校园智能文档助手')).toBeTruthy()
    expect(screen.getByText('登录工作台')).toBeTruthy()
    expect(screen.getByPlaceholderText('2023001 或教师工号')).toBeTruthy()
    expect(screen.getByPlaceholderText('••••••••••')).toBeTruthy()
    expect(screen.getByText('密码区分大小写')).toBeTruthy()
    expect(screen.getByText('记住此设备')).toBeTruthy()
    expect(screen.getByRole('button', { name: /登\s*录/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: '使用学校统一身份认证' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '忘记密码？' })).toBeTruthy()
  })

  it('空提交提示必填', async () => {
    renderLogin()

    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(await screen.findByText('请输入账号')).toBeTruthy()
    expect(await screen.findByText('请输入密码')).toBeTruthy()
    expect(loginMock).not.toHaveBeenCalled()
  })

  it('提交时用户名去首尾空格并登录成功进入身份认证成功页', async () => {
    loginMock.mockResolvedValue(alice)
    renderLogin()

    fillAndSubmit('  alice  ', 'pw')

    expect(await screen.findByText('身份认证成功页')).toBeTruthy()
    expect(loginMock).toHaveBeenCalledWith('alice', 'pw')
  })

  it('带 redirect 时登录成功回跳目标页', async () => {
    loginMock.mockResolvedValue(alice)
    renderLogin({ initialEntries: ['/login?redirect=%2Fteacher'] })

    fillAndSubmit('alice', 'pw')

    expect(await screen.findByText('教师工作台')).toBeTruthy()
  })

  it('账号受限跳受限页', async () => {
    loginMock.mockRejectedValue(new ApiError(40101, '账号受限，请联系管理员'))
    renderLogin()

    fillAndSubmit('locked', 'pw')

    expect(await screen.findByText('账号受限页')).toBeTruthy()
  })

  it('其它错误按 message 表单提示', async () => {
    loginMock.mockRejectedValue(new ApiError(40101, '用户名或密码错误'))
    renderLogin()

    fillAndSubmit('alice', 'wrong')

    expect(await screen.findByText('用户名或密码错误')).toBeTruthy()
    expect(screen.queryByText('账号受限页')).toBeNull()
  })

  it('已登录访问 /login 回跳工作台', async () => {
    renderLogin({ user: alice })

    expect(await screen.findByText('学生工作台')).toBeTruthy()
  })
})
