import { describe, expect, it } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'

import StudentCourses from './Courses'

describe('学生课程与作业（原型 22/23/24/25 屏）', () => {
  it('渲染作业列表，切换 Segmented 后展示下载中心记录', async () => {
    render(<StudentCourses />)
    expect(await screen.findByText('课程与作业')).toBeTruthy()
    expect(await screen.findByText('Web开发课程设计报告')).toBeTruthy()
    expect(screen.getByText('作业3 索引设计实验')).toBeTruthy()
    expect(screen.getByText('88 分')).toBeTruthy()

    // 交互：切换到「下载中心」段，导出记录可见
    fireEvent.click(screen.getByText('下载中心').closest('label')!.querySelector('input')!)
    expect(await screen.findByText('Web开发课程设计报告_张同学_v6.pdf')).toBeTruthy()
    const expired = screen.getByText('编译原理实验指导书_节选.docx').closest('tr')!
    expect(within(expired).getByText('重新导出')).toBeTruthy()
  })

  it('逾期作业标记 danger 且「提交」打开屏 25 提交弹窗（含格式预检清单）', async () => {
    render(<StudentCourses />)
    const cell = await screen.findByText('操作系统课程报告')
    const row = cell.closest('tr')!
    expect(within(row).getByText('已逾期 2 天')).toBeTruthy()
    // antd 会给两字按钮插空格（「提 交」），直接取该行唯一按钮
    const submitBtn = within(row).getByRole('button')
    expect(submitBtn.className).toContain('ant-btn-dangerous')

    fireEvent.click(submitBtn)
    expect(await screen.findByText('提交作业 · 操作系统课程报告')).toBeTruthy()
    expect(screen.getByText('格式预检清单')).toBeTruthy()
    expect(screen.getByText('确认提交')).toBeTruthy()
  })
})
