// B-11：详情抽屉——元数据 + 当前版本 + chunkCount + 重新解析/删除入口；不做预览页（排除项）。
import { useEffect, useState } from 'react'
import { Descriptions, Drawer, Popconfirm, Space, Tag, message } from 'antd'
import { AppButton } from '../components'
import { getErrorMessage } from '../api/http'
import { deleteDocument, getDocument, reparseDocument } from '../api/documents'
import type { DocumentDetail } from '../api/documents'
import { DOC_TYPE_LABEL, STATUS_META, formatSize } from './Documents'

interface Props {
  docId: string | null
  onClose: () => void
  onChanged: () => void
}

export default function DocumentDetailDrawer({ docId, onClose, onChanged }: Props) {
  const [detail, setDetail] = useState<DocumentDetail | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!docId) {
      setDetail(null)
      return
    }
    setLoading(true)
    getDocument(docId)
      .then(setDetail)
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setLoading(false))
  }, [docId])

  const onReparse = async () => {
    if (!detail) return
    try {
      await reparseDocument(detail.id)
      message.success('已提交重新解析')
      onChanged()
      onClose()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const onDelete = async () => {
    if (!detail) return
    try {
      await deleteDocument(detail.id)
      message.success('已移入回收站')
      onChanged()
      onClose()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  return (
    <Drawer open={!!docId} title="文档详情" width={480} onClose={onClose} loading={loading}>
      {detail && (
        <>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="标题">{detail.title}</Descriptions.Item>
            <Descriptions.Item label="类型">{DOC_TYPE_LABEL[detail.docType] ?? detail.docType}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={(STATUS_META[detail.status] ?? { color: 'default' }).color}>
                {(STATUS_META[detail.status] ?? { label: detail.status }).label}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="课程">{detail.courseName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="大小">{formatSize(detail.sizeBytes)}</Descriptions.Item>
            <Descriptions.Item label="当前版本">
              v{detail.versionNo ?? '-'}
              {detail.contentHash ? `（${detail.contentHash.slice(0, 8)}…）` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="解析分块">{detail.chunkCount ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{detail.createTime?.replace('T', ' ')}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{detail.updateTime?.replace('T', ' ')}</Descriptions.Item>
            {detail.deletedAt && (
              <Descriptions.Item label="删除时间">{detail.deletedAt.replace('T', ' ')}</Descriptions.Item>
            )}
            {detail.purgeAt && (
              <Descriptions.Item label="清理时间">{detail.purgeAt.replace('T', ' ')}</Descriptions.Item>
            )}
          </Descriptions>
          <Space style={{ marginTop: 16 }}>
            {(detail.status === 'READY' || detail.status === 'FAILED') && (
              <AppButton type="default" onClick={onReparse}>
                重新解析
              </AppButton>
            )}
            {detail.status !== 'DELETED' && (
              <Popconfirm title="移入回收站？" onConfirm={onDelete}>
                <AppButton danger>删除</AppButton>
              </Popconfirm>
            )}
          </Space>
        </>
      )}
    </Drawer>
  )
}
