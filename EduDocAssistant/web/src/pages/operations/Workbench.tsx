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
import { getWorkbenchSummary } from '../../api/opsWorkbench'
import type { WorkbenchSummary } from '../../api/opsWorkbench'
import { getErrorMessage } from '../../api/http'

/** 演示配额 2 TB：用量来自接口，配额暂无配置表，沿用门面口径。 */
const STORAGE_QUOTA_BYTES = 2 * 1024 ** 4

function storagePct(bytes: number) {
  return Math.min(100, Math.round((bytes / STORAGE_QUOTA_BYTES) * 1000) / 10)
}

function storageNote(bytes: number, objects: number) {
  const used = bytes >= 1024 ** 4
    ? `${(bytes / 1024 ** 4).toFixed(2)} TB`
    : `${(bytes / 1024 ** 3).toFixed(2)} GB`
  return `${used} / 2 TB · ${objects} 个对象`
}

/** 运维工作台：统一掌握课程运行、用户权限、审计安全与系统资源（对应原型：运维 01）。 */
export default function OpsWorkbench() {
  const navigate = useNavigate()
  const [data, setData] = useState<WorkbenchData | null>(null)
  const [summary, setSummary] = useState<WorkbenchSummary | null>(null)

  useEffect(() => {
    loadWorkbench().then(setData)
    getWorkbenchSummary().then(setSummary).catch((e) => message.error(getErrorMessage(e)))
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
      live
      mixedNote="真实数据：用户 / 文档 / 近 7 天高风险 / 存储；演示数据：风险事件明细 / 服务健康 / 待办审批"
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
            <Statistic title="用户总数" value={summary?.userTotal ?? '-'} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              冻结 {summary?.userLocked ?? '-'} 人 · 今日 AI 任务 {summary?.aiTasksToday ?? '-'} 项
              （成功率 {summary ? `${summary.aiSuccessRate}%` : '-'}）
            </Typography.Text>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="文档总数" value={summary?.documentTotal ?? '-'} suffix="份" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>不含已删除文档</Typography.Text>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="近 7 天高风险事件"
              value={summary?.auditHighRisk7d ?? '-'}
              valueStyle={(summary?.auditHighRisk7d ?? 0) > 0 ? { color: '#b91c1c' } : undefined}
              suffix="项"
            />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>审计口径：同一操作人 7 天内失败 ≥2 次</Typography.Text>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Typography.Text type="secondary">存储占用率</Typography.Text>
            <Progress percent={summary ? storagePct(summary.storageBytes) : 0} strokeColor="#0E7490" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {summary ? storageNote(summary.storageBytes, summary.storageObjects) : '-'}
            </Typography.Text>
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
