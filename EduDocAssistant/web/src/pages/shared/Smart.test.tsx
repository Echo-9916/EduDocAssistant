import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))

import Smart from './Smart'

describe('智能处理门面页（学生 06-10 / 教师 05-08）', () => {
  it('标题、演示文档与四个能力页签渲染', async () => {
    render(<Smart role="STUDENT" />)
    expect(await screen.findByText('智能处理')).toBeTruthy()
    expect(await screen.findByText(/Web开发课程设计报告_张同学_v3/)).toBeTruthy()
    expect(screen.getByRole('tab', { name: '摘要' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '要点提取' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '文档问答' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '文本润色' })).toBeTruthy()
  })

  it('摘要长度切换后重新生成，定位原文有反馈', async () => {
    render(<Smart role="STUDENT" />)
    // 标准长度：4 组 × 2 句
    expect(await screen.findByText(/共 8 句/)).toBeTruthy()
    fireEvent.click(screen.getByText('精简'))
    expect(await screen.findByText(/共 4 句/)).toBeTruthy()
    fireEvent.click(screen.getAllByText('定位原文')[0])
    expect(await screen.findByText(/已定位到「1 摘要与目标」原文/)).toBeTruthy()
  })

  it('无依据示例问题演示拒答态（需求 5.2.3：明确无依据、不编造）', async () => {
    render(<Smart role="STUDENT" />)
    fireEvent.click(await screen.findByRole('tab', { name: '文档问答' }))
    fireEvent.click(await screen.findByRole('button', { name: /无依据示例问题/ }))
    expect(await screen.findByText('当前文档无法回答这个问题（无依据拒答）')).toBeTruthy()
    expect(screen.getByText(/不会编造文档之外的内容/)).toBeTruthy()
  })

  it('顶部提示可直达我的文档（真实摘要/问答入口）', async () => {
    render(<Smart role="STUDENT" />)
    fireEvent.click(await screen.findByText('前往我的文档'))
    expect(navigateMock).toHaveBeenCalledWith('/student/documents')
  })

  it('教师视角使用教学口径（批注、评分标准核对）', async () => {
    render(<Smart role="TEACHER" />)
    expect(await screen.findByText(/评分标准核对/)).toBeTruthy()
    expect(screen.getByText(/教学批注 2 条/)).toBeTruthy()
  })
})
