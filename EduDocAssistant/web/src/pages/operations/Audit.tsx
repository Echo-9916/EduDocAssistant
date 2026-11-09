// 审计与安全（运维 13–20 屏）：操作日志 / 安全事件中心 / 异常与冲突复核 / 访问策略四段。
// 数据全部来自 mock/domain/security.ts，写操作就地改内存数组，刷新回到种子状态。
import { useEffect, useMemo, useState } from 'react'
import { Alert, Card, Col, Descriptions, Drawer, Input, Row, Select, Space, Statistic, Switch, Tabs, Tag, Timeline, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import { recordPolicyChange } from '../../mock/domain/resource'
import {
  accountOf,
  accountStatusLabel,
  anomalyConclusions,
  anomalyKindLabel,
  chainOf,
  conflictKindLabel,
  conflictResolutions,
  freezeAccount,
  listAnomalies,
  listAuditEvents,
  listConflicts,
  listPolicies,
  listSecurityEvents,
  policyGroups,
  resolveAnomaly,
  resolveConflict,
  resourceTypeLabel,
  riskLabel,
  savePolicies,
  securityStatusLabel,
  securityTypeLabel,
  setSecurityStatus,
} from '../../mock/domain/security'
import type {
  Anomaly,
  AnomalyKind,
  AuditEvent,
  AuditQuery,
  AuditResourceType,
  PermConflict,
  PolicyItem,
  PolicyValues,
  RiskLevel,
  SecurityEvent,
  SecurityStatus,
  SecurityType,
} from '../../mock/domain/security'

const RISK_COLOR: Record<RiskLevel, string> = { HIGH: 'red', MEDIUM: 'orange', LOW: 'blue' }
const RESULT_COLOR = { SUCCESS: 'green', FAIL: 'red' } as const
const RESULT_LABEL = { SUCCESS: '成功', FAIL: '失败' } as const
const SEC_STATUS_COLOR: Record<SecurityStatus, string> = { PENDING: 'orange', HANDLING: 'blue', CLOSED: 'green' }
const SECURITY_TYPES: SecurityType[] = ['LOGIN_RISK', 'PRIVILEGE_ATTEMPT', 'ABNORMAL_EXPORT', 'ACCOUNT_ACTION']

/** 屏 15 操作日志 + 屏 16 审计事件详情抽屉。 */
function AuditLogTab() {
  const [rows, setRows] = useState<AuditEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState<AuditQuery>({ range: '24h', resourceType: 'ALL', actor: '', keyword: '' })
  const [detailId, setDetailId] = useState<string | null>(null)

  const load = (q: AuditQuery) => {
    setLoading(true)
    return listAuditEvents(q).then((r) => {
      setRows(r)
      setLoading(false)
    })
  }
  // 首次加载走 loading 态，之后由 apply() 带着新条件主动重查
  useEffect(() => {
    load(query)
  }, [])
  const apply = (patch: Partial<AuditQuery>) => {
    const next = { ...query, ...patch }
    setQuery(next)
    return load(next)
  }

  const detail = rows.find((r) => r.id === detailId) ?? null

  const columns: ColumnsType<AuditEvent> = [
    { title: '时间', dataIndex: 'occurredAt', width: 165 },
    {
      title: '操作人',
      dataIndex: 'actor',
      width: 170,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.actorRole}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '动作', dataIndex: 'actionType', width: 150 },
    {
      title: '对象',
      dataIndex: 'resourceRef',
      render: (v: string, row) => (
        <Space size={4}>
          <Tag>{resourceTypeLabel[row.resourceType]}</Tag>
          <span>{v}</span>
        </Space>
      ),
    },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (v: AuditEvent['result']) => <Tag color={RESULT_COLOR[v]}>{RESULT_LABEL[v]}</Tag>,
    },
    { title: 'traceId', dataIndex: 'traceId', width: 150 },
    {
      title: '操作',
      width: 100,
      render: (_, row) => (
        <AppButton type="link" size="small" onClick={() => setDetailId(row.id)}>
          事件详情
        </AppButton>
      ),
    },
  ]

  return (
    <>
      <DemoCard title="检索条件">
        <Space wrap>
          <Select
            style={{ width: 150 }}
            value={query.range}
            onChange={(v: AuditQuery['range']) => apply({ range: v })}
            options={[
              { value: '24h', label: '最近 24 小时' },
              { value: '7d', label: '最近 7 天' },
              { value: '30d', label: '最近 30 天' },
            ]}
          />
          <Select
            style={{ width: 150 }}
            value={query.resourceType}
            onChange={(v: AuditResourceType | 'ALL') => apply({ resourceType: v })}
            options={
              [
                { value: 'ALL', label: '全部对象类型' },
                ...Object.entries(resourceTypeLabel).map(([value, label]) => ({ value, label })),
              ] as { value: AuditResourceType | 'ALL'; label: string }[]
            }
          />
          <Input
            allowClear
            style={{ width: 200 }}
            placeholder="操作人姓名 / 学号 / 工号"
            value={query.actor}
            onChange={(e) => setQuery({ ...query, actor: e.target.value })}
            onPressEnter={() => apply({})}
          />
          <Input.Search
            allowClear
            enterButton="检索"
            style={{ width: 360 }}
            placeholder="搜索事件编号、动作、对象或 traceId"
            value={query.keyword}
            onChange={(e) => setQuery({ ...query, keyword: e.target.value })}
            onSearch={(v) => apply({ keyword: v })}
          />
        </Space>
      </DemoCard>

      <DemoCard title="审计事件" extra={<Typography.Text type="secondary">成功与拒绝都写入 edudoc_audit_log</Typography.Text>}>
        <AppTable<AuditEvent> rowKey="id" size="middle" loading={loading} columns={columns} dataSource={rows} />
        <AppButton
          type="default"
          style={{ marginTop: 12 }}
          onClick={() => message.success(`已导出 ${rows.length} 条检索结果（CSV），下载链接已发送至站内消息`)}
        >
          导出检索结果
        </AppButton>
      </DemoCard>

      <Drawer
        open={!!detail}
        onClose={() => setDetailId(null)}
        width={760}
        title={`审计事件详情 · ${detail?.id ?? ''}`}
        extra={
          <AppButton type="default" size="small" onClick={() => message.success('审计事件报告已导出')}>
            导出事件报告
          </AppButton>
        }
      >
        {detail && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions
              size="small"
              column={2}
              bordered
              items={[
                { key: 'id', label: '事件编号', children: detail.id },
                { key: 'at', label: '发生时间', children: detail.occurredAt },
                { key: 'risk', label: '风险等级', children: <Tag color={RISK_COLOR[detail.riskLevel]}>{riskLabel[detail.riskLevel]}</Tag> },
                { key: 'result', label: '处理结果', children: <Tag color={RESULT_COLOR[detail.result]}>{RESULT_LABEL[detail.result]}</Tag> },
              ]}
            />
            <Alert type={detail.result === 'SUCCESS' ? 'info' : 'warning'} showIcon message="事件摘要" description={detail.summary} />
            <div>
              <Typography.Title level={5}>事件链路</Typography.Title>
              <Timeline
                items={chainOf(detail).map((s) => ({
                  children: (
                    <>
                      <strong>{s.title}</strong>
                      <div>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {s.time} · {s.desc}
                        </Typography.Text>
                      </div>
                    </>
                  ),
                }))}
              />
            </div>
            <div>
              <Typography.Title level={5}>请求上下文</Typography.Title>
              <Descriptions
                size="small"
                column={2}
                bordered
                items={[
                  { key: 'actor', label: '操作者', children: detail.actor },
                  { key: 'role', label: '操作时角色', children: detail.actorRole },
                  { key: 'ip', label: '来源 IP', children: detail.sourceIp },
                  { key: 'ua', label: '终端', children: detail.userAgent },
                  { key: 'res', label: '对象', children: `${resourceTypeLabel[detail.resourceType]} · ${detail.resourceRef}` },
                  { key: 'code', label: '结果码', children: detail.resultCode },
                  { key: 'trace', label: 'traceId', children: detail.traceId, span: 2 },
                ]}
              />
            </div>
            <div>
              <Typography.Title level={5}>影响对象</Typography.Title>
              <AppTable
                rowKey="name"
                size="small"
                pagination={false}
                dataSource={detail.impacts ?? []}
                columns={[
                  { title: '对象类型', dataIndex: 'type', width: 90 },
                  { title: '对象', dataIndex: 'name', width: 200 },
                  { title: '影响', dataIndex: 'detail' },
                  { title: '标记', dataIndex: 'mark', width: 90, render: (v: string) => <Tag>{v}</Tag> },
                ]}
              />
            </div>
            <div>
              <Typography.Title level={5}>处置建议</Typography.Title>
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {(detail.advice ?? ['无需处置，保留审计记录。']).map((a) => (
                  <li key={a}>{a}</li>
                ))}
              </ul>
            </div>
          </Space>
        )}
      </Drawer>
    </>
  )
}

