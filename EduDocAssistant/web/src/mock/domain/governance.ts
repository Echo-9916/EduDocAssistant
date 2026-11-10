import { ago, delay, insert, nextId, update } from '../store'
import { PermissionCode } from '../../api/types'
import type { AccountStatusValue, PermissionCodeValue } from '../../api/types'

// 运维端演示数据：工作台（01 屏）、课程治理（02–06 屏）、用户与权限（07–12 屏）。
// 世界观与 messages.ts 一致：学期 2026-2027 秋，基准时间 2026-11-09，运维 赵工。

/* ============================== 课程治理（02–06 屏） ============================== */

export type CourseStatus = 'RUNNING' | 'CLOSED' | 'ARCHIVED'

export const courseStatusLabel: Record<CourseStatus, string> = {
  RUNNING: '运行中',
  CLOSED: '已结课',
  ARCHIVED: '已归档',
}
export const courseStatusColor: Record<CourseStatus, string> = {
  RUNNING: 'green',
  CLOSED: 'blue',
  ARCHIVED: 'default',
}

export type GovCourse = {
  id: string
  code: string
  name: string
  term: string
  dept: string
  owner: string
  /** 课程组 */
  group: string
  teachers: number
  students: number
  /** 关联模板（含版本） */
  template: string
  weeklySubmits: number
  formatIssues: number
  status: CourseStatus
  createdAt: string
  /** 最近运行状态（详情抽屉时间线） */
  recent: string[]
}

const SEED_COURSES: GovCourse[] = [
  {
    id: 'CRS-01', code: 'CS-3101', name: 'Web开发技术', term: '2026-2027 秋', dept: '计算机学院', owner: '李老师',
    group: 'Web开发课程组', teachers: 2, students: 34, template: 'Web开发课程设计模板 v2.1',
    weeklySubmits: 28, formatIssues: 4, status: 'RUNNING', createdAt: ago(63),
    recent: ['教务名单于今天 09:12 更新，新增 1 名学生，已自动发送课程邀请。', '李老师上传课程模板 v2.2 草稿，待发布。', '作业提交检查完成：28 份提交 · 24 份格式通过。'],
  },
  {
    id: 'CRS-02', code: 'CS-3202', name: '数据库原理', term: '2026-2027 秋', dept: '计算机学院', owner: '王老师',
    group: '数据库课程组', teachers: 2, students: 36, template: '数据库实验报告模板 v1.8',
    weeklySubmits: 31, formatIssues: 2, status: 'RUNNING', createdAt: ago(63),
    recent: ['3 名课程成员账号未绑定已超过 24 小时，已发送激活邀请。', '作业 3「索引设计实验」将于 2026-11-12 截止。'],
  },
  {
    id: 'CRS-03', code: 'CS-3305', name: '操作系统', term: '2026-2027 秋', dept: '计算机学院', owner: '王老师',
    group: '操作系统课程组', teachers: 2, students: 32, template: '操作系统课程报告模板 v1.3',
    weeklySubmits: 25, formatIssues: 5, status: 'RUNNING', createdAt: ago(63),
    recent: ['课程组 3 的 5 份报告页眉缺失课程号，已生成格式异常清单。'],
  },
  {
    id: 'CRS-04', code: 'CS-3408', name: '编译原理', term: '2026-2027 秋', dept: '软件学院', owner: '李老师',
    group: '编译课程组', teachers: 3, students: 128, template: '编译实验报告模板 v2.0',
    weeklySubmits: 40, formatIssues: 1, status: 'RUNNING', createdAt: ago(63),
    recent: ['课程模板 v2 已发布，覆盖 3 个班级共 128 名学生。'],
  },
  {
    id: 'CRS-05', code: 'CS-2102', name: '软件工程导论', term: '2025-2026 春', dept: '计算机学院', owner: '陈老师',
    group: '软件工程课程组', teachers: 2, students: 41, template: '软件工程模板 v1.2',
    weeklySubmits: 0, formatIssues: 0, status: 'ARCHIVED', createdAt: ago(240),
    recent: ['课程已归档，内容转为只读，师生保留历史查看权限。'],
  },
  {
    id: 'CRS-06', code: 'CS-2206', name: '计算机网络实验', term: '2025-2026 春', dept: '软件学院', owner: '王老师',
    group: '网络课程组', teachers: 2, students: 38, template: '通用课程报告模板 v1.4',
    weeklySubmits: 0, formatIssues: 0, status: 'CLOSED', createdAt: ago(240),
    recent: ['结课超过 30 天，课程治理建议归档为只读。'],
  },
]

