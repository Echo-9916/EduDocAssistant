import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))

import Collab from './Collab'

describe('协作与版本门面页（学生 16-21 / 教师 15-19）', () => {
  it('协作编辑：在线成员、批注与实时协同说明渲染', async () => {
    render(<Collab role="STUDENT" />)
    expect(await screen.findByText('协作与版本')).toBeTruthy()
    expect(await screen.findByText(/随协作网关上线后接入/)).toBeTruthy()
    expect(screen.getByText(/3 人在线/)).toBeTruthy()
    expect(await screen.findByText(/补充权限校验流程/)).toBeTruthy()
  })

  it('批注可标记解决（写回演示数据）', async () => {
    render(<Collab role="STUDENT" />)
    const buttons = await screen.findAllByText('标记解决')
    fireEvent.click(buttons[0])
    expect(await screen.findByText('批注已标记为已解决')).toBeTruthy()
  })

  it('版本记录：恢复生成新版本且不覆盖历史，对比展示差异', async () => {
    render(<Collab role="STUDENT" />)
    fireEvent.click(await screen.findByRole('tab', { name: '版本记录' }))
    expect(await screen.findByText('v6')).toBeTruthy()
    expect(screen.getByText('格式修复与图表引用更新')).toBeTruthy()
    // 恢复：跳过当前版本的禁用按钮，走 Popconfirm 确认
    const restore = screen
      .getAllByText('恢复')
      .map((el) => el.closest('button'))
      .find((b) => b && !b.disabled)!
    fireEvent.click(restore)
    fireEvent.click(await screen.findByText('确认恢复'))
    expect(await screen.findByText('v7')).toBeTruthy()
    expect(screen.getByText(/由 v\d 恢复生成/)).toBeTruthy()
    // 原当前版本 v6 仍在版本链中，「当前」标记转移到 v7（版本不可变）
    expect(screen.getByText('v6')).toBeTruthy()
    expect(screen.getByText('当前')).toBeTruthy()
    // 对比 Modal（v6 行）：双栏差异 + 格式规则变化
    fireEvent.click(screen.getAllByText('对比')[1])
    expect(await screen.findByText('格式规则变化')).toBeTruthy()
    expect(screen.getByText(/页边距修复为 2.54 cm/)).toBeTruthy()
  })

  it('共享设置：切换共享链接范围并添加协作者', async () => {
    render(<Collab role="STUDENT" />)
    fireEvent.click(await screen.findByRole('tab', { name: '共享设置' }))
    expect(await screen.findByText('zhang@campus.edu.cn')).toBeTruthy()
    // 范围切换到共享链接后展示链接与有效期
    fireEvent.click(screen.getByText('共享链接'))
    expect(await screen.findByText(/edu\.example\/doc\/7Q4K2M/)).toBeTruthy()
    // 添加协作者 Modal：选人选权限后写入表格
    fireEvent.click(screen.getByText('添加协作者'))
    expect(await screen.findByText('选择成员（仅课程组成员可被邀请）')).toBeTruthy()
    fireEvent.click(screen.getByText('发送邀请'))
    expect(await screen.findByText('已邀请 王老师 加入协作（可编辑）')).toBeTruthy()
    expect(screen.getByText('wang@campus.edu.cn')).toBeTruthy()
  })
})
