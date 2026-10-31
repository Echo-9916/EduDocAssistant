import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, homeOf, useAuth } from './AuthContext'
import { get, post } from '../api/http'
import { clearToken, getToken } from '../api/token'

vi.mock('../api/http', () => ({
  get: vi.fn(),
  post: vi.fn(),
}))

function Probe() {
  const { user, login, logout } = useAuth()
  return (
    <div>
      <button onClick={() => login('alice', 'pw')}>登录</button>
      <button onClick={logout}>退出</button>
      <span>{user?.username ?? '未登录'}</span>
    </div>
  )
}

const alice = { userId: 'u1', username: 'alice', roles: ['STUDENT'], permissions: [], courseScope: [] }

describe('homeOf', () => {
  it('按角色优先级返回工作台路径', () => {
    expect(homeOf(['STUDENT'])).toBe('/student')
    expect(homeOf(['TEACHER'])).toBe('/teacher')
    expect(homeOf(['OPS'])).toBe('/operations')
    expect(homeOf([])).toBe('/student')
  })
})

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    clearToken()
  })

  it('无 token 时不请求 /auth/me', async () => {
    render(<AuthProvider><Probe /></AuthProvider>)
    expect(await screen.findByText('未登录')).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })

  it('login 成功写入 token 与用户', async () => {
    vi.mocked(post).mockResolvedValue({ token: 't1', user: alice })

    render(<AuthProvider><Probe /></AuthProvider>)
    fireEvent.click(screen.getByText('登录'))

    expect(await screen.findByText('alice')).toBeTruthy()
    expect(getToken()).toBe('t1')
  })

  it('logout 清空 token 与用户', async () => {
    vi.mocked(post).mockResolvedValue({ token: 't1', user: alice })

    render(<AuthProvider><Probe /></AuthProvider>)
    fireEvent.click(screen.getByText('登录'))
    await screen.findByText('alice')

    fireEvent.click(screen.getByText('退出'))

    expect(await screen.findByText('未登录')).toBeTruthy()
    expect(getToken()).toBeNull()
  })
})
