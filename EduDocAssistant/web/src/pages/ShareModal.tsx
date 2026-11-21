// 文档共享弹窗：对接真实 /documents/{id}/shares，创建/复制/撤销链接。
import { useCallback, useEffect, useState } from 'react'
import { Alert, Divider, Input, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal } from '../components'
import { getErrorMessage } from '../api/http'
import { createShare, listShares, revokeShare } from '../api/shares'
import type { SharePermission, ShareView } from '../api/shares'
import {
  addCollaborator,
  listCollaboratorCandidates,
  listCollaborators,
  removeCollaborator,
} from '../api/collaboration'
import type { CollaboratorCandidate, CollaboratorView } from '../api/collaboration'

const SHARE_PERMISSION_LABEL: Record<SharePermission, string> = {
  VIEW: '只读',
  COMMENT: '只读（历史）',
}
const COLLAB_PERMISSION_LABEL: Record<string, string> = {
  VIEW: '只读',
  COMMENT: '可批注',
  EDIT: '可编辑',
}
const STATUS_META: Record<ShareView['status'], { label: string; color: string }> = {
  A: { label: '有效', color: 'green' },
  R: { label: '已撤销', color: 'default' },
  E: { label: '已过期', color: 'orange' },
}

interface Props {
  open: boolean
  documentId: string | null
  documentTitle?: string
  courseId?: string | null
  onClose: () => void
}

const abs = (path: string) => `${window.location.origin}${path}`

