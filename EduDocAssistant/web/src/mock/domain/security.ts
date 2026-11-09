// 审计与安全 fixture（运维 13–20 屏）。演示世界观与 messages.ts 一致：
// 课程《Web开发技术》《数据库原理》《操作系统》《编译原理》，教师 李老师/王老师，
// 学生 张同学/陈同学/刘同学，运维 赵工，基准时间 2026-11-09。
// 时刻直接写死字符串：store.ago() 输出 UTC，用在「10:42:18」这类审计时刻上会偏 8 小时。
import { delay, insert, nextId, update } from '../store'
import { AccountStatus } from '../../api/types'
import type { AccountStatusValue } from '../../api/types'

/** 审计对象类型，对齐 edudoc_audit_log.resource_type。 */
export type AuditResourceType = 'ACCOUNT' | 'COURSE' | 'DOCUMENT' | 'PERMISSION' | 'SYSTEM'

export const resourceTypeLabel: Record<AuditResourceType, string> = {
  ACCOUNT: '账号',
  COURSE: '课程',
  DOCUMENT: '文档',
  PERMISSION: '权限',
  SYSTEM: '系统',
}

export type AuditResult = 'SUCCESS' | 'FAIL'
export type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export const riskLabel: Record<RiskLevel, string> = { HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }

export type ChainStep = { time: string; title: string; desc: string }

export type AuditEvent = {
  /** 事件编号，同时作为表格 rowKey */
  id: string
  occurredAt: string
  actor: string
  actorRole: string
  actionType: string
  resourceType: AuditResourceType
  resourceRef: string
  result: AuditResult
  resultCode: string
  traceId: string
  sourceIp: string
  userAgent: string
  riskLevel: RiskLevel
  summary: string
  /** 屏 16：完整链路，缺省时由 chainOf() 按字段推导 */
  chain?: ChainStep[]
  impacts?: { type: string; name: string; detail: string; mark: string }[]
  advice?: string[]
}

