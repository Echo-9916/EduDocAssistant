import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))

import TeacherWorkbench from './Workbench'

describe('教学工作台（原型教师 01 屏）', () => {
  it('渲染统计卡、优先处理列表与最近动态', async () => {
    render(<TeacherWorkbench />)
    expect(await screen.findByText('教学工作台')).toBeTruthy()
    expect(await screen.findByText('本周临近截止作业')).toBeTruthy()
    expect(screen.getByText('待批改提交')).toBeTruthy()
    expect(screen.getByText('格式异常提交')).toBeTruthy()
    expect(screen.getByText('进行中协作文档')).toBeTruthy()
    // 优先处理：按截止时间升序，首行为已截止的 Web开发课程设计报告
    expect(await screen.findByText('Web开发课程设计报告 · Web开发技术')).toBeTruthy()
    expect(screen.getByText('已截止')).toBeTruthy()
    // 最近动态时间线
    expect(screen.getByText(/王老师 发布《编译原理》课程模板 v2/)).toBeTruthy()
  })

  it('快捷入口「上传教学文档」直达教师文档页', async () => {
    render(<TeacherWorkbench />)
    const cards = await screen.findAllByText('上传教学文档')
    fireEvent.click(cards[0].closest('.ant-card')!.querySelector('button')!)
    expect(navigateMock).toHaveBeenCalledWith('/teacher/documents')
  })
})