/** 屏 18 安全事件中心 + 屏 19 事件详情抽屉 + 屏 20 冻结账号确认。 */
function SecurityTab() {
  const [rows, setRows] = useState<SecurityEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [level, setLevel] = useState<RiskLevel | 'ALL'>('ALL')
  const [status, setStatus] = useState<SecurityStatus | 'ALL'>('ALL')
  const [detailId, setDetailId] = useState<string | null>(null)
  const [freezeId, setFreezeId] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [notifyDept, setNotifyDept] = useState(true)

  const load = () => {
    setLoading(true)
    return listSecurityEvents().then((r) => {
      setRows(r)
      setLoading(false)
    })
  }
  useEffect(() => {
    load()
  }, [])

  const summary = useMemo(
    () =>
      SECURITY_TYPES.map((t) => {
        const list = rows.filter((r) => r.type === t)
        return { type: t, total: list.length, pending: list.filter((r) => r.status !== 'CLOSED').length }
      }),
    [rows],
  )
  const data = rows.filter((r) => (level === 'ALL' || r.level === level) && (status === 'ALL' || r.status === status))
  const detail = rows.find((r) => r.id === detailId) ?? null
  const freeze = rows.find((r) => r.id === freezeId) ?? null
  const freezeAcc = freeze ? accountOf(freeze.account) : undefined

  const act = async (id: string, next: SecurityStatus, tip: string) => {
    await setSecurityStatus(id, next)
    await load()
    message.success(tip)
  }
  const openFreeze = (ev: SecurityEvent) => {
    setReason(ev.reason)
    setNotifyDept(true)
    setFreezeId(ev.id)
  }
  const doFreeze = async () => {
    if (!freeze) return
    if (!reason.trim()) {
      message.warning('请填写处置原因')
      return
    }
    const name = await freezeAccount(freeze.id, reason.trim(), notifyDept)
    setFreezeId(null)
    await load()
    message.success(`账号 ${name} 已冻结，事件 ${freeze.id} 已关闭`)
  }

  const columns: ColumnsType<SecurityEvent> = [
    { title: '等级', dataIndex: 'level', width: 90, render: (v: RiskLevel) => <Tag color={RISK_COLOR[v]}>{riskLabel[v]}</Tag> },
    { title: '类型', dataIndex: 'type', width: 100, render: (v: SecurityType) => securityTypeLabel[v] },
    { title: '命中规则', dataIndex: 'rule', ellipsis: true },
    {
      title: '涉及账号',
      dataIndex: 'account',
      width: 150,
      render: (v: string) => `${accountOf(v)?.name ?? ''} · ${v}`,
    },
    { title: '风险评分', dataIndex: 'riskScore', width: 90 },
    { title: '首次发现', dataIndex: 'firstSeen', width: 150 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: SecurityStatus) => <Tag color={SEC_STATUS_COLOR[v]}>{securityStatusLabel[v]}</Tag>,
    },
    {
      title: '操作',
      width: 170,
      render: (_, row) => (
        <Space>
          <AppButton type="link" size="small" onClick={() => setDetailId(row.id)}>
            详情
          </AppButton>
          <AppButton type="link" size="small" danger disabled={row.status === 'CLOSED'} onClick={() => openFreeze(row)}>
            冻结账号
          </AppButton>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        {summary.map((s) => (
          <Col span={6} key={s.type}>
            <Card size="small">
              <Statistic title={`${securityTypeLabel[s.type]}（实时）`} value={s.total} suffix="件" valueStyle={{ color: '#0E7490' }} />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {s.pending > 0 ? `${s.pending} 项待处置` : '全部已关闭'}
              </Typography.Text>
            </Card>
          </Col>
        ))}
      </Row>

      <DemoCard
        title="安全事件"
        extra={
          <Space wrap>
            <Select
              style={{ width: 130 }}
              value={level}
              onChange={setLevel}
              options={
                [
                  { value: 'ALL', label: '全部等级' },
                  ...Object.entries(riskLabel).map(([value, label]) => ({ value, label })),
                ] as { value: RiskLevel | 'ALL'; label: string }[]
              }
            />
            <Select
              style={{ width: 130 }}
              value={status}
              onChange={setStatus}
              options={
                [
                  { value: 'ALL', label: '全部状态' },
                  ...Object.entries(securityStatusLabel).map(([value, label]) => ({ value, label })),
                ] as { value: SecurityStatus | 'ALL'; label: string }[]
              }
            />
            <AppButton type="default" onClick={() => message.success(`已导出 ${data.length} 条安全事件`)}>
              导出事件
            </AppButton>
          </Space>
        }
      >
        <AppTable<SecurityEvent> rowKey="id" size="middle" loading={loading} columns={columns} dataSource={data} />
      </DemoCard>

      <Drawer open={!!detail} onClose={() => setDetailId(null)} width={720} title={`安全事件详情 · ${detail?.id ?? ''}`}>
        {detail && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Row gutter={16} align="middle">
              <Col span={6}>
                <Statistic title="风险评分" value={detail.riskScore} valueStyle={{ color: detail.level === 'HIGH' ? '#b91c1c' : '#b45309' }} />
              </Col>
              <Col span={18}>
                <Tag color={RISK_COLOR[detail.level]}>{riskLabel[detail.level]}</Tag>
                <Tag color={SEC_STATUS_COLOR[detail.status]}>{securityStatusLabel[detail.status]}</Tag>
              </Col>
            </Row>
            <Descriptions
              size="small"
              column={2}
              bordered
              items={[
                { key: 'id', label: '事件编号', children: detail.id },
                { key: 'type', label: '事件类型', children: securityTypeLabel[detail.type] },
                { key: 'rule', label: '命中规则', children: detail.rule, span: 2 },
                { key: 'acc', label: '涉及账号', children: `${accountOf(detail.account)?.name ?? ''} · ${detail.account}` },
                { key: 'ip', label: '来源 IP', children: detail.sourceIp },
                { key: 'at', label: '首次发现', children: detail.firstSeen },
                { key: 'st', label: '处置状态', children: securityStatusLabel[detail.status] },
              ]}
            />
            <Alert type="warning" showIcon message="事件概览" description={detail.summary} />
            <div>
              <Typography.Title level={5}>事件时间线</Typography.Title>
              <Timeline
                items={detail.timeline.map((s) => ({
                  children: (
                    <>
                      <strong>{s.title}</strong>
                      <div>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {s.time} · {s.desc}
                        </Typography.Text>
                      </div>
                    </>
                  ),
                }))}
              />
            </div>
            <div>
              <Typography.Title level={5}>受影响资源</Typography.Title>
              <AppTable
                rowKey="name"
                size="small"
                pagination={false}
                dataSource={detail.resources}
                columns={[
                  { title: '资源', dataIndex: 'name', width: 180 },
                  { title: '说明', dataIndex: 'detail' },
                  { title: '标记', dataIndex: 'mark', width: 100, render: (v: string) => <Tag>{v}</Tag> },
                ]}
              />
            </div>
            <div>
              <Typography.Title level={5}>处置建议</Typography.Title>
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {detail.advice.map((a) => (
                  <li key={a}>{a}</li>
                ))}
              </ul>
            </div>
            <Space wrap>
              <AppButton type="default" onClick={() => message.info('事件已加入忽略列表，24 小时内不再提醒')}>
                忽略事件
              </AppButton>
              <AppButton type="default" disabled={detail.status === 'HANDLING'} onClick={() => act(detail.id, 'HANDLING', '事件已升级为处置中，已通知当班运维 赵工')}>
                升级事件
              </AppButton>
              <AppButton type="default" disabled={detail.status === 'CLOSED'} onClick={() => act(detail.id, 'CLOSED', `事件 ${detail.id} 已关闭`)}>
                关闭事件
              </AppButton>
              <AppButton danger disabled={detail.status === 'CLOSED'} onClick={() => openFreeze(detail)}>
                冻结账号
              </AppButton>
            </Space>
          </Space>
        )}
      </Drawer>

      <AppModal
        open={!!freeze}
        title={`冻结账号 ${freeze?.account ?? ''}？`}
        okText="确认冻结"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        onOk={doFreeze}
        onCancel={() => setFreezeId(null)}
      >
        {freeze && freezeAcc && (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Alert type="warning" showIcon message={`账号将立即退出全部 ${freezeAcc.sessions} 个活动会话，课程和文档访问会被停止。`} />
            <Descriptions
              size="small"
              column={1}
              bordered
              title="影响范围"
              items={[
                { key: 'acc', label: '账号', children: `${freezeAcc.name} · ${freezeAcc.account}` },
                { key: 'role', label: '身份', children: `${freezeAcc.roleLabel} · ${freezeAcc.dept}` },
                { key: 'scope', label: '课程范围', children: freezeAcc.courseScope },
                { key: 'st', label: '当前账号状态', children: accountStatusLabel(freezeAcc.status) },
                { key: 'ev', label: '关联安全事件', children: `${freeze.id}（${securityTypeLabel[freeze.type]}）` },
              ]}
            />
            <div>
              <Typography.Text strong>恢复方式</Typography.Text>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {freeze.restoreNote}
              </Typography.Paragraph>
            </div>
            <div>
              <Typography.Text strong>
                <span style={{ color: '#ff4d4f' }}>* </span>处置原因
              </Typography.Text>
              <Input.TextArea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="请填写处置原因，将写入审计日志" />
            </div>
            <Space>
              <Switch checked={notifyDept} onChange={setNotifyDept} />
              <Typography.Text>通知账号所属院系（{freezeAcc.dept}教学管理员）</Typography.Text>
            </Space>
          </Space>
        )}
      </AppModal>
    </>
  )
}

