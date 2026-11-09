import { describe, expect, it } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

import TeacherCourses from './Courses'

describe('教师课程与作业（原型 20–25 屏）', () => {
  it('渲染课程管理表，切到作业统计后「提醒未提交学生」弹出名单', async () => {
    render(<TeacherCourses />)
    expect(await screen.findByText('课程与作业')).toBeTruthy()
    // 屏 20：课程表有真实行（Web开发技术 2026-2027 秋 + 2025-2026 春归档行）
    expect((await screen.findAllByText('Web开发技术')).length).toBeGreaterThanOrEqual(2)
    expect(screen.getAllByText('李老师').length).toBeGreaterThan(0)
    expect(screen.getByText('数据库实验报告模板 v2')).toBeTruthy()

    // 交互：切到「作业与提交统计」Tab（屏 23）
    fireEvent.click(screen.getByText('作业与提交统计'))
    expect(await screen.findByText('作业3 索引设计实验')).toBeTruthy()

    // 行操作：提醒未提交学生（首行 Web开发课程设计报告 → 刘同学）
    fireEvent.click(screen.getAllByText('提醒未提交学生')[0])
    expect(await screen.findByText('刘同学（2023003）')).toBeTruthy()
    expect(screen.getByText('发送提醒')).toBeTruthy()
  })
})
