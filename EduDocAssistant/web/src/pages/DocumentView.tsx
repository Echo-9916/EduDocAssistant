// C-10：文档预览页（D2 文本阅读视图）——左正文块流+右四面板 Tabs；引用回跳=滚动至块并 <mark> 高亮；
// 块勾选为润色选择面；文档 PARSING 时 2s 轮询直至 READY（复用文档态停步逻辑）。
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Card, Checkbox, Spin, Tabs, Tag } from 'antd'
import { useLocation, useParams } from 'react-router-dom'
import { getErrorMessage } from '../api/http'
import { getDocument } from '../api/documents'
import type { DocumentDetail } from '../api/documents'
import { getPreview } from '../api/ai'
import type { Preview, PreviewBlock } from '../api/ai'
import { PermissionCode } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { splitAcrossBlocks, sliceMarks } from './view/highlight'
import { locateByParagraph, scrollToPageMarker } from './view/locate'
import SummaryPanel from './view/SummaryPanel'
import KeypointPanel from './view/KeypointPanel'
import QaPanel from './view/QaPanel'
import PolishPanel from './view/PolishPanel'
import FormatPanel from './view/FormatPanel'

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

  // 文档 PARSING（含采纳后的简化解析窗口）或 vectorReady=false（索引异步建立中）→ 2s 轮询至翻转。
  // 1106 复审 P1：仅判 PARSING 会让「文本 READY+向量 PENDING」停在禁用态需手动刷新。
  useEffect(() => {
    if (!documentId) return
    if (doc?.status !== 'PARSING' && preview?.vectorReady !== false) return
    const timer = setInterval(reload, 2000)
    return () => clearInterval(timer)
  }, [documentId, doc?.status, preview?.vectorReady, reload])

  return { preview, doc, error, loading, reload }
}

export default function DocumentView() {
  const { docId } = useParams()
  const location = useLocation()
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

  // 1108 P2-8：D-14 规范化页行点击携 {state:{focus}} 进入时，加载完成后按 D-11 同机制定位高亮
  // 1108 新一轮 P3-2：依赖纳入 focus/docId——同实例切文档且块数恰好相同时仍触发重新定位
  const focus = (location.state as { focus?: { paragraphIndex: number; charStart: number; charEnd: number } } | null)?.focus
  useEffect(() => {
    if (!focus || blocks.length === 0) return
    if (!locateByParagraph(blocks, focus, highlightRange)) {
      scrollToPageMarker(focus)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blocks.length, focus, docId])

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />
  if (error) return <Alert type="error" showIcon message="预览加载失败" description={error} />

  const renderBlockText = (block: PreviewBlock) => {
    const ranges = highlight ? splitAcrossBlocks([block], highlight.start, highlight.end) : []
    return sliceMarks(block.text, ranges.map((r) => r.range)).map(([seg, mark], i) =>
      mark ? <mark key={i} data-testid={`hl-${block.chunkNo}-${i}`}>{seg}</mark> : <span key={i}>{seg}</span>,
    )
  }

  // 1106 P2-1/R3：文本 READY 但向量未 READY（或刚成版 PARSING）同样视为索引中，禁用 AI 生成入口
  const indexing = doc?.status === 'PARSING' || preview?.vectorReady === false

  const tabItems = [
    {
      key: 'summary',
      label: '摘要',
      children: <SummaryPanel documentId={docId!} blocks={blocks} indexing={indexing}
        onHighlight={highlightRange} allowed={can(PermissionCode.AI_SUMMARY)} />,
    },
    {
      key: 'keypoint',
      label: '要点',
      children: <KeypointPanel documentId={docId!} blocks={blocks} indexing={indexing}
        onHighlight={highlightRange} allowed={can(PermissionCode.AI_KEYPOINT)} />,
    },
    {
      key: 'qa',
      label: '问答',
      children: <QaPanel documentId={docId!} blocks={blocks} indexing={indexing}
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
    {
      // D-11/D-12：格式校验面板（修复成版后 reload 同步新版本状态）
      key: 'format',
      label: '格式校验',
      children: (
        <FormatPanel
          documentId={docId!}
          documentReady={doc?.status === 'READY'}
          allowed={can(PermissionCode.FORMAT_CHECK)}
          canEdit={can(PermissionCode.DOCUMENT_EDIT)}
          blocks={blocks}
          onHighlight={highlightRange}
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
        {indexing && (
          <Alert style={{ marginBottom: 8 }} type="info" showIcon
            message="新版索引中：预览展示最新文本，摘要/要点/问答在索引完成后即可用（R3 口径）" />
        )}
        <div style={{ maxHeight: '70vh', overflowY: 'auto', padding: '4px 8px' }}>
          {blocks.map((block, i) => (
            <div key={block.chunkId}>
              {blocks[i - 1]?.pageNo !== block.pageNo && (
                // 1108 P2-5/P2-8：页分隔标记带锚点 id，供定位退化「跳至该页标记」
                <div id={block.pageNo != null ? `page-anchor-${block.pageNo}` : undefined}
                  style={{ textAlign: 'center', color: '#999', margin: '8px 0' }}>
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
