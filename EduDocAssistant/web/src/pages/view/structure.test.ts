import { describe, expect, it } from 'vitest'
import { buildBlockUnits, looksLikeCode, markerHeadings, toEditableBlocks } from './structure'
import type { PreviewBlock, StructureNode } from '../../api/ai'

const block: PreviewBlock = {
  chunkId: 'C1', chunkNo: 1, headingPath: null, pageNo: null, charStart: 0, charEnd: 8, text: 'A\nB\nC\nD\nE',
}

const nodes: StructureNode[] = [
  { no: 1, kind: 'HEADING', level: 2, text: '第一章', anchor: 0 },
  { no: 2, kind: 'PARAGRAPH', start: 0, end: 0 },
  { no: 3, kind: 'LIST_ITEM', ordinal: 1, start: 2, end: 2 },
  { no: 4, kind: 'LIST_ITEM', ordinal: 2, start: 4, end: 4 },
  { no: 5, kind: 'TABLE_CELL', tableNo: 1, row: 1, col: 1, start: 6, end: 6 },
  { no: 6, kind: 'TABLE_CELL', tableNo: 1, row: 1, col: 2, start: 8, end: 8 },
]

describe('structure 渲染段', () => {
  it('标记标题落在 anchor 所在块', () => {
    expect(markerHeadings(block, nodes)).toEqual([{ nodeNo: 1, level: 2, text: '第一章' }])
  })

  it('连续 LIST_ITEM 合成列表、连续 TABLE_CELL 按行合成表格', () => {
    const units = buildBlockUnits(block, nodes)
    expect(units.map((u) => u.type)).toEqual(['paragraph', 'list', 'table'])
    const list = units[1]
    if (list.type !== 'list') throw new Error('expected list')
    expect(list.items.map((i) => i.text)).toEqual(['B', 'C'])
    expect(list.items.map((i) => i.ordinal)).toEqual([1, 2])
    const table = units[2]
    if (table.type !== 'table') throw new Error('expected table')
    expect(table.rows).toHaveLength(1)
    expect(table.rows[0].cells.map((c) => c.text)).toEqual(['D', 'E'])
  })

  it('节点跨块时只裁剪本块交集', () => {
    const next: PreviewBlock = {
      chunkId: 'C2', chunkNo: 2, headingPath: null, pageNo: null, charStart: 9, charEnd: 13, text: '\nFG\nH',
    }
    const spanning: StructureNode[] = [{ no: 9, kind: 'PARAGRAPH', start: 8, end: 12 }]
    const units = buildBlockUnits(next, spanning)
    const unit = units[0]
    if (unit.type !== 'paragraph') throw new Error('expected paragraph')
    // 节点起点落在上一块，交集从本块的 charStart 开始（含块间连接符位）
    expect(unit.text).toBe('\nFG\n')
    expect(unit.localStart).toBe(0)
    expect(unit.localEnd).toBe(3)
  })

  it('无结构数据返回空数组（调用方回退纯文本）', () => {
    expect(buildBlockUnits(block, [])).toEqual([])
    expect(markerHeadings(block, [])).toEqual([])
  })

  it('预览结构还原为可编辑块', () => {
    const simple: PreviewBlock = {
      chunkId: 'C1', chunkNo: 1, headingPath: null, pageNo: null, charStart: 0, charEnd: 4, text: 'H\nB\nC',
    }
    const simpleNodes: StructureNode[] = [
      { no: 1, kind: 'HEADING', level: 1, start: 0, end: 0 },
      { no: 2, kind: 'PARAGRAPH', start: 2, end: 2 },
      { no: 3, kind: 'CODE', start: 4, end: 4 },
    ]
    expect(toEditableBlocks([simple], simpleNodes)).toEqual([
      { type: 'H1', text: 'H' },
      { type: 'BODY', text: 'B' },
      { type: 'CODE', text: 'C', lang: 'java' },
    ])
  })

  it('正文节点里的连续代码行合并为 Java 代码块（预览与编辑同口径）', () => {
    const lines = [
      'public interface Shape<T extends Number> {',
      '    T area();',
      '}',
      '定义泛型接口 Shape<T extends Number>，声明 area() 方法。',
    ]
    let offset = 0
    const codeNodes: StructureNode[] = lines.map((line, i) => {
      const start = offset
      offset += line.length + 1
      return { no: i + 1, kind: 'PARAGRAPH', start, end: start + line.length - 1 }
    })
    const text = lines.join('\n')
    const codeBlock: PreviewBlock = {
      chunkId: 'C1', chunkNo: 1, headingPath: null, pageNo: null,
      charStart: 0, charEnd: text.length - 1, text,
    }

    expect(looksLikeCode(lines[0])).toBe(true)
    expect(looksLikeCode(lines[2])).toBe(true)
    expect(looksLikeCode(lines[3])).toBe(false)

    const units = buildBlockUnits(codeBlock, codeNodes)
    expect(units.map((u) => u.type)).toEqual(['code', 'paragraph'])
    const code = units[0]
    if (code.type !== 'code') throw new Error('expected code')
    expect(code.lang).toBe('java')
    expect(code.text).toBe(lines.slice(0, 3).join('\n'))

    expect(toEditableBlocks([codeBlock], codeNodes)).toEqual([
      { type: 'CODE', text: lines.slice(0, 3).join('\n'), lang: 'java' },
      { type: 'BODY', text: lines[3] },
    ])
  })

  it('无结构时按行还原正文块', () => {
    const plain: PreviewBlock = {
      chunkId: 'C1', chunkNo: 1, headingPath: null, pageNo: null, charStart: 0, charEnd: 6, text: 'A\n\nB\nC',
    }
    expect(toEditableBlocks([plain], [])).toEqual([
      { type: 'BODY', text: 'A' },
      { type: 'BODY', text: 'B' },
      { type: 'BODY', text: 'C' },
    ])
  })
})