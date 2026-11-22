// 阅读结构层 → 块内渲染段。节点与 chunk 同坐标系（绝对闭区间），这里只做「节点 ∩ 本块」裁剪与分组，
// 不改变块文本、charStart/charEnd 或 <mark> 的偏移语义。
import type { PreviewBlock, StructureNode } from '../../api/ai'
import type { EditableBlock } from '../../api/documents'
import { normalizeLang } from './codeHighlight'

export interface MarkerHeading {
  nodeNo: number
  level: number
  text: string
}

export interface BlockRef {
  label: string
  start: number
  end: number
  node: StructureNode | null
}

/** C-01 全文（块文本以空串连接；块间 \n 已包含在块文本里）。 */
export function fullText(blocks: PreviewBlock[]): string {
  return blocks.map((b) => b.text).join('')
}

/** 结构节点的可见文本：区间节点从全文切片，标记标题用自带 text。 */
export function nodeText(node: StructureNode, full: string): string {
  if (node.start == null || node.end == null) return node.text ?? ''
  const start = Math.max(0, Math.min(node.start, full.length))
  const end = Math.max(start - 1, Math.min(node.end, full.length - 1))
  return end < start ? '' : full.slice(start, end + 1)
}

const HEADING_LABEL: Record<number, string> = { 1: '一级标题', 2: '二级标题', 3: '三级标题' }

/** 与块编辑相同的块划分口径：H1/H2、正文、代码块、列表项、表格单元格各算一个块。 */
export function blockLabel(node: StructureNode, full: string): string {
  const kind = node.kind === 'HEADING'
    ? HEADING_LABEL[node.level ?? 1] ?? '标题'
    : node.kind === 'CODE' ? '代码块'
      : node.kind === 'LIST_ITEM' ? '列表项'
        : node.kind === 'TABLE_CELL' ? '表格单元格' : '正文'
  const text = nodeText(node, full).replace(/\s+/g, ' ').trim()
  return text ? `${kind} · ${text.slice(0, 40)}` : kind
}

/** 引用落到具体结构块：优先引用区间交集最大的节点，退化为该 chunk 的首个区间节点。 */
export function blockRefAt(nodes: StructureNode[], full: string, start: number, end: number,
                           fallback: PreviewBlock): BlockRef {
  let best: StructureNode | null = null
  let bestOverlap = 0
  for (const n of nodes) {
    if (n.start == null || n.end == null) continue
    const overlap = Math.min(end, n.end) - Math.max(start, n.start)
    if (overlap >= 0 && overlap > bestOverlap) {
      best = n
      bestOverlap = overlap
    }
  }
  if (!best) {
    best = nodes.find((n) => n.start != null && n.end != null
      && n.end >= fallback.charStart && n.start <= fallback.charEnd) ?? null
  }
  if (!best || best.start == null || best.end == null) {
    return { label: `块 ${fallback.chunkNo}`, start, end, node: null }
  }
  return {
    label: blockLabel(best, full),
    start: Math.max(best.start, fallback.charStart),
    end: Math.min(best.end, fallback.charEnd),
    node: best,
  }
}

/** 引用只有 chunkId 时（摘要段落）：取该 chunk 内第一个可定位结构块。 */
export function blockRefForChunk(block: PreviewBlock, nodes: StructureNode[], full: string): BlockRef {
  return blockRefAt(nodes, full, block.charStart, block.charEnd, block)
}

/** 语义块 ID（后端 blockId）→ 结构节点引用。 */
export function blockRefForNode(node: StructureNode, full: string): BlockRef {
  return {
    label: blockLabel(node, full),
    start: node.start ?? 0,
    end: node.end ?? Math.max(0, (node.start ?? 0) - 1),
    node,
  }
}

/** 文档预览批注可选块：与块编辑一致，排除不在正文流里的标记标题。 */
export function annotationBlocks(nodes: StructureNode[], full: string): StructureNode[] {
  return nodes
    .filter((n) => n.start != null && n.end != null && n.end >= n.start)
    .filter((n) => nodeText(n, full).trim() !== '')
    .sort((a, b) => (a.start! - b.start!) || (a.no - b.no))
}