const AUDIT: AuditEvent[] = [
  {
    id: 'AUD-20261109-104218',
    occurredAt: '2026-11-09 10:42:18',
    actor: '陈同学',
    actorRole: '学生 · 2024017',
    actionType: '越权访问被拒绝',
    resourceType: 'DOCUMENT',
    resourceRef: '《Web开发课程设计报告》',
    result: 'FAIL',
    resultCode: 'ACL_DENIED(40302)',
    traceId: 'tr-9f2c41ab77e0',
    sourceIp: '10.20.12.5',
    userAgent: 'Chrome 130 / Windows 11',
    riskLevel: 'MEDIUM',
    summary: '账号尝试访问未加入课程的文档，服务端在权限校验阶段拒绝请求，内容未返回。',
    chain: [
      { time: '10:42:17', title: '身份校验通过', desc: '账号状态正常，会话有效。' },
      { time: '10:42:18', title: '课程访问校验', desc: '未找到《Web开发技术》课程成员关系。' },
      { time: '10:42:18', title: '策略引擎拒绝访问', desc: '返回 403，文档内容未加载。' },
      { time: '10:42:19', title: '写入审计日志', desc: '事件编号与 traceId 已生成。' },
    ],
    impacts: [
      { type: '文档', name: '《Web开发课程设计报告》', detail: '权限校验在内容返回前终止', mark: '无泄露' },
      { type: '课程', name: '《Web开发技术》', detail: '课程负责人 李老师 待通知', mark: '观察' },
      { type: '账号', name: '陈同学 · 2024017', detail: '近期第 1 次异常访问，未达冻结阈值', mark: '观察' },
    ],
    advice: [
      '通知课程负责人 李老师，确认目标文档的共享范围是否需要调整。',
      '若 24 小时内再次出现同类访问，自动升级为高风险安全事件。',
      '保留全部请求日志，满足安全调查与审计追踪要求。',
    ],
  },
  {
    id: 'AUD-20261109-091802',
    occurredAt: '2026-11-09 09:18:02',
    actor: '赵工',
    actorRole: '运维 · A2001',
    actionType: '变更用户角色',
    resourceType: 'PERMISSION',
    resourceRef: '王老师 · T2021043',
    result: 'SUCCESS',
    resultCode: 'OK(200)',
    traceId: 'tr-4b81d0c93af5',
    sourceIp: '10.20.8.2',
    userAgent: 'Chrome 130 / Windows 11',
    riskLevel: 'HIGH',
    summary: '为《数据库原理》课程组增加「课程模板管理员」角色，影响 2 门课程。',
    chain: [
      { time: '09:17:40', title: '二次验证通过', desc: '敏感操作要求动态验证码。' },
      { time: '09:18:02', title: '角色变更写入', desc: '新增 TEMPLATE_MANAGE，保留原有权限。' },
      { time: '09:18:03', title: '通知与审计', desc: '已通知本人并写入审计日志。' },
    ],
    impacts: [
      { type: '账号', name: '王老师 · T2021043', detail: '获得课程模板管理权限', mark: '已生效' },
      { type: '课程', name: '《数据库原理》《操作系统》', detail: '模板可编辑范围扩大', mark: '需复核' },
    ],
    advice: ['角色变更与审计日志查看属职责分离项，建议由独立审批人复核。'],
  },
  {
    id: 'AUD-20261109-085544',
    occurredAt: '2026-11-09 08:55:44',
    actor: '李老师',
    actorRole: '教师 · T2021001',
    actionType: '课程文档导出',
    resourceType: 'DOCUMENT',
    resourceRef: 'Web开发课程设计模板.pdf',
    result: 'SUCCESS',
    resultCode: 'OK(200)',
    traceId: 'tr-77aa19f02cd4',
    sourceIp: '10.20.9.18',
    userAgent: 'Safari 18 / macOS 15',
    riskLevel: 'LOW',
    summary: '课程负责人在课程范围内导出模板，10 分钟内共导出 3 份，未触发异常导出阈值。',
    impacts: [{ type: '文档', name: 'Web开发课程设计模板.pdf', detail: '导出 1 份，含水印与账号标识', mark: '正常' }],
    advice: ['导出行为在阈值内，无需处置。'],
  },
  {
    id: 'AUD-20261109-084210',
    occurredAt: '2026-11-09 08:42:10',
    actor: '王老师',
    actorRole: '教师 · T2021043',
    actionType: '异地设备登录',
    resourceType: 'SYSTEM',
    resourceRef: '统一身份认证服务',
    result: 'SUCCESS',
    resultCode: 'OK(200) · 二次验证通过',
    traceId: 'tr-c05e7712ba91',
    sourceIp: '183.12.8.46',
    userAgent: 'Edge 130 / Windows 11（新设备）',
    riskLevel: 'MEDIUM',
    summary: '新设备指纹 + 校外网络登录，命中异常登录二次验证策略，验证通过后放行。',
    chain: [
      { time: '08:41:52', title: '统一身份认证回调', desc: '校内身份服务返回认证通过。' },
      { time: '08:42:03', title: '命中异常登录规则', desc: '新设备 + 校外网络。' },
      { time: '08:42:10', title: '动态验证码校验通过', desc: '会话建立，写入登录记录。' },
    ],
    impacts: [{ type: '账号', name: '王老师 · T2021043', detail: '新设备已加入可信设备列表', mark: '验证通过' }],
    advice: ['保留新设备登录提醒，观察 7 天内是否重复出现异地登录。'],
  },
  {
    id: 'AUD-20261108-173500',
    occurredAt: '2026-11-08 17:35:00',
    actor: '赵工',
    actorRole: '运维 · A2001',
    actionType: '新增自定义角色',
    resourceType: 'PERMISSION',
    resourceRef: '院系教学审核员',
    result: 'SUCCESS',
    resultCode: 'OK(200)',
    traceId: 'tr-2de90bb41f67',
    sourceIp: '10.20.8.2',
    userAgent: 'Chrome 130 / Windows 11',
    riskLevel: 'HIGH',
    summary: '新建「院系教学审核员」角色，授权 4 项只读权限，提交前已通过职责冲突检查。',
    impacts: [{ type: '权限', name: '院系教学审核员', detail: '含课程只读、异常复核、日志查看', mark: '已生效' }],
    advice: ['角色不含角色变更权限，高风险职责保持分离。'],
  },
  {
    id: 'AUD-20261108-162000',
    occurredAt: '2026-11-08 16:20:00',
    actor: '赵工',
    actorRole: '运维 · A2001',
    actionType: '回收站对象恢复',
    resourceType: 'DOCUMENT',
    resourceRef: '旧版课程设计模板.docx',
    result: 'SUCCESS',
    resultCode: 'OK(200)',
    traceId: 'tr-6a1f83cd50e2',
    sourceIp: '10.20.8.2',
    userAgent: 'Chrome 130 / Windows 11',
    riskLevel: 'LOW',
    summary: '从回收站恢复 12 个对象，均在 30 天保留期内，版本链完整。',
    impacts: [{ type: '文档', name: '旧版课程设计模板.docx', detail: '恢复至《Web开发技术》课程空间', mark: '成功' }],
    advice: ['恢复操作已记录，无需额外处置。'],
  },
  {
    id: 'AUD-20261108-110500',
    occurredAt: '2026-11-08 11:05:00',
    actor: '教务同步任务',
    actorRole: '系统',
    actionType: '课程成员同步',
    resourceType: 'COURSE',
    resourceRef: '《数据库原理》',
    result: 'SUCCESS',
    resultCode: 'OK(200)',
    traceId: 'tr-b30c7e19d4a8',
    sourceIp: '127.0.0.1',
    userAgent: 'edudoc-sync/1.4',
    riskLevel: 'LOW',
    summary: '转专业插入 2 名学生，课程组 5 名单更新，账号自动绑定完成。',
    impacts: [
      { type: '课程', name: '《数据库原理》', detail: '成员 126 → 128', mark: '成功' },
      { type: '账号', name: '刘同学 · 2024033', detail: '新增课程访问范围', mark: '已生效' },
    ],
    advice: ['同步结果已通知课程负责人 王老师。'],
  },
  {
    id: 'AUD-20261107-093000',
    occurredAt: '2026-11-07 09:30:00',
    actor: '张同学',
    actorRole: '学生 · 2023001',
    actionType: '提交课程作业',
    resourceType: 'COURSE',
    resourceRef: '《操作系统》作业 3',
    result: 'FAIL',
    resultCode: 'FORMAT_BLOCKER(42202)',
    traceId: 'tr-e77120ab9c33',
    sourceIp: '10.20.31.7',
    userAgent: 'Chrome 130 / Windows 11',
    riskLevel: 'LOW',
    summary: '提交被格式校验阻塞：页边距、一级标题字号、代码块字体 3 项不通过，未生成提交记录。',
    impacts: [{ type: '文档', name: '操作系统课程报告_v2.docx', detail: '存在 3 项阻塞问题', mark: '待修正' }],
    advice: ['引导学生使用一键修复后重新提交，不涉及安全处置。'],
  },
]