export default function ShareModal({ open, documentId, documentTitle, courseId, onClose }: Props) {
  const [rows, setRows] = useState<ShareView[]>([])
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [expiresInDays, setExpiresInDays] = useState(7)
  const [createdLink, setCreatedLink] = useState<string | null>(null)
  const [members, setMembers] = useState<CollaboratorView[]>([])
  const [memberLoading, setMemberLoading] = useState(false)
  const [candidates, setCandidates] = useState<CollaboratorCandidate[]>([])
  const [candidateLoading, setCandidateLoading] = useState(false)
  const [selectedCandidates, setSelectedCandidates] = useState<string[]>([])
  const [inviting, setInviting] = useState(false)

  const reload = useCallback(() => {
    if (!documentId) return
    setLoading(true)
    listShares(documentId)
      .then(setRows)
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setLoading(false))
  }, [documentId])

  const reloadMembers = useCallback(() => {
    if (!documentId) return
    setMemberLoading(true)
    listCollaborators(documentId)
      .then(setMembers)
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setMemberLoading(false))
  }, [documentId])

  const reloadCandidates = useCallback(() => {
    if (!documentId || !courseId) {
      setCandidates([])
      return
    }
    setCandidateLoading(true)
    listCollaboratorCandidates(documentId)
      .then(setCandidates)
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setCandidateLoading(false))
  }, [documentId, courseId])

  useEffect(() => {
    if (open) {
      setCreatedLink(null)
      setSelectedCandidates([])
      reload()
      reloadMembers()
      reloadCandidates()
    }
  }, [open, reload, reloadMembers, reloadCandidates])

  const copy = (link: string) => {
    navigator.clipboard?.writeText(link).catch(() => undefined)
    message.success('链接已复制')
  }

  const doCreate = async () => {
    if (!documentId) return
    setCreating(true)
    try {
      const res = await createShare(documentId, { expiresInDays, permission: 'VIEW' })
      const link = abs(res.path)
      setCreatedLink(link)
      copy(link)
      reload()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setCreating(false)
    }
  }

  const invite = async () => {
    if (!documentId || selectedCandidates.length === 0) return
    const chosen = candidates.filter((c) => selectedCandidates.includes(c.userId))
    setInviting(true)
    const results = await Promise.allSettled(chosen.map((c) =>
      addCollaborator(documentId, { username: c.username, permission: 'COMMENT' })))
    const ok = results.filter((r) => r.status === 'fulfilled').length
    const failed = results.length - ok
    setInviting(false)
    setSelectedCandidates([])
    reloadMembers()
    reloadCandidates()
    if (ok > 0) message.success(`已邀请 ${ok} 位协作者（可批注）`)
    if (failed > 0) message.warning(`${failed} 位协作者邀请失败，请重试`)
  }

  const columns: ColumnsType<ShareView> = [
    {
      title: '权限', dataIndex: 'permission', width: 90,
      render: (v: SharePermission) => <Tag>{SHARE_PERMISSION_LABEL[v] ?? v}</Tag>,
    },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (v: ShareView['status']) => <Tag color={STATUS_META[v]?.color}>{STATUS_META[v]?.label ?? v}</Tag>,
    },
    {
      title: '有效期至', dataIndex: 'expiresAt', width: 170,
      render: (v: string | null) => (v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '长期'),
    },
    {
      title: '操作', width: 150,
      render: (_, row) => (
        <Space size={4}>
          <AppButton type="link" size="small" onClick={() => copy(abs(row.path))}>复制链接</AppButton>
          {row.status === 'A' && (
            <Popconfirm title="撤销后链接立即失效？" okText="撤销" cancelText="取消"
              onConfirm={async () => {
                try {
                  await revokeShare(row.id)
                  message.success('链接已撤销')
                  reload()
                } catch (e) {
                  message.error(getErrorMessage(e))
                }
              }}>
              <AppButton type="link" size="small" danger>撤销</AppButton>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  const memberColumns: ColumnsType<CollaboratorView> = [
    {
      title: '成员', dataIndex: 'displayName',
      render: (v: string, row) => (
        <Space size={6}>
          <span>{v}</span>
          <Typography.Text type="secondary">{row.username}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '权限', dataIndex: 'permission', width: 90,
      render: (v: string) => <Tag>{COLLAB_PERMISSION_LABEL[v] ?? v}</Tag>,
    },
    {
      title: '操作', width: 70,
      render: (_, row) => (
        <Popconfirm title={`移除协作者 ${row.displayName}？`} okText="移除" cancelText="取消"
          onConfirm={async () => {
            try {
              await removeCollaborator(documentId!, row.userId)
              message.success(`已移除协作者 ${row.displayName}`)
              reloadMembers()
              reloadCandidates()
            } catch (e) {
              message.error(getErrorMessage(e))
            }
          }}>
          <AppButton type="link" size="small" danger>移除</AppButton>
        </Popconfirm>
      ),
    },
  ]

  return (
    <AppModal open={open} onCancel={onClose} footer={null} width={760}
      title={`共享设置${documentTitle ? `：${documentTitle}` : ''}`}>
      <Typography.Title level={5} style={{ marginTop: 0 }}>共享链接（只读）</Typography.Title>
      <Space wrap size={12} style={{ marginBottom: 8 }}>
        <Select<number>
          value={expiresInDays}
          onChange={setExpiresInDays}
          style={{ width: 120 }}
          options={[{ value: 1, label: '1 天' }, { value: 7, label: '7 天' }, { value: 30, label: '30 天' }]}
        />
        <AppButton loading={creating} onClick={doCreate}>生成只读链接</AppButton>
      </Space>

      {createdLink && (
        <Alert
          type="success" showIcon style={{ marginBottom: 12 }}
          message="链接已生成"
          description={
            <Input readOnly value={createdLink} addonAfter={
              <a onClick={() => copy(createdLink)}>复制</a>
            } />
          }
        />
      )}

      <Table<ShareView>
        rowKey="id" size="small" loading={loading} dataSource={rows} pagination={false}
        columns={columns}
        locale={{ emptyText: '尚未生成共享链接' }}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 10 }}>
        链接免登录只读；过期或撤销后打开会提示「共享链接过期或撤销」。
      </Typography.Text>

      <Divider />
      <Typography.Title level={5} style={{ marginTop: 0 }}>邀请协作者（可批注）</Typography.Title>
      {!courseId ? (
        <Alert type="info" showIcon style={{ marginBottom: 12 }}
          message="该文档未关联课程，暂不能按课程教师/学生邀请协作者。" />
      ) : (
        <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
          <Select
            mode="multiple"
            style={{ flex: 1 }}
            placeholder="搜索课程教师/学生姓名或账号"
            showSearch
            optionFilterProp="label"
            loading={candidateLoading}
            value={selectedCandidates}
            onChange={setSelectedCandidates}
            options={candidates.map((c) => ({
              value: c.userId,
              label: `${c.displayName}（${c.role === 'TEACHER' ? '教师' : '学生'} · ${c.username}${c.groupName ? ` · ${c.groupName}` : ''}）`,
            }))}
            notFoundContent={candidateLoading ? '加载中…' : '暂无可邀请成员'}
          />
          <AppButton type="primary" loading={inviting}
            disabled={selectedCandidates.length === 0}
            onClick={() => void invite()}>
            邀请
          </AppButton>
        </Space.Compact>
      )}
      <Table<CollaboratorView>
        rowKey="userId" size="small" loading={memberLoading} dataSource={members} pagination={false}
        columns={memberColumns}
        locale={{ emptyText: courseId ? '尚无协作者' : '未关联课程' }}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
        被邀请成员立即成为「可批注」协作者，文档会出现在其“我的文档”和“协作文档”中；可在此移除。
      </Typography.Text>
    </AppModal>
  )
}