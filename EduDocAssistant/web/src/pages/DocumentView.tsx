// C-10：文档预览页（D2 文本阅读视图）——左正文块流+右四面板 Tabs；引用回跳=滚动至块并 <mark> 高亮；
// 块勾选为润色选择面；文档 PARSING 时 2s 轮询直至 READY（复用文档态停步逻辑）。
import { createElement, useCallback, useEffect, useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import { Alert, Card, Checkbox, Empty, Input, Popconfirm, Select, Space, Spin, Tabs, Tag, Typography, message } from 'antd'
import { AppButton } from '../components'
import { useLocation, useParams } from 'react-router-dom'
import { getErrorMessage } from '../api/http'
import { exportDocumentDocx, getDocument, reindexDocument, saveDocumentBlocks } from '../api/documents'
import type { CodeLang, DocumentDetail, EditableBlock } from '../api/documents'
import Editor from 'react-simple-code-editor'
import { CODE_LANGS, highlightCode, normalizeLang } from './view/codeHighlight'
import { getPreview } from '../api/ai'
import type { Preview, PreviewBlock, StructureNode } from '../api/ai'
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
import {
  annotationBlocks,
  blockLabel,
  buildBlockUnits,
  fullText,
  markerHeadings,
  nodeAtRange,
  nodeText,
  toEditableBlocks,
} from './view/structure'
import { locateByParagraph, scrollToPageMarker } from './view/locate'
import './view/structure.css'
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
  const [selected, setSelected] = useState<Set<string>>(new Set()) // 无结构老文档：chunk 级回退
  const [selectedNodes, setSelectedNodes] = useState<Set<number>>(new Set()) // 结构块级润色选择
  const { user } = useAuth()
  // 批注与在线状态：3 秒轮询（心跳 15 秒窗口）
  const [annotations, setAnnotations] = useState<AnnotationView[]>([])
  const [presence, setPresence] = useState<PresenceView[]>([])
  const [collabError, setCollabError] = useState<string | null>(null)
  const [annotationTarget, setAnnotationTarget] = useState<string>()
  const [activeTab, setActiveTab] = useState('summary')
  const [annotationText, setAnnotationText] = useState('')
  const [savingAnnotation, setSavingAnnotation] = useState(false)

  const can = useCallback(
    (code: string) => !!user?.permissions.includes(code),
    [user],
  )
  const blocks = preview?.blocks ?? []
  const structure = preview?.structure ?? []
  const full = useMemo(() => fullText(blocks), [blocks])
  // 批注/定位与块编辑同口径：一个 H1、一个正文段、一个代码块各是一个可批注块。
  const annotationOptions = useMemo(() => {
    const nodes = annotationBlocks(structure, full)
    const ranged = nodes.map((n) => ({
      value: `N${n.no}`,
      label: blockLabel(n, full),
      start: n.start!,
      end: n.end!,
    }))
    // 模板生成文档的标题是“标记标题”（文字不在正文流里），也要能选中并显示为一级/二级标题。
    const markers = structure
      .filter((n) => n.start == null && n.kind === 'HEADING' && n.anchor != null && (n.text ?? '').trim())
      .map((n) => {
        const anchor = Math.max(0, Math.min(n.anchor!, Math.max(0, full.length - 1)))
        return { value: `N${n.no}`, label: blockLabel(n, full), start: anchor, end: anchor }
      })
    const options = [...ranged, ...markers].sort((a, b) => a.start - b.start || a.end - b.end)
    if (options.length > 0) return options
    return blocks.map((b) => ({
      value: b.chunkId,
      label: `块 ${b.chunkNo} · ${b.text.replace(/\s+/g, ' ').slice(0, 40)}`,
      start: b.charStart,
      end: b.charEnd,
    }))
  }, [structure, full, blocks])
  const activeAnnotation = annotationOptions.find((o) => o.value === annotationTarget) ?? annotationOptions[0]
  const nodeByNo = useMemo(
    () => new Map(structure.filter((n) => n.start != null && n.end != null).map((n) => [n.no, n])),
    [structure],
  )
  const annotationMode = activeTab === 'collab'
  const selectAnnotationBlock = useCallback((nodeNo: number) => {
    if (annotationMode) setAnnotationTarget(`N${nodeNo}`)
  }, [annotationMode])
  const annotationStyle = (nodeNo: number): CSSProperties | undefined => annotationMode ? {
    cursor: 'pointer',
    ...(annotationTarget === `N${nodeNo}`
      ? { outline: '2px solid #1677ff', outlineOffset: 2, borderRadius: 4 }
      : {}),
  } : undefined
  // 润色以结构块为单位：一个正文段一个勾选单位；标题/代码/列表/表格不出现勾选框。
  const selectedBlocks = useMemo(() => {
    if (structure.length === 0) {
      return blocks.filter((b) => selected.has(b.chunkId))
    }
    return [...selectedNodes]
      .map((no) => nodeByNo.get(no))
      .filter((n): n is StructureNode => !!n && n.kind === 'PARAGRAPH')
      .map((n) => ({
        chunkId: `N${n.no}`,
        chunkNo: n.no,
        headingPath: null,
        pageNo: null,
        charStart: n.start!,
        charEnd: n.end!,
        text: nodeText(n, full),
      }))
      .sort((a, b) => a.charStart - b.charStart)
  }, [structure.length, blocks, selected, selectedNodes, nodeByNo, full])
  const [editMode, setEditMode] = useState(false)
  const [editBlocks, setEditBlocks] = useState<EditableBlock[]>([])
  const [savingEdit, setSavingEdit] = useState(false)
  const [reindexing, setReindexing] = useState(false)
  const [dragIndex, setDragIndex] = useState<number | null>(null)

  const BLOCK_OPTIONS: { value: EditableBlock['type']; label: string }[] = [
    { value: 'H1', label: '一级标题' },
    { value: 'H2', label: '二级标题' },
    { value: 'BODY', label: '正文' },
    { value: 'CODE', label: '代码块' },
  ]

  const startEdit = () => {
    setEditBlocks(toEditableBlocks(blocks, structure))
    setEditMode(true)
  }

  const saveEdit = async () => {
    if (!docId || !preview) return
    const blocksToSave = editBlocks.filter((b) => b.text.trim() !== '')
    if (blocksToSave.length === 0) {
      message.warning('文档内容不能为空')
      return
    }
    setSavingEdit(true)
    try {
      const res = await saveDocumentBlocks(docId, {
        baseVersionId: preview.versionId,
        blocks: blocksToSave,
      })
      message.success(`已保存为新版本 v${res.versionNo}`)
      setEditMode(false)
      reload()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSavingEdit(false)
    }
  }

  const addBlock = (type: EditableBlock['type']) => {
    setEditBlocks((prev) => [...prev, {
      type,
      text: '',
      ...(type === 'CODE' ? { lang: 'java' as CodeLang } : {}),
    }])
  }

  const updateBlock = (index: number, patch: Partial<EditableBlock>) => {
    setEditBlocks((prev) => prev.map((b, i) => (i === index ? { ...b, ...patch } : b)))
  }

  const moveBlock = (from: number, to: number) => {
    if (from === to || to < 0 || to >= editBlocks.length) return
    setEditBlocks((prev) => {
      const next = [...prev]
      const [item] = next.splice(from, 1)
      next.splice(to, 0, item)
      return next
    })
  }

  const downloadWord = async () => {
    if (!docId) return
    try {
      const blob = await exportDocumentDocx(docId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${doc?.title ?? 'document'}.docx`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const reindex = async () => {
    if (!docId) return
    setReindexing(true)
    try {
      await reindexDocument(docId)
      message.success('已重新提交索引，正在重试向量化')
      reload()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setReindexing(false)
    }
  }

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

  const submitAnnotation = async () => {
    if (!docId || !activeAnnotation) return
    if (!annotationText.trim()) {
      message.warning('请输入批注内容')
      return
    }
    setSavingAnnotation(true)
    try {
      await createAnnotation(docId, {
        versionId: preview?.versionId,
        charStart: activeAnnotation.start,
        charEnd: activeAnnotation.end,
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

  // 协作者只有批注/问答/格式校验：默认 Tab 落在被隐藏的摘要时切到批注。
  useEffect(() => {
    if (doc?.relation === 'COLLAB' && ['summary', 'keypoint', 'polish'].includes(activeTab)) {
      setActiveTab('collab')
    }
  }, [doc?.relation, activeTab])

  if (loading) return <Spin style={{ display: 'block', margin: '80px auto' }} />
  if (error) return <Alert type="error" showIcon message="预览加载失败" description={error} />

  /** 在块文本上渲染一段文字；ranges 是块内局部高亮段，mark 序号在整块内连续。 */
  const renderText = (block: PreviewBlock, text: string, localStart: number,
                      ranges: { start: number; end: number }[], counter: { value: number }) => {
    const end = localStart + text.length - 1
    const local = ranges
      .map((r) => ({ start: Math.max(r.start, localStart) - localStart, end: Math.min(r.end, end) - localStart }))
      .filter((r) => r.end >= r.start)
    return sliceMarks(text, local).map(([seg, mark]) => {
      const i = counter.value++
      return mark
        ? <mark key={i} data-testid={`hl-${block.chunkNo}-${i}`}>{seg}</mark>
        : <span key={i}>{seg}</span>
    })
  }

  const renderBlockText = (block: PreviewBlock) => {
    const ranges = highlight ? splitAcrossBlocks([block], highlight.start, highlight.end).map((r) => r.range) : []
    const counter = { value: 0 }
    const markers = markerHeadings(block, structure)
    const units = buildBlockUnits(block, structure)
    if (units.length === 0 && markers.length === 0) {
      return renderText(block, block.text, 0, ranges, counter)
    }
    return (
      <>
        {markers.map((m) => createElement(
          `h${Math.min(4, Math.max(1, m.level))}`,
          {
            key: `marker-${m.nodeNo}`,
            style: annotationStyle(m.nodeNo),
            onClick: () => selectAnnotationBlock(m.nodeNo),
          },
          m.text,
        ))}
        {units.map((unit, ui) => {
          if (unit.type === 'heading') {
            return createElement(
              `h${Math.min(4, Math.max(1, unit.level))}`,
              {
                key: `unit-${ui}`,
                style: annotationStyle(unit.nodeNo),
                onClick: () => selectAnnotationBlock(unit.nodeNo),
              },
              renderText(block, unit.text, unit.localStart, ranges, counter),
            )
          }
          if (unit.type === 'paragraph') {
            const node = nodeByNo.get(unit.nodeNo)
            const selectable = node?.kind === 'PARAGRAPH' && node.start != null
              && node.start >= block.charStart && node.start <= block.charEnd
            const checked = selectedNodes.has(unit.nodeNo)
            return (
              <div key={`unit-${ui}`} onClick={() => selectAnnotationBlock(unit.nodeNo)} style={{
                display: 'flex', alignItems: 'flex-start', gap: 8,
                background: checked ? '#f0f7ff' : undefined, borderRadius: 4,
                ...annotationStyle(unit.nodeNo),
              }}>
                {can(PermissionCode.AI_POLISH) && activeTab !== 'collab' && selectable && (
                  <Checkbox
                    style={{ marginTop: 3 }}
                    checked={checked}
                    onClick={(e) => e.stopPropagation()}
                    onChange={(e) => setSelectedNodes((prev) => {
                      const next = new Set(prev)
                      if (e.target.checked) next.add(unit.nodeNo)
                      else next.delete(unit.nodeNo)
                      return next
                    })}
                  />
                )}
                <p style={{ flex: 1, margin: 0 }}>{renderText(block, unit.text, unit.localStart, ranges, counter)}</p>
              </div>
            )
          }
          if (unit.type === 'code') {
            const marked = ranges.some((r) => r.end >= unit.localStart && r.start <= unit.localEnd)
            return (
              <pre
                key={`unit-${ui}`}
                onClick={() => selectAnnotationBlock(unit.nodeNo)}
                style={{
                  margin: '6px 0', padding: '8px 10px', borderRadius: 4,
                  background: '#f6f8fa', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                  fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                  ...annotationStyle(unit.nodeNo),
                }}
              >
                {marked
                  ? renderText(block, unit.text, unit.localStart, ranges, counter)
                  : <code dangerouslySetInnerHTML={{ __html: highlightCode(unit.text, unit.lang) }} />}
              </pre>
            )
          }
          if (unit.type === 'list') {
            return (
              <ol key={`unit-${ui}`} start={unit.items[0]?.ordinal ?? 1}>
                {unit.items.map((item) => (
                  <li key={`item-${item.nodeNo}`} onClick={() => selectAnnotationBlock(item.nodeNo)}
                    style={annotationStyle(item.nodeNo)}>
                    {renderText(block, item.text, item.localStart, ranges, counter)}
                  </li>
                ))}
              </ol>
            )
          }
          return (
            <table key={`unit-${ui}`}>
              <tbody>
                {unit.rows.map((row, ri) => (
                  <tr key={`row-${ri}`}>
                    {row.cells.map((cell) => (
                      <td key={`cell-${cell.nodeNo}`} onClick={() => selectAnnotationBlock(cell.nodeNo)}
                        style={annotationStyle(cell.nodeNo)}>
                        {renderText(block, cell.text, cell.localStart, ranges, counter)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          )
        })}
      </>
    )
  }

  // 1106 P2-1/R3：文本 READY 但向量未 READY（或刚成版 PARSING）同样视为索引中，禁用 AI 生成入口
  const indexing = doc?.status === 'PARSING' || preview?.vectorReady === false
  const isCollab = doc?.relation === 'COLLAB'

  const tabItems = [
    ...(isCollab ? [] : [
      {
        key: 'summary',
        label: '摘要',
        children: <SummaryPanel documentId={docId!} blocks={blocks} structure={structure} indexing={indexing}
          onHighlight={highlightRange} allowed={can(PermissionCode.AI_SUMMARY)} />,
      },
      {
        key: 'keypoint',
        label: '要点',
        children: <KeypointPanel documentId={docId!} blocks={blocks} structure={structure} indexing={indexing}
          onHighlight={highlightRange} allowed={can(PermissionCode.AI_KEYPOINT)} />,
      },
    ]),
    {
      key: 'qa',
      label: '问答',
      children: <QaPanel documentId={docId!} blocks={blocks} structure={structure} indexing={indexing}
        onHighlight={highlightRange} allowed={can(PermissionCode.AI_QA)} />,
    },
    ...(isCollab ? [] : [{
      key: 'polish',
      label: '润色',
      children: (
        <PolishPanel
          documentId={docId!}
          selectedBlocks={selectedBlocks}
          onHighlight={highlightRange}
          allowed={can(PermissionCode.AI_POLISH)}
          onChanged={() => {
            setSelected(new Set())
            setSelectedNodes(new Set())
            reload()
          }}
        />
      ),
    }]),
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
              value={activeAnnotation?.value}
              placeholder="搜索并选择批注块"
              showSearch
              optionFilterProp="label"
              onChange={setAnnotationTarget}
              options={annotationOptions.map((o) => ({ value: o.value, label: o.label }))}
            />
            <Input.TextArea rows={3} value={annotationText}
              onChange={(e) => setAnnotationText(e.target.value)}
              placeholder="对该块添加批注（owner 与协作者可见）…" />
            <AppButton loading={savingAnnotation} disabled={!activeAnnotation}
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
                <Tag>
                  {(() => {
                    const marker = structure.find((n) => n.start == null && n.kind === 'HEADING'
                      && n.anchor != null && n.anchor >= a.charStart && n.anchor <= a.charEnd)
                    const node = marker ?? nodeAtRange(structure, a.charStart, a.charEnd)
                    return node ? blockLabel(node, full) : `字符 ${a.charStart}-${a.charEnd}`
                  })()}
                </Tag>
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
          canEdit={!isCollab && can(PermissionCode.DOCUMENT_EDIT)}
          blocks={blocks}
          onHighlight={highlightRange}
        />
      ),
    },
  ]

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'minmax(0, 1fr) 420px',
      gap: 16,
      alignItems: 'stretch',
      height: '100%',
      minHeight: 0,
    }}>
      <Card
        style={{ height: '100%', minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
        styles={{ body: { flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' } }}
        title={
          <span>
            {doc?.title ?? '文档预览'}
            {doc && <Tag style={{ marginLeft: 8 }} color={doc.status === 'READY' ? 'success' : 'processing'}>
              v{doc.versionNo ?? '?'} · {doc.status === 'PARSING' ? '索引重建中' : doc.status}
            </Tag>}
          </span>
        }
        extra={!editMode && !isCollab ? (
          <Space>
            {can(PermissionCode.DOCUMENT_EDIT) && (
              <AppButton size="small" onClick={startEdit}>编辑</AppButton>
            )}
            <AppButton size="small" onClick={downloadWord}>导出 Word</AppButton>
          </Space>
        ) : undefined}
        size="small"
      >
        {editMode ? (
          <div style={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
            {editBlocks.map((b, i) => (
              <div
                key={i}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault()
                  const from = dragIndex ?? Number(e.dataTransfer.getData('text/plain'))
                  if (Number.isInteger(from)) moveBlock(from, i)
                  setDragIndex(null)
                }}
                style={{
                  display: 'flex', gap: 8, marginBottom: 8, alignItems: 'flex-start',
                  opacity: dragIndex === i ? 0.45 : 1,
                }}
              >
                <span
                  draggable
                  onDragStart={(e) => {
                    setDragIndex(i)
                    e.dataTransfer.setData('text/plain', String(i))
                    e.dataTransfer.effectAllowed = 'move'
                  }}
                  onDragEnd={() => setDragIndex(null)}
                  title="拖动调整顺序"
                  style={{ cursor: 'grab', color: '#999', paddingTop: 4, userSelect: 'none' }}
                >
                  ⠿
                </span>
                <Select
                  size="small"
                  style={{ width: 110, flex: '0 0 110px' }}
                  value={b.type}
                  options={BLOCK_OPTIONS}
                  onChange={(type) => updateBlock(i, { type })}
                />
                {b.type === 'CODE' && (
                  <Select
                    size="small"
                    style={{ width: 96, flex: '0 0 96px' }}
                    value={normalizeLang(b.lang)}
                    options={CODE_LANGS}
                    onChange={(lang) => updateBlock(i, { lang })}
                  />
                )}
                {b.type === 'CODE' ? (
                  <Editor
                    value={b.text}
                    onValueChange={(text) => updateBlock(i, { text })}
                    highlight={(code) => highlightCode(code, b.lang)}
                    padding={10}
                    textareaId={`block-code-${i}`}
                    style={{
                      flex: 1, minHeight: 60, fontFamily: 'ui-monospace, Consolas, monospace',
                      fontSize: 12, background: '#fafafa', borderRadius: 4, border: '1px solid #d9d9d9',
                    }}
                  />
                ) : (
                  <Input.TextArea
                    autoSize={{ minRows: 1, maxRows: 10 }}
                    value={b.text}
                    placeholder="内容…"
                    style={{
                      flex: 1,
                      ...(b.type === 'H1' ? { fontSize: 20, fontWeight: 600 } : {}),
                      ...(b.type === 'H2' ? { fontSize: 17, fontWeight: 600 } : {}),
                    }}
                    onChange={(e) => updateBlock(i, { text: e.target.value })}
                  />
                )}
                <AppButton type="text" danger
                  onClick={() => setEditBlocks((prev) => prev.filter((_, j) => j !== i))}>
                  删除
                </AppButton>
              </div>
            ))}
            <Space wrap>
              <AppButton onClick={() => addBlock('BODY')}>+</AppButton>
              <AppButton type="primary" loading={savingEdit} onClick={saveEdit}>保存为新版本</AppButton>
              <AppButton disabled={savingEdit} onClick={() => setEditMode(false)}>取消</AppButton>
            </Space>
          </div>
        ) : (
        <>
        {indexing && (
          <Alert style={{ marginBottom: 8 }} type="info" showIcon
            message="新版索引中：预览展示最新文本，摘要/要点/问答在索引完成后即可用（R3 口径）"
            action={!isCollab && doc?.status === 'READY' && preview?.vectorReady === false ? (
              <AppButton size="small" loading={reindexing} onClick={() => void reindex()}>
                重新索引
              </AppButton>
            ) : undefined} />
        )}
        <div className="doc-structure"
          onClick={() => setHighlight(null)}
          style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '4px 8px' }}>
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
                  background: structure.length === 0 && selected.has(block.chunkId) ? '#f0f7ff' : undefined,
                  borderRadius: 4,
                }}
              >
                {can(PermissionCode.AI_POLISH) && structure.length === 0 && (
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
      <Card
        size="small"
        title="智能处理"
        style={{ height: '100%', minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
        styles={{ body: { flex: 1, minHeight: 0, overflow: 'auto' } }}
      >
        <Tabs size="small" activeKey={activeTab} onChange={setActiveTab} items={tabItems} destroyOnHidden />
      </Card>
    </div>
  )
}