/** 屏 15 检索条件：时间范围用枚举，避免引入日期控件依赖。 */
export type AuditQuery = {
  range: '24h' | '7d' | '30d'
  resourceType: AuditResourceType | 'ALL'
  actor: string
  keyword: string
}

const RANGE_DAYS = { '24h': 1, '7d': 7, '30d': 30 } as const
const BASE_TIME = new Date('2026-11-09T23:59:59+08:00').getTime()

function within(occurredAt: string, days: number): boolean {
  const t = new Date(`${occurredAt.replace(' ', 'T')}+08:00`).getTime()
  return BASE_TIME - t <= days * 86_400_000
}

export function listAuditEvents(q: AuditQuery) {
  const kw = q.keyword.trim().toLowerCase()
  const actor = q.actor.trim()
  const rows = AUDIT.filter(
    (e) =>
      within(e.occurredAt, RANGE_DAYS[q.range]) &&
      (q.resourceType === 'ALL' || e.resourceType === q.resourceType) &&
      (!actor || e.actor.includes(actor) || e.actorRole.includes(actor)) &&
      (!kw || [e.id, e.actor, e.actionType, e.resourceRef, e.traceId].some((v) => v.toLowerCase().includes(kw))),
  )
  return delay(rows.map((e) => ({ ...e })))
}

/** 屏 16 事件链路：种子未写链路时按字段推导，保证每个事件都有可讲的时间线。 */
export function chainOf(e: AuditEvent): ChainStep[] {
  const t = e.occurredAt.slice(11)
  return (
    e.chain ?? [
      { time: t, title: '身份校验通过', desc: '账号状态正常，会话有效。' },
      { time: t, title: e.actionType, desc: `对象：${e.resourceRef}` },
      {
        time: t,
        title: e.result === 'SUCCESS' ? '操作完成并写入审计日志' : '请求被终止并写入审计日志',
        desc: `结果码 ${e.resultCode} · traceId ${e.traceId}`,
      },
    ]
  )
}