export function listCourses() {
  return delay(SEED_COURSES.map((c) => ({ ...c, recent: [...c.recent] })))
}

export type CourseInput = { name: string; code: string; term: string; dept: string; owner: string; template: string }

export function createCourse(input: CourseInput) {
  const row: GovCourse = {
    id: nextId('CRS'), ...input, group: `${input.name}课程组`, teachers: 1, students: 0,
    weeklySubmits: 0, formatIssues: 0, status: 'RUNNING', createdAt: ago(0),
    recent: ['课程空间已创建，成员名单待从教务系统导入。'],
  }
  return delay(insert(SEED_COURSES, row), 200)
}

/** 成员导入（05 屏）：教务名单同步的绑定结果统计，确认后学生数 +added。 */
export const MEMBER_IMPORT = { rosterTotal: 36, bound: 4, pending: 2, conflict: 1, added: 6 }

export function importMembers(courseId: string) {
  const row = SEED_COURSES.find((c) => c.id === courseId)
  if (row) {
    row.students += MEMBER_IMPORT.added
    row.recent.unshift(`教务名单同步完成：成功 ${MEMBER_IMPORT.bound} · 待激活 ${MEMBER_IMPORT.pending} · 冲突跳过 ${MEMBER_IMPORT.conflict}，学生数 +${MEMBER_IMPORT.added}。`)
  }
  return delay(MEMBER_IMPORT, 300)
}

/** 归档（06 屏）：状态改「已归档」，课程进入只读口径。 */
export function archiveCourse(courseId: string, note: string) {
  const row = SEED_COURSES.find((c) => c.id === courseId)
  if (row) {
    row.status = 'ARCHIVED'
    row.recent.unshift(`课程已归档：${note}`)
  }
  return delay(true, 200)
}

/* ============================== 运维工作台（01 屏） ============================== */

export type RiskLevel = 'HIGH' | 'MID' | 'LOW'
export const riskLevelLabel: Record<RiskLevel, string> = { HIGH: '高', MID: '中', LOW: '低' }
export const riskLevelColor: Record<RiskLevel, string> = { HIGH: 'red', MID: 'orange', LOW: 'blue' }

export type RiskEvent = { id: string; level: RiskLevel; title: string; detail: string; time: string; pending: boolean }

const SEED_EVENTS: RiskEvent[] = [
  { id: 'EV-01', level: 'HIGH', title: '检测到越权访问尝试', detail: '账号 2024017 尝试访问未加入课程《Web开发技术》的资料，已被拒绝，待复核。', time: ago(0, 2), pending: true },
  { id: 'EV-02', level: 'HIGH', title: '文档解析队列积压', detail: '解析任务积压 47 条，平均等待 6 分 12 秒，已超过 5 分钟阈值。', time: ago(0, 1), pending: true },
  { id: 'EV-03', level: 'MID', title: '课程成员账号未绑定', detail: '《数据库原理》3 名成员超过 24 小时未绑定账号，已发送激活邀请。', time: ago(1, 3), pending: true },
  { id: 'EV-04', level: 'MID', title: '临时权限即将到期', detail: '3 个学期内授予的临时权限将于 7 天内到期，需权限冲突复核。', time: ago(1, 6), pending: true },
  { id: 'EV-05', level: 'LOW', title: '异常登录地检测', detail: '账号 T2021043 异地登录已自动阻断并通知本人，无需进一步处置。', time: ago(2, 4), pending: false },
]

export type ServiceHealth = { name: string; ok: boolean; latency: string }

const SEED_SERVICES: ServiceHealth[] = [
  { name: '文档解析服务', ok: false, latency: 'P95 8.4 s' },
  { name: '向量化服务', ok: true, latency: '126 ms' },
  { name: '协作同步服务', ok: true, latency: '42 ms' },
  { name: '通知服务', ok: true, latency: '86 ms' },
]

