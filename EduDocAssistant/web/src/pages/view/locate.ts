// 1108 复审 P2-5/P2-8：段-块映射与页面退化的共享机制（D-11 校验面板与 D-14 规范化行点击同口径）。
// 语义：location {paragraphIndex, charStart, charEnd} 的 char 区间是 docx 段落文本内偏移；
// 文本视图是 Tika 流分块（POI 段流与其不同源），故用「段均字符」估计段起点 → 命中块后用
// 段内 charStart/charEnd 得实际高亮区间；估计越界则退化为「跳至该页标记」。精确段-块映射待协作切片。
import type { PreviewBlock } from '../../api/ai'

export interface ParaLoc {
  paragraphIndex: number
  charStart: number
  charEnd: number
}

/** 中文课程报告经验均值：约 120 字/段（错题集 P-07 校准时可回调） */
export const AVG_PARA_CHARS = 120
/** 约 2000 字符/页（A4 小四 1.5 倍行距经验值，用于退化页码估算） */
export const AVG_PAGE_CHARS = 2000

export function paragraphRange(
  blocks: PreviewBlock[],
  loc: ParaLoc,
): { start: number; end: number; chunkNo: number } | null {
  if (blocks.length === 0) return null
  const total = blocks[blocks.length - 1].charEnd
  if (total <= 0) return null
  const paraStart = loc.paragraphIndex * AVG_PARA_CHARS
  if (paraStart > total + AVG_PARA_CHARS) return null // 段数估计越界 → 映射失败
  const start = Math.min(total - 1, paraStart + Math.max(0, loc.charStart))
  const end = Math.min(total, Math.max(start + 1, paraStart + Math.max(1, loc.charEnd)))
  const anchor = blocks.find((b) => start >= b.charStart && start < b.charEnd)
  if (!anchor) return null
  return { start, end, chunkNo: anchor.chunkNo }
}

/** 段落映射成功：滚动到锚块并高亮区间；返回是否成功 */
export function locateByParagraph(
  blocks: PreviewBlock[],
  loc: ParaLoc,
  onHighlight: (start: number, end: number) => void,
): boolean {
  const range = paragraphRange(blocks, loc)
  if (!range) return false
  document.getElementById(`block-${range.chunkNo}`)
    ?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  onHighlight(range.start, range.end)
  return true
}

/** 退化路径：按估计页码跳至页分隔标记；命中返回 true */
export function scrollToPageMarker(loc: ParaLoc): boolean {
  const pageNo = 1 + Math.floor((loc.paragraphIndex * AVG_PARA_CHARS) / AVG_PAGE_CHARS)
  // 1108 新一轮 P3-1：首块所在页无页分隔标记（锚只在页号变化处渲染），第 1 页退化为滚至首个块
  const el = document.getElementById(`page-anchor-${pageNo}`)
    ?? (pageNo === 1 ? document.getElementById('block-1') : null)
  if (!el) return false
  el.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  return true
}