/** 安全事件类型（屏 18 实时汇总的四个口径）。 */
export type SecurityType = 'LOGIN_RISK' | 'PRIVILEGE_ATTEMPT' | 'ABNORMAL_EXPORT' | 'ACCOUNT_ACTION'
export type SecurityStatus = 'PENDING' | 'HANDLING' | 'CLOSED'

export const securityTypeLabel: Record<SecurityType, string> = {
  LOGIN_RISK: '登录风险',
  PRIVILEGE_ATTEMPT: '越权尝试',
  ABNORMAL_EXPORT: '异常导出',
  ACCOUNT_ACTION: '账号处置',
}

export const securityStatusLabel: Record<SecurityStatus, string> = {
  PENDING: '待处置',
  HANDLING: '处置中',
  CLOSED: '已关闭',
}

/** 事件涉及的账号，冻结后状态就地改为 LOCKED（屏 20）。 */
export type SecurityAccount = {
  account: string
  name: string
  roleLabel: string
  dept: string
  courseScope: string
  status: AccountStatusValue
  sessions: number
}

const ACCOUNTS: SecurityAccount[] = [
  { account: '2024017', name: '陈同学', roleLabel: '学生', dept: '计算机学院', courseScope: '《数据库原理》《操作系统》', status: AccountStatus.ACTIVE, sessions: 2 },
  { account: 'T2021043', name: '王老师', roleLabel: '教师', dept: '计算机学院', courseScope: '《数据库原理》', status: AccountStatus.ACTIVE, sessions: 3 },
  { account: 'T2021001', name: '李老师', roleLabel: '教师', dept: '计算机学院', courseScope: '《Web开发技术》《编译原理》', status: AccountStatus.ACTIVE, sessions: 1 },
  { account: '2023001', name: '张同学', roleLabel: '学生', dept: '计算机学院', courseScope: '《Web开发技术》《操作系统》', status: AccountStatus.ACTIVE, sessions: 1 },
  { account: 'A2007', name: '陈工', roleLabel: '运维值班', dept: '教务处', courseScope: '全课程只读', status: AccountStatus.ACTIVE, sessions: 2 },
]

export type SecurityEvent = {
  id: string
  level: RiskLevel
  type: SecurityType
  rule: string
  account: string
  sourceIp: string
  riskScore: number
  firstSeen: string
  status: SecurityStatus
  summary: string
  timeline: ChainStep[]
  resources: { name: string; detail: string; mark: string }[]
  advice: string[]
  /** 屏 20 冻结确认：恢复方式说明 */
  restoreNote: string
  reason: string
}

