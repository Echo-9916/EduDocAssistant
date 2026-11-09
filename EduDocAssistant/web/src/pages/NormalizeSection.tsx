// D-14：代码与图表规范化（只读，J3 口径）。
// 数据源=结构树元素端点（docx 解析）；编辑态粘贴识别/增删自动重排归协作切片（05 期），
// 本页提供编号清单、题注查看与跳转预览，页脚注明能力边界。
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Select, Space, Table, Typography } from 'antd'
import { AppButton } from '../components'
import { getErrorMessage } from '../api/http'
import { listDocuments } from '../api/documents'
import type { DocumentSummary } from '../api/documents'
import { listElements } from '../api/format'
import type { ElementView } from '../api/format'

const TYPE_LABEL: Record<string, string> = {
  HEADING: '标题', FIGURE: '图', TABLE: '表', CODE: '代码块', EQUATION: '公式',
}

export default function NormalizeSection() {
  const navigate = useNavigate()
  const [docs, setDocs] = useState<DocumentSummary[]>([])
  const [docId, setDocId] = useState<string | undefined>()
  const [elements, setElements] = useState<ElementView[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    listDocuments({ page: 1, pageSize: 100 })
      .then((r) => setDocs(r.items.filter((d) => d.status === 'READY')))
      .catch((e) => setError(getErrorMessage(e)))
  }, [])

  useEffect(() => {
    if (!docId) {
      setElements([])
      return
    }
    setLoading(true)
    listElements(docId)
      .then((rs) => { setElements(rs); setError(null) })
      .catch((e) => { setElements([]); setError(getErrorMessage(e)) })
      .finally(() => setLoading(false))
  }, [docId])

  // 1108 P2-8：行点击=按 D-11 同机制定位（携带段内字符区间 focus，预览页加载后高亮，映射失败退化跳页标记）
  const navigateToElement = (r: ElementView) => {
    if (!docId) return
    let focus: { paragraphIndex: number; charStart: number; charEnd: number } | undefined
    try {
      const loc = JSON.parse(r.locationJson ?? '{}')
      if (typeof loc.paragraphIndex === 'number') {
        focus = {
          paragraphIndex: loc.paragraphIndex,
          charStart: loc.charStart ?? 0,
          charEnd: loc.charEnd ?? (r.captionText?.length ?? 0),
        }
      }
    } catch {
      /* 无区间信息时仅跳转不高亮 */
    }
    navigate(`../documents/${docId}/view`, { relative: 'path', state: focus ? { focus } : null })
  }

  const activeDoc = docs.find((d) => d.id === docId)
  const isDocx = activeDoc?.mimeType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Space wrap>
        <Select
          placeholder="选择文档（仅已就绪）"
          style={{ width: 320 }}
          showSearch
          optionFilterProp="label"
          value={docId}
          onChange={setDocId}
          options={docs.map((d) => ({ value: d.id, label: d.title }))}
        />
        {docId && (
          // 1106 P1-1：当前路由已是 .../documents，相对段只补 `{id}/view`
          <AppButton type="default" onClick={() => navigate(`../documents/${docId}/view`, { relative: 'path' })}>
            打开预览
          </AppButton>
        )}
      </Space>

      {error && <Alert type="error" showIcon message={error} />}
      {docId && !loading && !isDocx && elements.length === 0 && (
        <Alert type="info" showIcon message="非 Word 文档暂无结构树（PDF 结构识别列入后续迭代）" />
      )}
      {docId && isDocx && !loading && elements.length === 0 && !error && (
        <Alert type="info" showIcon message="该文档结构树将在首次格式校验时建立" />
      )}

      {/* 1107 P2-8：行点击=打开预览定位（与 D-11 机制一致，页内提供段落位置列） */}
      <Table<ElementView>
        size="small"
        rowKey="elementKey"
        loading={loading}
        dataSource={elements}
        pagination={{ pageSize: 20 }}
        onRow={(r) => ({ onClick: () => navigateToElement(r), style: { cursor: 'pointer' } })}
        columns={[
          { title: '元素', dataIndex: 'elementType', width: 100,
            render: (v: string) => TYPE_LABEL[v] ?? v },
          { title: '章节', dataIndex: 'chapterNo', width: 90, render: (v: string | null) => v ?? '-' },
          { title: '编号', dataIndex: 'displayLabel', width: 120, render: (v: string | null) => v ?? '-' },
          { title: '题注/名称', dataIndex: 'captionText', ellipsis: true,
            render: (v: string | null) => v ?? '-' },
          { title: '章内序', dataIndex: 'sequenceNo', width: 80,
            render: (v: number | null) => v ?? '-' },
          { title: '位置', key: 'pos', width: 90,
            render: (_, r) => {
              try {
                const loc = JSON.parse(r.locationJson ?? '{}')
                return typeof loc.paragraphIndex === 'number' ? `第 ${loc.paragraphIndex + 1} 段` : '-'
              } catch {
                return '-'
              }
            } },
        ]}
      />

      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        编辑区粘贴代码自动识别、增删图表后全文编号重排将随协作版本上线；本页提供模板口径下的编号总览。
        代码语言标注随编辑器切片提供（当前结构树识别代码块样式与位置）。
      </Typography.Text>
    </Space>
  )
}
