// C-10：文档预览页（D2 文本阅读视图）——左正文块流+右四面板 Tabs；引用回跳=滚动至块并 <mark> 高亮；
// 块勾选为润色选择面；文档 PARSING 时 2s 轮询直至 READY（复用文档态停步逻辑）。
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Card, Checkbox, Spin, Tabs, Tag } from 'antd'
import { useParams } from 'react-router-dom'
import { getErrorMessage } from '../api/http'
import { getDocument } from '../api/documents'
import type { DocumentDetail } from '../api/documents'
import { getPreview } from '../api/ai'
import type { Preview, PreviewBlock } from '../api/ai'
import { PermissionCode } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { splitAcrossBlocks, sliceMarks } from './view/highlight'
import SummaryPanel from './view/SummaryPanel'
import KeypointPanel from './view/KeypointPanel'
import QaPanel from './view/QaPanel'
import PolishPanel from './view/PolishPanel'

interface Highlight {
  start: number
  end: number
}

export function usePreview(documentId: string | undefined) {
  const [preview, setPreview] = useState<Preview | null>(null)
  const [doc, setDoc] = useState<DocumentDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const reload = useCallback(() => {
    if (!documentId) return
    Promise.all([getDocument(documentId), getPreview(documentId)])
      .then(([d, p]) => {
        setDoc(d)
        setPreview(p)
        setError(null)
      })
      .catch((e) => setError(getErrorMessage(e)))
      .finally(() => setLoading(false))
  }, [documentId])

  useEffect(reload, [reload])

  // 文档 PARSING（含采纳后的简化解析+向量化窗口）→ 2s 轮询至状态翻转
  useEffect(() => {
    if (!documentId || doc?.status !== 'PARSING') return
    const timer = setInterval(reload, 2000)
    return () => clearInterval(timer)
  }, [documentId, doc?.status, reload])

  return { preview, doc, error, loading, reload }
}

export default function DocumentView() {
  const { docId } = useParams()
  const { preview, doc, error, loading, reload } = usePreview(docId)
  const [highlight, setHighlight] = useState<Highlight | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const { user } = useAuth()

  const can = useCallback(
    (code: string) => !!user?.permissions.includes(code),
    [user],
  )
  const blocks = preview?.blocks ?? []

  const byChunk = useMemo(() => new Map(blocks.map((b) => [b.chunkId, b])), [blocks])

  const highlightRange = useCallback(
    (absStart: number, absEnd: number) => {
      setHighlight({ start: absStart, end: absEnd })
      const anchor = blocks.find((b) => b.charEnd >= absStart && b.charStart <= absEnd)
      if (anchor) {
        const el = document.getElementById(`block-${anchor.chunkNo}`)
        el?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
      }
    },
    [blocks],
  )

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />
  if (error) return <Alert type="error" showIcon message="预览加载失败" description={error} />

  const renderBlockText = (block: PreviewBlock) => {
    const ranges = highlight ? splitAcrossBlocks([block], highlight.start, highlight.end) : []
    return sliceMarks(block.text, ranges.map((r) => r.range)).map(([seg, mark], i) =>
      mark ? <mark key={i} data-testid={`hl-${block.chunkNo}-${i}`}>{seg}</mark> : <span key={i}>{seg}</span>,
    )
  }

  const tabItems = [
    {
      key: 'summary',
      label: '摘要',
      children: <SummaryPanel documentId={docId!} blocks={blocks}
        onHighlight={highlightRange} allowed={can(PermissionCode.AI_SUMMARY)} />,
    },
    {
      key: 'keypoint',
      label: '要点',
      children: <KeypointPanel documentId={docId!} blocks={blocks}
        onHighlight={highlightRange} allowed={can(PermissionCode.AI_KEYPOINT)} />,
    },
    {
      key: 'qa',
      label: '问答',
      children: <QaPanel documentId={docId!} blocks={blocks}
        onHighlight={highlightRange} allowed={can(PermissionCode.AI_QA)} />,
    },
    {
      key: 'polish',
      label: '润色',
      children: (
        <PolishPanel
          documentId={docId!}
          selectedBlocks={blocks.filter((b) => selected.has(b.chunkId)).sort((a, b) => a.chunkNo - b.chunkNo)}
          onHighlight={highlightRange}
          allowed={can(PermissionCode.AI_POLISH)}
          onChanged={() => {
            setSelected(new Set())
            reload()
          }}
        />
      ),
    },
  ]

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 420px', gap: 16, alignItems: 'start' }}>
      <Card
        title={
          <span>
            {doc?.title ?? '文档预览'}
            {doc && <Tag style={{ marginLeft: 8 }} color={doc.status === 'READY' ? 'success' : 'processing'}>
              v{doc.versionNo ?? '?'} · {doc.status === 'PARSING' ? '索引重建中' : doc.status}
            </Tag>}
          </span>
        }
        size="small"
      >
        {doc?.status === 'PARSING' && (
          <Alert style={{ marginBottom: 8 }} type="info" showIcon
            message="新版索引中：预览展示最新文本，问答/摘要在索引完成后即可用（R3 口径）" />
        )}
        <div style={{ maxHeight: '70vh', overflowY: 'auto', padding: '4px 8px' }}>
          {blocks.map((block, i) => (
            <div key={block.chunkId}>
              {blocks[i - 1]?.pageNo !== block.pageNo && (
                <div style={{ textAlign: 'center', color: '#999', margin: '8px 0' }}>
                  — 第 {block.pageNo ?? '?'} 页 —
                </div>
              )}
              <div
                id={`block-${block.chunkNo}`}
                data-testid={`block-${block.chunkNo}`}
                style={{
                  margin: '6px 0',
                  padding: '6px 8px',
                  background: selected.has(block.chunkId) ? '#f0f7ff' : undefined,
                  borderRadius: 4,
                }}
              >
                {can(PermissionCode.AI_POLISH) && (
                  <Checkbox
                    style={{ marginRight: 8 }}
                    checked={selected.has(block.chunkId)}
                    onChange={(e) =>
                      setSelected((prev) => {
                        const next = new Set(prev)
                        if (e.target.checked) next.add(block.chunkId)
                        else next.delete(block.chunkId)
                        return next
                      })
                    }
                  />
                )}
                {block.headingPath && <Tag color="purple">{block.headingPath}</Tag>}
                {renderBlockText(block)}
              </div>
            </div>
          ))}
          {blocks.length === 0 && <Alert type="warning" showIcon message="暂无分块，请先解析文档" />}
        </div>
      </Card>
      <Card size="small" title="智能处理">
        <Tabs size="small" items={tabItems} destroyOnHidden />
      </Card>
    </div>
  )
}
