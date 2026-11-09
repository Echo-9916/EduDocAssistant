import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }))
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles: ['OPS'], permissions: [] } as never }),
}))

import OpsStorage from './Storage'

describe('运维存储与资源（21/22/23/24 屏门面）', () => {
  it('存储与回收站渲染容量总览与对象列表', async () => {
    render(<OpsStorage />)
    expect(await screen.findByText('存储与资源')).toBeTruthy()
    expect(screen.getByText('团队存储配额')).toBeTruthy()
    expect(await screen.findByText(/已用 1.36 TB \/ 配额 2.00 TB/)).toBeTruthy()
    expect(screen.getByText('回收站容量')).toBeTruthy()
    expect(screen.getByText('过期对象数')).toBeTruthy()
    // 屏 21：对象行 + 状态 Tag + 回收站恢复入口
    expect(await screen.findByText('旧版课程设计模板.docx')).toBeTruthy()
    expect(screen.getAllByText('回收站').length).toBeGreaterThan(0)
    expect(screen.getAllByText('恢复').length).toBeGreaterThan(0)
  })

  it('清理确认后对象永久删除并提示释放容量（屏 22）', async () => {
    render(<OpsStorage />)
    fireEvent.click((await screen.findAllByText('清理'))[0])
    expect(await screen.findByText('永久删除后不可恢复')).toBeTruthy()
    expect(screen.getByText('恢复窗口')).toBeTruthy()
    fireEvent.click(screen.getByText('确认清理'))
    await waitFor(() => expect(screen.getByText(/已永久清理，释放 42 MB/)).toBeTruthy())
  })

  it('系统服务状态列出可用性，异常服务可查看积压任务（屏 23）', async () => {
    render(<OpsStorage />)
    fireEvent.click(await screen.findByText('系统服务状态'))
    expect(await screen.findByText('身份认证')).toBeTruthy()
    expect(screen.getAllByText('中断').length).toBeGreaterThan(0)
    expect(screen.getByText('8.4 s')).toBeTruthy()

    fireEvent.click(screen.getAllByText('查看积压任务')[0])
    expect(await screen.findByText(/47 条解析任务积压/)).toBeTruthy()
  })

  it('配置变更记录支持就地回滚（屏 24）', async () => {
    render(<OpsStorage />)
    fireEvent.click(await screen.findByText('配置变更记录'))
    expect(await screen.findByText('文档解析并发上限调整')).toBeTruthy()
    fireEvent.click(screen.getAllByText('回滚')[0])
    fireEvent.click(await screen.findByText('确认回滚'))
    await waitFor(() => expect(screen.getByText(/已回滚至：并发上限 8/)).toBeTruthy())
  })
})
