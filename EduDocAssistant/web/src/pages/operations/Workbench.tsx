import { useEffect, useState } from 'react'
import { Card, Col, Progress, Row, Space, Statistic, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { AppButton, AppTable } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import { rolePath } from '../../routes'
import {
  approvalStatusLabel, approvalTypeLabel, decideApproval, loadWorkbench,
  riskLevelColor, riskLevelLabel,
} from '../../mock/domain/governance'
import type { Approval, RiskEvent, ServiceHealth, WorkbenchData } from '../../mock/domain/governance'

/** 运维工作台：统一掌握课程运行、用户权限、审计安全与系统资源（对应原型：运维 01）。 */
export default function OpsWorkbench() {
  const navigate = useNavigate()
  const [data, setData] = useState<WorkbenchData | null>(null)

  useEffect(() => {
    loadWorkbench().then(setData)
  }, [])

  const decide = async (row: Approval, approve: boolean) => {
    await decideApproval(row.id, approve)
    setData((prev) =>
      prev && {
        ...prev,
        approvals: prev.approvals.map((a) =>
          a.id === row.id ? { ...a, status: approve ? 'APPROVED' as const : 'REJECTED' as const } : a,
        ),
      },
    )
    message.success(approve ? '已通过，处理结果写入审计日志' : '已驳回，已通知申请方')
  }

  if (!data) {
    return (
      <DemoPage title="运维工作台" screens="对应原型：运维 01" desc="统一掌握课程运行、用户权限、审计安全与系统资源状态，优先处理高风险事件。">
        <Card loading />
      </DemoPage>
    )
  }

  const highPending = data.events.filter((e) => e.level === 'HIGH' && e.pending).length

  const eventColumns: ColumnsType<RiskEvent> = [
    {
      title: '等级', dataIndex: 'level', width: 70,
      render: (v: RiskEvent['level']) => <Tag color={riskLevelColor[v]}>{riskLevelLabel[v]}</Tag>,
    },
    {
      title: '事件', dataIndex: 'title',
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{row.detail}</Typography.Text>
        </Space>
      ),
    },
    { title: '时间', dataIndex: 'time', width: 150 },
    {
      title: '操作', width: 110,
      render: (_, row) =>
        row.pending ? (
          <AppButton type="link" size="small" onClick={() => navigate(`${rolePath.OPS}/audit`)}>去处置</AppButton>
        ) : (
          <Tag>已处置</Tag>
        ),
    },
  ]

  const serviceColumns: ColumnsType<ServiceHealth> = [
    { title: '服务', dataIndex: 'name' },
    {
      title: '状态', dataIndex: 'ok', width: 100,
      render: (ok: boolean) => <Tag color={ok ? 'green' : 'orange'}>{ok ? '正常' : '轻微延迟'}</Tag>,
    },
    { title: '延迟', dataIndex: 'latency', width: 110 },
  ]

  const approvalColumns: ColumnsType<Approval> = [
    {
      title: '类型', dataIndex: 'type', width: 100,
      render: (v: Approval['type']) => <Tag color="blue">{approvalTypeLabel[v]}</Tag>,
    },
    {
      title: '事项', dataIndex: 'title',
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{row.detail}</Typography.Text>
        </Space>
      ),
    },
    { title: '来源', dataIndex: 'from', width: 100 },
    { title: '时间', dataIndex: 'time', width: 150 },
    {
      title: '处理', width: 150,
      render: (_, row) =>
        row.status === 'PENDING' ? (
          <Space>
            <AppButton type="link" size="small" onClick={() => decide(row, true)}>通过</AppButton>
            <AppButton type="link" size="small" danger onClick={() => decide(row, false)}>驳回</AppButton>
          </Space>
        ) : (
          <Tag color={row.status === 'APPROVED' ? 'green' : 'default'}>{approvalStatusLabel[row.status]}</Tag>
        ),
    },
  ]

  return (
    <DemoPage
      title="运维工作台"
      screens="对应原型：运维 01"
      desc="统一掌握课程运行、用户权限、审计安全与系统资源状态，优先处理高风险事件。"
      extra={
        <Space>
          <AppButton type="default" onClick={() => message.success('运维运行报告已生成')}>下载运行报告</AppButton>
          <AppButton onClick={() => navigate(`${rolePath.OPS}/audit`)}>创建处置任务</AppButton>
        </Space>
      }
    >
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card>
            <Statistic title="运行中课程数" value={data.stats.activeCourses} suffix="门" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>学期 2026-2027 秋 · 4 门待同步名单</Typography.Text>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="活跃账号数" value={data.stats.activeAccounts} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>今日登录 {data.stats.todayLogin}</Typography.Text>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="待处理高风险事件" value={highPending} valueStyle={highPending > 0 ? { color: '#b91c1c' } : undefined} suffix="项" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>共 {data.events.length} 条风险事件在列</Typography.Text>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Typography.Text type="secondary">存储占用率</Typography.Text>
            <Progress percent={data.stats.storagePct} strokeColor="#0E7490" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>{data.stats.storageNote}</Typography.Text>
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={14}>
          <DemoCard title="高风险事件">
            <AppTable<RiskEvent> rowKey="id" size="small" pagination={false} columns={eventColumns} dataSource={data.events} />
          </DemoCard>
        </Col>
        <Col span={10}>
          <DemoCard title="服务健康摘要" extra={<AppButton type="link" size="small" onClick={() => navigate(`${rolePath.OPS}/storage`)}>查看全部</AppButton>}>
            <AppTable<ServiceHealth> rowKey="name" size="small" pagination={false} columns={serviceColumns} dataSource={data.services} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>解析延迟已持续 12 分钟，若继续上升将自动降级非关键任务。</Typography.Text>
          </DemoCard>
        </Col>
      </Row>

      <DemoCard title="待办审批">
        <AppTable<Approval> rowKey="id" size="small" pagination={false} columns={approvalColumns} dataSource={data.approvals} />
      </DemoCard>
    </DemoPage>
  )
}
