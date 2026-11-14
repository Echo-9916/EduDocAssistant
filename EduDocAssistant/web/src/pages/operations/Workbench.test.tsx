import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles: ['OPS'], permissions: ['ROLE_MANAGE', 'AUDIT_READ'] } as never, login: vi.fn(), logout: vi.fn() }),
}))
vi.mock('../../api/opsWorkbench', () => ({
  getWorkbenchSummary: vi.fn().mockResolvedValue({
    userTotal: 12, userLocked: 2, documentTotal: 23, aiTasksToday: 3, aiSuccessRate: 66.7,
    storageBytes: 1024 ** 4, storageObjects: 7, auditHighRisk7d: 4,
  }),
}))

import OpsWorkbench from './Workbench'

describe('运维工作台（对应原型：运维 01）', () => {
  it('渲染真实 KPI 卡与演示的风险事件、服务健康、待办审批', async () => {
    render(<OpsWorkbench />)
    expect(await screen.findByText('用户总数')).toBeTruthy()
    expect(await screen.findByText(/冻结 2 人/)).toBeTruthy()
    expect(screen.getByText(/成功率 66.7%/)).toBeTruthy()
    expect(screen.getByText('文档总数')).toBeTruthy()
    expect(screen.getByText('近 7 天高风险事件')).toBeTruthy()
    expect(screen.getByText('存储占用率')).toBeTruthy()
    expect(await screen.findByText('检测到越权访问尝试')).toBeTruthy()
    expect(screen.getByText('文档解析队列积压')).toBeTruthy()
    expect(screen.getByText('文档解析服务')).toBeTruthy()
    expect(screen.getByText('待办审批')).toBeTruthy()
  })

  it('「去处置」跳转审计与安全，「查看全部」跳转存储与资源', async () => {
    render(<OpsWorkbench />)
    fireEvent.click((await screen.findAllByText('去处置'))[0])
    expect(navigateMock).toHaveBeenCalledWith('/operations/audit')
    fireEvent.click(screen.getByText('查看全部'))
    expect(navigateMock).toHaveBeenCalledWith('/operations/storage')
  })

  it('待办审批通过后就地更新为「已通过」', async () => {
    render(<OpsWorkbench />)
    fireEvent.click((await screen.findAllByText('通过'))[0])
    expect(await screen.findByText('已通过')).toBeTruthy()
  })
})
