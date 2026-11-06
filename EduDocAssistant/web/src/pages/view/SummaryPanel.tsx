// C-11：摘要面板——分组折叠、组展开显示原文、句点击跳原文高亮、复制/导出 txt(客户端)/docx(后端)。
import { useMemo, useState } from 'react'
import { Alert, Collapse, Space, Tag, message } from 'antd'
import { AppButton } from '../../components'
import { getErrorMessage } from '../../api/http'
import { createSummaryTask, fetchExportBlob } from '../../api/ai'
import type { PreviewBlock } from '../../api/ai'
import { TaskHeader, downloadBlob, downloadText, useGenerationTask } from './PanelShared'
import type { HighlightFn } from './types'

interface Props {
  documentId: string
  blocks: PreviewBlock[]
  onHighlight: HighlightFn
  allowed: boolean
}

interface Segment {
  text: string
  chunkIds: string[]
}

export function parseSummarySegments(structuredJson: string | null): Segment[] {
  if (!structuredJson) return []
  try {
    const parsed = JSON.parse(structuredJson) as { segments?: unknown }
    if (!Array.isArray(parsed.segments)) return []
    return parsed.segments
      .filter((s): s is { text?: unknown; chunkIds?: unknown } => !!s && typeof s === 'object')
      .map((s) => ({
        text: typeof s.text === 'string' ? s.text : '',
        chunkIds: Array.isArray(s.chunkIds) ? s.chunkIds.filter((x): x is string => typeof x === 'string') : [],
      }))
      .filter((s) => s.text.length > 0)
  } catch {
    return []
  }
}

export default function SummaryPanel({ documentId, blocks, onHighlight, allowed }: Props) {
  const ctrl = useGenerationTask(documentId, 'SUMMARY', createSummaryTask)
  const [pendingDocx, setPendingDocx] = useState(false)
  const segments = useMemo(
    () => parseSummarySegments(ctrl.task?.result?.structuredJson ?? null),
    [ctrl.task],
  )
  const byChunk = useMemo(() => new Map(blocks.map((b) => [b.chunkId, b])), [blocks])

  const jump = (chunkId: string) => {
    const block = byChunk.get(chunkId)
    if (block) onHighlight(block.charStart, block.charEnd)
    else message.warning('引用原文不在当前版本')
  }

  const exportDocx = async () => {
    const resultId = ctrl.task?.result?.resultId
    if (!resultId) return
    setPendingDocx(true)
    try {
      downloadBlob(await fetchExportBlob(resultId, 'docx'), '摘要.docx')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setPendingDocx(false)
    }
  }

  if (!allowed) return <Alert type="info" showIcon message="无 AI_SUMMARY 权限，摘要功能不可用" />

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <TaskHeader ctrl={ctrl} label="摘要" />
      {ctrl.error && <Alert type="error" showIcon message={ctrl.error} />}
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
            size="small"
            defaultActiveKey={segments.map((_, i) => String(i))}
            items={segments.map((seg, i) => ({
              key: String(i),
              label: (
                <span>
                  {seg.text.slice(0, 40)}
                  {seg.chunkIds.map((id) => (
                    <Tag key={id} color="blue" style={{ marginLeft: 6 }}
                      onClick={(e) => { e.stopPropagation(); jump(id) }}>
                      #{byChunk.get(id)?.chunkNo ?? '?'}
                    </Tag>
                  ))}
                </span>
              ),
              children: (
                <div data-testid={`segment-${i}`}>
                  <div style={{ whiteSpace: 'pre-wrap' }}>{seg.text}</div>
                  {seg.chunkIds.map((id) => {
                    const b = byChunk.get(id)
                    if (!b) return null
                    return (
                      <Alert key={id} style={{ marginTop: 8 }} type="info"
                        message={`原文（第 ${b.pageNo ?? '-'} 页 · 块 ${b.chunkNo}）`}
                        description={<span style={{ whiteSpace: 'pre-wrap' }}>{b.text}</span>}
                        action={<AppButton type="link" onClick={() => onHighlight(b.charStart, b.charEnd)}>定位</AppButton>}
                      />
                    )
                  })}
                </div>
              ),
            }))}
          />
        </>
      )}
    </Space>
  )
}
