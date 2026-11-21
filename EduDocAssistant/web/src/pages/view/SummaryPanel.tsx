// C-11：摘要面板——分组折叠、组展开显示原文、句点击跳原文高亮、复制/导出 txt(客户端)/docx(后端)。
import { useMemo, useState } from 'react'
import { Alert, Collapse, Space, Tag, message } from 'antd'
import { AppButton } from '../../components'
import { getErrorMessage } from '../../api/http'
import { createSummaryTask, fetchExportBlob } from '../../api/ai'
import type { PreviewBlock, StructureNode } from '../../api/ai'
import { TaskHeader, downloadBlob, downloadText, useGenerationTask } from './PanelShared'
import { blockRefForChunk, blockRefForNode, fullText, nodeText } from './structure'
import type { BlockRef } from './structure'
import type { HighlightFn } from './types'

interface Props {
  documentId: string
  blocks: PreviewBlock[]
  structure?: StructureNode[]
  onHighlight: HighlightFn
  allowed: boolean
  indexing?: boolean
}

interface Segment {
  text: string
  chunkIds: string[]
  /** 新结果：与块编辑同口径的语义块 ID（B{nodeNo}）；旧结果为空。 */
  blockIds: string[]
}

export function parseSummarySegments(structuredJson: string | null): Segment[] {
  if (!structuredJson) return []
  try {
    const parsed = JSON.parse(structuredJson) as { segments?: unknown }
    if (!Array.isArray(parsed.segments)) return []
    return parsed.segments
      .filter((s): s is { text?: unknown; chunkIds?: unknown; blockIds?: unknown } => !!s && typeof s === 'object')
      .map((s) => ({
        text: typeof s.text === 'string' ? s.text : '',
        chunkIds: Array.isArray(s.chunkIds) ? s.chunkIds.filter((x): x is string => typeof x === 'string') : [],
        blockIds: Array.isArray(s.blockIds) ? s.blockIds.filter((x): x is string => typeof x === 'string') : [],
      }))
      .filter((s) => s.text.length > 0)
  } catch {
    return []
  }
}

export default function SummaryPanel({ documentId, blocks, structure = [], onHighlight, allowed, indexing }: Props) {
  const ctrl = useGenerationTask(documentId, 'SUMMARY', createSummaryTask)
  const [pendingDocx, setPendingDocx] = useState(false)
  const segments = useMemo(
    () => parseSummarySegments(ctrl.task?.result?.structuredJson ?? null),
    [ctrl.task],
  )
  const byChunk = useMemo(() => new Map(blocks.map((b) => [b.chunkId, b])), [blocks])
  const byBlockId = useMemo(
    () => new Map(structure.filter((n) => n.start != null).map((n) => [`B${n.no}`, n])),
    [structure],
  )
  const full = useMemo(() => fullText(blocks), [blocks])
  const refOf = (b: PreviewBlock) => blockRefForChunk(b, structure, full)

  /** 新结果按 blockId 取块原文；旧结果回退到 chunk 映射。 */
  const refsOf = (seg: Segment): { ref: BlockRef; text: string }[] => {
    const fromBlocks = seg.blockIds
      .map((id) => byBlockId.get(id))
      .filter((n): n is StructureNode => !!n)
      .map((n) => ({ ref: blockRefForNode(n, full), text: nodeText(n, full) }))
    if (fromBlocks.length > 0) return fromBlocks
    return seg.chunkIds.flatMap((id) => {
      const b = byChunk.get(id)
      return b ? [{ ref: refOf(b), text: b.text }] : []
    })
  }

  const jump = (ref: BlockRef) => onHighlight(ref.start, ref.end)

  const exportDocx = async () => {
    const resultId = ctrl.task?.result?.resultId
    if (!resultId) return
    setPendingDocx(true)
    try {
      const { blob, filename } = await fetchExportBlob(resultId, 'docx')
      downloadBlob(blob, filename ?? '摘要.docx')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setPendingDocx(false)
    }
  }

  if (!allowed) return <Alert type="info" showIcon message="无 AI_SUMMARY 权限，摘要功能不可用" />

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <TaskHeader ctrl={ctrl} label="摘要" indexing={indexing} />
      {segments.length > 0 && ctrl.task?.result && (
        <>
          <Space wrap>
            <AppButton type="default" onClick={() => {
              navigator.clipboard?.writeText(ctrl.task!.result!.contentText ?? '')
                .then(() => message.success('已复制'))
                .catch(() => message.error('复制失败'))
            }}>复制全文</AppButton>
            <AppButton type="default" onClick={() => downloadText(ctrl.task!.result!.contentText ?? '', '摘要.txt')}>
              导出 .txt
            </AppButton>
            <AppButton type="default" loading={pendingDocx} onClick={() => void exportDocx()}>
              导出 .docx
            </AppButton>
          </Space>
          <Collapse
            key={ctrl.task?.taskId ?? 'summary'}
            size="small"
            defaultActiveKey={[]}
            items={segments.map((seg, i) => ({
              key: String(i),
              label: (
                <span>
                  {seg.text.slice(0, 40)}
                  {refsOf(seg).map((x, ri) => (
                    <Tag key={`${x.ref.node?.no ?? 'C'}-${ri}`} color="blue" style={{ marginLeft: 6 }}
                      onClick={(e) => { e.stopPropagation(); jump(x.ref) }}>
                      {x.ref.label.slice(0, 24)}
                    </Tag>
                  ))}
                </span>
              ),
              children: (
                <div data-testid={`segment-${i}`}>
                  <div style={{ whiteSpace: 'pre-wrap' }}>{seg.text}</div>
                  {refsOf(seg).map((x, ri) => (
                    <Alert key={`${x.ref.node?.no ?? 'C'}-${ri}`} style={{ marginTop: 8 }} type="info"
                      message={`原文（${x.ref.label}）`}
                      action={<AppButton type="default" size="small" onClick={() => jump(x.ref)}>定位</AppButton>}
                    />
                  ))}
                </div>
              ),
            }))}
          />
        </>
      )}
    </Space>
  )
}
