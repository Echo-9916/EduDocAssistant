import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { totalChars } from './PolishPanel'

vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
const api = vi.hoisted(() => ({
  polishParagraphs: vi.fn(),
  polishDecision: vi.fn(),
}))
vi.mock('../../api/ai', () => api)

import PolishPanel from './PolishPanel'
import type { PreviewBlock } from '../../api/ai'

const block = (no: number, start: number, text: string): PreviewBlock => ({
  chunkId: `C${no}`, chunkNo: no, headingPath: null, pageNo: 1,
  charStart: start, charEnd: start + text.length - 1, text,
})

const rows = [
  { polishId: 'P1', sourceText: '原文甲', polishedText: '润色甲', decision: 'PENDING' as const },
  { polishId: 'P2', sourceText: '原文乙', polishedText: '润色乙', decision: 'PENDING' as const },
]

describe('PolishPanel（C-13）', () => {
  beforeEach(() => vi.clearAllMocks())

  it('totalChars 按 strip 后累计（与后端预检同口径）', () => {
    expect(totalChars([block(1, 0, '  甲  '), block(2, 3, '乙')])).toBe(2)
  })

  it('超限禁点，未超限提交润色渲染左右对照', async () => {
    const big = block(1, 0, '字'.repeat(5001))
    const { rerender } = render(
      <PolishPanel documentId="D1" selectedBlocks={[big]} onHighlight={vi.fn()} allowed onChanged={vi.fn()} />)
    expect(await screen.findByText(/超出请分批/)).toBeTruthy()
    expect((screen.getByText('润色选中段落').closest('button') as HTMLButtonElement).disabled).toBe(true)

    rerender(<PolishPanel documentId="D1" selectedBlocks={[block(1, 0, '原文甲'), block(2, 4, '原文乙')]}
      onHighlight={vi.fn()} allowed onChanged={vi.fn()} />)
    api.polishParagraphs.mockResolvedValue(rows)
    fireEvent.click(screen.getByTestId('run-polish'))
    await waitFor(() => expect(api.polishParagraphs).toHaveBeenCalledWith('D1', [
      { chunkId: 'C1', text: '原文甲' }, { chunkId: 'C2', text: '原文乙' },
    ]))
    expect(await screen.findByText(/润色甲/)).toBeTruthy()
    expect(screen.getByText('原文乙')).toBeTruthy()
  })

  it('采纳带 charStart 消歧并回调 onChanged；采纳后出现撤销；撤销 REVERT', async () => {
    const onChanged = vi.fn()
    const selected = [block(1, 0, '原文甲'), block(2, 4, '原文乙')]
    api.polishParagraphs.mockResolvedValue(rows)
    api.polishDecision.mockResolvedValue({ polishId: 'P1', documentId: 'D1', versionId: 'V2', versionNo: 2, documentStatus: 'READY' })
    render(<PolishPanel documentId="D1" selectedBlocks={selected} onHighlight={vi.fn()} allowed onChanged={onChanged} />)
    fireEvent.click(screen.getByTestId('run-polish'))
    await screen.findByText('润色甲')

    fireEvent.click(screen.getByTestId('accept-P1'))
    await waitFor(() => expect(api.polishDecision).toHaveBeenCalledWith('P1',
      { decision: 'ACCEPT', acceptedText: undefined, charStart: 0 }))
    expect(onChanged).toHaveBeenCalledTimes(1)

    const undo = await screen.findByTestId('revert-P1')
    fireEvent.click(undo)
    await waitFor(() => expect(api.polishDecision).toHaveBeenCalledWith('P1',
      expect.objectContaining({ decision: 'REVERT' })))
  })

  it('放弃 REJECT 不触发 onChanged（不动版本）', async () => {
    const onChanged = vi.fn()
    api.polishParagraphs.mockResolvedValue(rows)
    api.polishDecision.mockResolvedValue(null)
    render(<PolishPanel documentId="D1" selectedBlocks={[block(1, 0, '原文甲')]}
      onHighlight={vi.fn()} allowed onChanged={onChanged} />)
    fireEvent.click(screen.getByTestId('run-polish'))
    await screen.findByText('润色甲')
    fireEvent.click(screen.getByTestId('reject-P1'))
    await waitFor(() => expect(api.polishDecision).toHaveBeenCalledWith('P1',
      { decision: 'REJECT', acceptedText: undefined, charStart: 0 }))
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('无权限提示且不渲染操作', () => {
    render(<PolishPanel documentId="D1" selectedBlocks={[block(1, 0, '甲')]}
      onHighlight={vi.fn()} allowed={false} onChanged={vi.fn()} />)
    expect(screen.getByText(/AI_POLISH/)).toBeTruthy()
    expect(screen.queryByText('润色选中段落')).toBeNull()
  })
})