/** 屏 17 异常复核 + 屏 14 权限冲突复核。 */
function ReviewTab() {
  const [anomalies, setAnomalies] = useState<Anomaly[]>([])
  const [conflicts, setConflicts] = useState<PermConflict[]>([])
  const [loading, setLoading] = useState(true)
  const [currentId, setCurrentId] = useState<string | null>(null)
  const [conclusion, setConclusion] = useState<string>(anomalyConclusions[0])
  const [note, setNote] = useState('')
  const [notifyTeacher, setNotifyTeacher] = useState(true)
  const [watch, setWatch] = useState(true)
  const [draft, setDraft] = useState<Record<string, string>>({})

  const load = () => {
    setLoading(true)
    return Promise.all([listAnomalies(), listConflicts()]).then(([a, c]) => {
      setAnomalies(a)
      setConflicts(c)
      setLoading(false)
    })
  }
  useEffect(() => {
    load()
  }, [])

  const current = anomalies.find((a) => a.id === currentId) ?? null
  const openReview = (row: Anomaly) => {
    setCurrentId(row.id)
    setConclusion(row.conclusion ?? anomalyConclusions[0])
    setNote(row.note ?? '账号近期首次出现同类行为，尚未达到冻结阈值，先通知课程负责人并持续观察 24 小时。')
  }
  const submit = async () => {
    if (!current) return
    if (!note.trim()) {
      message.warning('请填写处置说明')
      return
    }
    await resolveAnomaly(current.id, conclusion, note.trim())
    await load()
    setCurrentId(null)
    message.success(`异常复核结论已提交：${conclusion}`)
  }

  const anomalyColumns: ColumnsType<Anomaly> = [
    {
      title: '异常编号',
      dataIndex: 'id',
      width: 190,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            关联 {row.ref}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '指标类型', dataIndex: 'kind', width: 110, render: (v: AnomalyKind) => <Tag color="orange">{anomalyKindLabel[v]}</Tag> },
    { title: '异常描述', dataIndex: 'title' },
    { title: '对象', dataIndex: 'account', width: 150 },
    { title: '风险评分', dataIndex: 'riskScore', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: Anomaly['status'], row) => (
        <Tag color={v === 'DONE' ? 'green' : 'orange'}>{v === 'DONE' ? `已复核 · ${row.conclusion ?? ''}` : '待复核'}</Tag>
      ),
    },
    {
      title: '操作',
      width: 80,
      render: (_, row) => (
        <AppButton type="link" size="small" onClick={() => openReview(row)}>
          复核
        </AppButton>
      ),
    },
  ]

  const conflictColumns: ColumnsType<PermConflict> = [
    {
      title: '对象',
      dataIndex: 'target',
      width: 180,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.targetMeta}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '冲突项', dataIndex: 'item' },
    { title: '冲突类型', dataIndex: 'kind', width: 130, render: (v: PermConflict['kind']) => <Tag>{conflictKindLabel[v]}</Tag> },
    { title: '风险', dataIndex: 'level', width: 90, render: (v: RiskLevel) => <Tag color={RISK_COLOR[v]}>{riskLabel[v]}</Tag> },
    { title: '发现时间', dataIndex: 'foundAt', width: 150 },
    {
      title: '处理结论',
      width: 200,
      render: (_, row) => (
        <Select
          style={{ width: 190 }}
          size="small"
          disabled={row.status === 'DONE'}
          value={row.resolution ?? draft[row.id] ?? conflictResolutions[0]}
          onChange={(v: string) => setDraft({ ...draft, [row.id]: v })}
          options={conflictResolutions.map((r) => ({ value: r, label: r }))}
        />
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: PermConflict['status']) => <Tag color={v === 'DONE' ? 'green' : 'orange'}>{v === 'DONE' ? '已处理' : '待处理'}</Tag>,
    },
    {
      title: '操作',
      width: 80,
      render: (_, row) => (
        <AppButton
          type="link"
          size="small"
          disabled={row.status === 'DONE'}
          onClick={async () => {
            const resolution = draft[row.id] ?? conflictResolutions[0]
            await resolveConflict(row.id, resolution)
            await load()
            message.success(`已记录处理结论：${resolution}`)
          }}
        >
          记录
        </AppButton>
      ),
    },
  ]

  const stat = (label: string, value: number, color: string) => (
    <Card size="small" style={{ width: 160 }}>
      <Statistic title={label} value={value} valueStyle={{ color }} />
    </Card>
  )

  return (
    <>
      <DemoCard title="异常复核" extra={<Typography.Text type="secondary">按访问频率、权限边界、登录风险三类指标复核</Typography.Text>}>
        <AppTable<Anomaly> rowKey="id" size="middle" loading={loading} columns={anomalyColumns} dataSource={anomalies} pagination={false} />
        {current && (
          <Card size="small" style={{ marginTop: 16 }} title={`处置结论 · ${current.id}`}>
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Descriptions
                size="small"
                column={1}
                bordered
                items={(Object.keys(anomalyKindLabel) as AnomalyKind[]).map((k) => ({
                  key: k,
                  label: anomalyKindLabel[k],
                  children: (
                    <Space>
                      <span>{current.metrics[k]}</span>
                      {current.kind === k && <Tag color="red">命中</Tag>}
                    </Space>
                  ),
                }))}
              />
              <div>
                <Typography.Text strong>风险因子</Typography.Text>
                <div>
                  {current.factors.map((f) => (
                    <div key={f.title} style={{ marginBottom: 4 }}>
                      <Tag color={f.hit ? 'red' : 'default'}>{f.hit ? '命中' : '未命中'}</Tag>
                      <Typography.Text strong>{f.title}</Typography.Text>
                      <Typography.Text type="secondary"> · {f.desc}</Typography.Text>
                    </div>
                  ))}
                </div>
              </div>
              <Alert type="info" showIcon message="证据摘要" description={current.evidence} />
              <Space wrap align="center">
                <Typography.Text strong>结论</Typography.Text>
                <Select
                  style={{ width: 180 }}
                  value={conclusion}
                  onChange={setConclusion}
                  options={anomalyConclusions.map((c) => ({ value: c, label: c }))}
                />
              </Space>
              <div>
                <Typography.Text strong>
                  <span style={{ color: '#ff4d4f' }}>* </span>处置说明
                </Typography.Text>
                <Input.TextArea rows={3} value={note} onChange={(e) => setNote(e.target.value)} />
              </div>
              <Space size={24} wrap>
                <Space>
                  <Switch checked={notifyTeacher} onChange={setNotifyTeacher} />
                  <Typography.Text>通知课程负责人</Typography.Text>
                </Space>
                <Space>
                  <Switch checked={watch} onChange={setWatch} />
                  <Typography.Text>加入观察名单（24 小时内重复出现时升级）</Typography.Text>
                </Space>
              </Space>
              <Space>
                <AppButton onClick={submit}>提交复核</AppButton>
                <AppButton type="default" onClick={() => setCurrentId(null)}>
                  取消
                </AppButton>
              </Space>
            </Space>
          </Card>
        )}
      </DemoCard>

      <DemoCard
        title="权限冲突复核"
        extra={
          <AppButton type="default" onClick={() => message.success('权限冲突扫描已完成，未发现新增冲突')}>
            重新扫描
          </AppButton>
        }
      >
        <Space wrap style={{ marginBottom: 16 }}>
          {stat('高风险冲突', conflicts.filter((c) => c.level === 'HIGH' && c.status === 'PENDING').length, '#b91c1c')}
          {stat('重复授权', conflicts.filter((c) => c.kind === 'DUPLICATE').length, '#b45309')}
          {stat('过期临时权限', conflicts.filter((c) => c.kind === 'EXPIRED').length, '#2563eb')}
          {stat('已处理', conflicts.filter((c) => c.status === 'DONE').length, '#15803d')}
        </Space>
        <AppTable<PermConflict> rowKey="id" size="middle" loading={loading} columns={conflictColumns} dataSource={conflicts} pagination={false} />
        <Alert
          style={{ marginTop: 12 }}
          type="warning"
          showIcon
          message="高风险冲突需在审计日志中记录处置原因与审批人；角色变更与审计查看不得集中在同一账号。"
        />
      </DemoCard>
    </>
  )
}

