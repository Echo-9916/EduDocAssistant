import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import RequireAuth from './RequireAuth'

vi.mock('./AuthContext', () => ({ useAuth: vi.fn() }))

function LoginProbe() {
  const location = useLocation()
  return <div>登录页{location.search}</div>
}

function renderGuard() {
  return render(
    <MemoryRouter initialEntries={['/student/workbench']}>
      <Routes>
        <Route path="/login" element={<LoginProbe />} />
        <Route path="/403" element={<div>无权限</div>} />
        <Route
          path="/student/workbench"
          element={
            <RequireAuth roles={['STUDENT']}>
              <div>内容区</div>
            </RequireAuth>
          }
        />
      </Routes>
    </MemoryRouter>
  )
}

const stub = { login: vi.fn(), logout: vi.fn() }

describe('RequireAuth', () => {
  beforeEach(() => vi.resetAllMocks())

  it('loading 时不渲染子内容', () => {
    vi.mocked(useAuth).mockReturnValue({ ...stub, user: null, loading: true })
    renderGuard()
    expect(screen.queryByText('内容区')).toBeNull()
  })

  it('未登录重定向到 /login 并保留回跳', () => {
    vi.mocked(useAuth).mockReturnValue({ ...stub, user: null, loading: false })
    renderGuard()
    expect(screen.getByText('登录页?redirect=%2Fstudent%2Fworkbench')).toBeTruthy()
  })

  it('角色不符重定向到 /403', () => {
    vi.mocked(useAuth).mockReturnValue({
      ...stub,
      user: { userId: 'u1', username: 'a', roles: ['TEACHER'], permissions: [], courseScope: [] },
      loading: false,
    })
    renderGuard()
    expect(screen.getByText('无权限')).toBeTruthy()
  })

  it('角色匹配时渲染子内容', () => {
    vi.mocked(useAuth).mockReturnValue({
      ...stub,
      user: { userId: 'u1', username: 'a', roles: ['STUDENT'], permissions: [], courseScope: [] },
      loading: false,
    })
    renderGuard()
    expect(screen.getByText('内容区')).toBeTruthy()
  })
})
