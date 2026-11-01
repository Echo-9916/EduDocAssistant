import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import AccessRequest from './AccessRequest'
import AccountRestricted from './AccountRestricted'
import { post } from '../api/http'

vi.mock('../api/http', () => ({ post: vi.fn() }))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/access-request']}>
      <Routes>
        <Route path="/access-request" element={<AccessRequest />} />
        <Route path="/login" element={<div>登录页</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('AccessRequest', () => {
  beforeEach(() => vi.resetAllMocks())

  it('渲染期望权限范围、申请原因与提交按钮', () => {
    renderPage()

    expect(screen.getByText('期望权限范围')).toBeTruthy()
    expect(screen.getByText('申请原因')).toBeTruthy()
    expect(screen.getByRole('button', { name: '提交申请' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '返回登录' })).toBeTruthy()
  })

  it('提交对接占位接口并提示占位文案', async () => {
    vi.mocked(post).mockResolvedValue(null)
    renderPage()

    fireEvent.change(screen.getByPlaceholderText('请说明申请临时访问权限的原因'), {
      target: { value: '需要临时查阅资料' },
    })
    fireEvent.click(screen.getByRole('button', { name: '提交申请' }))

    expect(await screen.findByText('该功能正在开发中')).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/auth/access-request', expect.objectContaining({
      scope: 'DOCUMENT_READ',
      reason: '需要临时查阅资料',
    }))
  })

  it('账号受限页申请入口可达', () => {
    render(
      <MemoryRouter initialEntries={['/account-restricted']}>
        <Routes>
          <Route path="/account-restricted" element={<AccountRestricted />} />
          <Route path="/access-request" element={<AccessRequest />} />
        </Routes>
      </MemoryRouter>
    )

    fireEvent.click(screen.getByRole('button', { name: '申请临时访问权限' }))

    expect(screen.getByRole('button', { name: '提交申请' })).toBeTruthy()
  })
})