/** 屏 13 访问策略：四组配置项，保存后写入配置变更记录（屏 24）。 */
function PolicyTab() {
  const [items, setItems] = useState<PolicyItem[]>([])
  const [values, setValues] = useState<PolicyValues>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listPolicies().then((r) => {
      setItems(r)
      setValues(Object.fromEntries(r.map((i) => [i.id, i.value])))
      setLoading(false)
    })
  }, [])

  const save = async () => {
    const changed = await savePolicies(values)
    await recordPolicyChange(changed)
    message.success(
      changed.length
        ? `访问策略已保存，${changed.join(' / ')} 已写入配置变更记录（待审批）`
        : '访问策略已保存，本次无配置项变更',
    )
  }

  const set = (id: string, v: boolean | string) => setValues({ ...values, [id]: v })

  return (
    <>
      {policyGroups.map((group) => (
        <DemoCard key={group} title={group}>
          {items
            .filter((i) => i.group === group)
            .map((i) => (
              <div
                key={i.id}
                style={{ display: 'flex', justifyContent: 'space-between', gap: 16, padding: '10px 0', borderBottom: '1px solid #F0F2F5' }}
              >
                <div>
                  <Typography.Text strong>{i.label}</Typography.Text>
                  <div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {i.desc}
                    </Typography.Text>
                  </div>
                </div>
                {i.type === 'switch' ? (
                  <Switch checked={values[i.id] === true} disabled={loading} onChange={(v) => set(i.id, v)} />
                ) : (
                  <Select
                    style={{ width: 180 }}
                    value={String(values[i.id] ?? '')}
                    disabled={loading}
                    onChange={(v: string) => set(i.id, v)}
                    options={(i.options ?? []).map((o) => ({ value: o, label: o }))}
                  />
                )}
              </div>
            ))}
        </DemoCard>
      ))}
      <Alert
        style={{ marginBottom: 12 }}
        type="info"
        showIcon
        message="课程文档访问仍以课程身份和共享范围为主，不由运维角色继承；策略变更需审批后生效。"
      />
      <AppButton onClick={save} disabled={loading}>
        保存策略
      </AppButton>
    </>
  )
}

/** 审计与安全：运维 13/14/15/16/17/18/19/20 屏门面。 */
export default function OpsAudit() {
  return (
    <DemoPage
      title="审计与安全"
      screens="对应原型：运维 13/14/15/16/17/18/19/20"
      desc="检索账号、课程、文档、权限与系统级操作，处置登录风险、越权尝试与异常导出事件，并维护访问策略。"
    >
      <Tabs
        defaultActiveKey="log"
        items={[
          { key: 'log', label: '操作日志', children: <AuditLogTab /> },
          { key: 'security', label: '安全事件', children: <SecurityTab /> },
          { key: 'review', label: '异常与冲突复核', children: <ReviewTab /> },
          { key: 'policy', label: '访问策略', children: <PolicyTab /> },
        ]}
      />
    </DemoPage>
  )
}
