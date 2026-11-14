import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/shares', () => ({ getPublicShare: vi.fn() }))

import { getPublicShare } from '../api/shares'
import PublicShare from './PublicShare'

function view(token = 'tok') {
  return (
    <MemoryRouter initialEntries={[`/s/${token}`]}>
      <Routes>
        <Route path="/s/:token" element={<PublicShare />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('公开共享只读页 /s/:token', () => {
  afterEach(() => vi.clearAllMocks())

  it('免登录展示标题、权限与当前版本文本', async () => {
    vi.mocked(getPublicShare).mockResolvedValue({
      title: '课程设计报告', versionNo: 2, permission: 'VIEW',
      text: '一、项目背景\n本项目基于 React 与 Spring Boot。',
    })
    render(view('abc'))
    expect(await screen.findByText('课程设计报告')).toBeTruthy()
    expect(screen.getByText('只读')).toBeTruthy()
    expect(screen.getByText('v2')).toBeTruthy()
    expect(screen.getByText(/本项目基于 React 与 Spring Boot/)).toBeTruthy()
    expect(getPublicShare).toHaveBeenCalledWith('abc')
  })

  it('过期或撤销时给明确失效提示', async () => {
    vi.mocked(getPublicShare).mockRejectedValue(new Error('共享链接过期或撤销'))
    render(view('old'))
    expect(await screen.findByText('共享链接不可用')).toBeTruthy()
    expect(screen.getByText('共享链接过期或撤销')).toBeTruthy()
  })
})