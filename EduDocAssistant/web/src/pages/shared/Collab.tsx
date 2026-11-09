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
import {
  COLLAB_CANDIDATES,
  ONLINE_MEMBERS,
  addCollaborator,
  getShare,
  linkExpiryOptions,
  listActivities,
  listComments,
  listVersions,
  ownerName,
  removeCollaborator,
  restoreVersion,
  setCollabPerm,
  setCommentStatus,
  setLinkExpiry,
  setShareScope,
  shareScopeOptions,
  versionSourceColor,
  versionSourceLabel,
  collabPermOptions,
} from '../../mock/domain/collab'
import type {
  Activity,
  CollabPerm,
  Collaborator,
  DocComment,
  DocVersion,
  PermLog,
  ShareData,
  ShareScope,
  VersionSource,
} from '../../mock/domain/collab'

/** 协作与版本：协作编辑 / 版本记录 / 共享设置（学生 16/17/18/19/20/21、教师 15/16/17/18/19 屏）。 */
export default function Collab({ role }: { role: RoleCodeValue }) {
  const isTeacher = role === 'TEACHER'
  const owner = ownerName(role)
  return (
    <DemoPage
      title="协作与版本"
      screens="对应原型：学生 16/17/18/19/20/21、教师 15/16/17/18/19"
      desc={
        isTeacher
          ? '与课程组成员共同维护教学文档，集中查看教师批注、编辑活动、版本链与成员权限。'
          : '与小组共同维护《Web开发课程设计报告_张同学_v3.docx》，追溯版本链并管理访问范围。'
      }
    >
      <Tabs
        defaultActiveKey="collab"
        items={[
          { key: 'collab', label: '协作编辑', children: <CollabEditTab owner={owner} /> },
          { key: 'versions', label: '版本记录', children: <VersionsTab owner={owner} /> },
          { key: 'share', label: '共享设置', children: <ShareTab owner={owner} /> },
        ]}
      />
    </DemoPage>
  )
}

/* ---------- 协作编辑 ---------- */

function CollabEditTab({ owner }: { owner: string }) {
  const [comments, setComments] = useState<DocComment[]>([])
  const [activities, setActivities] = useState<Activity[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<'ALL' | 'OPEN' | 'RESOLVED'>('ALL')

  const reload = () => {
    Promise.all([listComments(), listActivities()]).then(([c, a]) => {
      setComments(c)
      setActivities(a)
      setLoading(false)
    })
  }
  useEffect(reload, [])

  const open = comments.filter((c) => c.status === 'OPEN').length
  const shown = comments.filter((c) => filter === 'ALL' || c.status === filter)

  const toggle = async (row: DocComment) => {
    const next = row.status === 'OPEN' ? 'RESOLVED' : 'OPEN'
    setComments(await setCommentStatus(row.id, next, owner))
    setActivities(await listActivities())
    message.success(next === 'RESOLVED' ? '批注已标记为已解决' : '批注已重新打开')
  }

  return (
    <Spin spinning={loading}>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="真实实时协同（在线光标、并发合并、自动保存）将随协作网关上线后接入；本页为门面演示，批注与活动流可交互。"
      />
      <DemoCard
        title="在线成员"
        extra={<Badge status="processing" text={`${ONLINE_MEMBERS.length} 人在线 · 自动保存已开启`} />}
      >
        <Space size="large" wrap>
          {ONLINE_MEMBERS.map((m) => (
            <Space key={m.name} size={6}>
              <Avatar style={{ background: m.color }}>{m.name.slice(0, 1)}</Avatar>
              <span>{m.name}</span>
            </Space>
          ))}
        </Space>
        <div
          style={{ marginTop: 12, border: '1px solid #f0f0f0', borderRadius: 8, padding: '16px 24px', background: '#fff' }}
        >
          <Typography.Title level={5} style={{ marginTop: 0 }}>
            3 系统设计
          </Typography.Title>
          <Typography.Paragraph>
            系统采用前后端分离结构。前端负责工作台、文档预览与交互状态；服务层承担权限验证、任务调度和格式处理。
          </Typography.Paragraph>
          <Typography.Paragraph>
            文档数据与处理结果通过对象存储分层保存，原始文件、解析结果和导出文件分别归档，避免版本覆盖。
            <Tag color="purple" style={{ marginLeft: 8 }}>
              陈同学正在编辑
            </Tag>
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
              { label: `全部 ${comments.length}`, value: 'ALL' },
              { label: `待处理 ${open}`, value: 'OPEN' },
              { label: '已解决', value: 'RESOLVED' },
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
                <AppButton key="locate" type="link" size="small" onClick={() => message.info(`已定位到「${c.anchor}」`)}>
                  定位原文
                </AppButton>,
                <AppButton key="toggle" type="link" size="small" onClick={() => toggle(c)}>
                  {c.status === 'OPEN' ? '标记解决' : '重新打开'}
                </AppButton>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space wrap size={4}>
                    <span>{c.author}</span>
                    <Tag color={c.authorRole === '教师' ? 'purple' : 'blue'}>{c.authorRole}</Tag>
                    <Tag>锚点：{c.anchor}</Tag>
                    <Tag color={c.status === 'OPEN' ? 'orange' : 'green'}>{c.status === 'OPEN' ? '待处理' : '已解决'}</Tag>
                    <Typography.Text type="secondary">{c.time}</Typography.Text>
                  </Space>
                }
                description={c.content}
              />
            </List.Item>
          )}
        />
      </DemoCard>
      <DemoCard title="编辑活动流">
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

