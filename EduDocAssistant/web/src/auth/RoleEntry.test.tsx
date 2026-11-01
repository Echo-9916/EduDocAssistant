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

  it('单角色直接回工作台，不显示选择页', () => {
    renderRoleEntry({ ...multiRoleUser, roles: ['STUDENT'] })

    expect(screen.getByText('学生工作台')).toBeTruthy()
    expect(screen.queryByText('选择身份进入')).toBeNull()
  })

  it('多角色显示身份卡片与角色入口', () => {
    renderRoleEntry(multiRoleUser)

    expect(screen.getByText('alice')).toBeTruthy()
    expect(screen.getByText(/学生、教师/)).toBeTruthy()
    expect(screen.getByRole('button', { name: '进入学生工作台' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '进入教师工作台' })).toBeTruthy()
    // 未持有的运维角色不出现入口
    expect(screen.queryByRole('button', { name: '进入运维工作台' })).toBeNull()
  })

  it('点击角色入口进入对应工作台', () => {
    renderRoleEntry(multiRoleUser)

    fireEvent.click(screen.getByRole('button', { name: '进入教师工作台' }))

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
