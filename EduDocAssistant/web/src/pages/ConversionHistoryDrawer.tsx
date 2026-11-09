// D-13（1107 P2-7）：转换记录抽屉——文档选择 + 关键字检索 + 完整源/目标/时间列 + 签名直链下载。
// 下载通道裁定（审查建议先行统一）：presigned GET 直链为唯一口径（红线「短期 STS/签名 URL」），
// fetch blob 口径废弃；后端 listByDocument 已随 SUCCEEDED 行返回签名 URL。
import { useEffect, useMemo, useState } from 'react'
import { Alert, Drawer, Input, Select, Space, Table, Tag, Typography } from 'antd'
import { AppButton } from '../components'
import { getErrorMessage } from '../api/http'
import { listDocuments } from '../api/documents'
import type { DocumentSummary } from '../api/documents'
import { listConversions } from '../api/format'
import type { ConversionView } from '../api/format'

const STATUS_META: Record<string, { label: string; color: string }> = {
  QUEUED: { label: '排队', color: 'default' },
  RUNNING: { label: '转换中', color: 'processing' },
  SUCCEEDED: { label: '完成', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  CANCELED: { label: '已取消', color: 'warning' },
}

interface Props {
  open: boolean
  onClose: () => void
  /** 打开时预选的文档（行级入口传入；不选则抽屉内自选） */
  initialDocumentId?: string | null
}

export default function ConversionHistoryDrawer({ open, onClose, initialDocumentId }: Props) {
  const [docs, setDocs] = useState<DocumentSummary[]>([])
  const [docId, setDocId] = useState<string | undefined>()
  const [keyword, setKeyword] = useState('')
  const [tasks, setTasks] = useState<ConversionView[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    listDocuments({ page: 1, pageSize: 100 })
      .then((r) => setDocs(r.items))
      .catch((e) => setError(getErrorMessage(e)))
    setDocId(initialDocumentId ?? undefined)
  }, [open, initialDocumentId])

  useEffect(() => {
    if (!docId) {
      setTasks([])
      return
    }
    setLoading(true)
    listConversions(docId)
      .then((rs) => { setTasks(rs); setError(null) })
      .catch((e) => { setTasks([]); setError(getErrorMessage(e)) })
      .finally(() => setLoading(false))
  }, [docId])

  const titleOf = (documentId: string) => docs.find((d) => d.id === documentId)?.title ?? documentId

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase()
    if (!kw) return tasks
    // 检索覆盖：文档名/目标格式/状态/失败码与原因（按原因查记录是排障主路径）
    return tasks.filter((t) =>
      [titleOf(t.documentId), t.targetFormat, STATUS_META[t.status]?.label ?? t.status,
        t.failureCode ?? '', t.failureMessage ?? '']
        .join(' ')
        .toLowerCase()
        .includes(kw))
  }, [tasks, keyword, docs])

  return (
    <Drawer title="转换记录" width={720} open={open} onClose={onClose}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Select
          placeholder="选择文档"
          style={{ width: '100%' }}
          showSearch
          optionFilterProp="label"
          value={docId}
          onChange={setDocId}
          options={docs.map((d) => ({ value: d.id, label: d.title }))}
        />
        <Input.Search allowClear placeholder="按名称/格式/状态检索" onSearch={setKeyword} />
        {error && <Alert type="error" showIcon message={error} />}
        {!docId && <Typography.Text type="secondary">选择一个文档查看其转换记录</Typography.Text>}
        <Table<ConversionView>
          size="small"
          rowKey="id"
          loading={loading}
          dataSource={filtered}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: '源文档', key: 'src', ellipsis: true, render: (_, t) => titleOf(t.documentId) },
            { title: '目标', dataIndex: 'targetFormat', width: 70 },
            { title: '状态', dataIndex: 'status', width: 90,
              render: (v: string) => <Tag color={STATUS_META[v]?.color}>{STATUS_META[v]?.label ?? v}</Tag> },
            { title: '开始', dataIndex: 'startedAt', width: 150, render: (v: string | null) => v?.replace('T', ' ') ?? '-' },
            { title: '结束', dataIndex: 'finishedAt', width: 150, render: (v: string | null) => v?.replace('T', ' ') ?? '-' },
            { title: '操作', key: 'act', width: 120,
              render: (_, t) => t.status === 'SUCCEEDED' && t.downloadUrl
                ? <AppButton type="link" size="small"
                    onClick={() => window.open(t.downloadUrl!, '_blank')}>下载</AppButton>
                : t.status === 'FAILED'
                  ? <Typography.Text type="danger" style={{ fontSize: 12 }}>
                      {t.failureCode ?? ''} {(t.failureMessage ?? '').slice(0, 20)}
                    </Typography.Text>
                  : '-' },
          ]}
        />
      </Space>
    </Drawer>
  )
}
