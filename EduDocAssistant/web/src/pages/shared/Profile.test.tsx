import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
let roles: string[] = ['STUDENT']
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles, permissions: ['DOCUMENT_READ'] } as never }),
}))

import Profile from './Profile'

describe('个人中心（学生 27 / 教师 27 / 运维 26+27 屏门面）', () => {
  beforeEach(() => {
    roles = ['STUDENT']
  })

  it('学生端渲染个人资料与选课课程，不出现运维特有项', async () => {
    render(<Profile />)
    expect(await screen.findByText('个人中心')).toBeTruthy()
    expect(await screen.findByText('张同学')).toBeTruthy()
    expect(screen.getByText('选课课程')).toBeTruthy()
    expect(screen.getByDisplayValue('《Web开发技术》《操作系统》《编译原理》')).toBeTruthy()
    expect(screen.queryByText('值班组')).toBeNull()
    expect(screen.queryByText('敏感操作二次验证')).toBeNull()
  })

  it('运维端显示值班组，账号与安全默认开启双因素认证并含运维特有项', async () => {
    roles = ['OPS']
    render(<Profile />)
    expect(await screen.findByText('赵工')).toBeTruthy()
    expect(screen.getByText('值班组')).toBeTruthy()

    fireEvent.click(screen.getByText('账号与安全'))
    expect(await screen.findByText('双因素认证')).toBeTruthy()
    expect(screen.getByText('敏感操作二次验证')).toBeTruthy()
    // 开关顺序：双因素认证、敏感操作二次验证、新设备登录提醒、作业提醒、格式异常、系统告警
    const switches = screen.getAllByRole('switch')
    expect(switches[0].getAttribute('aria-checked')).toBe('true')
    expect(switches[1].getAttribute('aria-checked')).toBe('true')
    expect(switches[5].getAttribute('aria-checked')).toBe('true')
  })

  it('修改密码弹窗校验两次输入一致，通知偏好开关就地生效', async () => {
    render(<Profile />)
    fireEvent.click(await screen.findByText('账号与安全'))
    // 登录记录含一次异地登录标记
    expect(await screen.findByText('异地登录')).toBeTruthy()

    // 学生端开关顺序：双因素认证、新设备登录提醒、作业提醒、格式异常、系统告警
    const switches = screen.getAllByRole('switch')
    expect(switches[2].getAttribute('aria-checked')).toBe('true')
    fireEvent.click(switches[2])
    await waitFor(() => expect(screen.getByText('作业提醒已关闭')).toBeTruthy())

    fireEvent.click(screen.getByText('修改密码'))
    expect(await screen.findByText('确认新密码')).toBeTruthy()
    fireEvent.click(screen.getByText('确认修改'))
    expect(await screen.findByText('请输入当前密码')).toBeTruthy()

    fireEvent.change(screen.getByPlaceholderText('至少 8 位，建议含字母与数字'), { target: { value: 'Passw0rd!2026' } })
    fireEvent.change(screen.getByPlaceholderText('请再次输入新密码'), { target: { value: 'Passw0rd?2026' } })
    fireEvent.click(screen.getByText('确认修改'))
    expect(await screen.findByText('两次输入的密码不一致')).toBeTruthy()
  })
})
