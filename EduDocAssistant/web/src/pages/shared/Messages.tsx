import { useEffect, useMemo, useState } from 'react'
import { Badge, Segmented, Select, Space, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { AppButton, AppTable } from '../../components'
import DemoPage from '../DemoPage'
import { useAuth } from '../../auth/AuthContext'
import { rolePath } from '../../routes'
import { PermissionCode } from '../../api/types'
import type { RoleCodeValue } from '../../api/types'
import { listMessages, markRead, messageTypeLabel } from '../../mock/domain/messages'
import type { Message, MessageType } from '../../mock/domain/messages'

const ROLE_ORDER: RoleCodeValue[] = ['OPS', 'TEACHER', 'STUDENT']

/** 消息中心：三端共用一套壳，按角色取不同消息与筛选项（学生 26 / 教师 26 / 运维 25 屏）。 */
export default function Messages() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const role = ROLE_ORDER.find((r) => user?.roles.includes(r)) ?? 'STUDENT'
  const isOps = role === 'OPS'

  const [rows, setRows] = useState<Message[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('all')
  const [type, setType] = useState<MessageType | 'ALL'>('ALL')

  const reload = () => {
    setLoading(true)
    listMessages(role).then((r) => {
      setRows(r)
      setLoading(false)
    })
  }
  useEffect(reload, [role])

  const data = useMemo(
    () =>
      rows
        .filter((m) => (filter === 'unread' ? !m.read : filter === 'read' ? m.read : true))
        .filter((m) => type === 'ALL' || m.type === type),
    [rows, filter, type],
  )
  const unread = rows.filter((m) => !m.read).length

  const toggle = async (row: Message, read: boolean) => {
    await markRead([row.id], read)
    setRows((prev) => prev.map((m) => (m.id === row.id ? { ...m, read } : m)))
  }

  const columns: ColumnsType<Message> = [
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
    { title: '内容', dataIndex: 'content', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      width: 120,
      render: (v: MessageType) => <Tag color={isOps && v === 'ALERT' ? 'red' : 'blue'}>{messageTypeLabel[v]}</Tag>,
    },
    { title: '来源', dataIndex: 'from', width: 130 },
    { title: '时间', dataIndex: 'createTime', width: 150 },
    {
      title: '操作',
      width: 190,
      render: (_, row) => (
        <Space>
          <AppButton type="link" size="small" onClick={() => toggle(row, !row.read)}>
            {row.read ? '标记未读' : '标记已读'}
          </AppButton>
          {row.link && (
            <AppButton type="link" size="small" onClick={() => navigate(`${rolePath[role]}/${row.link}`)}>
              前往处理
            </AppButton>
          )}
        </Space>
      ),
    },
  ]

  return (
    <DemoPage
      title={isOps ? '消息与告警' : '消息中心'}
      screens="对应原型：学生 26 / 教师 26 / 运维 25"
      desc={isOps ? '集中处理系统告警、权限变更、课程治理与审批通知。' : '集中查看课程更新、提交与反馈、格式异常和系统通知。'}
      extra={
        <Space wrap>
          <Segmented
            value={filter}
            onChange={(v) => setFilter(v as string)}
            options={[
              { label: `全部 ${rows.length}`, value: 'all' },
              { label: `未读 ${unread}`, value: 'unread' },
              { label: '已读', value: 'read' },
            ]}
          />
          <Select
            style={{ width: 160 }}
            value={type}
            onChange={setType}
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
              await markRead(rows.filter((m) => !m.read).map((m) => m.id))
              setRows((prev) => prev.map((m) => ({ ...m, read: true })))
              message.success('已全部标记为已读')
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
      <AppTable<Message> rowKey="id" size="middle" loading={loading} columns={columns} dataSource={data} />
    </DemoPage>
  )
}
