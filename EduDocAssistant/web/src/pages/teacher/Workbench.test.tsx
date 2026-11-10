import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
// 后端实跑样例（/teacher/workbench-summary），第二条为逾期变体，覆盖 danger Tag 分支
vi.mock('../../api/coursework', () => ({
  workbenchSummary: vi.fn(() =>
    Promise.resolve({
      dueThisWeek: 1,
      pendingGrade: 0,
      formatIssues: 0,
      collabActive: 0,
      priority: [
        {
          assignmentId: 'A1',
          courseName: 'Web开发技术',
          title: 'Web开发课程设计报告',
          dueTime: '2026-11-15T23:59:59+08:00',
          expected: 42,
          submitted: 37,
          graded: 37,
          remainText: '5 天后截止',
        },
        {
          assignmentId: 'A2',
          courseName: '编译原理',
          title: '词法分析实验',
          dueTime: '2026-11-05T23:59:59+08:00',
          expected: 40,
          submitted: 31,
          graded: 31,
          remainText: '已逾期 3 天',
        },
      ],
    }),
  ),
}))

import TeacherWorkbench from './Workbench'

const statValue = (title: string) =>
  screen.getByText(title).closest('.ant-statistic')!.querySelector('.ant-statistic-content-value')!.textContent

describe('教学工作台（原型教师 01 屏，已接 /teacher/workbench-summary）', () => {
  it('统计卡直接用后端四个数，协作卡带切片 3 说明', async () => {
    render(<TeacherWorkbench />)
    // 等接口数据落地（priority 行与统计卡同一次 setData）
    expect(await screen.findByText('Web开发课程设计报告 · Web开发技术')).toBeTruthy()
    expect(statValue('本周临近截止作业')).toBe('1')
    expect(statValue('待批改提交')).toBe('0')
    expect(statValue('格式异常提交')).toBe('0')
    expect(statValue('进行中协作文档')).toBe('0')
    // collabActive 恒为 0：Tooltip 说明来源，不当假数字也不当缺失
    fireEvent.mouseEnter(screen.getByText('进行中协作文档').closest('.ant-card')!)
    expect(await screen.findByText('协作统计随切片 3 接入')).toBeTruthy()
  })

  it('优先处理渲染 priority 行，逾期项用 danger Tag，点行去课程与作业', async () => {
    render(<TeacherWorkbench />)
    expect(await screen.findByText('Web开发课程设计报告 · Web开发技术')).toBeTruthy()
    expect(screen.getByText('截止 2026-11-15 23:59')).toBeTruthy()
    expect(screen.getByText('5 天后截止')).toBeTruthy()
    expect(screen.getByText('已提交 37/42')).toBeTruthy()
    expect(screen.getByText('已提交 31/40')).toBeTruthy()
    expect(screen.getByText('已逾期 3 天').closest('.ant-tag')!.className).toContain('ant-tag-error')
    fireEvent.click(screen.getByText('词法分析实验 · 编译原理'))
    expect(navigateMock).toHaveBeenCalledWith('/teacher/courses')
  })

  it('最近动态区已删除（消息投递随切片 2，页面无空洞）', async () => {
    render(<TeacherWorkbench />)
    expect(await screen.findByText('优先处理')).toBeTruthy()
    expect(screen.getByText('快捷入口')).toBeTruthy()
    expect(screen.queryByText('最近动态')).toBeNull()
  })

  it('快捷入口「上传教学文档」直达教师文档页', async () => {
    render(<TeacherWorkbench />)
    const cards = await screen.findAllByText('上传教学文档')
    fireEvent.click(cards[0].closest('.ant-card')!.querySelector('button')!)
    expect(navigateMock).toHaveBeenCalledWith('/teacher/documents')
  })
})
