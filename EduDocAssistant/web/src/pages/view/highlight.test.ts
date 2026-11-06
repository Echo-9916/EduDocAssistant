import { describe, expect, it } from 'vitest'
import { sliceMarks, splitAcrossBlocks, toLocalRange } from './highlight'
import type { PreviewBlock } from '../../api/ai'

const block = (no: number, start: number, end: number): PreviewBlock => ({
  chunkId: `C${no}`, chunkNo: no, headingPath: null, pageNo: 1,
  charStart: start, charEnd: end, text: 'x'.repeat(end - start + 1),
})

describe('highlight 原语（C-01 闭区间口径）', () => {
  const blocks = [block(1, 0, 4), block(2, 5, 10), block(3, 11, 16)]

  it('块内区间映射为局部偏移', () => {
    expect(toLocalRange(blocks[1], 6, 8)).toEqual({ start: 1, end: 3 })
  })

  it('越界截断与无交集', () => {
    expect(toLocalRange(blocks[0], -3, 20)).toEqual({ start: 0, end: 4 })
    expect(toLocalRange(blocks[0], 5, 9)).toBeNull() // 5 属块 2
  })

  it('跨块引用分段（连接符归后块 → 块1 尾不含 5，块2 从 5 起）', () => {
    const parts = splitAcrossBlocks(blocks, 3, 12)
    expect(parts.map((p) => [p.block.chunkId, p.range])).toEqual([
      ['C1', { start: 3, end: 4 }],
      ['C2', { start: 0, end: 5 }],
      ['C3', { start: 0, end: 1 }],
    ])
  })

  it('sliceMarks 生成 mark 片段并合并相邻/重叠区间', () => {
    expect(sliceMarks('abcdef', [{ start: 1, end: 2 }])).toEqual([['a', false], ['bc', true], ['def', false]])
    expect(sliceMarks('abcdef', [{ start: 0, end: 1 }, { start: 2, end: 3 }])).toEqual([['abcd', true], ['ef', false]])
    expect(sliceMarks('abcdef', [{ start: 0, end: 1 }, { start: 3, end: 4 }])).toEqual([['ab', true], ['c', false], ['de', true], ['f', false]])
    expect(sliceMarks('abc', [])).toEqual([['abc', false]])
  })
})
