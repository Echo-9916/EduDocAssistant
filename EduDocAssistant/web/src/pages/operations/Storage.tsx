// 存储与资源（运维 21–24 屏）：存储与回收站 / 系统服务状态 / 配置变更记录三段。
// 数据来自 mock/domain/resource.ts，清理与回滚就地改内存数组，容量随清理释放。
import { useEffect, useState } from 'react'
import { Alert, Card, Col, Descriptions, Input, Popconfirm, Progress, Row, Select, Space, Statistic, Tabs, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import {
  changeStatusLabel,
  cleanupObject,
  fmtGb,
  fmtTb,
  getCapacity,
  listConfigChanges,
  listServices,
  listStorageObjects,
  reclaimableGb,
  refreshServices,
  restoreObject,
  rollbackChange,
  serviceHealthLabel,
  storageStatusLabel,
} from '../../mock/domain/resource'
import type { Capacity, ChangeStatus, ConfigChange, ServiceHealth, ServiceStatusRow, StorageObject, StorageStatus } from '../../mock/domain/resource'

const STORAGE_COLOR: Record<StorageStatus, string> = { NORMAL: 'green', PENDING_CLEAN: 'orange', RECYCLE: 'gold', CLEANED: 'default' }
const HEALTH_COLOR: Record<ServiceHealth, string> = { NORMAL: 'green', DEGRADED: 'orange', DOWN: 'red' }
const CHANGE_COLOR: Record<ChangeStatus, string> = { ACTIVE: 'green', PENDING: 'orange', ROLLED_BACK: 'red' }

/** 屏 21 存储与回收站 + 屏 22 清理确认。 */
function StorageTab() {
  const [rows, setRows] = useState<StorageObject[]>([])
  const [cap, setCap] = useState<Capacity>(getCapacity())
  const [reclaim, setReclaim] = useState(0)
  const [loading, setLoading] = useState(true)
  const [status, setStatus] = useState<StorageStatus | 'ALL'>('ALL')
  const [keyword, setKeyword] = useState('')
  const [cleanId, setCleanId] = useState<string | null>(null)

  const load = (s: StorageStatus | 'ALL', kw: string) => {
    setLoading(true)
    return listStorageObjects(s, kw).then((r) => {
      setRows(r)
      setCap(getCapacity())
      setReclaim(reclaimableGb())
      setLoading(false)
    })
  }
  // 首次加载走 loading 态，之后由 apply() 带着新条件主动重查
  useEffect(() => {
    load(status, keyword)
  }, [])
  const apply = (patch: { status?: StorageStatus | 'ALL'; keyword?: string }) => {
    const s = patch.status ?? status
    const kw = patch.keyword ?? keyword
    if (patch.status !== undefined) setStatus(s)
    if (patch.keyword !== undefined) setKeyword(kw)
    return load(s, kw)
  }

  const target = rows.find((r) => r.id === cleanId) ?? null
  const pct = Math.round((cap.usedGb / cap.quotaGb) * 100)

  const columns: ColumnsType<StorageObject> = [
    {
      title: '对象',
      dataIndex: 'name',
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.note}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '归属课程', dataIndex: 'course', width: 140 },
    { title: '大小', dataIndex: 'sizeGb', width: 100, render: (v: number) => fmtGb(v) },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: StorageStatus) => <Tag color={STORAGE_COLOR[v]}>{storageStatusLabel[v]}</Tag>,
    },
    { title: '保留期', dataIndex: 'retention', width: 170 },
    { title: '最近访问', dataIndex: 'lastAccess', width: 150 },
    {
      title: '操作',
      width: 150,
      render: (_, row) => (
        <Space>
          {row.status === 'RECYCLE' && (
            <Popconfirm
              title={`从回收站恢复「${row.name}」？`}
              description="恢复后回到原课程空间，保留期恢复为长期保留。"
              okText="恢复"
              cancelText="取消"
              onConfirm={async () => {
                const name = await restoreObject(row.id)
                await load(status, keyword)
                message.success(`「${name}」已从回收站恢复`)
              }}
            >
              <AppButton type="link" size="small">
                恢复
              </AppButton>
            </Popconfirm>
          )}
          <AppButton type="link" size="small" danger disabled={row.status === 'CLEANED'} onClick={() => setCleanId(row.id)}>
            清理
          </AppButton>
        </Space>
      ),
    },
  ]

  const card = (title: string, value: string, note: string, color = '#0E7490') => (
    <Card size="small">
      <Statistic title={title} value={value} valueStyle={{ color, fontSize: 20 }} />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {note}
      </Typography.Text>
    </Card>
  )

  return (
    <>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={9}>
          <Card size="small" title="团队存储配额">
            <Progress percent={pct} strokeColor="#0E7490" />
            <Typography.Text type="secondary">
              已用 {fmtTb(cap.usedGb)} / 配额 {fmtTb(cap.quotaGb)}（占用 {pct}%）
            </Typography.Text>
          </Card>
        </Col>
        <Col span={5}>{card('回收站容量', fmtGb(cap.recycleGb), '保留 30 天，到期后按策略清理', '#b45309')}</Col>
        <Col span={5}>{card('过期对象数', `${cap.expiredCount} 个`, '已超过保留期，等待清理确认', '#2563eb')}</Col>
        <Col span={5}>{card('本次可释放', fmtGb(reclaim), '待清理与回收站对象合计', '#15803d')}</Col>
      </Row>

      <DemoCard
        title="存储对象"
        extra={
          <Space wrap>
            <Select
              style={{ width: 140 }}
              value={status}
              onChange={(v: StorageStatus | 'ALL') => apply({ status: v })}
              options={
                [
                  { value: 'ALL', label: '全部状态' },
                  ...Object.entries(storageStatusLabel).map(([value, label]) => ({ value, label })),
                ] as { value: StorageStatus | 'ALL'; label: string }[]
              }
            />
            <Input.Search
              allowClear
              enterButton="检索"
              style={{ width: 280 }}
              placeholder="搜索对象名称或归属课程"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onSearch={(v) => apply({ keyword: v })}
            />
            <AppButton type="default" onClick={() => message.success(`已导出 ${rows.length} 条存储清单`)}>
              导出存储清单
            </AppButton>
          </Space>
        }
      >
        <AppTable<StorageObject> rowKey="id" size="middle" loading={loading} columns={columns} dataSource={rows} />
      </DemoCard>

      <AppModal
        open={!!target}
        title="存储清理确认"
        okText="确认清理"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        onCancel={() => setCleanId(null)}
        onOk={async () => {
          if (!target) return
          const freed = await cleanupObject(target.id)
          setCleanId(null)
          await load(status, keyword)
          message.success(`「${target.name}」已永久清理，释放 ${freed}`)
        }}
      >
        {target && (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Alert type="error" showIcon message="永久删除后不可恢复" description="清理动作会同时删除版本链派生对象与分块缓存，仅保留审计摘要。" />
            <Descriptions
              size="small"
              column={1}
              bordered
              items={[
                { key: 'name', label: '清理对象', children: target.name },
                { key: 'course', label: '归属课程', children: target.course },
                { key: 'size', label: '对象大小', children: fmtGb(target.sizeGb) },
                { key: 'status', label: '当前状态', children: storageStatusLabel[target.status] },
                { key: 'retention', label: '保留期', children: target.retention },
                { key: 'window', label: '恢复窗口', children: target.recoverWindow },
              ]}
            />
            <Typography.Text type="secondary">审批人：赵工（敏感操作已按访问策略完成二次确认）</Typography.Text>
          </Space>
        )}
      </AppModal>
    </>
  )
}

