import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import AppShell from './AppShell'

vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }))

function userWith(roles: string[], permissions: string[] = []) {
  return { userId: 'u1', username: 'alice', roles, permissions, courseScope: [] }
}

function renderShell() {
  return render(
    <MemoryRouter initialEntries={['/student/workbench']}>
      <AppShell />
    </MemoryRouter>
  )
}

const stub = { login: vi.fn(), logout: vi.fn() }

describe('AppShell', () => {
  beforeEach(() => vi.resetAllMocks())

  it('学生端渲染 8 项导航且含工作台', () => {
    vi.mocked(useAuth).mockReturnValue({
      ...stub,
      user: userWith(['STUDENT'], ['DOCUMENT_READ', 'SUBMISSION_READ']),
      loading: false,
    })
    renderShell()
    expect(screen.getByText('工作台')).toBeTruthy()
    expect(screen.getByText('我的文档')).toBeTruthy()
    expect(screen.getByText('消息通知')).toBeTruthy()
    expect(screen.queryByText('运维工作台')).toBeNull()
  })

  it('无权限时隐藏对应导航项', () => {
    vi.mocked(useAuth).mockReturnValue({ ...stub, user: userWith(['STUDENT'], []), loading: false })
    renderShell()
    expect(screen.getByText('工作台')).toBeTruthy()
    expect(screen.queryByText('我的文档')).toBeNull()
  })

  it('运维端渲染课程治理与用户与权限', () => {
    vi.mocked(useAuth).mockReturnValue({
      ...stub,
      user: userWith(['OPS'], ['ROLE_MANAGE', 'AUDIT_READ']),
      loading: false,
    })
    renderShell()
    expect(screen.getByText('运维工作台')).toBeTruthy()
    expect(screen.getByText('用户与权限')).toBeTruthy()
    expect(screen.getByText('审计与安全')).toBeTruthy()
  })

  it('顶部展示用户名', () => {
    vi.mocked(useAuth).mockReturnValue({ ...stub, user: userWith(['TEACHER']), loading: false })
    renderShell()
    expect(screen.getByText('alice')).toBeTruthy()
  })
})