export type ApprovalType = 'ROLE_CHANGE' | 'CLEANUP' | 'ARCHIVE'
export const approvalTypeLabel: Record<ApprovalType, string> = {
  ROLE_CHANGE: '角色变更',
  CLEANUP: '清理确认',
  ARCHIVE: '归档确认',
}

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export const approvalStatusLabel: Record<ApprovalStatus, string> = {
  PENDING: '待处理',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

export type Approval = { id: string; type: ApprovalType; title: string; detail: string; from: string; time: string; status: ApprovalStatus }

const SEED_APPROVALS: Approval[] = [
  { id: 'APR-01', type: 'ROLE_CHANGE', title: '角色变更申请', detail: '王老师（T2021043）申请获得「课程模板管理员」角色，影响 4 门课程。', from: '审批流', time: ago(0, 4), status: 'PENDING' },
  { id: 'APR-02', type: 'CLEANUP', title: '回收站超期对象清理', detail: '预计释放 182 GB，清理后永久删除不可恢复，需确认保留期已过。', from: '存储治理', time: ago(1), status: 'PENDING' },
  { id: 'APR-03', type: 'ARCHIVE', title: '批量归档结课课程', detail: '6 门课程结课超过 30 天，建议归档为只读。', from: '课程治理', time: ago(2), status: 'PENDING' },
]

export type OpsStats = { activeCourses: number; activeAccounts: number; todayLogin: number; storagePct: number; storageNote: string }

export type WorkbenchData = { stats: OpsStats; events: RiskEvent[]; services: ServiceHealth[]; approvals: Approval[] }

export function loadWorkbench() {
  const data: WorkbenchData = {
    stats: { activeCourses: 26, activeAccounts: 2418, todayLogin: 1206, storagePct: 68, storageNote: '1.36 TB / 2 TB' },
    events: SEED_EVENTS.map((e) => ({ ...e })),
    services: SEED_SERVICES.map((s) => ({ ...s })),
    approvals: SEED_APPROVALS.map((a) => ({ ...a })),
  }
  return delay(data)
}

export function decideApproval(id: string, approve: boolean) {
  update(SEED_APPROVALS, (a) => a.id === id, { status: approve ? 'APPROVED' : 'REJECTED' })
  return delay(true, 160)
}

/* ============================== 用户与权限（07–12 屏） ============================== */

export const accountStatusLabel: Record<AccountStatusValue, string> = { A: '正常', I: '待激活', L: '已锁定', D: '已停用' }
export const accountStatusColor: Record<AccountStatusValue, string> = { A: 'green', I: 'orange', L: 'red', D: 'default' }

export type UserVisit = { time: string; event: string; source: string; result: 'SUCCESS' | 'DENIED' }

export type GovUser = {
  id: string
  /** 工号 / 学号 */
  accountNo: string
  name: string
  /** 内置角色编码或自定义角色 code */
  roleCode: string
  org: string
  status: AccountStatusValue
  /** 授权来源 */
  identitySource: string
  courseScope: string[]
  lastLogin: string
  registeredAt: string
  visits: UserVisit[]
}

const SEED_USERS: GovUser[] = [
  {
    id: 'USR-01', accountNo: 'T2021001', name: '李老师', roleCode: 'TEACHER', org: '计算机学院', status: 'A',
    identitySource: '统一身份认证 · 教务同步', courseScope: ['Web开发技术', '编译原理'], lastLogin: ago(0, 1), registeredAt: '2024-09-01',
    visits: [
      { time: ago(0, 1), event: '上传课程模板 v2.2 草稿', source: 'Web 端 / 10.20.8.31', result: 'SUCCESS' },
      { time: ago(1, 2), event: '批改《Web开发课程设计报告》并下发反馈', source: 'Web 端 / 10.20.8.31', result: 'SUCCESS' },
    ],
  },
  {
    id: 'USR-02', accountNo: 'T2021043', name: '王老师', roleCode: 'TEACHER', org: '计算机学院', status: 'A',
    identitySource: '统一身份认证 · 教务同步', courseScope: ['数据库原理', '操作系统', '计算机网络实验'], lastLogin: ago(0, 3), registeredAt: '2024-09-01',
    visits: [
      { time: ago(0, 4), event: '提交「课程模板管理员」角色变更申请', source: '审批流', result: 'SUCCESS' },
      { time: ago(1, 1), event: '导出作业 3 提交清单', source: 'Web 端 / 10.20.9.7', result: 'SUCCESS' },
    ],
  },
  {
    id: 'USR-03', accountNo: '2023001', name: '张同学', roleCode: 'STUDENT', org: '计算机学院 / 课程组 3', status: 'A',
    identitySource: '统一身份认证 · 教务名单导入', courseScope: ['Web开发技术', '数据库原理'], lastLogin: ago(0, 2), registeredAt: '2024-09-01',
    visits: [
      { time: ago(0, 2), event: '访问《Web开发课程设计报告》', source: 'Web 端 / 10.20.12.5', result: 'SUCCESS' },
      { time: ago(0, 5), event: '尝试访问未加入课程《编译原理》', source: 'Web 端 / 10.20.12.5', result: 'DENIED' },
    ],
  },
  {
    id: 'USR-04', accountNo: '2023002', name: '陈同学', roleCode: 'STUDENT', org: '计算机学院 / 课程组 3', status: 'A',
    identitySource: '统一身份认证 · 教务名单导入', courseScope: ['Web开发技术', '操作系统'], lastLogin: ago(1, 2), registeredAt: '2024-09-01',
    visits: [{ time: ago(1, 2), event: '提交文档版本 v3', source: 'Web 端 / 10.20.12.9', result: 'SUCCESS' }],
  },
  {
    id: 'USR-05', accountNo: '2023003', name: '刘同学', roleCode: 'STUDENT', org: '软件学院 / 课程组 5', status: 'I',
    identitySource: '教务名单导入 · 待激活', courseScope: ['编译原理'], lastLogin: '尚未登录', registeredAt: '2026-09-08',
    visits: [],
  },
  {
    id: 'USR-06', accountNo: '2024017', name: '钱同学', roleCode: 'STUDENT', org: '人工智能学院', status: 'L',
    identitySource: '统一身份认证 · 教务名单导入', courseScope: [], lastLogin: ago(0, 2), registeredAt: '2025-03-01',
    visits: [
      { time: ago(0, 2), event: '越权访问《Web开发技术》课程资料被拒绝', source: 'Web 端 / 10.20.31.6', result: 'DENIED' },
      { time: ago(0, 2), event: '账号已锁定，转入安全事件复核', source: '安全中心', result: 'SUCCESS' },
    ],
  },
  {
    id: 'USR-07', accountNo: 'A2001', name: '赵工', roleCode: 'OPS', org: '教务处 / 信息中心', status: 'A',
    identitySource: '手工创建 · 双因素认证', courseScope: ['全部课程（治理范围）'], lastLogin: ago(0, 1), registeredAt: '2023-08-15',
    visits: [
      { time: ago(0, 1), event: '查看操作日志并导出高风险事件清单', source: 'Web 端 / 10.20.1.2', result: 'SUCCESS' },
      { time: ago(0, 6), event: '审批存储清理任务', source: '运维工作台', result: 'SUCCESS' },
    ],
  },
  {
    id: 'USR-08', accountNo: 'T2019008', name: '陈老师', roleCode: 'TEACHER', org: '计算机学院', status: 'D',
    identitySource: '统一身份认证', courseScope: ['软件工程导论'], lastLogin: ago(120), registeredAt: '2023-09-01',
    visits: [{ time: ago(120), event: '离岗交接完成，账号停用，历史文档保留', source: '用户治理', result: 'SUCCESS' }],
  },
]

export function listUsers() {
  return delay(SEED_USERS.map((u) => ({ ...u, courseScope: [...u.courseScope], visits: u.visits.map((v) => ({ ...v })) })))
}

export type UserInput = { accountNo: string; name: string; roleCode: string; org: string; scope: string[] }

export function addUser(input: UserInput) {
  const row: GovUser = {
    id: nextId('USR'), accountNo: input.accountNo, name: input.name, roleCode: input.roleCode, org: input.org,
    status: 'I', identitySource: '统一身份认证 · 待绑定',
    courseScope: input.scope.filter((s) => s !== '暂不分配'),
    lastLogin: '尚未登录', registeredAt: ago(0).slice(0, 10), visits: [],
  }
  return delay(insert(SEED_USERS, row), 200)
}

/** 变更用户角色（12 屏）：就地更新并追加一条访问记录，口径为「立即写入审计日志」。 */
export function changeUserRole(userId: string, roleCode: string, effective: string, reason: string) {
  const row = SEED_USERS.find((u) => u.id === userId)
  if (row) {
    row.roleCode = roleCode
    row.visits.unshift({ time: ago(0), event: `角色变更（${effective}）：${reason}`, source: '运维工作台 / 赵工', result: 'SUCCESS' })
  }
  return delay(true, 200)
}

/* ---- 角色与权限矩阵（10/11 屏） ---- */

export type GovRole = { code: string; name: string; builtin: boolean; purpose: string; permissions: PermissionCodeValue[] }

const { DOCUMENT_READ, DOCUMENT_CREATE, DOCUMENT_EDIT, DOCUMENT_DELETE, DOCUMENT_DOWNLOAD, DOCUMENT_SHARE,
  AI_SUMMARY, AI_KEYPOINT, AI_QA, AI_POLISH, TEMPLATE_READ, TEMPLATE_MANAGE, FORMAT_CHECK, FORMAT_CONVERT,
  COLLAB_EDIT, VERSION_RESTORE, ROLE_MANAGE, AUDIT_READ, ASSIGNMENT_MANAGE, SUBMISSION_READ } = PermissionCode

const BASE_DOC_PERMS: PermissionCodeValue[] = [
  DOCUMENT_READ, DOCUMENT_CREATE, DOCUMENT_EDIT, DOCUMENT_DELETE, DOCUMENT_DOWNLOAD, DOCUMENT_SHARE,
  AI_SUMMARY, AI_KEYPOINT, AI_QA, AI_POLISH,
]

const SEED_ROLES: GovRole[] = [
  { code: 'STUDENT', name: '学生', builtin: true, purpose: '个人文档、协作与提交', permissions: [...BASE_DOC_PERMS, TEMPLATE_READ, FORMAT_CHECK, FORMAT_CONVERT, COLLAB_EDIT, VERSION_RESTORE, SUBMISSION_READ] },
  { code: 'TEACHER', name: '教师', builtin: true, purpose: '教学文档、课程与作业', permissions: [...BASE_DOC_PERMS, TEMPLATE_READ, TEMPLATE_MANAGE, FORMAT_CHECK, FORMAT_CONVERT, COLLAB_EDIT, VERSION_RESTORE, ASSIGNMENT_MANAGE, SUBMISSION_READ] },
  { code: 'OPS', name: '运维管理员', builtin: true, purpose: '课程治理、审计、安全与存储', permissions: [DOCUMENT_READ, TEMPLATE_READ, ROLE_MANAGE, AUDIT_READ] },
  { code: 'COURSE_AUDITOR', name: '院系教学审核员', builtin: false, purpose: '教学治理 · 查看课程运行数据、复核异常提交', permissions: [DOCUMENT_READ, SUBMISSION_READ, FORMAT_CHECK, AUDIT_READ] },
]

export function listRoles() {
  return delay(SEED_ROLES.map((r) => ({ ...r, permissions: [...r.permissions] })))
}

export const permissionLabel: Record<PermissionCodeValue, string> = {
  DOCUMENT_READ: '查看文档', DOCUMENT_CREATE: '上传文档', DOCUMENT_EDIT: '编辑文档', DOCUMENT_DELETE: '删除文档',
  DOCUMENT_DOWNLOAD: '下载文档', DOCUMENT_SHARE: '分享文档',
  AI_SUMMARY: 'AI 摘要', AI_KEYPOINT: '要点提取', AI_QA: '智能问答', AI_POLISH: 'AI 润色',
  TEMPLATE_READ: '查看模板', TEMPLATE_MANAGE: '管理模板', FORMAT_CHECK: '格式校验', FORMAT_CONVERT: '格式转换',
  COLLAB_EDIT: '协作编辑', VERSION_RESTORE: '版本恢复',
  ROLE_MANAGE: '角色管理', AUDIT_READ: '查看审计日志', ASSIGNMENT_MANAGE: '作业管理', SUBMISSION_READ: '查看提交',
  SUBMISSION_SELF_READ: '查看本人作业',
}

export const ALL_PERMISSIONS = Object.values(PermissionCode) as PermissionCodeValue[]

/** 互斥对：同授即构成职责冲突（10 屏风险提示口径）。 */
export const CONFLICT_PAIRS: { a: PermissionCodeValue; b: PermissionCodeValue; why: string }[] = [
  { a: ROLE_MANAGE, b: ASSIGNMENT_MANAGE, why: '教学管理者可同时修改自身权限边界，建议拆分职责' },
]

export function conflictsOf(perms: PermissionCodeValue[]) {
  return CONFLICT_PAIRS.filter(({ a, b }) => perms.includes(a) && perms.includes(b))
}

export function setRolePermission(roleCode: string, perm: PermissionCodeValue, checked: boolean) {
  const role = SEED_ROLES.find((r) => r.code === roleCode)
  if (role) {
    role.permissions = checked ? [...new Set([...role.permissions, perm])] : role.permissions.filter((p) => p !== perm)
  }
  return delay(true, 100)
}

export type RoleInput = { name: string; purpose: string; scope: string; permissions: PermissionCodeValue[] }

export function addRole(input: RoleInput) {
  const role: GovRole = { code: nextId('ROLE'), builtin: false, ...input }
  return delay(insert(SEED_ROLES, role), 200)
}

/** 添加用户弹窗的初始访问范围候选。 */
export const SCOPE_OPTIONS = ['Web开发技术', '数据库原理', '操作系统', '编译原理', '暂不分配']
