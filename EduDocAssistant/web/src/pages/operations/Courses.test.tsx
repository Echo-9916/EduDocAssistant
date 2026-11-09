import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles: ['OPS'], permissions: ['ROLE_MANAGE'] } as never, login: vi.fn(), logout: vi.fn() }),
}))

import OpsCourses from './Courses'

describe('课程治理（对应原型：运维 02–06）', () => {
  it('列表渲染世界观课程与运行指标', async () => {
    render(<OpsCourses />)
    expect(await screen.findByText('Web开发技术')).toBeTruthy()
    expect(screen.getByText('数据库原理')).toBeTruthy()
    expect(screen.getByText('操作系统')).toBeTruthy()
    expect(screen.getByText('编译原理')).toBeTruthy()
    expect(screen.getAllByText('运行中')).toHaveLength(4)
    expect(screen.getByText('格式异常 4')).toBeTruthy()
  })

  it('详情抽屉展示基础信息与最近运行状态', async () => {
    render(<OpsCourses />)
    fireEvent.click((await screen.findAllByText('详情'))[0])
    expect(await screen.findByText('课程代码')).toBeTruthy()
    expect(screen.getByText('最近运行状态')).toBeTruthy()
    expect(screen.getByText('Web开发课程组')).toBeTruthy()
  })

  it('归档确认后课程转只读且状态更新', async () => {
    render(<OpsCourses />)
    fireEvent.click((await screen.findAllByText('归档'))[0])
    expect(await screen.findByText('归档「Web开发技术」？')).toBeTruthy()
    fireEvent.click(screen.getByText('确认归档'))
    expect(await screen.findByText('课程已归档，转为只读')).toBeTruthy()
    await waitFor(() => expect(screen.getAllByText('已归档')).toHaveLength(2))
  })
})