const SECURITY: SecurityEvent[] = [
  {
    id: 'SEC-20261109-0042',
    level: 'HIGH',
    type: 'PRIVILEGE_ATTEMPT',
    rule: '短时间访问多个未授权课程文档（3 分钟 ≥ 5 次）',
    account: '2024017',
    sourceIp: '10.20.12.5',
    riskScore: 86,
    firstSeen: '2026-11-09 10:39',
    status: 'PENDING',
    summary: '账号 3 分钟内连续请求 6 份《Web开发技术》课程文档，全部在内容返回前被阻断。',
    timeline: [
      { time: '10:39', title: '首次异常请求', desc: '访问未加入课程的文档。' },
      { time: '10:41', title: '规则命中', desc: '3 分钟内 4 次同类请求。' },
      { time: '10:42', title: '自动阻断', desc: '返回 403，内容未加载。' },
      { time: '10:43', title: '事件生成', desc: '已通知当班运维 赵工。' },
    ],
    resources: [
      { name: '陈同学 · 2024017', detail: '学生 · 计算机学院', mark: '高风险' },
      { name: '6 份课程文档', detail: '均未返回内容', mark: '未泄露' },
      { name: '《Web开发技术》', detail: '课程负责人：李老师', mark: '已通知' },
    ],
    advice: ['保留全部请求日志', '持续观察 24 小时，再次越权时自动升级', '通知课程负责人未发生内容泄露'],
    restoreNote: '冻结不会删除用户数据。账号可由运维管理员在「用户与权限」复核后恢复为正常状态，恢复动作同样写入审计日志。',
    reason: '短时间连续访问多个未授权课程文档，达到高风险处置阈值。',
  },
  {
    id: 'SEC-20261109-0038',
    level: 'MEDIUM',
    type: 'LOGIN_RISK',
    rule: '异地或新设备登录需二次验证',
    account: 'T2021043',
    sourceIp: '183.12.8.46',
    riskScore: 54,
    firstSeen: '2026-11-09 08:41',
    status: 'HANDLING',
    summary: '校外网络 + 新设备指纹登录，动态验证码校验通过后放行，设备已加入可信列表待复核。',
    timeline: [
      { time: '08:41', title: '统一身份认证回调', desc: '校内身份服务认证通过。' },
      { time: '08:42', title: '二次验证通过', desc: '动态验证码校验成功。' },
      { time: '08:45', title: '事件转处置中', desc: '等待运维确认设备可信。' },
    ],
    resources: [
      { name: '王老师 · T2021043', detail: '教师 · 计算机学院', mark: '验证通过' },
      { name: '《数据库原理》课程空间', detail: '登录后访问 2 份课程文档', mark: '在授权范围内' },
    ],
    advice: ['确认设备归属后关闭事件', '若 7 天内重复出现异地登录，升级为高风险'],
    restoreNote: '本事件未冻结账号，无需恢复操作。',
    reason: '异地设备登录已通过二次验证，待确认设备可信。',
  },
  {
    id: 'SEC-20261108-0031',
    level: 'MEDIUM',
    type: 'ABNORMAL_EXPORT',
    rule: '单账号 10 分钟内导出超过 20 份文档',
    account: 'T2021001',
    sourceIp: '10.20.9.18',
    riskScore: 47,
    firstSeen: '2026-11-08 18:20',
    status: 'CLOSED',
    summary: '课程负责人批量导出期末归档材料 24 份，导出请求在阈值触发后被限流，经复核属正常教学行为。',
    timeline: [
      { time: '18:20', title: '导出限流触发', desc: '第 21 份导出被暂缓。' },
      { time: '18:26', title: '运维复核', desc: '确认为期末归档，课程范围内。' },
      { time: '18:31', title: '事件关闭', desc: '解除限流，导出继续。' },
    ],
    resources: [
      { name: '李老师 · T2021001', detail: '教师 · 课程负责人', mark: '正常' },
      { name: '24 份课程文档', detail: '均带水印与账号标识', mark: '已导出' },
    ],
    advice: ['期末归档期可临时上调导出阈值'],
    restoreNote: '本事件未冻结账号，无需恢复操作。',
    reason: '批量导出经复核为期末归档，属正常教学行为。',
  },
  {
    id: 'SEC-20261108-0027',
    level: 'LOW',
    type: 'LOGIN_RISK',
    rule: '10 分钟内密码错误 ≥ 3 次自动限制',
    account: '2023001',
    sourceIp: '10.20.31.7',
    riskScore: 22,
    firstSeen: '2026-11-08 16:42',
    status: 'CLOSED',
    summary: '连续 3 次密码错误触发自动限制 15 分钟，限制到期后本人登录成功，判定为误输入。',
    timeline: [
      { time: '16:42', title: '第 3 次密码错误', desc: '触发自动限制。' },
      { time: '16:57', title: '限制解除', desc: '15 分钟冷却结束。' },
      { time: '17:03', title: '登录成功', desc: '事件自动关闭。' },
    ],
    resources: [{ name: '张同学 · 2023001', detail: '学生 · 计算机学院', mark: '已恢复' }],
    advice: ['无需人工处置，保留登录记录 90 天'],
    restoreNote: '自动限制已到期解除，无需恢复操作。',
    reason: '密码错误触发自动限制，已自动解除。',
  },
  {
    id: 'SEC-20261107-0019',
    level: 'HIGH',
    type: 'ACCOUNT_ACTION',
    rule: '运维账号敏感操作未通过二次验证',
    account: 'A2007',
    sourceIp: '10.20.8.9',
    riskScore: 78,
    firstSeen: '2026-11-07 22:14',
    status: 'PENDING',
    summary: '值班账号在非值班时段尝试执行存储清理，二次验证失败 2 次后中止，需确认是否账号外借。',
    timeline: [
      { time: '22:14', title: '发起存储清理', desc: '命中敏感操作二次验证。' },
      { time: '22:15', title: '验证码错误 2 次', desc: '操作中止并生成事件。' },
      { time: '22:16', title: '通知当班运维', desc: '赵工 待确认账号归属。' },
    ],
    resources: [
      { name: '陈工 · A2007', detail: '运维值班账号', mark: '待确认' },
      { name: '回收站清理任务', detail: '未执行，128 个对象仍在保留期', mark: '未受影响' },
    ],
    advice: ['当面确认账号是否外借', '必要时重置密码并冻结账号', '核对值班排班表'],
    restoreNote: '冻结后由运维管理员在「用户与权限」页复核恢复，需同时重置密码与动态验证码绑定。',
    reason: '非值班时段敏感操作二次验证失败，疑似账号外借。',
  },
]

