import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Avatar,
  Badge,
  Empty,
  Input,
  List,
  Popconfirm,
  Radio,
  Segmented,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import type { RoleCodeValue } from '../../api/types'
import { getErrorMessage } from '../../api/http'
import { listDocuments } from '../../api/documents'
import type { DocumentSummary } from '../../api/documents'
import {
  addCollaborator,
  deleteAnnotation,
  heartbeatPresence,
  listAnnotations,
  listCollaborators,
  listPresence,
  removeCollaborator,
  resolveAnnotation,
} from '../../api/collaboration'
import type {
  AnnotationView,
  CollabPermission,
  CollaboratorView,
  PresenceView,
} from '../../api/collaboration'
import {
  getShare,
  linkExpiryOptions,
  listActivities,
  listVersions,
  ownerName,
  restoreVersion,
  setLinkExpiry,
  setShareScope,
  shareScopeOptions,
  versionSourceColor,
  versionSourceLabel,
} from '../../mock/domain/collab'
import type {
  Activity,
  DocVersion,
  PermLog,
  ShareData,
  ShareScope,
  VersionSource,
} from '../../mock/domain/collab'

const PERM_LABEL: Record<CollabPermission, string> = { VIEW: '只读', COMMENT: '可批注', EDIT: '可编辑' }
const PERM_OPTIONS: { value: CollabPermission; label: string }[] = [
  { value: 'VIEW', label: '只读' },
  { value: 'COMMENT', label: '可批注' },
  { value: 'EDIT', label: '可编辑' },
]

const fmt = (v: string | null) => (v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '-')

/** 协作与版本：批注/在线/协作者已接真实接口；版本记录与共享范围仍为演示数据。 */
export default function Collab({ role }: { role: RoleCodeValue }) {
  const isTeacher = role === 'TEACHER'
  const owner = ownerName(role)
  const [docs, setDocs] = useState<DocumentSummary[]>([])
  const [docId, setDocId] = useState<string>()

  useEffect(() => {
    listDocuments({ status: 'READY', pageSize: 100 })
      .then((r) => {
        setDocs(r.items)
        setDocId((current) => current ?? r.items[0]?.id)
      })
      .catch((e) => message.error(getErrorMessage(e)))
  }, [])

  return (
    <DemoPage
      live
      mixedNote="版本记录与共享范围仍为演示数据"
      title="协作与版本"
      screens="对应原型：学生 16/17/18/19/20/21、教师 15/16/17/18/19"
      desc={
        isTeacher
          ? '与课程组成员共同维护教学文档，集中查看教师批注、编辑活动、版本链与成员权限。'
          : '与小组共同维护课程文档，批注与在线状态实时同步。'
      }
    >
      <Space wrap style={{ marginBottom: 12 }}>
        <Typography.Text type="secondary">协作文档</Typography.Text>
        <Select
          style={{ width: 360 }}
          value={docId}
          placeholder="选择已解析完成的文档"
          options={docs.map((d) => ({
            value: d.id,
            label: `${d.title}（v${d.versionNo ?? '?'}）${d.relation === 'COLLAB' ? ` · 协作 · ${d.ownerName ?? ''}` : ''}`,
          }))}
          onChange={setDocId}
          notFoundContent="暂无已解析文档"
        />
      </Space>
      <Tabs
        defaultActiveKey="collab"
        items={[
          { key: 'collab', label: '协作编辑', children: <CollabEditTab documentId={docId} /> },
          { key: 'versions', label: '版本记录', children: <VersionsTab owner={owner} /> },
          { key: 'share', label: '共享设置', children: <ShareTab owner={owner} documentId={docId} /> },
        ]}
      />
    </DemoPage>
  )
}

/* ---------- 协作编辑：批注 + 在线成员（真实接口，3s 轮询） ---------- */

