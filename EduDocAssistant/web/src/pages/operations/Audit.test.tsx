import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles: ['OPS'], permissions: ['AUDIT_READ'] } as never }),
}))
// 操作日志已接真实接口：网络层与审计 API 一并隔离；其余三个页签仍走 mock/domain/security 内存数据
vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/audit', () => ({ listAuditEvents: vi.fn(), getAuditEvent: vi.fn() }))

import OpsAudit from './Audit'
import { getAuditEvent, listAuditEvents } from '../../api/audit'
import type { AuditEventDetailView, AuditEventView } from '../../api/audit'

/** 两行样本：一行 FAIL+HIGH、一行 SUCCESS+LOW，字段形状对齐 /ops/audit-events 实测返回。 */
const ROWS: AuditEventView[] = [
  {
    id: 'AUD-20261110-0001',
    occurredAt: '2026-11-10T11:41:44.931+08:00',
    actor: '李老师',
    actorAccount: 'teacher',
    actionType: 'REMIND_UNSUBMITTED',
    resourceType: 'COURSE',
    resourceRef: '《Web开发技术》',
    result: 'FAIL',
    resultCode: '40101',
    traceId: 'tr-9f2c41ab77e0',
    sourceIp: '10.20.12.5',
    userAgent: 'Chrome 130 / Windows 11',
    riskLevel: 'HIGH',
    summary: '越权访问被拒绝，内容未返回。',
  },
  {
    id: 'AUD-20261110-0002',
    occurredAt: '2026-11-10T09:12:03.120+08:00',
    actor: '赵工',
    actorAccount: 'ops',
    actionType: 'AUTH',
    resourceType: 'SYSTEM',
    resourceRef: '统一身份认证服务',
    result: 'SUCCESS',
    resultCode: '200',
    traceId: 'tr-4b81d0c93af5',
    sourceIp: '10.20.8.2',
    userAgent: 'Chrome 130 / Windows 11',
    riskLevel: 'LOW',
    summary: '登录成功。',
  },
]

const DETAIL: AuditEventDetailView = {
  ...ROWS[0],
  chain: [{ step: 1, at: '2026-11-10T11:41:44.931+08:00', detail: '策略引擎拒绝访问' }],
  impacts: [{ type: '文档', name: '《Web开发课程设计报告》', detail: '权限校验在内容返回前终止' }],
  advice: ['通知课程负责人复核共享范围。', '若 24 小时内再次出现同类访问，升级为高风险事件。'],
}

beforeEach(() => {
  vi.mocked(listAuditEvents).mockResolvedValue({ items: ROWS, total: 2, page: 1, pageSize: 10 })
  vi.mocked(getAuditEvent).mockResolvedValue(DETAIL)
})

describe('运维审计与安全（13/14/15/16/17/18/19/20 屏门面）', () => {
  it('操作日志：点检索走真实接口（range=24h），行含结果/风险 Tag，详情抽屉展示链路与处置建议（屏 15/16）', async () => {
    render(<OpsAudit />)
    expect(await screen.findByText('审计与安全')).toBeTruthy()
    expect(screen.getByText('最近 24 小时')).toBeTruthy()
    // 点「检索」前不打接口
    expect(listAuditEvents).not.toHaveBeenCalled()

    // antd Button 会给两个汉字的按钮自动插空格（「检 索」），用 role + 正则匹配
    fireEvent.click(screen.getByRole('button', { name: /^检\s*索$/ }))
    await waitFor(() =>
      expect(listAuditEvents).toHaveBeenCalledWith(expect.objectContaining({ range: '24h', page: 1, pageSize: 10 })),
    )

    // 两行样本：FAIL+HIGH / SUCCESS+LOW，分页总数来自后端 total
    expect(await screen.findByText('tr-9f2c41ab77e0')).toBeTruthy()
    expect(screen.getByText('tr-4b81d0c93af5')).toBeTruthy()
    expect(screen.getByText('REMIND_UNSUBMITTED')).toBeTruthy()
    expect(screen.getByText('失败')).toBeTruthy()
    expect(screen.getByText('成功')).toBeTruthy()
    expect(screen.getByText('高风险')).toBeTruthy()
    expect(screen.getByText('低风险')).toBeTruthy()
    expect(screen.getByText(/共 2 条/)).toBeTruthy()

    // 屏 16：事件详情抽屉走 getAuditEvent（链路 + 请求上下文 + 影响对象 + 处置建议）
    fireEvent.click(screen.getAllByText('事件详情')[0])
    await waitFor(() => expect(getAuditEvent).toHaveBeenCalledWith('AUD-20261110-0001'))
    expect(await screen.findByText('通知课程负责人复核共享范围。')).toBeTruthy()
    expect(screen.getByText('事件链路')).toBeTruthy()
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