/** 批注已有区间按结构块回显；找不到时返回 null，调用方回退字符区间。 */
export function nodeAtRange(nodes: StructureNode[], start: number, end: number): StructureNode | null {
  let best: StructureNode | null = null
  let bestOverlap = 0
  for (const n of nodes) {
    if (n.start == null || n.end == null) continue
    const overlap = Math.min(end, n.end) - Math.max(start, n.start)
    if (overlap >= 0 && overlap > bestOverlap) {
      best = n
      bestOverlap = overlap
    }
  }
  return best
}

// ---------- 代码块启发式：Tika 对 DOCX 只输出普通段落，代码样式在结构层丢失 ----------
// 编辑/预览时把连续的「像代码」的正文段合并成一个代码块，并按关键字识别语言（默认 Java）。

const CODE_STANDALONE = /^[{}()[\];,]+$/
const CODE_START = /^(?:public|private|protected|static|final|abstract|synchronized|native|class|interface|enum|record|package|import|return|throw|if|else|for|while|switch|case|try|catch|finally|new|this\b|super\b|const|let|var|def|function|SELECT|INSERT|UPDATE|DELETE|CREATE)\b/i
const CODE_TAIL = /[;{}]\s*$/
const CJK_PUNCT_END = /[。！？；：，]\s*$/
const METHOD_LIKE = /^[A-Za-z_$][\w$<>[\], ?]*\s+[A-Za-z_$]\w*\s*\([^)]*\)\s*\{?$/

/** 单行文本是否像代码；偏保守，宁可漏判也不把中文正文误判成代码。 */
export function looksLikeCode(text: string): boolean {
  const t = text.trim()
  if (!t) return false
  if (CODE_STANDALONE.test(t)) return true
  if (/^@[A-Za-z]\w*$/.test(t)) return true
  if (CODE_START.test(t) && (CODE_TAIL.test(t) || t.includes('{') || t.includes('('))) return true
  if (CODE_TAIL.test(t) && !CJK_PUNCT_END.test(t)) return true
  if (t.length < 160 && METHOD_LIKE.test(t)) return true
  return false
}

/** 代码语言识别；识别不出时由调用方回落到 Java（校园模板以 Java/文档代码为主）。 */
export function detectCodeLang(text: string): 'java' | 'python' | 'cpp' | 'sql' | null {
  if (/@Override|System\.out\.|import\s+java\.|\b(?:extends|implements)\b|public\s+(?:static\s+)?(?:class|interface|void)\b/.test(text)) {
    return 'java'
  }
  if (/^\s*(?:def\s+\w+|from\s+\w+\s+import|import\s+\w+\s*$)/m.test(text) || /print\(/.test(text)) {
    return 'python'
  }
  if (/^\s*#include\b/m.test(text) || /\bstd::/.test(text)) {
    return 'cpp'
  }
  if (/^\s*(?:SELECT|INSERT|UPDATE|DELETE|CREATE)\b/im.test(text)) {
    return 'sql'
  }
  return null
}

/** 本块内节点可见文本（与 nodeText 同口径，只是数据源是单个 chunk）。 */
function blockSlice(block: PreviewBlock, node: StructureNode): string {
  if (node.start == null || node.end == null) return node.text ?? ''
  const start = Math.max(node.start, block.charStart) - block.charStart
  const end = Math.min(node.end, block.charEnd) - block.charStart
  return end < start ? '' : block.text.slice(start, end + 1)
}

interface BaseSegment {
  nodeNo: number
  text: string
  localStart: number
  localEnd: number
}

export type StructureUnit =
  | ({ type: 'heading'; level: number } & BaseSegment)
  | ({ type: 'paragraph' } & BaseSegment)
  | ({ type: 'code'; lang: string | null } & BaseSegment)
  | {
      type: 'list'
      items: (BaseSegment & { ordinal: number | null })[]
    }
  | {
      type: 'table'
      rows: { cells: BaseSegment[] }[]
    }

/** 落在本块的图片节点（anchor 在块区间内或正好在块尾之后）。 */
export function imageNodes(block: PreviewBlock, nodes: StructureNode[]): StructureNode[] {
  return nodes
    .filter((n) => n.kind === 'IMAGE' && !!n.imageId && n.anchor != null
      && n.anchor >= block.charStart && n.anchor <= block.charEnd + 1)
    .sort((a, b) => (a.anchor! - b.anchor!) || (a.no - b.no))
}

/** 标记型标题（文字不在正文流里，anchor 指向本节起点）。 */
export function markerHeadings(block: PreviewBlock, nodes: StructureNode[]): MarkerHeading[] {
  return nodes
    .filter((n) => n.start == null && n.anchor != null
      && n.anchor >= block.charStart && n.anchor <= block.charEnd)
    .sort((a, b) => a.no - b.no)
    .map((n) => ({ nodeNo: n.no, level: n.level ?? 1, text: n.text ?? '' }))
    .filter((n) => n.text.length > 0)
}

/**
 * 把结构节点裁剪到本块并分组：连续 LIST_ITEM → 一个列表，连续 TABLE_CELL → 一张表。
 * 返回空数组表示本块没有结构数据，调用方走原来的纯文本渲染（老文档逐字节同构）。
 */
export function buildBlockUnits(block: PreviewBlock, nodes: StructureNode[]): StructureUnit[] {
  const ranged = nodes
    .filter((n) => n.start != null && n.end != null
      && n.end >= block.charStart && n.start <= block.charEnd)
    .sort((a, b) => (a.start! - b.start!) || (a.no - b.no))
  const units: StructureUnit[] = []
  let i = 0
  while (i < ranged.length) {
    const node = ranged[i]
    if (node.kind === 'LIST_ITEM') {
      const items: (BaseSegment & { ordinal: number | null })[] = []
      while (i < ranged.length && ranged[i].kind === 'LIST_ITEM') {
        items.push({ ...baseOf(ranged[i], block), ordinal: ranged[i].ordinal ?? null })
        i += 1
      }
      units.push({ type: 'list', items })
    } else if (node.kind === 'TABLE_CELL') {
      const cells: (BaseSegment & { row: number; col: number })[] = []
      while (i < ranged.length && ranged[i].kind === 'TABLE_CELL') {
        const cell = ranged[i]
        cells.push({ ...baseOf(cell, block), row: cell.row ?? 0, col: cell.col ?? 0 })
        i += 1
      }
      units.push({ type: 'table', rows: groupRows(cells) })
    } else if (node.kind === 'PARAGRAPH' && looksLikeCode(blockSlice(block, node))) {
      // 结构层把代码段落标成了正文：把连续代码行合并成一个代码块，避免一行一个 <pre>
      let last = i
      while (last + 1 < ranged.length) {
        const next = ranged[last + 1]
        if (next.kind !== 'PARAGRAPH' || !looksLikeCode(blockSlice(block, next))) break
        last += 1
      }
      const absStart = Math.max(node.start!, block.charStart)
      const absEnd = Math.min(ranged[last].end!, block.charEnd)
      const localStart = absStart - block.charStart
      const localEnd = absEnd - block.charStart
      const text = block.text.slice(localStart, localEnd + 1)
      units.push({ type: 'code', lang: detectCodeLang(text) ?? 'java', nodeNo: node.no, text, localStart, localEnd })
      i = last + 1
      continue
    } else if (node.kind === 'HEADING') {
      units.push({ type: 'heading', level: node.level ?? 1, ...baseOf(node, block) })
      i += 1
    } else if (node.kind === 'CODE') {
      units.push({ type: 'code', lang: node.lang ?? null, ...baseOf(node, block) })
      i += 1
    } else {
      units.push({ type: 'paragraph', ...baseOf(node, block) })
      i += 1
    }
  }
  return units
}

/** 节点 ∩ 块：返回块内局部区间与对应文本切片。 */
function baseOf(node: StructureNode, block: PreviewBlock): BaseSegment {
  const absStart = Math.max(node.start!, block.charStart)
  const absEnd = Math.min(node.end!, block.charEnd)
  const localStart = absStart - block.charStart
  const localEnd = absEnd - block.charStart
  return {
    nodeNo: node.no,
    text: block.text.slice(localStart, localEnd + 1),
    localStart,
    localEnd,
  }
}

/**
 * 预览结构 → 可编辑块。标记型标题（文字不在流里）补成 H1/H2；表格/列表暂按正文块拍平
 * （P1 只编辑标题/正文/代码块；表格编辑留后续）。
 */
export function toEditableBlocks(blocks: PreviewBlock[], nodes: StructureNode[]): EditableBlock[] {
  const full = blocks.map((b) => b.text).join('')
  if (nodes.length === 0) {
    return groupLineBlocks(full.split('\n').filter((line) => line.trim() !== ''))
  }
  const out: EditableBlock[] = []
  const sorted = [...nodes].sort((a, b) => {
    const pa = a.start ?? a.anchor ?? 0
    const pb = b.start ?? b.anchor ?? 0
    return pa - pb || a.no - b.no
  })
  for (let i = 0; i < sorted.length; i++) {
    const node = sorted[i]
    if (node.start == null) {
      if (node.kind === 'IMAGE' && node.imageId) {
        out.push({ type: 'IMAGE', text: '', imageId: node.imageId })
        continue
      }
      const text = node.text?.trim()
      if (text) out.push({ type: (node.level ?? 1) <= 1 ? 'H1' : 'H2', text })
      continue
    }
    const start = Math.max(0, Math.min(node.start, full.length))
    const end = Math.max(start - 1, Math.min(node.end ?? start, full.length - 1))
    const text = end < start ? '' : full.slice(start, end + 1)
    if (text.trim() === '') continue
    if (node.kind === 'CODE') {
      out.push({ type: 'CODE', text, lang: normalizeLang(node.lang) })
    } else if (node.kind === 'PARAGRAPH' && looksLikeCode(text)) {
      // 连续「像代码」的正文段合并为一个代码块（结构层未识别代码样式时的兜底）
      let last = i
      while (last + 1 < sorted.length) {
        const next = sorted[last + 1]
        if (next.start == null || next.kind !== 'PARAGRAPH') break
        const nextStart = Math.max(0, Math.min(next.start, full.length))
        const nextEnd = Math.max(nextStart - 1, Math.min(next.end ?? nextStart, full.length - 1))
        if (!looksLikeCode(full.slice(nextStart, nextEnd + 1))) break
        last += 1
      }
      const lastEnd = Math.max(start - 1, Math.min(sorted[last].end ?? start, full.length - 1))
      const codeText = full.slice(start, lastEnd + 1)
      out.push({ type: 'CODE', text: codeText, lang: normalizeLang(detectCodeLang(codeText) ?? 'java') })
      i = last
    } else if (node.kind === 'HEADING') {
      out.push({ type: (node.level ?? 1) <= 1 ? 'H1' : 'H2', text })
    } else {
      out.push({ type: 'BODY', text })
    }
  }
  return out
}

/** 无结构层兜底：按行还原正文，并把连续的代码行合并为代码块。 */
function groupLineBlocks(lines: string[]): EditableBlock[] {
  const out: EditableBlock[] = []
  let i = 0
  while (i < lines.length) {
    if (looksLikeCode(lines[i])) {
      let last = i
      while (last + 1 < lines.length && looksLikeCode(lines[last + 1])) last += 1
      const text = lines.slice(i, last + 1).join('\n')
      out.push({ type: 'CODE', text, lang: normalizeLang(detectCodeLang(text) ?? 'java') })
      i = last + 1
    } else {
      out.push({ type: 'BODY', text: lines[i] })
      i += 1
    }
  }
  return out
}

function groupRows(cells: (BaseSegment & { row: number; col: number })[]) {
  const rows: { cells: BaseSegment[] }[] = []
  const byRow = new Map<number, (BaseSegment & { col: number })[]>()
  for (const cell of cells) {
    const list = byRow.get(cell.row) ?? []
    list.push(cell)
    byRow.set(cell.row, list)
  }
  for (const list of byRow.values()) {
    rows.push({ cells: list.sort((a, b) => a.col - b.col) })
  }
  return rows
}