export function listSecurityAccounts() {
  return delay(ACCOUNTS.map((a) => ({ ...a })))
}

/** 同步读账号：冻结确认框需要即时拿到会话数与影响范围。 */
export function accountOf(account: string): SecurityAccount | undefined {
  return ACCOUNTS.find((a) => a.account === account)
}

export function accountStatusLabel(s: AccountStatusValue): string {
  return s === AccountStatus.LOCKED ? '已锁定' : s === AccountStatus.DISABLED ? '已停用' : s === AccountStatus.INACTIVE ? '未激活' : '正常'
}

export function listSecurityEvents() {
  return delay(SECURITY.map((e) => ({ ...e })))
}

/** 屏 19 应急处置：忽略 / 升级 / 关闭。 */
export function setSecurityStatus(id: string, status: SecurityStatus) {
  update(SECURITY, (e) => e.id === id, { status })
  return delay(true, 120)
}

/**
 * 屏 20 冻结账号：事件关闭 + 账号状态改 LOCKED + 会话清零，并追加一条审计事件，
 * 让「操作日志」页签能看到处置结果。
 */
export function freezeAccount(eventId: string, reason: string, notifyDept: boolean) {
  const ev = SECURITY.find((e) => e.id === eventId)
  if (!ev) return delay('')
  const acc = accountOf(ev.account)
  const sessions = acc?.sessions ?? 0
  update(SECURITY, (e) => e.id === eventId, { status: 'CLOSED', reason })
  update(ACCOUNTS, (a) => a.account === ev.account, { status: AccountStatus.LOCKED, sessions: 0 })
  insert(AUDIT, {
    id: nextId('AUD-20261109'),
    occurredAt: '2026-11-09 11:05:00',
    actor: '赵工',
    actorRole: '运维 · A2001',
    actionType: '冻结账号',
    resourceType: 'ACCOUNT',
    resourceRef: `${acc?.name ?? ''} · ${ev.account}`,
    result: 'SUCCESS',
    resultCode: 'OK(200)',
    traceId: nextId('tr'),
    sourceIp: '10.20.8.2',
    userAgent: 'Chrome 130 / Windows 11',
    riskLevel: 'HIGH',
    summary: `处置安全事件 ${ev.id}：终止 ${sessions} 个活动会话，账号状态改为锁定${notifyDept ? '，已通知所属院系' : ''}。原因：${reason}`,
    impacts: [{ type: '账号', name: `${acc?.name ?? ''} · ${ev.account}`, detail: `终止 ${sessions} 个会话，课程与文档访问停止`, mark: '已冻结' }],
    advice: ['复核后可在「用户与权限」恢复账号状态'],
  })
  return delay(acc?.name ?? ev.account, 200)
}

/** 屏 17 异常复核：三类指标 + 处置结论。 */
export type AnomalyKind = 'FREQUENCY' | 'PERMISSION' | 'LOGIN'
export type AnomalyStatus = 'PENDING' | 'DONE'

export const anomalyKindLabel: Record<AnomalyKind, string> = {
  FREQUENCY: '访问频率',
  PERMISSION: '权限边界',
  LOGIN: '登录风险',
}

export const anomalyConclusions = ['疑似越权尝试', '误报', '账号异常', '需要安全调查'] as const

export type Anomaly = {
  id: string
  ref: string
  kind: AnomalyKind
  title: string
  account: string
  riskScore: number
  metrics: Record<AnomalyKind, string>
  factors: { title: string; desc: string; hit: boolean }[]
  evidence: string
  status: AnomalyStatus
  conclusion?: string
  note?: string
}

