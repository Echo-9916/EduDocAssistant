// C-10：文档预览页（D2 文本阅读视图）——左正文块流+右四面板 Tabs；引用回跳=滚动至块并 <mark> 高亮；
// 块勾选为润色选择面；文档 PARSING 时 2s 轮询直至 READY（复用文档态停步逻辑）。
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Card, Checkbox, Empty, Input, Popconfirm, Segmented, Select, Space, Spin, Tabs, Tag, Typography, message } from 'antd'
import FormatPane from './view/FormatPane'
import { AppButton } from '../components'
import { useLocation, useParams } from 'react-router-dom'
import { getErrorMessage } from '../api/http'
import { getDocument } from '../api/documents'
import type { DocumentDetail } from '../api/documents'
import { getPreview } from '../api/ai'
import type { Preview, PreviewBlock } from '../api/ai'
import { PermissionCode } from '../api/types'
import {
  createAnnotation,
  deleteAnnotation,
  heartbeatPresence,
  listAnnotations,
  listPresence,
  resolveAnnotation,
} from '../api/collaboration'
import type { AnnotationView, PresenceView } from '../api/collaboration'
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
  // 格式视图开关（docx-preview 版式还原 / PDF iframe；文本视图承载 AI 与问题定位）
  const [viewMode, setViewMode] = useState<'text' | 'format'>('text')
  const [highlight, setHighlight] = useState<Highlight | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const { user } = useAuth()
  // 批注与在线状态：3 秒轮询（心跳 15 秒窗口）
  const [annotations, setAnnotations] = useState<AnnotationView[]>([])
  const [presence, setPresence] = useState<PresenceView[]>([])
  const [collabError, setCollabError] = useState<string | null>(null)
  const [annotationBlock, setAnnotationBlock] = useState<string>()
  const [annotationText, setAnnotationText] = useState('')
  const [savingAnnotation, setSavingAnnotation] = useState(false)

  const can = useCallback(
    (code: string) => !!user?.permissions.includes(code),
    [user],
  )
  const blocks = preview?.blocks ?? []

  const byChunk = useMemo(() => new Map(blocks.map((b) => [b.chunkId, b])), [blocks])

  const refreshCollab = useCallback(async () => {
    if (!docId) return
    try {
      const [a, p] = await Promise.all([listAnnotations(docId), listPresence(docId)])
      setAnnotations(a)
      setPresence(p)
      setCollabError(null)
    } catch (e) {
      setCollabError(getErrorMessage(e))
    }
  }, [docId])

  // 3 秒轮询批注与在线列表；心跳写入进程内 TTL Map（15 秒窗口）
  useEffect(() => {
    if (!docId) return
    let alive = true
    const tick = async () => {
      await heartbeatPresence(docId).catch(() => undefined)
      if (!alive) return
      try {
        const [a, p] = await Promise.all([listAnnotations(docId), listPresence(docId)])
        if (!alive) return
        setAnnotations(a)
        setPresence(p)
        setCollabError(null)
      } catch (e) {
        if (alive) setCollabError(getErrorMessage(e))
      }
    }
    void tick()
    const timer = setInterval(tick, 3000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [docId])

  const activeAnnotationBlock = blocks.find((b) => b.chunkId === annotationBlock) ?? blocks[0]

  const submitAnnotation = async () => {
    if (!docId || !activeAnnotationBlock) return
    if (!annotationText.trim()) {
      message.warning('请输入批注内容')
      return
    }
    setSavingAnnotation(true)
    try {
      await createAnnotation(docId, {
        versionId: preview?.versionId,
        charStart: activeAnnotationBlock.charStart,
        charEnd: activeAnnotationBlock.charEnd,
        content: annotationText.trim(),
      })
      setAnnotationText('')
      message.success('批注已添加')
      await refreshCollab()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSavingAnnotation(false)
    }
  }

  const toggleAnnotation = async (row: AnnotationView) => {
    try {
      await resolveAnnotation(row.id, row.status === 'O')
      message.success(row.status === 'O' ? '批注已标记为已解决' : '批注已重新打开')
      await refreshCollab()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const removeAnnotation = async (row: AnnotationView) => {
    try {
      await deleteAnnotation(row.id)
      message.success('批注已删除')
      await refreshCollab()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

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
      key: 'collab',
      label: '批注',
      children: (
        <div>
          <Space wrap style={{ marginBottom: 8 }}>
            <Typography.Text type="secondary">在线</Typography.Text>
            {presence.length === 0 ? (
              <Typography.Text type="secondary">15 秒内暂无活跃成员</Typography.Text>
            ) : presence.map((m) => (
              <Tag key={m.userId} color="blue">{m.displayName}</Tag>
            ))}
          </Space>
          {collabError && <Alert type="warning" showIcon message={collabError} style={{ marginBottom: 8 }} />}
          <Space direction="vertical" style={{ width: '100%', marginBottom: 12 }} size={8}>
            <Select
              style={{ width: '100%' }}
              value={activeAnnotationBlock?.chunkId}
              placeholder="选择批注段落"
              onChange={setAnnotationBlock}
              options={blocks.map((b) => ({
                value: b.chunkId,
                label: `字符 ${b.charStart}-${b.charEnd}：${b.text.replace(/\s+/g, ' ').slice(0, 36)}`,
              }))}
            />
            <Input.TextArea rows={3} value={annotationText}
              onChange={(e) => setAnnotationText(e.target.value)}
              placeholder="对该段落添加批注（owner 与协作者可见）…" />
            <AppButton loading={savingAnnotation} disabled={!activeAnnotationBlock}
              onClick={submitAnnotation}>
              添加批注
            </AppButton>
          </Space>
          {annotations.length === 0 ? (
            <Empty description="暂无批注" />
          ) : annotations.map((a) => (
            <div key={a.id} style={{ borderTop: '1px solid #f0f0f0', padding: '8px 0' }}>
              <Space wrap size={4}>
                <Typography.Text strong>{a.authorName}</Typography.Text>
                <Tag>字符 {a.charStart}-{a.charEnd}</Tag>
                <Tag color={a.status === 'O' ? 'orange' : 'green'}>
                  {a.status === 'O' ? '待处理' : '已解决'}
                </Tag>
                {a.resolvedByName && <Tag color="green">由 {a.resolvedByName} 解决</Tag>}
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {new Date(a.createTime).toLocaleString('zh-CN', { hour12: false })}
                </Typography.Text>
              </Space>
              <div style={{ margin: '4px 0' }}>{a.content}</div>
              <Space size={4}>
                {a.canResolve && (
                  <AppButton type="link" size="small" onClick={() => toggleAnnotation(a)}>
                    {a.status === 'O' ? '标记解决' : '重新打开'}
                  </AppButton>
                )}
                {a.canDelete && (
                  <Popconfirm title="删除该批注？" okText="删除" cancelText="取消"
                    onConfirm={() => removeAnnotation(a)}>
                    <AppButton type="link" size="small" danger>删除</AppButton>
                  </Popconfirm>
                )}
              </Space>
            </div>
          ))}
        </div>
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
        <Segmented
          value={viewMode}
          onChange={(v) => setViewMode(v as 'text' | 'format')}
          options={[
            { label: '文本视图（AI/定位）', value: 'text' },
            { label: '格式视图（版式）', value: 'format' },
          ]}
          style={{ marginBottom: 8 }}
        />
        {viewMode === 'format' ? (
          <FormatPane documentId={docId!} mimeType={doc?.mimeType} documentUpdateTime={doc?.updateTime} />
        ) : (
        <>
        {indexing && (
          <Alert style={{ marginBottom: 8 }} type="info" showIcon
            message="新版索引中：预览展示最新文本，摘要/要点/问答在索引完成后即可用（R3 口径）" />
        )}
        <div style={{ maxHeight: '70vh', overflowY: 'auto', padding: '4px 8px' }}>
          {blocks.map((block, i) => (
            <div key={block.chunkId}>
              {/* 验收 M-05：无页号的块流不再渲染「第 ? 页」占位分隔线；有页号才带锚点 */}
              {block.pageNo != null && blocks[i - 1]?.pageNo !== block.pageNo && (
                <div id={`page-anchor-${block.pageNo}`}
                  style={{ textAlign: 'center', color: '#999', margin: '8px 0' }}>
                  — 第 {block.pageNo} 页 —
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
        </>
        )}
      </Card>
      <Card size="small" title="智能处理">
        <Tabs size="small" items={tabItems} destroyOnHidden />
      </Card>
    </div>
  )
}
