import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({
  get: vi.fn(() => Promise.resolve({})),
  put: vi.fn(() => Promise.resolve({})),
  post: vi.fn(() => Promise.resolve(undefined)),
  del: vi.fn(() => Promise.resolve(undefined)),
}))

import { del, get, post, put } from './http'
import {
  changePassword,
  endSession,
  getProfile,
  getSecurity,
  listLoginRecords,
  listPreferences,
  listSessions,
  savePreferences,
  updateProfile,
  updateSecurity,
} from './me'

const g = vi.mocked(get)
const u = vi.mocked(put)
const p = vi.mocked(post)
const d = vi.mocked(del)

describe('me API 契约（06 切片2，资料白名单只提交 name/org/email/phone）', () => {
  beforeEach(() => vi.clearAllMocks())

  it('资料与安全读写路径', async () => {
    await getProfile()
    expect(g).toHaveBeenCalledWith('/me/profile')
    await updateProfile({ name: '张同学', org: '计算机学院', email: 'a@b.edu.cn', phone: '13800000000' })
    expect(u).toHaveBeenCalledWith('/me/profile', {
      name: '张同学', org: '计算机学院', email: 'a@b.edu.cn', phone: '13800000000',
    })
    await getSecurity()
    expect(g).toHaveBeenCalledWith('/me/security')
    await updateSecurity({ twoFactor: true, newDeviceAlert: false })
    expect(u).toHaveBeenCalledWith('/me/security', { twoFactor: true, newDeviceAlert: false })
  })

  it('改密 body 是 {oldPassword,newPassword}（confirmPassword 只在前端校验）', async () => {
    await changePassword('Old12345', 'New67890')
    expect(p).toHaveBeenCalledWith('/me/password', { oldPassword: 'Old12345', newPassword: 'New67890' })
  })

  it('会话与登录记录路径', async () => {
    await listSessions()
    expect(g).toHaveBeenCalledWith('/me/sessions')
    await endSession('S1')
    expect(d).toHaveBeenCalledWith('/me/sessions/S1')
    await listLoginRecords({ range: '7d' })
    expect(g).toHaveBeenCalledWith('/me/login-records', { params: { range: '7d' } })
  })

  it('通知偏好：GET 全量、PUT 带 items 数组', async () => {
    await listPreferences()
    expect(g).toHaveBeenCalledWith('/me/notification-preferences')
    await savePreferences([{ type: 'ALERT', enabled: false }, { type: 'FORMAT', enabled: true }])
    expect(u).toHaveBeenCalledWith('/me/notification-preferences', {
      items: [{ type: 'ALERT', enabled: false }, { type: 'FORMAT', enabled: true }],
    })
  })
})