const ANOMALIES: Anomaly[] = [
  {
    id: 'ANO-20261109-01',
    ref: 'AUD-20261109-104218',
    kind: 'FREQUENCY',
    title: '访问频率异常：3 分钟内 6 次未授权文档请求',
    account: '陈同学 · 2024017',
    riskScore: 72,
    metrics: {
      FREQUENCY: '6 次 / 3 分钟（阈值 5 次 / 10 分钟）',
      PERMISSION: '越界 6 份，均不在课程范围内',
      LOGIN: '可信设备 · 校内网络',
    },
    factors: [
      { title: '未加入课程', desc: '目标文档不属于当前账号课程范围。', hit: true },
      { title: '连续访问', desc: '3 分钟内 6 次同类请求。', hit: true },
      { title: '可信设备', desc: '登录设备与近期常用设备一致。', hit: false },
    ],
    evidence: '请求均来自同一会话与 IP（10.20.12.5），目标课程为《Web开发技术》。内容返回前已被策略引擎终止，未发现文档读取。',
    status: 'PENDING',
  },
  {
    id: 'ANO-20261109-02',
    ref: 'AUD-20261109-091802',
    kind: 'PERMISSION',
    title: '权限边界异常：同一账号持有角色变更与审计查看',
    account: '赵工 · A2001',
    riskScore: 64,
    metrics: {
      FREQUENCY: '1 次角色变更 / 24 小时（阈值内）',
      PERMISSION: '职责分离冲突 1 项（角色变更 + 审计日志）',
      LOGIN: '可信设备 · 二次验证通过',
    },
    factors: [
      { title: '职责分离冲突', desc: '角色变更权限与审计日志查看集中在同一账号。', hit: true },
      { title: '二次验证通过', desc: '敏感操作已完成动态验证码校验。', hit: false },
      { title: '变更范围可控', desc: '本次仅影响 2 门课程、1 个账号。', hit: false },
    ],
    evidence: '变更操作有完整审批记录与 traceId，未发现越权范围扩大；冲突来自角色配置而非本次操作。',
    status: 'PENDING',
  },
  {
    id: 'ANO-20261108-03',
    ref: 'AUD-20261109-084210',
    kind: 'LOGIN',
    title: '登录风险异常：校外网络 + 新设备指纹',
    account: '王老师 · T2021043',
    riskScore: 48,
    metrics: {
      FREQUENCY: '1 次登录 / 24 小时（阈值内）',
      PERMISSION: '登录后访问均在课程范围内',
      LOGIN: '校外 IP 183.12.8.46 · 新设备',
    },
    factors: [
      { title: '新设备指纹', desc: '与近 30 天常用设备不一致。', hit: true },
      { title: '校外网络', desc: '来源 IP 不属于校内网段。', hit: true },
      { title: '二次验证通过', desc: '动态验证码校验成功。', hit: false },
    ],
    evidence: '登录成功后仅访问《数据库原理》课程文档 2 份，行为与教学场景一致，本人已确认为出差期间登录。',
    status: 'DONE',
    conclusion: '误报',
    note: '本人确认为出差登录，设备已加入可信列表，观察 7 天。',
  },
]

export function listAnomalies() {
  return delay(ANOMALIES.map((a) => ({ ...a })))
}

export function resolveAnomaly(id: string, conclusion: string, note: string) {
  update(ANOMALIES, (a) => a.id === id, { status: 'DONE', conclusion, note })
  return delay(true, 160)
}

/** 屏 14 权限冲突复核。 */
export type ConflictKind = 'SOD' | 'DUPLICATE' | 'EXPIRED'
export type ConflictStatus = 'PENDING' | 'DONE'

export const conflictKindLabel: Record<ConflictKind, string> = {
  SOD: '职责分离冲突',
  DUPLICATE: '重复授权',
  EXPIRED: '过期临时权限',
}

export const conflictResolutions = ['拆分权限，保留职责分离', '保留并记录原因', '到期自动回收', '判定为误报'] as const

export type PermConflict = {
  id: string
  target: string
  targetMeta: string
  kind: ConflictKind
  item: string
  level: RiskLevel
  foundAt: string
  status: ConflictStatus
  resolution?: string
}

const CONFLICTS: PermConflict[] = [
  { id: 'CF-20261109-01', target: '赵工', targetMeta: 'A2001 · 运维管理员', kind: 'SOD', item: '审计日志查看 + 角色变更', level: 'HIGH', foundAt: '2026-11-09 09:18', status: 'PENDING' },
  { id: 'CF-20261108-02', target: '李老师', targetMeta: 'T2021001 · 教师 / 课程负责人', kind: 'DUPLICATE', item: '课程成员管理权限来自角色与课程组两处', level: 'MEDIUM', foundAt: '2026-11-08 15:22', status: 'PENDING' },
  { id: 'CF-20261108-03', target: '王老师', targetMeta: 'T2021043 · 临时批改授权', kind: 'EXPIRED', item: '《操作系统》临时批改权限今日到期', level: 'LOW', foundAt: '2026-11-08 11:05', status: 'PENDING' },
  { id: 'CF-20261107-04', target: '陈工', targetMeta: 'A2007 · 运维值班', kind: 'SOD', item: '存储清理 + 清理审批', level: 'HIGH', foundAt: '2026-11-07 17:40', status: 'DONE', resolution: '拆分权限，保留职责分离' },
]

