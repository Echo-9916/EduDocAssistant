// 06 切片 2：个人中心 API（契约=后端 MeController / MeService 嵌套 record +
// InboxController 的 /me/notification-preferences，见 docs/design/接口契约速查.md §8）。
import { del, get, post, put } from './http'
import type { MessageType } from './messages'

/** 只读口径项（学生=选课课程、教师=授课课程、运维=值班组/账号来源），后端不给 name 键，仅 label+value。 */
export interface ExtraField {
  label: string
  value: string
}

/** account 学生=学号、教师/运维=工号；role 为主角色（OPS>TEACHER>STUDENT）。 */
export interface ProfileView {
  name: string | null
  account: string
  roleLabel: string | null
  org: string | null
  email: string | null
  phone: string | null
  role: string
  extra: ExtraField[]
}

export interface SecurityView {
  twoFactor: boolean
  newDeviceAlert: boolean
  /** 以 PASSWORD_CHANGE 审计时间近似，UTC ISO 带 Z；上线前改密不可追溯 → null */
  passwordUpdated: string | null
  /** 后端只读（策略默认 OPS 开），PUT /me/security 不接受该键 */
  sensitiveVerify: boolean
}

export interface SessionView {
  id: string
  device: string
  ip: string | null
  /** UTC ISO 带 Z，须 new Date() 本地化 */
  loginTime: string
  current: boolean
}

export interface LoginRecordView {
  /** UTC ISO 带 Z */
  time: string
  device: string
  ip: string | null
  location: string | null
  result: 'SUCCESS' | 'BLOCKED'
  remote: boolean
}

export interface PreferenceView {
  type: MessageType
  enabled: boolean
}

export type LoginRange = '24h' | '7d' | '30d'

export const getProfile = () => get<ProfileView>('/me/profile')

/** 后端白名单只收 name/org/email/phone；带 account/role/课程等同步字段键 → 40001。 */
export const updateProfile = (patchBody: { name?: string; org?: string; email?: string; phone?: string }) =>
  put<ProfileView>('/me/profile', patchBody)

export const getSecurity = () => get<SecurityView>('/me/security')

/** OPS 关双因素 → 40301；返回更新后的完整视图。 */
export const updateSecurity = (patchBody: { twoFactor?: boolean; newDeviceAlert?: boolean }) =>
  put<SecurityView>('/me/security', patchBody)

/** 成功后当前令牌即被后端撤销：调用方须清本地令牌并跳登录页。旧密码错 40101、强度不足 40001。 */
export const changePassword = (oldPassword: string, newPassword: string) =>
  post<void>('/me/password', { oldPassword, newPassword })

/** 会话=AUTH 审计成功记录派生的「登录设备列表」，最近一条标 current。 */
export const listSessions = () => get<SessionView[]>('/me/sessions')

/** 后端现阶段一律 40901（无令牌会话索引，见 docs/tasks/06-待确认清单.md B2）：调用方须如实提示，不得假装下线成功。 */
export const endSession = (sessionId: string) => del<void>(`/me/sessions/${sessionId}`)

export const listLoginRecords = (query: { range?: LoginRange }) =>
  get<LoginRecordView[]>('/me/login-records', { params: query })

/** 八类通知偏好；无记录=接收（enabled 默认 true）。 */
export const listPreferences = () => get<PreferenceView[]>('/me/notification-preferences')

/** 逐项幂等 upsert（MERGE），返回写入/更新条数（后端 data 是裸整数）。 */
export const savePreferences = (items: { type: MessageType; enabled: boolean }[]) =>
  put<number>('/me/notification-preferences', { items })
