import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
let role = 'STUDENT'
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles: [role], permissions: ['DOCUMENT_READ'] } as never }),
}))

import Messages from './Messages'

describe('消息中心（学生 26 / 教师 26 / 运维 25 屏门面）', () => {
  it('学生端渲染未读消息，标记已读后从未读筛选中消失', async () => {
    role = 'STUDENT'
    render(<Messages />)
    expect(await screen.findByText('《Web开发课程设计报告》批改反馈')).toBeTruthy()

    fireEvent.click(screen.getByText(/^未读/))
    await waitFor(() => expect(screen.getAllByText('《Web开发课程设计报告》批改反馈')).toHaveLength(1))
    fireEvent.click(screen.getAllByText('标记已读')[0])
    await waitFor(() => expect(screen.queryByText('《Web开发课程设计报告》批改反馈')).toBeNull())
  })

  it('运维端标题改为「消息与告警」并出现告警类型', async () => {
    role = 'OPS'
    render(<Messages />)
    expect(await screen.findByText('解析队列积压')).toBeTruthy()
    expect(screen.getByText('消息与告警')).toBeTruthy()
  })
})
