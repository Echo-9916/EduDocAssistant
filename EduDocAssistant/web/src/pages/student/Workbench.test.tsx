import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))

import StudentWorkbench from './Workbench'

describe('学生工作台快捷入口（1107 P2-4）', () => {
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
})