function CollabEditTab({ documentId }: { documentId?: string }) {
  const [annotations, setAnnotations] = useState<AnnotationView[]>([])
  const [presence, setPresence] = useState<PresenceView[]>([])
  const [activities, setActivities] = useState<Activity[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<'ALL' | 'O' | 'R'>('ALL')

  useEffect(() => {
    listActivities().then(setActivities)
  }, [])

  useEffect(() => {
    if (!documentId) {
      setAnnotations([])
      setPresence([])
      setLoading(false)
      return
    }
    let alive = true
    const tick = async () => {
      try {
        await heartbeatPresence(documentId).catch(() => undefined)
        const [a, p] = await Promise.all([listAnnotations(documentId), listPresence(documentId)])
        if (!alive) return
        setAnnotations(a)
        setPresence(p)
        setError(null)
      } catch (e) {
        if (alive) setError(getErrorMessage(e))
      } finally {
        if (alive) setLoading(false)
      }
    }
    setLoading(true)
    void tick()
    const timer = setInterval(tick, 3000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [documentId])

  const open = annotations.filter((c) => c.status === 'O').length
  const shown = annotations.filter((c) => filter === 'ALL' || c.status === filter)

  const toggle = async (row: AnnotationView) => {
    try {
      await resolveAnnotation(row.id, row.status === 'O')
      message.success(row.status === 'O' ? '批注已标记为已解决' : '批注已重新打开')
      if (documentId) setAnnotations(await listAnnotations(documentId))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const remove = async (row: AnnotationView) => {
    try {
      await deleteAnnotation(row.id)
      message.success('批注已删除')
      if (documentId) setAnnotations(await listAnnotations(documentId))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  if (!documentId) return <Empty description="请选择一份已解析文档后再查看批注与在线成员" />

  return (
    <Spin spinning={loading}>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Alert
        type="info" showIcon style={{ marginBottom: 12 }}
        message="在线成员由 15 秒心跳维护，批注与在线列表每 3 秒刷新；正文与活动流仍为演示内容。"
      />
      <DemoCard
        title="在线成员"
        extra={<Badge status="processing" text={`${presence.length} 人在线 · 自动保存已开启`} />}
      >
        {presence.length === 0 ? (
          <Typography.Text type="secondary">15 秒内暂无活跃成员</Typography.Text>
        ) : (
          <Space size="large" wrap>
            {presence.map((m) => (
              <Space key={m.userId} size={6}>
                <Avatar style={{ background: m.role === 'TEACHER' ? '#6d28d9' : '#2563eb' }}>
                  {m.displayName?.slice(0, 1) ?? '?'}
                </Avatar>
                <span>{m.displayName}</span>
                {m.role && <Tag>{m.role === 'TEACHER' ? '教师' : m.role === 'STUDENT' ? '学生' : m.role}</Tag>}
              </Space>
            ))}
          </Space>
        )}
        <div
          style={{ marginTop: 12, border: '1px solid #f0f0f0', borderRadius: 8, padding: '16px 24px', background: '#fff' }}
        >
          <Space style={{ marginBottom: 6 }}>
            <Tag>正文示意</Tag>
            <Typography.Text type="secondary">真实正文与批注定位请打开文档预览页</Typography.Text>
          </Space>
          <Typography.Paragraph>
            系统采用前后端分离结构。前端负责工作台、文档预览与交互状态；服务层承担权限验证、任务调度和格式处理。
          </Typography.Paragraph>
        </div>
      </DemoCard>
      <DemoCard
        title="批注"
        extra={
          <Segmented
            value={filter}
            onChange={(v) => setFilter(v as typeof filter)}
            options={[
              { label: `全部 ${annotations.length}`, value: 'ALL' },
              { label: `待处理 ${open}`, value: 'O' },
              { label: '已解决', value: 'R' },
            ]}
          />
        }
      >
        <List
          dataSource={shown}
          locale={{ emptyText: '没有符合筛选条件的批注' }}
          renderItem={(c) => (
            <List.Item
              actions={[
                <AppButton key="locate" type="link" size="small"
                  onClick={() => message.info(`批注区间：字符 ${c.charStart}-${c.charEnd}`)}>
                  定位区间
                </AppButton>,
                c.canResolve && (
                  <AppButton key="toggle" type="link" size="small" onClick={() => toggle(c)}>
                    {c.status === 'O' ? '标记解决' : '重新打开'}
                  </AppButton>
                ),
                c.canDelete && (
                  <Popconfirm key="del" title="删除该批注？" okText="删除" cancelText="取消"
                    onConfirm={() => remove(c)}>
                    <AppButton type="link" size="small" danger>删除</AppButton>
                  </Popconfirm>
                ),
              ].filter(Boolean)}
            >
              <List.Item.Meta
                title={
                  <Space wrap size={4}>
                    <span>{c.authorName}</span>
                    <Tag>字符 {c.charStart}-{c.charEnd}</Tag>
                    <Tag color={c.status === 'O' ? 'orange' : 'green'}>
                      {c.status === 'O' ? '待处理' : '已解决'}
                    </Tag>
                    {c.resolvedByName && <Tag color="green">由 {c.resolvedByName} 解决</Tag>}
                    <Typography.Text type="secondary">{fmt(c.createTime)}</Typography.Text>
                  </Space>
                }
                description={c.content}
              />
            </List.Item>
          )}
        />
      </DemoCard>
      <DemoCard title="编辑活动流" extra={<Tag color="orange">演示数据</Tag>}>
        <Timeline
          items={activities.map((a) => ({
            children: (
              <Space size={6} wrap>
                <Typography.Text strong>{a.author}</Typography.Text>
                <span>{a.action}</span>
                <Typography.Text type="secondary">{a.time}</Typography.Text>
              </Space>
            ),
          }))}
        />
      </DemoCard>
    </Spin>
  )
}

/* ---------- 版本记录 / 对比 / 恢复（演示数据） ---------- */

function VersionsTab({ owner }: { owner: string }) {
  const [rows, setRows] = useState<DocVersion[]>([])
  const [loading, setLoading] = useState(true)
  const [source, setSource] = useState<VersionSource | 'ALL'>('ALL')
  const [kw, setKw] = useState('')
  const [diffRow, setDiffRow] = useState<DocVersion | null>(null)

  useEffect(() => {
    listVersions().then((r) => {
      setRows(r)
      setLoading(false)
    })
  }, [])

  const data = useMemo(
    () =>
      rows
        .filter((v) => source === 'ALL' || v.source === source)
        .filter((v) => !kw || v.note.includes(kw) || v.version.includes(kw) || v.author.includes(kw)),
    [rows, source, kw],
  )
  const nextNo = rows.length ? Math.max(...rows.map((v) => Number(v.version.slice(1)))) + 1 : 1

  const columns: ColumnsType<DocVersion> = [
    {
      title: '版本号',
      dataIndex: 'version',
      width: 110,
      render: (v: string, row) => (
        <Space size={4}>
          <Tag color={row.current ? 'green' : 'default'}>{v}</Tag>
          {row.current && <Tag color="green">当前</Tag>}
        </Space>
      ),
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 90,
      render: (v: VersionSource) => <Tag color={versionSourceColor[v]}>{versionSourceLabel[v]}</Tag>,
    },
    { title: '提交说明', dataIndex: 'note' },
    { title: '修改人', dataIndex: 'author', width: 100 },
    { title: '时间', dataIndex: 'time', width: 150 },
    { title: '大小', dataIndex: 'size', width: 90 },
    {
      title: '操作',
      width: 130,
      render: (_, row) => (
        <Space size={0}>
          <AppButton type="link" size="small" onClick={() => setDiffRow(row)}>
            对比
          </AppButton>
          <Popconfirm
            title={`恢复 ${row.version}？`}
            description={`版本不可变：恢复会把 ${row.version} 的内容复制为新版本 v${nextNo}，当前版本保留在历史记录中。`}
            okText="确认恢复"
            cancelText="取消"
            disabled={row.current}
            onConfirm={async () => {
              setRows(await restoreVersion(row.id, owner))
              message.success(`已恢复：由 ${row.version} 生成新版本 v${nextNo}，历史版本保持不变。`)
            }}
          >
            <AppButton type="link" size="small" disabled={row.current}>
              恢复
            </AppButton>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <DemoCard
        title="版本链"
        extra={
          <Space wrap>
            <Tag color="orange">演示数据</Tag>
            <Select
              style={{ width: 130 }}
              value={source}
              onChange={setSource}
              options={
                [
                  { value: 'ALL', label: '全部来源' },
                  ...(Object.keys(versionSourceLabel) as VersionSource[]).map((k) => ({
                    value: k,
                    label: versionSourceLabel[k],
                  })),
                ] as { value: VersionSource | 'ALL'; label: string }[]
              }
            />
            <Input.Search
              allowClear
              placeholder="搜索版本号 / 说明 / 修改人"
              style={{ width: 220 }}
              onChange={(e) => setKw(e.target.value)}
            />
          </Space>
        }
      >
        <AppTable<DocVersion> rowKey="id" size="middle" loading={loading} columns={columns} dataSource={data} />
        <Typography.Text type="secondary">
          版本记录不可变：任何保存、修复、协作、提交与恢复都追加新版本，不删除、不覆盖历史。
        </Typography.Text>
      </DemoCard>
      <AppModal
        open={!!diffRow}
        title={`版本对比 · ${diffRow?.version ?? ''}（与上一版本）`}
        width={760}
        onCancel={() => setDiffRow(null)}
        footer={<AppButton type="default" onClick={() => setDiffRow(null)}>关闭</AppButton>}
      >
        {diffRow?.diff ? (
          <>
            <Space wrap style={{ marginBottom: 12 }}>
              <Tag color="red">旧内容 {diffRow.diff.before.length} 处</Tag>
              <Tag color="green">新内容 {diffRow.diff.after.length} 处</Tag>
              <Tag color="orange">格式规则变化 {diffRow.diff.format.length} 项</Tag>
            </Space>
            <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
              <div style={{ flex: 1, minWidth: 0, background: '#fff1f0', borderRadius: 8, padding: 12 }}>
                <Typography.Text type="secondary">上一版本</Typography.Text>
                {diffRow.diff.before.map((line) => (
                  <div key={line} style={{ color: '#cf1322', marginTop: 6 }}>
                    − {line}
                  </div>
                ))}
              </div>
              <div style={{ flex: 1, minWidth: 0, background: '#f6ffed', borderRadius: 8, padding: 12 }}>
                <Typography.Text type="secondary">{diffRow.version}</Typography.Text>
                {diffRow.diff.after.map((line) => (
                  <div key={line} style={{ color: '#389e0d', marginTop: 6 }}>
                    + {line}
                  </div>
                ))}
              </div>
            </div>
            {diffRow.diff.format.length > 0 && (
              <div style={{ marginTop: 12 }}>
                <Typography.Text strong>格式规则变化</Typography.Text>
                <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
                  {diffRow.diff.format.map((f) => (
                    <li key={f}>{f}</li>
                  ))}
                </ul>
              </div>
            )}
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
              恢复 {diffRow.version} 会把历史内容复制为新版本，当前版本不会被删除。
            </Typography.Text>
          </>
        ) : (
          <Empty description={`${diffRow?.version ?? ''} 为首个版本，无更早基线可对比`} />
        )}
      </AppModal>
    </>
  )
}

/* ---------- 共享设置：访问范围/链接仍演示；协作者接真实接口 ---------- */

function ShareTab({ owner, documentId }: { owner: string; documentId?: string }) {
  const [data, setData] = useState<ShareData | null>(null)
  const [loading, setLoading] = useState(true)
  const [members, setMembers] = useState<CollaboratorView[]>([])
  const [memberLoading, setMemberLoading] = useState(false)
  const [modal, setModal] = useState(false)
  const [account, setAccount] = useState('')
  const [perm, setPerm] = useState<CollabPermission>('COMMENT')
  const [inviteNote, setInviteNote] = useState('请协助完成系统设计章节')

  const reload = () => getShare().then((d) => {
    setData(d)
    setLoading(false)
  })
  useEffect(() => {
    reload()
  }, [])

  const loadMembers = () => {
    if (!documentId) {
      setMembers([])
      return
    }
    setMemberLoading(true)
    listCollaborators(documentId)
      .then(setMembers)
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setMemberLoading(false))
  }
  useEffect(loadMembers, [documentId])

  const memberColumns: ColumnsType<CollaboratorView> = [
    {
      title: '成员',
      dataIndex: 'displayName',
      render: (v: string, row) => (
        <Space size={6}>
          <Avatar size="small" style={{ background: '#2563eb' }}>{v?.slice(0, 1)}</Avatar>
          <span>{v}</span>
          <Typography.Text type="secondary">{row.username}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '权限',
      dataIndex: 'permission',
      width: 100,
      render: (v: CollabPermission) => <Tag>{PERM_LABEL[v] ?? v}</Tag>,
    },
    { title: '加入时间', dataIndex: 'joinedAt', width: 170, render: fmt },
    {
      title: '操作',
      width: 80,
      render: (_, row) => (
        <Popconfirm
          title={`移除协作者 ${row.displayName}？`}
          description="移除后对方立即失去文档读取与批注权限。"
          okText="移除"
          cancelText="取消"
          onConfirm={async () => {
            try {
              await removeCollaborator(documentId!, row.userId)
              message.success(`已移除协作者 ${row.displayName}`)
              loadMembers()
            } catch (e) {
              message.error(getErrorMessage(e))
            }
          }}
        >
          <AppButton type="link" size="small" danger>移除</AppButton>
        </Popconfirm>
      ),
    },
  ]

  const logColumns: ColumnsType<PermLog> = [
    { title: '变更内容', dataIndex: 'content' },
    { title: '操作人', dataIndex: 'operator', width: 110 },
    { title: '时间', dataIndex: 'time', width: 150 },
  ]

  return (
    <Spin spinning={loading}>
      {data && (
        <>
          <DemoCard title="访问范围" extra={<Tag color="orange">演示数据</Tag>}>
            <Alert type="info" showIcon style={{ marginBottom: 12 }}
              message="访问范围与链接有效期仍为演示数据；真实共享链接请在文档列表「共享」中生成。" />
            <Radio.Group
              value={data.scope}
              onChange={async (e) => {
                const scope = e.target.value as ShareScope
                await setShareScope(scope, owner)
                await reload()
                message.success('共享范围已保存（演示）')
              }}
            >
              <Space direction="vertical">
                {shareScopeOptions.map((o) => (
                  <Radio key={o.value} value={o.value}>
                    <Space size={6}>
                      <Typography.Text strong>{o.label}</Typography.Text>
                      <Typography.Text type="secondary">{o.desc}</Typography.Text>
                    </Space>
                  </Radio>
                ))}
              </Space>
            </Radio.Group>
            {data.scope === 'LINK' && (
              <div style={{ marginTop: 12, background: '#f6f8fa', borderRadius: 8, padding: 12 }}>
                <Space wrap>
                  <Typography.Text copyable={{ text: data.link }}>{data.link}</Typography.Text>
                  <Typography.Text type="secondary">链接有效期</Typography.Text>
                  <Select
                    size="small"
                    style={{ width: 100 }}
                    value={data.linkExpiry}
                    options={linkExpiryOptions.map((v) => ({ value: v, label: v }))}
                    onChange={async (v) => {
                      await setLinkExpiry(v, owner)
                      await reload()
                      message.success(`链接有效期已调整为「${v}」（演示）`)
                    }}
                  />
                </Space>
              </div>
            )}
          </DemoCard>
          <DemoCard
            title={`协作者（${members.length} 人）`}
            extra={
              <AppButton
                size="small"
                disabled={!documentId}
                onClick={() => {
                  setAccount('')
                  setPerm('COMMENT')
                  setModal(true)
                }}
              >
                添加协作者
              </AppButton>
            }
          >
            <AppTable<CollaboratorView>
              rowKey="userId"
              size="middle"
              loading={memberLoading}
              pagination={false}
              columns={memberColumns}
              dataSource={members}
              locale={{ emptyText: documentId ? '尚无协作者' : '请先选择协作文档' }}
            />
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
              协作者拥有文档读取与批注权限；公开共享链接不支持批注。
            </Typography.Text>
          </DemoCard>
          <DemoCard title="权限变更记录" extra={<Tag color="orange">演示数据</Tag>}>
            <AppTable<PermLog>
              rowKey="id"
              size="small"
              pagination={{ pageSize: 5 }}
              columns={logColumns}
              dataSource={data.logs}
            />
          </DemoCard>
        </>
      )}
      <AppModal
        open={modal}
        title="添加协作者"
        okText="添加"
        cancelText="取消"
        onCancel={() => setModal(false)}
        onOk={async () => {
          if (!documentId || !account.trim()) {
            message.warning('请填写协作者账号')
            return
          }
          try {
            await addCollaborator(documentId, { username: account.trim(), permission: perm })
            setModal(false)
            loadMembers()
            message.success(`已添加协作者 ${account.trim()}（${PERM_LABEL[perm]}）`)
          } catch (e) {
            message.error(getErrorMessage(e))
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <Typography.Text type="secondary">协作者账号（登录名 / 学号）</Typography.Text>
            <Input style={{ marginTop: 4 }} value={account} placeholder="如 student 或 2023001"
              onChange={(e) => setAccount(e.target.value)} />
          </div>
          <div>
            <Typography.Text type="secondary">协作权限</Typography.Text>
            <Select style={{ width: '100%', marginTop: 4 }} value={perm} options={PERM_OPTIONS}
              onChange={setPerm} />
          </div>
          <div>
            <Typography.Text type="secondary">邀请说明</Typography.Text>
            <Input style={{ marginTop: 4 }} value={inviteNote} onChange={(e) => setInviteNote(e.target.value)} />
          </div>
          <Alert type="info" showIcon message="邀请说明仅用于演示；协作者添加后立即拥有读取与批注权限。" />
        </Space>
      </AppModal>
    </Spin>
  )
}