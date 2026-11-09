import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles: ['OPS'], permissions: ['AUDIT_READ'] } as never }),
}))

import OpsAudit from './Audit'

describe('运维审计与安全（13/14/15/16/17/18/19/20 屏门面）', () => {
  it('操作日志渲染检索条件与审计事件，行操作打开事件详情抽屉', async () => {
    render(<OpsAudit />)
    expect(await screen.findByText('审计与安全')).toBeTruthy()
    // 屏 15：检索条件 + 事件行（时间/操作人/动作/对象/结果/traceId）
    expect(await screen.findByText('越权访问被拒绝')).toBeTruthy()
    expect(screen.getAllByText('tr-9f2c41ab77e0').length).toBeGreaterThan(0)
    expect(screen.getByText('最近 24 小时')).toBeTruthy()

    // 屏 16：事件详情抽屉（链路 + 请求上下文 + 影响对象 + 处置建议）
    fireEvent.click(screen.getAllByText('事件详情')[0])
    expect(await screen.findByText('事件链路')).toBeTruthy()
    expect(screen.getByText('请求上下文')).toBeTruthy()
    expect(screen.getByText('影响对象')).toBeTruthy()
    expect(screen.getByText('处置建议')).toBeTruthy()
    expect(screen.getByText('策略引擎拒绝访问')).toBeTruthy()
  })

  it('安全事件中心：冻结账号确认后事件关闭并提示已冻结（屏 18/19/20）', async () => {
    render(<OpsAudit />)
    fireEvent.click(await screen.findByText('安全事件'))
    // 屏 18：实时汇总卡 + 事件表
    expect(await screen.findByText('陈同学 · 2024017')).toBeTruthy()
    expect(screen.getByText('登录风险（实时）')).toBeTruthy()
    expect(screen.getAllByText('待处置').length).toBeGreaterThan(0)

    // 屏 20：冻结确认（影响范围 + 会话数 + 恢复方式）
    fireEvent.click(screen.getAllByText('冻结账号')[0])
    expect(await screen.findByText(/立即退出全部 2 个活动会话/)).toBeTruthy()
    expect(screen.getByText('恢复方式')).toBeTruthy()
    fireEvent.click(screen.getByText('确认冻结'))
    await waitFor(() => expect(screen.getAllByText(/已冻结/).length).toBeGreaterThan(0))
  })

  it('异常复核提交处置结论、权限冲突记录处理结论（屏 17/14）', async () => {
    render(<OpsAudit />)
    fireEvent.click(await screen.findByText('异常与冲突复核'))
    expect(await screen.findByText('访问频率异常：3 分钟内 6 次未授权文档请求')).toBeTruthy()
    expect(screen.getByText('权限冲突复核')).toBeTruthy()
    expect(screen.getAllByText('职责分离冲突').length).toBeGreaterThan(0)

    // 屏 17：三类指标 + 处置结论表单
    fireEvent.click(screen.getAllByText('复核')[0])
    expect(await screen.findByText(/处置结论 · ANO-20261109-01/)).toBeTruthy()
    expect(screen.getByText('6 次 / 3 分钟（阈值 5 次 / 10 分钟）')).toBeTruthy()
    fireEvent.click(screen.getByText('提交复核'))
    await waitFor(() => expect(screen.getByText(/异常复核结论已提交/)).toBeTruthy())

    // 屏 14：处理结论下拉 + 记录
    fireEvent.click(screen.getAllByText('记录')[0])
    await waitFor(() => expect(screen.getByText(/已记录处理结论/)).toBeTruthy())
  })

  it('访问策略保存后提示写入配置变更记录（屏 13）', async () => {
    render(<OpsAudit />)
    fireEvent.click(await screen.findByText('访问策略'))
    expect(await screen.findByText('强制统一身份认证')).toBeTruthy()
    expect(screen.getByText('会话空闲超时')).toBeTruthy()

    fireEvent.click(screen.getAllByRole('switch')[0])
    fireEvent.click(screen.getByText('保存策略'))
    await waitFor(() => expect(screen.getByText(/访问策略已保存/)).toBeTruthy())
  })
})