/** 屏 23 系统服务状态：异常服务可查看积压任务。 */
function ServicesTab() {
  const [rows, setRows] = useState<ServiceStatusRow[]>([])
  const [loading, setLoading] = useState(true)
  const [backlog, setBacklog] = useState<ServiceStatusRow | null>(null)

  const load = () => {
    setLoading(true)
    return listServices().then((r) => {
      setRows(r)
      setLoading(false)
    })
  }
  useEffect(() => {
    load()
  }, [])

  const columns: ColumnsType<ServiceStatusRow> = [
    { title: '服务', dataIndex: 'name', width: 140 },
    {
      title: '可用性',
      dataIndex: 'health',
      width: 100,
      render: (v: ServiceHealth) => <Tag color={HEALTH_COLOR[v]}>{serviceHealthLabel[v]}</Tag>,
    },
    { title: 'P95 延迟', dataIndex: 'p95', width: 110 },
    {
      title: '队列积压',
      dataIndex: 'backlog',
      width: 110,
      render: (v: number) => <Typography.Text type={v > 20 ? 'danger' : undefined}>{v} 条</Typography.Text>,
    },
    { title: '最近检查时间', dataIndex: 'lastCheck', width: 170 },
    {
      title: '操作',
      render: (_, row) =>
        row.health === 'NORMAL' ? (
          <Typography.Text type="secondary">运行正常，无积压任务</Typography.Text>
        ) : (
          <AppButton type="link" size="small" onClick={() => setBacklog(row)}>
            查看积压任务
          </AppButton>
        ),
    },
  ]

  const count = (h: ServiceHealth) => rows.filter((r) => r.health === h).length

  return (
    <>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="服务正常" value={count('NORMAL')} suffix={`/ ${rows.length}`} valueStyle={{ color: '#15803d' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="降级" value={count('DEGRADED')} valueStyle={{ color: '#b45309' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="中断" value={count('DOWN')} valueStyle={{ color: '#b91c1c' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="队列积压合计" value={rows.reduce((s, r) => s + r.backlog, 0)} suffix="条" valueStyle={{ color: '#0E7490' }} />
          </Card>
        </Col>
      </Row>

      <DemoCard
        title="系统服务"
        extra={
          <AppButton
            type="default"
            onClick={async () => {
              setRows(await refreshServices())
              message.success('服务状态已刷新')
            }}
          >
            刷新状态
          </AppButton>
        }
      >
        <AppTable<ServiceStatusRow> rowKey="id" size="middle" loading={loading} columns={columns} dataSource={rows} pagination={false} />
        <Alert
          style={{ marginTop: 12 }}
          type="info"
          showIcon
          message="今天 22:00–23:00 计划进行文档解析服务扩缩容，预计无用户可见中断。"
        />
      </DemoCard>

      <AppModal open={!!backlog} title={`积压任务 · ${backlog?.name ?? ''}`} footer={null} onCancel={() => setBacklog(null)}>
        {backlog && (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Alert
              type={backlog.health === 'DOWN' ? 'error' : 'warning'}
              showIcon
              message={`${serviceHealthLabel[backlog.health]} · 积压 ${backlog.backlog} 条 · P95 ${backlog.p95}`}
              description={backlog.backlogNote}
            />
            <Typography.Text type="secondary">最近检查时间：{backlog.lastCheck}</Typography.Text>
          </Space>
        )}
      </AppModal>
    </>
  )
}

/** 屏 24 配置变更记录：已生效项可就地回滚。 */
function ConfigTab() {
  const [rows, setRows] = useState<ConfigChange[]>([])
  const [loading, setLoading] = useState(true)

  const load = () => {
    setLoading(true)
    return listConfigChanges().then((r) => {
      setRows(r)
      setLoading(false)
    })
  }
  useEffect(() => {
    load()
  }, [])

  const columns: ColumnsType<ConfigChange> = [
    { title: '时间', dataIndex: 'time', width: 160 },
    {
      title: '变更项',
      dataIndex: 'item',
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.detail}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '影响面', dataIndex: 'scope', width: 160 },
    { title: '提交人', dataIndex: 'submitter', width: 90 },
    { title: '审批人', dataIndex: 'approver', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: ChangeStatus) => <Tag color={CHANGE_COLOR[v]}>{changeStatusLabel[v]}</Tag>,
    },
    {
      title: '操作',
      width: 90,
      render: (_, row) =>
        row.status === 'ACTIVE' ? (
          <Popconfirm
            title={`回滚「${row.item}」？`}
            description={`回滚后恢复到：${row.rollbackTo}，并写入一条新的变更记录。`}
            okText="确认回滚"
            cancelText="取消"
            onConfirm={async () => {
              await rollbackChange(row.id)
              await load()
              message.success(`已回滚至：${row.rollbackTo}`)
            }}
          >
            <AppButton type="link" size="small" danger>
              回滚
            </AppButton>
          </Popconfirm>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
  ]

  return (
    <DemoCard title="变更记录" extra={<AppButton type="default" onClick={() => message.success(`已导出 ${rows.length} 条配置变更记录`)}>导出记录</AppButton>}>
      <AppTable<ConfigChange> rowKey="id" size="middle" loading={loading} columns={columns} dataSource={rows} />
      <Alert
        style={{ marginTop: 12 }}
        type="warning"
        showIcon
        message="影响访问控制的配置变更需审批后生效；访问策略页保存的策略会以「待审批」出现在本列表。"
      />
    </DemoCard>
  )
}

/** 存储与资源：运维 21/22/23/24 屏门面。 */
export default function OpsStorage() {
  return (
    <DemoPage
      title="存储与资源"
      screens="对应原型：运维 21/22/23/24"
      desc="查看团队存储、过期对象与回收站容量，安排安全清理与恢复，同时监控服务健康与配置变更。"
    >
      <Tabs
        defaultActiveKey="storage"
        items={[
          { key: 'storage', label: '存储与回收站', children: <StorageTab /> },
          { key: 'services', label: '系统服务状态', children: <ServicesTab /> },
          { key: 'config', label: '配置变更记录', children: <ConfigTab /> },
        ]}
      />
    </DemoPage>
  )
}
