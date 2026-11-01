import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import ForgotPassword from './ForgotPassword'
import { post } from '../api/http'

vi.mock('../api/http', () => ({ post: vi.fn() }))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/forgot-password']}>
      <Routes>
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/login" element={<div>登录页</div>} />
      </Routes>
    </MemoryRouter>
  )
}

function fillValid() {
  fireEvent.change(screen.getByPlaceholderText('验证码'), { target: { value: '123456' } })
  fireEvent.change(screen.getByPlaceholderText('新密码'), { target: { value: 'Abcd1234' } })
  fireEvent.change(screen.getByPlaceholderText('确认新密码'), { target: { value: 'Abcd1234' } })
}

describe('ForgotPassword', () => {
  beforeEach(() => vi.resetAllMocks())

  it('渲染验证方式、验证码、新密码、确认新密码与重置按钮', () => {
    renderPage()

    expect(screen.getByText('验证方式')).toBeTruthy()
    expect(screen.getByPlaceholderText('验证码')).toBeTruthy()
    expect(screen.getByPlaceholderText('新密码')).toBeTruthy()
    expect(screen.getByPlaceholderText('确认新密码')).toBeTruthy()
    expect(screen.getByRole('button', { name: '重置密码' })).toBeTruthy()
    expect(screen.getByRole('link', { name: '返回登录' })).toBeTruthy()
  })

  it('两次密码不一致时校验失败', async () => {
    renderPage()

    fireEvent.change(screen.getByPlaceholderText('验证码'), { target: { value: '123456' } })
    fireEvent.change(screen.getByPlaceholderText('新密码'), { target: { value: 'Abcd1234' } })
    fireEvent.change(screen.getByPlaceholderText('确认新密码'), { target: { value: 'Abcd9999' } })
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }))

    expect(await screen.findByText('两次输入的密码不一致')).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it('提交对接占位接口并提示占位文案', async () => {
    vi.mocked(post).mockResolvedValue(null)
    renderPage()

    fillValid()
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }))

    expect(await screen.findByText('该功能正在开发中')).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/auth/reset-password', expect.objectContaining({
      method: 'email',
      code: '123456',
      newPassword: 'Abcd1234',
      confirmPassword: 'Abcd1234',
    }))
  })
})