export function listConflicts() {
  return delay(CONFLICTS.map((c) => ({ ...c })))
}

export function resolveConflict(id: string, resolution: string) {
  update(CONFLICTS, (c) => c.id === id, { status: 'DONE', resolution })
  return delay(true, 160)
}

/** 屏 13 访问策略：四组配置项，值类型只有开关与单选。 */
export type PolicyGroup = '统一身份认证' | '课程访问' | '会话安全' | '敏感操作验证'
export const policyGroups: PolicyGroup[] = ['统一身份认证', '课程访问', '会话安全', '敏感操作验证']

export type PolicyItem = {
  id: string
  group: PolicyGroup
  label: string
  desc: string
  type: 'switch' | 'select'
  value: boolean | string
  options?: string[]
}

const POLICIES: PolicyItem[] = [
  { id: 'P-01', group: '统一身份认证', label: '强制统一身份认证', desc: '教师、学生和运维账号均使用校内身份服务登录。', type: 'switch', value: true },
  { id: 'P-02', group: '统一身份认证', label: '异常登录二次验证', desc: '检测到异地或新设备登录时要求动态验证码。', type: 'switch', value: true },
  { id: 'P-03', group: '统一身份认证', label: '本地账号兜底登录', desc: '身份服务不可用时允许运维账号本地登录。', type: 'switch', value: false },
  { id: 'P-04', group: '课程访问', label: '课程文档以课程身份为准', desc: '运维角色不继承课程文档访问权限。', type: 'switch', value: true },
  { id: 'P-05', group: '课程访问', label: '结课课程自动只读', desc: '结课超过 30 天的课程空间转为只读。', type: 'switch', value: true },
  { id: 'P-06', group: '课程访问', label: '跨课程访问审批人', desc: '申请课程范围外文档时的审批角色。', type: 'select', value: '课程负责人审批', options: ['课程负责人审批', '院系教学审核员', '禁止跨课程访问'] },
  { id: 'P-07', group: '课程访问', label: '共享链接有效期', desc: '课程内共享链接的最长有效期。', type: 'select', value: '30 天', options: ['7 天', '30 天', '90 天'] },
  { id: 'P-08', group: '会话安全', label: '会话空闲超时', desc: '超过 30 分钟无操作自动退出。', type: 'switch', value: true },
  { id: 'P-09', group: '会话安全', label: '新设备登录提醒', desc: '通过站内消息与邮件通知本人。', type: 'switch', value: true },
  { id: 'P-10', group: '会话安全', label: '单次登录有效期', desc: '令牌最长有效期，到期需重新认证。', type: 'select', value: '12 小时', options: ['8 小时', '12 小时', '24 小时'] },
  { id: 'P-11', group: '会话安全', label: '并发会话上限', desc: '同一账号可同时保持的会话数。', type: 'select', value: '3 个', options: ['1 个', '3 个', '5 个'] },
  { id: 'P-12', group: '敏感操作验证', label: '角色变更二次确认', desc: '要求二次确认并填写变更原因。', type: 'switch', value: true },
  { id: 'P-13', group: '敏感操作验证', label: '账号冻结二次确认', desc: '要求输入完整工号或学号后执行。', type: 'switch', value: true },
  { id: 'P-14', group: '敏感操作验证', label: '存储清理审批', desc: '要求管理员审批并保留对象清单。', type: 'switch', value: true },
]

export type PolicyValues = Record<string, boolean | string>

export function listPolicies() {
  return delay(POLICIES.map((p) => ({ ...p })))
}

/** 保存策略并返回发生变更的策略组，供页面写入配置变更记录（屏 24）。 */
export function savePolicies(values: PolicyValues): Promise<PolicyGroup[]> {
  const changed = new Set<PolicyGroup>()
  POLICIES.forEach((p) => {
    const next = values[p.id]
    if (next !== undefined && next !== p.value) {
      p.value = next
      changed.add(p.group)
    }
  })
  return delay([...changed], 200)
}
