// 06 切片 2：消息中心接真实后端（学生 26 / 教师 26 / 运维 25 屏共用一套壳）。
// 列表 GET /messages（type/unread 服务端筛选，不再本地过滤）、未读数 GET /messages/unread-count、
// 标记已读 PATCH /messages:read、全部已读 PATCH /messages:read-all。
// ALERT/APPROVAL 两类暂无生产者（切片 2 只接提交/批改/格式/临近截止四类），页头 mixedNote 如实标注。
import { useEffect, useState } from 'react'
import { Badge, Segmented, Select, Space, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { AppButton, AppTable } from '../../components'
import DemoPage from '../DemoPage'
import { useAuth } from '../../auth/AuthContext'
import { rolePath } from '../../routes'
import { PermissionCode } from '../../api/types'
import type { RoleCodeValue } from '../../api/types'
import { getErrorMessage } from '../../api/http'
import { listMessages, markRead, messageTypeLabel, readAll, unreadCount } from '../../api/messages'
import type { MessageType, MessageView } from '../../api/messages'

const ROLE_ORDER: RoleCodeValue[] = ['OPS', 'TEACHER', 'STUDENT']

/** resourceType → 一级导航 key（「前往处理」落点），未映射的类型不出按钮。
 * 切片 2 四类生产者实际只写两种：提交/批改/临近截止 → ASSIGNMENT、格式异常 → DOCUMENT
 * （见后端 StudentCourseworkServiceImpl / TeacherCourseworkServiceImpl / FormatCheckTxServiceImpl / DeadlineReminderScheduler）；
 * 其余为切片 4 审批/告警消息预留。 */
const NAV_BY_RESOURCE: Record<RoleCodeValue, Record<string, string>> = {
  STUDENT: { ASSIGNMENT: 'courses', SUBMISSION: 'courses', COURSE: 'courses', DOCUMENT: 'documents' },
  TEACHER: { ASSIGNMENT: 'courses', SUBMISSION: 'courses', COURSE: 'courses', DOCUMENT: 'documents' },
  OPS: { ASSIGNMENT: 'courses', SUBMISSION: 'courses', COURSE: 'courses', DOCUMENT: 'storage', ACCOUNT: 'users', PERMISSION: 'users' },
}

/** 后端 ISO 时间串（UTC 带 Z）→ 本地「YYYY-MM-DD HH:mm」。
 * 不能裸 slice：会把 UTC 墙钟当本地显示，早 8 小时（切片 1 审查教训）。 */
const dt = (v: string | null) => {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString('zh-CN', { hour12: false }).slice(0, 16)
}

/** 消息中心：三端共用一套壳，数据全部来自 /messages 端点（学生 26 / 教师 26 / 运维 25 屏）。 */
export default function Messages() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const role = ROLE_ORDER.find((r) => user?.roles.includes(r)) ?? 'STUDENT'
  const isOps = role === 'OPS'

  const [rows, setRows] = useState<MessageView[]>([])
  const [total, setTotal] = useState(0)
  const [unread, setUnread] = useState(0)
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('all')
  const [type, setType] = useState<MessageType | 'ALL'>('ALL')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [reloadKey, setReloadKey] = useState(0)
  const reload = () => setReloadKey((k) => k + 1)

  // 筛选值直接传后端（已读/未读用 unread 布尔口径），切换即重新请求
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    listMessages({
      type: type === 'ALL' ? undefined : type,
      unread: filter === 'all' ? undefined : filter === 'unread',
      page,
      pageSize,
    })
      .then((r) => { if (!cancelled) { setRows(r.items); setTotal(r.total) } })
      .catch((e) => !cancelled && message.error(getErrorMessage(e)))
      .finally(() => !cancelled && setLoading(false))
    return () => { cancelled = true }
  }, [filter, type, page, pageSize, reloadKey])

  // 未读数单独端点（不靠拉全量算），已读操作后随 reloadKey 一起刷新
  useEffect(() => {
    let cancelled = false
    unreadCount()
      .then((n) => !cancelled && setUnread(n))
      .catch(() => { /* 角标失败不打扰列表 */ })
    return () => { cancelled = true }
  }, [reloadKey])

  const toggle = async (row: MessageView, read: boolean) => {
    try {
      const r = await markRead([row.id], read)
      message.success(read ? `已标记 ${r.updated} 条为已读` : `已标记 ${r.updated} 条为未读`)
      reload()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<MessageView> = [
    {
      title: '标题',
      dataIndex: 'title',
      render: (v: string, row) => (
        <Space>
          {!row.read && <Badge status="processing" />}
          <span style={row.read ? { color: '#5B6B80' } : undefined}>{v}</span>
        </Space>
      ),
    },
    { title: '内容', dataIndex: 'body', ellipsis: true, render: (v: string | null) => v ?? '-' },
    {
      title: '类型',
      dataIndex: 'type',
      width: 120,
      render: (v: MessageType) => <Tag color={isOps && v === 'ALERT' ? 'red' : 'blue'}>{messageTypeLabel[v] ?? v}</Tag>,
    },
    { title: '来源', dataIndex: 'actorName', width: 130, render: (v: string | null) => v ?? '系统' },
    { title: '时间', dataIndex: 'createdAt', width: 150, render: (v: string) => dt(v) },
    {
      title: '操作',
      width: 190,
      render: (_, row) => {
        const nav = row.resourceType ? NAV_BY_RESOURCE[role][row.resourceType] : undefined
        return (
          <Space>
            <AppButton type="link" size="small" onClick={() => toggle(row, !row.read)}>
              {row.read ? '标记未读' : '标记已读'}
            </AppButton>
            {nav && (
              <AppButton type="link" size="small" onClick={() => navigate(`${rolePath[role]}/${nav}`)}>
                前往处理
              </AppButton>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <DemoPage
      title={isOps ? '消息与告警' : '消息中心'}
      screens="对应原型：学生 26 / 教师 26 / 运维 25"
      desc={isOps ? '集中处理系统告警、权限变更、课程治理与审批通知。' : '集中查看课程更新、提交与反馈、格式异常和系统通知。'}
      live
      mixedNote="告警与审批类消息随切片 4 产生"
      extra={
        <Space wrap>
          <Segmented
            value={filter}
            onChange={(v) => { setFilter(v as string); setPage(1) }}
            options={[
              { label: `全部 ${total}`, value: 'all' },
              { label: `未读 ${unread}`, value: 'unread' },
              { label: '已读', value: 'read' },
            ]}
          />
          <Select
            style={{ width: 160 }}
            value={type}
            onChange={(v) => { setType(v); setPage(1) }}
            options={
              [
                { value: 'ALL', label: '全部类型' },
                ...Object.entries(messageTypeLabel).map(([value, label]) => ({ value, label })),
              ] as { value: MessageType | 'ALL'; label: string }[]
            }
          />
          <AppButton
            type="default"
            disabled={unread === 0}
            onClick={async () => {
              try {
                const r = await readAll()
                message.success(`已全部标记为已读（${r.updated} 条）`)
                reload()
              } catch (e) {
                message.error(getErrorMessage(e))
              }
            }}
          >
            全部已读
          </AppButton>
          {user?.permissions.includes(PermissionCode.AUDIT_READ) && (
            <AppButton type="default" onClick={() => navigate(`${rolePath[role]}/audit`)}>
              查看审计日志
            </AppButton>
          )}
        </Space>
      }
    >
      <AppTable<MessageView>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page,
          pageSize,
          total,
          onChange: (p, ps) => { setPage(p); setPageSize(ps) },
        }}
      />
    </DemoPage>
  )
}
