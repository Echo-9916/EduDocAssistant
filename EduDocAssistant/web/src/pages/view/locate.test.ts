// 1108 复审 P2-5/P2-8：段-块映射与页退化机制单测
import { describe, expect, it, vi } from 'vitest'
import { AVG_PARA_CHARS, paragraphRange, scrollToPageMarker, locateByParagraph } from './locate'

const mk = (chunkNo: number, charStart: number, text: string) => ({
  chunkId: `B${chunkNo}`, chunkNo, headingPath: null, pageNo: 1 + Math.floor(charStart / 2000),
  charStart, charEnd: charStart + text.length, text,
})
const blocks = [mk(1, 0, 'a'.repeat(AVG_PARA_CHARS * 3)), mk(2, AVG_PARA_CHARS * 3, 'b'.repeat(AVG_PARA_CHARS * 3))]

describe('paragraphRange', () => {
  it('段起点+段内区间映射进块', () => {
    const r = paragraphRange(blocks, { paragraphIndex: 1, charStart: 5, charEnd: 45 })
    expect(r).toEqual({ start: AVG_PARA_CHARS + 5, end: AVG_PARA_CHARS + 45, chunkNo: 1 })
  })
  it('段数估计越界或锚点缺失返回 null（触发页退化）', () => {
    expect(paragraphRange(blocks, { paragraphIndex: 500, charStart: 0, charEnd: 10 })).toBeNull()
  })
})

describe('locateByParagraph / scrollToPageMarker', () => {
  it('映射成功：滚动锚块并回调 highlightRange', () => {
    const el = { scrollIntoView: vi.fn() }
    vi.spyOn(document, 'getElementById').mockImplementation((id) =>
      (id === 'block-1' ? el : null) as unknown as HTMLElement)
    const onHighlight = vi.fn()
    expect(locateByParagraph(blocks, { paragraphIndex: 1, charStart: 0, charEnd: 10 }, onHighlight)).toBe(true)
    expect(el.scrollIntoView).toHaveBeenCalled()
    expect(onHighlight).toHaveBeenCalledWith(AVG_PARA_CHARS, AVG_PARA_CHARS + 10)
  })
  it('退化：跳至估计页码的页分隔锚点', () => {
    const hit = { scrollIntoView: vi.fn() }
    vi.spyOn(document, 'getElementById').mockImplementation((id) =>
      (id.startsWith('page-anchor-') ? hit : null) as unknown as HTMLElement)
    expect(scrollToPageMarker({ paragraphIndex: 500, charStart: 0, charEnd: 5 })).toBe(true)
    expect(hit.scrollIntoView).toHaveBeenCalled()
  })
  it('页锚也不存在 → false（调用方仅提示）', () => {
    vi.spyOn(document, 'getElementById').mockReturnValue(null)
    expect(scrollToPageMarker({ paragraphIndex: 999, charStart: 0, charEnd: 0 })).toBe(false)
  })
  it('第 1 页无页分隔锚 → 退化滚至首个块（新一轮 P3-1）', () => {
    const first = { scrollIntoView: vi.fn() }
    vi.spyOn(document, 'getElementById').mockImplementation((id) =>
      (id === 'block-1' ? first : null) as unknown as HTMLElement)
    expect(scrollToPageMarker({ paragraphIndex: 0, charStart: 0, charEnd: 5 })).toBe(true)
    expect(first.scrollIntoView).toHaveBeenCalled()
  })
})
