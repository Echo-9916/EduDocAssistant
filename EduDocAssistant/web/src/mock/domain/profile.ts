// 个人中心 fixture（学生 27 / 教师 27 / 运维 26+27 屏）。三端共用一套壳，按角色取不同资料。
// 基准时间 2026-11-09；实名信息由学校统一身份认证同步，演示页只允许改联系方式。
import { delay, remove, update } from '../store'
import type { RoleCodeValue } from '../../api/types'

export type ProfileInfo = {
  role: RoleCodeValue
  name: string
  /** 学号 / 工号 */
  account: string
  /** 组织或院系 */
  org: string
  email: string
  phone: string
  roleLabel: string
  stats: { label: string; value: string }[]
  /** 角色差异字段：学生=学号与选课课程，教师=院系与授课课程，运维=值班组与联系方式 */
  extra: { name: string; label: string; value: string }[]
}

const PROFILES: ProfileInfo[] = [
  {
    role: 'STUDENT',
    name: '张同学',
    account: '2023001',
    org: '计算机学院',
    email: 'zhang.student@university.edu.cn',
    phone: '138****6721',
    roleLabel: '学生账号',
    stats: [
      { label: '本月文档', value: '12' },
      { label: '格式通过', value: '8' },
    ],
    extra: [
      { name: 'studentId', label: '学号', value: '2023001' },
      { name: 'courses', label: '选课课程', value: '《Web开发技术》《操作系统》《编译原理》' },
      { name: 'group', label: '课程组', value: '《Web开发技术》课程组 3' },
    ],
  },
  {
    role: 'TEACHER',
    name: '李老师',
    account: 'T2021001',
    org: '计算机学院 · Web开发课程组',
    email: 'li.teacher@university.edu.cn',
    phone: '139****2088',
    roleLabel: '教师账号 · 课程负责人',
    stats: [
      { label: '授课课程', value: '2' },
      { label: '教学文档', value: '128' },
    ],
    extra: [
      { name: 'dept', label: '所属院系', value: '计算机学院' },
      { name: 'courses', label: '授课课程', value: '《Web开发技术》《编译原理》' },
      { name: 'office', label: '办公地点', value: '信息楼 A-410' },
    ],
  },
  {
    role: 'OPS',
    name: '赵工',
    account: 'A2001',
    org: '教务处 · 教学运行与系统运维',
    email: 'zhao.ops@university.edu.cn',
    phone: '138 0000 2401',
    roleLabel: '运维管理员',
    stats: [
      { label: '治理课程', value: '4' },
      { label: '待办事项', value: '8' },
    ],
    extra: [
      { name: 'dutyGroup', label: '值班组', value: '运维一组（本周值班 · 08:00–20:00）' },
      { name: 'scope', label: '职责范围', value: '用户与权限、审计与安全、存储与资源、课程治理' },
      { name: 'dutyPhone', label: '值班电话', value: '校内 8401（高风险告警即时电话）' },
    ],
  },
]

export type SecurityPref = {
  role: RoleCodeValue
  twoFactor: boolean
  /** 运维特有：角色变更、冻结账号、存储清理需二次验证 */
  sensitiveVerify: boolean
  newDeviceAlert: boolean
  notify: { homework: boolean; format: boolean; alert: boolean }
  passwordUpdated: string
}

const PREFS: SecurityPref[] = [
  { role: 'STUDENT', twoFactor: false, sensitiveVerify: false, newDeviceAlert: true, notify: { homework: true, format: true, alert: false }, passwordUpdated: '90 天前' },
  { role: 'TEACHER', twoFactor: true, sensitiveVerify: false, newDeviceAlert: true, notify: { homework: true, format: true, alert: false }, passwordUpdated: '32 天前' },
  { role: 'OPS', twoFactor: true, sensitiveVerify: true, newDeviceAlert: true, notify: { homework: false, format: true, alert: true }, passwordUpdated: '15 天前' },
]

export type Session = {
  id: string
  role: RoleCodeValue
  device: string
  ip: string
  loginTime: string
  current: boolean
}

const SESSIONS: Session[] = [
  { id: 'SES-S1', role: 'STUDENT', device: 'Windows · Chrome', ip: '10.20.31.7', loginTime: '2026-11-09 08:12', current: true },
  { id: 'SES-S2', role: 'STUDENT', device: 'iPhone · 校园 App', ip: '10.20.31.44', loginTime: '2026-11-08 21:40', current: false },
  { id: 'SES-T1', role: 'TEACHER', device: 'macOS · Safari', ip: '10.20.9.18', loginTime: '2026-11-09 07:58', current: true },
  { id: 'SES-T2', role: 'TEACHER', device: 'Windows · Edge', ip: '10.20.9.31', loginTime: '2026-11-08 18:42', current: false },
  { id: 'SES-O1', role: 'OPS', device: 'Windows · Chrome', ip: '10.20.8.2', loginTime: '2026-11-09 08:05', current: true },
  { id: 'SES-O2', role: 'OPS', device: 'MacBook · Safari', ip: '10.20.8.7', loginTime: '2026-11-08 18:42', current: false },
]

