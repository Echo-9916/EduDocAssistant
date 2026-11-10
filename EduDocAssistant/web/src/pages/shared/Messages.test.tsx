import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
let role = 'STUDENT'
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles: [role], permissions: ['DOCUMENT_READ'] } as never }),
}))
vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/messages', () => ({
  messageTypeLabel: {
    COURSE: '课程组更新', SUBMISSION: '提交提醒', FEEDBACK: '教师反馈', FORMAT: '格式异常',
    PERMISSION: '权限变更', ALERT: '系统告警', APPROVAL: '审批通知', SYSTEM: '系统通知',
  },
  listMessages: vi.fn(),
  unreadCount: vi.fn(),
  markRead: vi.fn(),
  readAll: vi.fn(),
}))

import { listMessages, markRead, readAll, unreadCount } from '../../api/messages'
import type { MessageView } from '../../api/messages'
import Messages from './Messages'

const MSG: MessageView = {
  id: 'M1', type: 'FEEDBACK', title: '《Web开发课程设计报告》批改反馈',
  body: '参考文献需按 GB/T 7714 补齐卷期号，请在本周五前重新提交。',
  resourceType: 'ASSIGNMENT', resourceRef: 'A1', actorName: '李老师',
  createdAt: '2026-11-10T01:00:00Z', read: false,
}

const page = (items: MessageView[]) => ({ items, total: items.length, page: 1, pageSize: 10 })

describe('消息中心（学生 26 / 教师 26 / 运维 25 屏，真实端点）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    role = 'STUDENT'
    vi.mocked(listMessages).mockResolvedValue(page([MSG]))
    vi.mocked(unreadCount).mockResolvedValue(1)
    vi.mocked(markRead).mockResolvedValue({ updated: 1 })
    vi.mocked(readAll).mockResolvedValue({ updated: 1 })
  })

  it('渲染真实消息行，未读筛选把 unread=true 传后端', async () => {
    render(<Messages />)
    expect(await screen.findByText('消息中心')).toBeTruthy()
    expect(await screen.findByText('《Web开发课程设计报告》批改反馈')).toBeTruthy()
    // 首次加载不带 unread；未读数来自 /messages/unread-count
    expect(listMessages).toHaveBeenCalledWith(expect.objectContaining({ unread: undefined, page: 1 }))
    expect(unreadCount).toHaveBeenCalled()
    expect(screen.getByText('未读 1')).toBeTruthy()

    fireEvent.click(screen.getByText(/^未读/))
    expect(listMessages).toHaveBeenLastCalledWith(expect.objectContaining({ unread: true, page: 1 }))
  })

  it('标记已读打到 PATCH messages:read，全部已读打到 readAll，前往处理按 resourceType 映射导航', async () => {
    render(<Messages />)
    fireEvent.click(await screen.findByText('标记已读'))
    expect(markRead).toHaveBeenCalledWith(['M1'], true)

    fireEvent.click(screen.getByText('全部已读'))
    expect(readAll).toHaveBeenCalled()

    // ASSIGNMENT → 学生端 courses
    fireEvent.click(screen.getByText('前往处理'))
    expect(navigateMock).toHaveBeenCalledWith('/student/courses')
  })

  it('运维端标题「消息与告警」并如实标注告警/审批类随切片 4 产生', async () => {
    role = 'OPS'
    render(<Messages />)
    expect(await screen.findByText('消息与告警')).toBeTruthy()
    expect(screen.getByText('告警与审批类消息随切片 4 产生')).toBeTruthy()
  })
})
