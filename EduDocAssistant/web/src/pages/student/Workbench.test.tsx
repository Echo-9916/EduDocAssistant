import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
vi.mock('../../api/studentWorkbench', () => ({
  studentWorkbenchSummary: vi.fn().mockResolvedValue({
    documentTotal: 7,
    pendingAssignmentTotal: 2,
    unreadMessageTotal: 3,
    activeCollaborationTotal: 1,
    todos: [
      {
        assignmentId: 'A1', courseName: 'Web开发技术', title: '设计报告',
        deadline: '2026-11-20T18:00:00+08:00', remainText: '剩余 5 天', status: 'PENDING',
      },
      {
        assignmentId: 'A2', courseName: '数据库原理', title: '索引实验',
        deadline: '2026-11-16T18:00:00+08:00', remainText: '已逾期 1 天', status: 'OVERDUE',
      },
    ],
    recentDocuments: [],
  }),
}))
let perms: string[] = ['DOCUMENT_READ', 'FORMAT_CHECK', 'TEMPLATE_READ']
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { permissions: perms } as never, login: vi.fn(), logout: vi.fn(), reload: vi.fn() }),
}))

import StudentWorkbench from './Workbench'

describe('学生工作台（1107 P2-4）', () => {
  beforeEach(() => {
    perms = ['DOCUMENT_READ', 'FORMAT_CHECK', 'TEMPLATE_READ']
  })
  it('设计 05 四入口齐备且不出现教师/运维能力', async () => {
    render(<StudentWorkbench />)
    expect(await screen.findByText('上传文档')).toBeTruthy()
    expect(screen.getByText('按模板新建')).toBeTruthy()
    expect(screen.getByText('查看作业')).toBeTruthy()
    expect(screen.getByText('教学消息')).toBeTruthy()
    expect(screen.queryByText('模板管理')).toBeNull()
    expect(screen.queryByText('审计')).toBeNull()
  })

  it('「按模板新建」直达格式与模板页', async () => {
    render(<StudentWorkbench />)
    const cards = screen.getAllByText('按模板新建')
    const card = cards[0].closest('.ant-card')!
    fireEvent.click(card.querySelector('button')!)
    expect(navigateMock).toHaveBeenCalledWith('/student/format')
  })

  it('查看作业入口直达课程与作业页（门面页已上线，不再按权限禁用）', async () => {
    render(<StudentWorkbench />)
    const cards = screen.getAllByText('查看作业')
    fireEvent.click(cards[0].closest('.ant-card')!.querySelector('button')!)
    expect(navigateMock).toHaveBeenCalledWith('/student/courses')
    fireEvent.click(screen.getByText('上传文档').closest('.ant-card')!.querySelector('button')!)
    expect(navigateMock).toHaveBeenCalledWith('/student/documents')
  })

  it('四个统计卡与待办栏取聚合接口真实数据', async () => {
    render(<StudentWorkbench />)
    expect(await screen.findByText('我的文档')).toBeTruthy()
    expect(await screen.findByText('7')).toBeTruthy()
    expect(screen.getByText('2')).toBeTruthy()
    expect(screen.getByText('3')).toBeTruthy()
    expect(screen.getByText('1')).toBeTruthy()
    expect(await screen.findByText('设计报告 · Web开发技术')).toBeTruthy()
    expect(screen.getByText('剩余 5 天')).toBeTruthy()
    expect(screen.getByText('已逾期 1 天')).toBeTruthy()
  })
})