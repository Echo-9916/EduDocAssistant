// C-11b：要点面板——勾选+复制选中+导出清单（REQ-11 逐条可定位），underMin 提示不判失败。
import { useMemo, useState } from 'react'
import { Alert, Checkbox, List, Space, Tag, message } from 'antd'
import { AppButton } from '../../components'
import { createKeypointTask } from '../../api/ai'
import type { PreviewBlock, StructureNode } from '../../api/ai'
import { TaskHeader, downloadText, useGenerationTask } from './PanelShared'
import { blockRefAt, blockRefForChunk, blockRefForNode, fullText } from './structure'
import type { HighlightFn } from './types'

interface Props {
  documentId: string
  blocks: PreviewBlock[]
  structure?: StructureNode[]
  onHighlight: HighlightFn
  allowed: boolean
  indexing?: boolean
}

export interface Keypoint {
  text: string
  blockId: string | null
  chunkId: string | null
  charStart: number | null
}

export function parseKeypoints(structuredJson: string | null): { keypoints: Keypoint[]; underMin: boolean } {
  if (!structuredJson) return { keypoints: [], underMin: false }
  try {
    const parsed = JSON.parse(structuredJson) as { keypoints?: unknown; underMin?: unknown }
    const list = Array.isArray(parsed.keypoints)
      ? parsed.keypoints
          .filter((k): k is Record<string, unknown> => !!k && typeof k === 'object')
          .map((k) => ({
            text: typeof k.text === 'string' ? k.text : '',
            blockId: typeof k.blockId === 'string' ? k.blockId : null,
            chunkId: typeof k.chunkId === 'string' ? k.chunkId : null,
            charStart: typeof k.charStart === 'number' ? k.charStart : null,
          }))
          .filter((k) => k.text.length > 0)
      : []
    return { keypoints: list, underMin: parsed.underMin === true }
  } catch {
    return { keypoints: [], underMin: false }
  }
}

export default function KeypointPanel({ documentId, blocks, structure = [], onHighlight, allowed, indexing }: Props) {
  const ctrl = useGenerationTask(documentId, 'KEYPOINT', createKeypointTask)
  const [picked, setPicked] = useState<Set<number>>(new Set())
  const { keypoints, underMin } = useMemo(
    () => parseKeypoints(ctrl.task?.result?.structuredJson ?? null),
    [ctrl.task],
  )
  const byChunk = useMemo(() => new Map(blocks.map((b) => [b.chunkId, b])), [blocks])
  const byBlockId = useMemo(
    () => new Map(structure.filter((n) => n.start != null).map((n) => [`B${n.no}`, n])),
    [structure],
  )
  const full = useMemo(() => fullText(blocks), [blocks])

  if (!allowed) return <Alert type="info" showIcon message="无 AI_KEYPOINT 权限，要点功能不可用" />

  const toggle = (i: number, checked: boolean) =>
    setPicked((prev) => {
      const next = new Set(prev)
      if (checked) next.add(i)
      else next.delete(i)
      return next
    })

  const selected = keypoints.filter((_, i) => picked.has(i))
  const asText = (items: Keypoint[]) => items.map((k, i) => `${i + 1}. ${k.text}`).join('\n')

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <TaskHeader ctrl={ctrl} label="要点" indexing={indexing} />
      {keypoints.length > 0 && underMin && (
        <Alert type="warning" showIcon message={`仅提取到 ${keypoints.length} 条要点（少于 5 条），请人工复核覆盖度`} />
      )}
      {keypoints.length > 0 && (
        <>
          <Space wrap>
            <AppButton type="default" disabled={selected.length === 0}
              onClick={() => navigator.clipboard?.writeText(asText(selected))
                .then(() => message.success('已复制选中要点')).catch(() => message.error('复制失败'))}>
              复制选中（{selected.length}）
            </AppButton>
            <AppButton type="default" disabled={selected.length === 0}
              onClick={() => downloadText(asText(selected), '要点清单.txt')}>
              导出选中清单
            </AppButton>
          </Space>
          <List
            size="small"
            bordered
            dataSource={keypoints}
            renderItem={(k, i) => {
              const block = k.chunkId ? byChunk.get(k.chunkId) : undefined
              const node = k.blockId ? byBlockId.get(k.blockId) : undefined
              const start = k.charStart ?? block?.charStart ?? 0
              const ref = node ? blockRefForNode(node, full) : (block ? (structure.length > 0
                ? blockRefAt(structure, full, start, Math.min(start + 1, block.charEnd), block)
                : blockRefForChunk(block, structure, full)) : null)
              return (
                <List.Item
                  actions={ref ? [
                    <AppButton key="jump" type="link"
                      onClick={() => onHighlight(ref.start, ref.end)}>定位</AppButton>,
                  ] : [<Tag key="lost" color="orange">引用缺失</Tag>]}
                >
                  <Checkbox checked={picked.has(i)} onChange={(e) => toggle(i, e.target.checked)} />
                  <span style={{ marginLeft: 8 }} data-testid={`kp-${i}`}>
                    {i + 1}. {k.text}
                    {ref && <Tag style={{ marginLeft: 6 }}>{ref.label}</Tag>}
                  </span>
                </List.Item>
              )
            }}
          />
        </>
      )}
    </Space>
  )
}
