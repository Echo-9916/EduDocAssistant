// 文档共享弹窗：对接真实 /documents/{id}/shares，创建/复制/撤销链接。
import { useCallback, useEffect, useState } from 'react'
import { Alert, Input, Popconfirm, Radio, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal } from '../components'
import { getErrorMessage } from '../api/http'
import { createShare, listShares, revokeShare } from '../api/shares'
import type { SharePermission, ShareView } from '../api/shares'

const PERMISSION_LABEL: Record<SharePermission, string> = { VIEW: '只读', COMMENT: '可批注' }
const STATUS_META: Record<ShareView['status'], { label: string; color: string }> = {
  A: { label: '有效', color: 'green' },
  R: { label: '已撤销', color: 'default' },
  E: { label: '已过期', color: 'orange' },
}

interface Props {
  open: boolean
  documentId: string | null
  documentTitle?: string
  onClose: () => void
}

const abs = (path: string) => `${window.location.origin}${path}`

export default function ShareModal({ open, documentId, documentTitle, onClose }: Props) {
  const [rows, setRows] = useState<ShareView[]>([])
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [expiresInDays, setExpiresInDays] = useState(7)
  const [permission, setPermission] = useState<SharePermission>('VIEW')
  const [createdLink, setCreatedLink] = useState<string | null>(null)

  const reload = useCallback(() => {
    if (!documentId) return
    setLoading(true)
    listShares(documentId)
      .then(setRows)
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setLoading(false))
  }, [documentId])

  useEffect(() => {
    if (open) {
      setCreatedLink(null)
      reload()
    }
  }, [open, reload])

  const copy = (link: string) => {
    navigator.clipboard?.writeText(link).catch(() => undefined)
    message.success('链接已复制')
  }

  const doCreate = async () => {
    if (!documentId) return
    setCreating(true)
    try {
      const res = await createShare(documentId, { expiresInDays, permission })
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

  const columns: ColumnsType<ShareView> = [
    {
      title: '权限', dataIndex: 'permission', width: 90,
      render: (v: SharePermission) => <Tag>{PERMISSION_LABEL[v] ?? v}</Tag>,
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

  return (
    <AppModal open={open} onCancel={onClose} footer={null} width={760}
      title={`共享设置${documentTitle ? `：${documentTitle}` : ''}`}>
      <Space wrap size={12} style={{ marginBottom: 12 }}>
        <Select<number>
          value={expiresInDays}
          onChange={setExpiresInDays}
          style={{ width: 120 }}
          options={[{ value: 1, label: '1 天' }, { value: 7, label: '7 天' }, { value: 30, label: '30 天' }]}
        />
        <Radio.Group value={permission} onChange={(e) => setPermission(e.target.value as SharePermission)}>
          <Radio.Button value="VIEW">只读</Radio.Button>
          <Radio.Button value="COMMENT">可批注</Radio.Button>
        </Radio.Group>
        <AppButton loading={creating} onClick={doCreate}>生成共享链接</AppButton>
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
        链接免登录只读；过期或撤销后打开会提示「共享链接过期或撤销」。可批注链接仅记录权限级别，公开页仍为只读。
      </Typography.Text>
    </AppModal>
  )
}