export type LoginRecord = {
  id: string
  role: RoleCodeValue
  time: string
  device: string
  ip: string
  location: string
  result: 'SUCCESS' | 'VERIFY' | 'BLOCKED'
  /** 异地登录标记 */
  remote: boolean
}

export const loginResultLabel: Record<LoginRecord['result'], string> = {
  SUCCESS: '成功',
  VERIFY: '二次验证通过',
  BLOCKED: '已阻断',
}

const RECORDS: LoginRecord[] = [
  { id: 'LR-S1', role: 'STUDENT', time: '2026-11-09 08:12', device: 'Windows · Chrome', ip: '10.20.31.7', location: '校内网络', result: 'SUCCESS', remote: false },
  { id: 'LR-S2', role: 'STUDENT', time: '2026-11-08 23:47', device: '未知设备 · Android', ip: '183.12.44.9', location: '校外网络', result: 'VERIFY', remote: true },
  { id: 'LR-S3', role: 'STUDENT', time: '2026-11-08 16:42', device: 'Windows · Chrome', ip: '10.20.31.7', location: '校内网络', result: 'BLOCKED', remote: false },
  { id: 'LR-T1', role: 'TEACHER', time: '2026-11-09 07:58', device: 'macOS · Safari', ip: '10.20.9.18', location: '校内网络', result: 'SUCCESS', remote: false },
  { id: 'LR-T2', role: 'TEACHER', time: '2026-11-08 18:42', device: 'Windows · Edge', ip: '10.20.9.31', location: '校内网络', result: 'SUCCESS', remote: false },
  { id: 'LR-T3', role: 'TEACHER', time: '2026-11-07 22:16', device: 'iPad · 校园 App', ip: '117.88.20.6', location: '校外网络', result: 'VERIFY', remote: true },
  { id: 'LR-O1', role: 'OPS', time: '2026-11-09 08:05', device: 'Windows · Chrome', ip: '10.20.8.2', location: '校内网络', result: 'SUCCESS', remote: false },
  { id: 'LR-O2', role: 'OPS', time: '2026-11-08 18:42', device: 'MacBook · Safari', ip: '10.20.8.7', location: '校内网络', result: 'SUCCESS', remote: false },
  { id: 'LR-O3', role: 'OPS', time: '2026-11-08 02:14', device: '未知设备', ip: '183.12.8.46', location: '校外网络', result: 'BLOCKED', remote: true },
]

export function getProfile(role: RoleCodeValue) {
  const p = PROFILES.find((x) => x.role === role) ?? PROFILES[0]
  return delay({ ...p, extra: p.extra.map((e) => ({ ...e })) })
}

/** 保存资料：姓名与联系方式可改，实名与学号/工号由学校同步，页面置只读。 */
export function saveProfile(role: RoleCodeValue, patch: Partial<Pick<ProfileInfo, 'name' | 'org' | 'email' | 'phone'>>) {
  update<ProfileInfo>(PROFILES, (p) => p.role === role, patch)
  return delay(true, 200)
}

export function getSecurity(role: RoleCodeValue) {
  const s = PREFS.find((x) => x.role === role) ?? PREFS[0]
  return delay({ ...s, notify: { ...s.notify } })
}

export function saveSecurity(role: RoleCodeValue, patch: Partial<Omit<SecurityPref, 'role' | 'notify'>> & { notify?: Partial<SecurityPref['notify']> }) {
  const cur = PREFS.find((x) => x.role === role)
  if (!cur) return delay(false)
  const { notify, ...rest } = patch
  Object.assign(cur, rest)
  if (notify) Object.assign(cur.notify, notify)
  return delay(true, 160)
}

export function listSessions(role: RoleCodeValue) {
  return delay(SESSIONS.filter((s) => s.role === role).map((s) => ({ ...s })))
}

/** 下线远程会话；当前会话不可下线。 */
export function endSession(id: string) {
  remove(SESSIONS, (s) => s.id === id && !s.current)
  return delay(true, 160)
}

export function listLoginRecords(role: RoleCodeValue) {
  return delay(RECORDS.filter((r) => r.role === role).map((r) => ({ ...r })))
}

export function changePassword(role: RoleCodeValue) {
  update(PREFS, (p) => p.role === role, { passwordUpdated: '刚刚' })
  return delay(true, 240)
}
