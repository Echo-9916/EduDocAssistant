// 验收决议④：文档版本历史 Drawer——版本号降序、当前版本标注、每版本源文件短期签名下载。
// 回滚到历史版本不在本期（协作与版本切片），此处只读+下载。
import { useEffect, useState } from 'react'
import { Alert, Drawer, Table, Tag, Typography } from 'antd'
import { AppButton } from '../components'
import { getErrorMessage } from '../api/http'
import { listDocumentVersions } from '../api/documents'
import type { DocumentVersionRow } from '../api/documents'

const TYPE_LABEL: Record<string, string> = {
  UPLOAD: '上传', EDIT: '编辑/修复', COLLABORATION: '协作', RESTORE: '恢复', SUBMISSION: '提交',
}

interface Props {
  open: boolean
  documentId: string | null
  documentTitle?: string
  onClose: () => void
}

export default function VersionDrawer({ open, documentId, documentTitle, onClose }: Props) {
  const [rows, setRows] = useState<DocumentVersionRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open || !documentId) return
    setLoading(true)
    setError(null)
    listDocumentVersions(documentId)
      .then(setRows)
      .catch((e) => setError(getErrorMessage(e)))
      .finally(() => setLoading(false))
  }, [open, documentId])

  return (
    <Drawer open={open} onClose={onClose} width={640}
      title={`版本历史${documentTitle ? `：${documentTitle}` : ''}`}>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Table<DocumentVersionRow>
        size="small" rowKey="versionId" loading={loading} dataSource={rows} pagination={false}
        columns={[
          { title: '版本', dataIndex: 'versionNo', width: 90,
            render: (v: number, r) => <span>v{v} {r.current && <Tag color="blue">当前</Tag>}</span> },
          { title: '来源', dataIndex: 'versionType', width: 100,
            render: (v: string) => TYPE_LABEL[v] ?? v },
          { title: '说明', dataIndex: 'changeSummary', render: (v: string | null) => v ?? '-' },
          { title: '时间', dataIndex: 'createTime', width: 160 },
          { title: '操作', key: 'act', width: 70,
            render: (_: unknown, r) => r.downloadUrl
              ? <AppButton type="link" onClick={() => window.open(r.downloadUrl!, '_blank')}>下载</AppButton>
              : <Typography.Text type="secondary">-</Typography.Text> },
        ]}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        链接 15 分钟内有效；回滚到历史版本随「协作与版本」切片提供。
      </Typography.Text>
    </Drawer>
  )
}
