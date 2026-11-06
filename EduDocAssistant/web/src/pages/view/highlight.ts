// C-10 高亮原语：引用回跳=滚动至对应块并高亮 char_start~char_end（C-01 闭区间全文偏移）。
import type { PreviewBlock } from '../../api/ai'

export interface Range {
  start: number
  end: number
}

/** 绝对闭区间 [absStart, absEnd] 映射到块内局部区间（越界截断；无交集返回 null）。 */
export function toLocalRange(block: PreviewBlock, absStart: number, absEnd: number): Range | null {
  const start = Math.max(absStart, block.charStart)
  const end = Math.min(absEnd, block.charEnd)
  if (end < start) return null
  return { start: start - block.charStart, end: end - block.charStart }
}

/** 跨块分段：绝对区间覆盖到的所有块及其局部高亮段（引用可能跨块连接符）。 */
export function splitAcrossBlocks(blocks: PreviewBlock[], absStart: number, absEnd: number) {
  const out: { block: PreviewBlock; range: Range }[] = []
  for (const block of blocks) {
    const range = toLocalRange(block, absStart, absEnd)
    if (range) out.push({ block, range })
  }
  return out
}

/** 按局部高亮段把块文本切成 [文本, 是否高亮] 片段序列（<mark> 渲染用）。 */
export function sliceMarks(text: string, ranges: Range[]): [string, boolean][] {
  if (ranges.length === 0) return [[text, false]]
  const merged = mergeRanges(ranges)
  const out: [string, boolean][] = []
  let cursor = 0
  for (const r of merged) {
    if (r.start > cursor) out.push([text.slice(cursor, r.start), false])
    out.push([text.slice(r.start, r.end + 1), true])
    cursor = r.end + 1
  }
  if (cursor < text.length) out.push([text.slice(cursor), false])
  return out.filter(([s]) => s.length > 0)
}

function mergeRanges(ranges: Range[]): Range[] {
  const sorted = [...ranges].sort((a, b) => a.start - b.start)
  const out: Range[] = []
  for (const r of sorted) {
    const last = out[out.length - 1]
    if (last && r.start <= last.end + 1) last.end = Math.max(last.end, r.end)
    else out.push({ ...r })
  }
  return out
}
