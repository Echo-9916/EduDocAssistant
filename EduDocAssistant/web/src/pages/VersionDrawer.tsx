// 文档版本历史 Drawer：版本号降序、当前版本标注、签名下载、历史版本恢复与行级文本对比。
// 文本对比复用 GET /documents/{id}/preview?versionId=...（历史版本分支已放开），不新增文本端点。
import { useCallback, useEffect, useState } from 'react'
import { Alert, Drawer, Modal, Popconfirm, Table, Tag, Typography, message } from 'antd'
import { diffLines } from 'diff'
import type { Change } from 'diff'
import { AppButton } from '../components'
import { getErrorMessage } from '../api/http'
import { getPreview } from '../api/ai'
import { listDocumentVersions, restoreDocumentVersion } from '../api/documents'
import type { DocumentVersionRow } from '../api/documents'

const TYPE_LABEL: Record<string, string> = {
  UPLOAD: '上传', EDIT: '编辑/修复', COLLABORATION: '协作', RESTORE: '恢复', SUBMISSION: '提交',
}

interface Props {
  open: boolean
  documentId: string | null
  documentTitle?: string
  onClose: () => void
  /** 恢复成功后通知外层刷新文档列表/当前版本号。 */
  onRestored?: () => void
}

interface CompareState {
  title: string
  parts: Change[]
}

export default function VersionDrawer({ open, documentId, documentTitle, onClose, onRestored }: Props) {
  const [rows, setRows] = useState<DocumentVersionRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [restoring, setRestoring] = useState<string | null>(null)
  const [comparing, setComparing] = useState<string | null>(null)
  const [compare, setCompare] = useState<CompareState | null>(null)

  const loadRows = useCallback(() => {
    if (!documentId) return
    setLoading(true)
    setError(null)
    listDocumentVersions(documentId)
      .then(setRows)
      .catch((e) => setError(getErrorMessage(e)))
      .finally(() => setLoading(false))
  }, [documentId])

  useEffect(() => {
    if (open) loadRows()
  }, [open, loadRows])

  const doRestore = async (row: DocumentVersionRow) => {
    if (!documentId) return
    setRestoring(row.versionId)
    try {
      const res = await restoreDocumentVersion(documentId, row.versionId)
      message.success(`已恢复为 v${res.versionNo}（新版本，历史版本未改动）`)
      loadRows()
      onRestored?.()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRestoring(null)
    }
  }

  /** 历史版本 vs 当前版本的行级对比（文本按分块拼接，块间已由后端保证密铺）。 */
  const doCompare = async (row: DocumentVersionRow) => {
    const current = rows.find((r) => r.current)
    if (!documentId || row.current || !current) return
    setComparing(row.versionId)
    try {
      const [oldPreview, newPreview] = await Promise.all([
        getPreview(documentId, row.versionId),
        getPreview(documentId, current.versionId),
      ])
      const oldText = oldPreview.blocks.map((b) => b.text).join('')
      const newText = newPreview.blocks.map((b) => b.text).join('')
      setCompare({
        title: `v${row.versionNo}（旧）→ v${current.versionNo}（当前）`,
        parts: diffLines(oldText, newText),
      })
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setComparing(null)
    }
  }

  const hasDiff = (compare?.parts ?? []).some((p) => p.added || p.removed)

  return (
    <Drawer open={open} onClose={onClose} width={720}
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
          {
            title: '操作', key: 'act', width: 190,
            render: (_: unknown, r) => (
              <>
                {!r.current && (
                  <>
                    <AppButton type="link" size="small" loading={comparing === r.versionId}
                      onClick={() => doCompare(r)}>
                      对比
                    </AppButton>
                    <Popconfirm title="恢复该版本为当前版本？" okText="确认恢复" cancelText="取消"
                      onConfirm={() => doRestore(r)}>
                      <AppButton type="link" size="small" loading={restoring === r.versionId}>
                        恢复
                      </AppButton>
                    </Popconfirm>
                  </>
                )}
                {r.downloadUrl ? (
                  <AppButton type="link" size="small" onClick={() => window.open(r.downloadUrl!, '_blank')}>
                    下载
                  </AppButton>
                ) : null}
              </>
            ),
          },
        ]}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        链接 15 分钟内有效；恢复会复制为新版本（来源标记「恢复」），历史版本保持只读。
      </Typography.Text>

      <Modal open={!!compare} onCancel={() => setCompare(null)} footer={null} width={860}
        title={`版本对比：${compare?.title ?? ''}`}>
        {!hasDiff ? (
          <Alert type="info" showIcon
            message="两个版本文本内容相同（如恢复或格式修复产生的版本）" />
        ) : (
          <>
            <div style={{ maxHeight: '62vh', overflow: 'auto', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12, lineHeight: 1.55 }}>
              {(compare?.parts ?? []).flatMap((p, i) => {
                const kind = p.added ? 'add' : p.removed ? 'del' : 'same'
                return p.value.replace(/\n$/, '').split('\n').map((line, j) => (
                  <div key={`${i}-${j}`} style={{
                    // 整行铺底色 + +/- 前缀：投影仪下不靠字色也能分辨
                    background: kind === 'add' ? '#e6ffec' : kind === 'del' ? '#ffebe9' : undefined,
                    padding: '0 6px', whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                  }}>
                    <span style={{
                      userSelect: 'none', marginRight: 6,
                      color: kind === 'add' ? '#1a7f37' : kind === 'del' ? '#cf222e' : '#8c959f',
                    }}>
                      {kind === 'add' ? '+' : kind === 'del' ? '-' : ' '}
                    </span>
                    {line || ' '}
                  </div>
                ))
              })}
            </div>
            <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
              <span style={{ background: '#e6ffec', color: '#1a7f37', padding: '0 4px' }}>+ 绿色整行为当前版本新增</span>
              {' '}
              <span style={{ background: '#ffebe9', color: '#cf222e', padding: '0 4px' }}>- 红色整行为旧版本删除</span>
            </Typography.Text>
          </>
        )}
      </Modal>
    </Drawer>
  )
}