import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
const logoutMock = vi.fn(() => Promise.resolve())
let roles: string[] = ['STUDENT']
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { roles, permissions: ['DOCUMENT_READ'] } as never, logout: logoutMock }),
}))
vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/messages', () => ({
  messageTypeLabel: {
    COURSE: '课程组更新', SUBMISSION: '提交提醒', FEEDBACK: '教师反馈', FORMAT: '格式异常',
    PERMISSION: '权限变更', ALERT: '系统告警', APPROVAL: '审批通知', SYSTEM: '系统通知',
  },
}))
vi.mock('../../api/me', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  getSecurity: vi.fn(),
  updateSecurity: vi.fn(),
  changePassword: vi.fn(),
  listSessions: vi.fn(),
  endSession: vi.fn(),
  listLoginRecords: vi.fn(),
  listPreferences: vi.fn(),
  savePreferences: vi.fn(),
}))

import {
  changePassword,
  endSession,
  getProfile,
  getSecurity,
  listLoginRecords,
  listPreferences,
  listSessions,
  savePreferences,
} from '../../api/me'
import type { LoginRecordView, PreferenceView, ProfileView, SecurityView, SessionView } from '../../api/me'
import Profile from './Profile'

const PROFILE: ProfileView = {
  name: '张同学', account: '2023001', roleLabel: '学生账号', org: '计算机学院',
  email: 'zhang.student@university.edu.cn', phone: '138****6721', role: 'STUDENT',
  extra: [{ label: '选课课程', value: '《Web开发技术》' }],
}
const SEC: SecurityView = { twoFactor: false, newDeviceAlert: true, passwordUpdated: null, sensitiveVerify: false }
const SESSIONS: SessionView[] = [
  { id: 'S1', device: 'Windows · Chrome', ip: '10.20.31.7', loginTime: '2026-11-09T00:12:00Z', current: true },
  { id: 'S2', device: 'iPhone · 校园 App', ip: null, loginTime: '2026-11-08T13:40:00Z', current: false },
]
// ip/location 后端可能为空（B3）：空值应显示「-」
const RECORDS: LoginRecordView[] = [
  { time: '2026-11-09T00:12:00Z', device: 'Windows · Chrome', ip: null, location: null, result: 'SUCCESS', remote: false },
]
const PREFS: PreferenceView[] = (
  ['COURSE', 'SUBMISSION', 'FEEDBACK', 'FORMAT', 'PERMISSION', 'ALERT', 'APPROVAL', 'SYSTEM'] as const
).map((type) => ({ type, enabled: true }))

describe('个人中心（学生 27 / 教师 27 / 运维 26+27 屏，真实端点）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    roles = ['STUDENT']
    vi.mocked(getProfile).mockResolvedValue(PROFILE)
    vi.mocked(getSecurity).mockResolvedValue(SEC)
    vi.mocked(listSessions).mockResolvedValue(SESSIONS)
    vi.mocked(listLoginRecords).mockResolvedValue(RECORDS)
    vi.mocked(listPreferences).mockResolvedValue(PREFS)
    vi.mocked(savePreferences).mockResolvedValue(1)
    vi.mocked(changePassword).mockResolvedValue(undefined)
  })

  it('渲染真实资料：extra 只读展示、空 ip/位置显示「-」、不出现运维特有项', async () => {
    render(<Profile />)
    expect(await screen.findByText('个人中心')).toBeTruthy()
    expect(await screen.findByText('张同学')).toBeTruthy()
    expect(screen.getByText('选课课程')).toBeTruthy()
    expect((screen.getByDisplayValue('《Web开发技术》') as HTMLInputElement).disabled).toBe(true)
    // 学号只读
    expect((screen.getByDisplayValue('2023001') as HTMLInputElement).disabled).toBe(true)

    fireEvent.click(screen.getByText('账号与安全'))
    // 会话表与登录记录表都有这台设备
    expect((await screen.findAllByText('Windows · Chrome')).length).toBeGreaterThanOrEqual(2)
    // 登录记录 ip/location 为空 → 「-」（两处：来源 IP 列与位置列）
    expect(screen.getAllByText('-').length).toBeGreaterThanOrEqual(2)
    expect(screen.queryByText('敏感操作二次验证')).toBeNull()
  })

  it('通知偏好八类开关打到 PUT /me/notification-preferences', async () => {
    render(<Profile />)
    fireEvent.click(await screen.findByText('账号与安全'))
    expect(await screen.findByText('提交提醒')).toBeTruthy()
    // 开关顺序：双因素、新设备提醒、八类偏好（COURSE 起）
    const switches = screen.getAllByRole('switch')
    expect(switches).toHaveLength(10)
    fireEvent.click(switches[3]) // SUBMISSION 关
    await waitFor(() =>
      expect(savePreferences).toHaveBeenCalledWith([{ type: 'SUBMISSION', enabled: false }]),
    )
  })

  it('改密成功后清登录态并跳登录页', async () => {
    render(<Profile />)
    fireEvent.click(await screen.findByText('账号与安全'))
    fireEvent.click(await screen.findByText('修改密码'))
    fireEvent.change(screen.getByPlaceholderText('请输入当前密码'), { target: { value: 'Old12345' } })
    fireEvent.change(screen.getByPlaceholderText('至少 8 位，需同时包含字母和数字'), { target: { value: 'New12345' } })
    fireEvent.change(screen.getByPlaceholderText('请再次输入新密码'), { target: { value: 'New12345' } })
    fireEvent.click(screen.getByText('确认修改'))

    await waitFor(() => expect(changePassword).toHaveBeenCalledWith('Old12345', 'New12345'))
    await waitFor(() => expect(logoutMock).toHaveBeenCalled())
    expect(navigateMock).toHaveBeenCalledWith('/login', { replace: true })
  })

  it('会话下线不假装成功：40901 如实提示且行不删', async () => {
    vi.mocked(endSession).mockRejectedValue(
      new Error('该会话暂不支持单独下线：精确下线依赖令牌会话索引（后续项），令牌将在有效期（30 分钟）内自然过期'),
    )
    render(<Profile />)
    fireEvent.click(await screen.findByText('账号与安全'))
    fireEvent.click(await screen.findByText('下线'))
    expect(await screen.findByText(/令牌将在有效期（30 分钟）内自然过期/)).toBeTruthy()
    // 不删行、不弹「已下线」成功提示
    expect(screen.getByText('iPhone · 校园 App')).toBeTruthy()
    expect(screen.queryByText('会话已下线')).toBeNull()
  })
})
