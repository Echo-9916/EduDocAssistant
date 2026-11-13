import { useEffect, useState } from 'react'
import { Empty, Spin, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { AppButton } from '../../components'
import DemoPage from '../DemoPage'
import { getErrorMessage } from '../../api/http'
import { listDocuments } from '../../api/documents'
import type { DocumentSummary } from '../../api/documents'
import { rolePath } from '../../routes'
import type { RoleCodeValue } from '../../api/types'

/** 后端 ISO 串 → 本地「YYYY-MM-DD HH:mm」（与文档页同口径）。 */
const fmt = (v: string) => {
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? v.slice(0, 16).replace('T', ' ') : d.toLocaleString('zh-CN', { hour12: false }).slice(0, 16)
}

/**
 * 智能处理入口：列出当前用户已解析完成的文档，点击进入文档预览使用真实摘要/问答/润色。
 * 不再提供演示数据——结果统一在 /documents/:id/view 内产生。
 */
export default function Smart({ role }: { role: RoleCodeValue }) {
  const navigate = useNavigate()
  const [items, setItems] = useState<DocumentSummary[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    listDocuments({ status: 'READY', pageSize: 100 })
      .then((r) => alive && setItems(r.items))
      .catch((e) => alive && message.error(getErrorMessage(e)))
      .finally(() => alive && setLoading(false))
    return () => {
      alive = false
    }
  }, [])

  const open = (doc: DocumentSummary) => navigate(`${rolePath[role]}/documents/${doc.id}/view`)

  const columns: ColumnsType<DocumentSummary> = [
    { title: '文档', dataIndex: 'title', ellipsis: true },
    { title: '课程', dataIndex: 'courseName', width: 150, render: (v: string | null) => v ?? '-' },
    { title: '版本', dataIndex: 'versionNo', width: 80, render: (v: number | null) => (v ? `v${v}` : '-') },
    { title: '更新时间', dataIndex: 'updateTime', width: 170, render: fmt },
    {
      title: '操作',
      width: 100,
      render: (_, doc) => (
        <AppButton type="link" size="small" onClick={() => open(doc)}>
          打开
        </AppButton>
      ),
    },
  ]

  return (
    <DemoPage
      live
      title="智能处理"
      screens="对应原型：学生 06/07/08/09/10、教师 05/06/07/08"
      desc="选择一份已解析完成的文档，进入预览后使用摘要、要点提取、文档问答与文本润色；结果均可定位原文。"
    >
      {loading ? (
        <Spin style={{ display: 'block', padding: 32, textAlign: 'center' }} />
      ) : items.length === 0 ? (
        <Empty description="暂无已解析完成的文档，请先在「我的文档」上传并等待解析完成" />
      ) : (
        <>
          <Table
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={items}
            pagination={false}
            onRow={(doc) => ({ onClick: () => open(doc), style: { cursor: 'pointer' } })}
          />
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
            共 {items.length} 份就绪文档；点击任意一行进入预览。
          </Typography.Text>
        </>
      )}
    </DemoPage>
  )
}