/* ---------- 版本记录 / 对比 / 恢复 ---------- */

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

/* ---------- 共享设置 / 协作者 ---------- */

function ShareTab({ owner }: { owner: string }) {
  const [data, setData] = useState<ShareData | null>(null)
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState(false)
  const [pick, setPick] = useState(COLLAB_CANDIDATES[0]?.name ?? '')
  const [perm, setPerm] = useState<CollabPerm>('可编辑')
  const [inviteNote, setInviteNote] = useState('请协助完成系统设计章节')

  const reload = () => getShare().then((d) => {
    setData(d)
    setLoading(false)
  })
  useEffect(() => {
    reload()
  }, [])

  const candidates = useMemo(
    () => COLLAB_CANDIDATES.filter((c) => !data?.collaborators.some((x) => x.name === c.name)),
    [data],
  )

  const memberColumns: ColumnsType<Collaborator> = [
    {
      title: '成员',
      dataIndex: 'name',
      render: (v: string, row) => (
        <Space size={6}>
          <Avatar size="small" style={{ background: '#2563eb' }}>
            {v.slice(0, 1)}
          </Avatar>
          <span>{v}</span>
          <Typography.Text type="secondary">{row.account}</Typography.Text>
        </Space>
      ),
    },
    { title: '角色', dataIndex: 'memberRole', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    {
      title: '权限',
      dataIndex: 'perm',
      width: 130,
      render: (v: Collaborator['perm'], row) =>
        row.owner ? (
          <Tag color="gold">所有者</Tag>
        ) : (
          <Select
            size="small"
            style={{ width: 100 }}
            value={v as CollabPerm}
            options={collabPermOptions.map((p) => ({ value: p, label: p }))}
            onChange={async (p) => {
              await setCollabPerm(row.id, p, owner)
              await reload()
              message.success(`已将 ${row.name} 的权限调整为「${p}」`)
            }}
          />
        ),
    },
    { title: '加入时间', dataIndex: 'time', width: 150 },
    {
      title: '操作',
      width: 80,
      render: (_, row) =>
        row.owner ? (
          <Typography.Text type="secondary">—</Typography.Text>
        ) : (
          <Popconfirm
            title={`移除协作者 ${row.name}？`}
            description="移除后对方立即失去文档访问权限，变更写入权限记录。"
            okText="移除"
            cancelText="取消"
            onConfirm={async () => {
              await removeCollaborator(row.id, owner)
              await reload()
              message.success(`已移除协作者 ${row.name}`)
            }}
          >
            <AppButton type="link" size="small" danger>
              移除
            </AppButton>
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
          <DemoCard title="访问范围">
            <Radio.Group
              value={data.scope}
              onChange={async (e) => {
                const scope = e.target.value as ShareScope
                await setShareScope(scope, owner)
                await reload()
                message.success('共享范围已保存，变更已写入权限记录')
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
                      message.success(`链接有效期已调整为「${v}」，到期后自动失效`)
                    }}
                  />
                  <AppButton
                    size="small"
                    type="default"
                    onClick={() => {
                      navigator.clipboard?.writeText(data.link).catch(() => undefined)
                      message.success('链接已复制，仅校内账号登录后可访问')
                    }}
                  >
                    复制链接
                  </AppButton>
                </Space>
              </div>
            )}
          </DemoCard>
          <DemoCard
            title={`协作者（${data.collaborators.length} 人）`}
            extra={
              <AppButton
                size="small"
                disabled={candidates.length === 0}
                onClick={() => {
                  setPick(candidates[0]?.name ?? '')
                  setModal(true)
                }}
              >
                添加协作者
              </AppButton>
            }
          >
            <AppTable<Collaborator>
              rowKey="id"
              size="middle"
              pagination={false}
              columns={memberColumns}
              dataSource={data.collaborators}
            />
          </DemoCard>
          <DemoCard title="权限变更记录">
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
        okText="发送邀请"
        cancelText="取消"
        onCancel={() => setModal(false)}
        onOk={async () => {
          await addCollaborator(pick, perm, owner)
          setModal(false)
          await reload()
          message.success(`已邀请 ${pick} 加入协作（${perm}）`)
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <Typography.Text type="secondary">选择成员（仅课程组成员可被邀请）</Typography.Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={pick || undefined}
              placeholder="搜索姓名、学号或校园邮箱"
              showSearch
              options={candidates.map((c) => ({
                value: c.name,
                label: `${c.name}（${c.account}） · ${c.memberRole}`,
              }))}
              onChange={setPick}
            />
          </div>
          <div>
            <Typography.Text type="secondary">协作权限</Typography.Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={perm}
              options={collabPermOptions.map((p) => ({ value: p, label: p }))}
              onChange={setPerm}
            />
          </div>
          <div>
            <Typography.Text type="secondary">邀请说明</Typography.Text>
            <Input style={{ marginTop: 4 }} value={inviteNote} onChange={(e) => setInviteNote(e.target.value)} />
          </div>
          <Alert type="info" showIcon message="邀请会发送到消息中心；演示环境直接写入协作者列表与权限变更记录。" />
        </Space>
      </AppModal>
    </Spin>
  )
}
