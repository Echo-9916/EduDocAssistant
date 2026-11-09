// 协作与版本门面演示数据（学生 16-21 / 教师 15-19 屏）。
// 演示文档沿用原型：《Web开发课程设计报告_张同学_v3.docx》 · 《Web开发技术》。
// 口径：版本不可变——恢复不覆盖历史，而是复制生成新版本（说明标注「由 vN 恢复生成」）。

import { ago, delay, insert, nextId, remove, update } from '../store'
import type { RoleCodeValue } from '../../api/types'

/** 当前登录视角的文档所有者姓名（写操作记录用）。 */
export function ownerName(role: RoleCodeValue): string {
  return role === 'TEACHER' ? '李老师' : '张同学'
}

/* ---------- 版本记录 / 对比 / 恢复（学生 17/18/19 · 教师 16/17/18） ---------- */

export type VersionSource = 'UPLOAD' | 'EDIT' | 'FIX' | 'COLLAB' | 'RESTORE' | 'SUBMIT'

export const versionSourceLabel: Record<VersionSource, string> = {
  UPLOAD: '上传',
  EDIT: '编辑',
  FIX: '修复',
  COLLAB: '协作',
  RESTORE: '恢复',
  SUBMIT: '提交',
}

export const versionSourceColor: Record<VersionSource, string> = {
  UPLOAD: 'blue',
  EDIT: 'geekblue',
  FIX: 'orange',
  COLLAB: 'purple',
  RESTORE: 'cyan',
  SUBMIT: 'green',
}

export type VersionDiff = {
  /** 对比基线（上一版本）侧的差异行。 */
  before: string[]
  /** 本版本侧的差异行。 */
  after: string[]
  /** 格式规则变化。 */
  format: string[]
}

export type DocVersion = {
  id: string
  version: string
  source: VersionSource
  note: string
  author: string
  time: string
  size: string
  current: boolean
  /** 与上一版本的差异；首个版本为 null（无更早基线）。 */
  diff: VersionDiff | null
}

const VERSIONS: DocVersion[] = [
  {
    id: 'VER-6',
    version: 'v6',
    source: 'FIX',
    note: '格式修复与图表引用更新',
    author: '张同学',
    time: ago(0, 2),
    size: '2.9 MB',
    current: true,
    diff: {
      before: ['3.2 节：接口说明未包含越权处理结果。', '图表题注沿用旧编号「图3-1」。'],
      after: ['3.2 节：补充越权访问拒绝与审计日志说明。', '图表题注统一为「图 3-1 系统总体架构」。'],
      format: ['页边距修复为 2.54 cm', '代码块字体统一为 Consolas 10.5 pt'],
    },
  },
  {
    id: 'VER-5',
    version: 'v5',
    source: 'COLLAB',
    note: '合并陈同学的系统设计章节',
    author: '陈同学',
    time: ago(0, 5),
    size: '2.8 MB',
    current: false,
    diff: {
      before: ['3.2 节仅有接口清单，缺少流程说明。'],
      after: ['3.2 节补充权限校验流程图与文字说明。', '新增 3.3 节异步任务状态机描述。'],
      format: ['图 3-2 题注按模板 v3 规则重新编号'],
    },
  },
  {
    id: 'VER-4',
    version: 'v4',
    source: 'EDIT',
    note: '应用 1 段文本润色',
    author: '张同学',
    time: ago(1, 3),
    size: '2.7 MB',
    current: false,
    diff: {
      before: ['3.1 节：「由于格式调整占用了较多时间……」表述冗长。'],
      after: ['3.1 节：润色为「针对格式调整耗时的问题……」，保持原意。'],
      format: [],
    },
  },
  {
    id: 'VER-3',
    version: 'v3',
    source: 'SUBMIT',
    note: '提交《Web开发技术》作业 2',
    author: '张同学',
    time: ago(2),
    size: '2.7 MB',
    current: false,
    diff: {
      before: ['提交前存在 2 项格式警告（目录页码、题注）。'],
      after: ['生成提交快照，警告项已在提交确认页核对。'],
      format: ['提交快照锁定为只读'],
    },
  },
  {
    id: 'VER-2',
    version: 'v2',
    source: 'UPLOAD',
    note: '首次上传并完成解析',
    author: '张同学',
    time: ago(3),
    size: '2.6 MB',
    current: false,
    diff: null,
  },
]

export function listVersions() {
  return delay(VERSIONS.map((v) => ({ ...v })), 320)
}

function nextVersionNo(): number {
  return Math.max(...VERSIONS.map((v) => Number(v.version.slice(1)))) + 1
}

/** 恢复：不覆盖历史，复制目标版本生成新版本并标记为当前。返回最新列表。 */
export function restoreVersion(id: string, operator: string) {
  const src = VERSIONS.find((v) => v.id === id)
  const prevCurrent = VERSIONS.find((v) => v.current)
  if (!src) return delay(VERSIONS.map((v) => ({ ...v })), 150)
  update(VERSIONS, () => true, { current: false })
  const row: DocVersion = {
    id: nextId('VER'),
    version: `v${nextVersionNo()}`,
    source: 'RESTORE',
    note: `由 ${src.version} 恢复生成`,
    author: operator,
    time: ago(0, 0),
    size: src.size,
    current: true,
    diff: {
      before: [`恢复前当前版本 ${prevCurrent?.version ?? src.version}：${prevCurrent?.note ?? src.note}`],
      after: [`内容回退到 ${src.version}：${src.note}`],
      format: ['恢复后需重新执行格式校验'],
    },
  }
  insert(VERSIONS, row)
  return delay(VERSIONS.map((v) => ({ ...v })), 200)
}

/* ---------- 协作编辑：在线成员 / 批注 / 活动流（学生 16 · 教师 15） ---------- */

export const ONLINE_MEMBERS = [
  { name: '张同学', color: '#2563eb' },
  { name: '陈同学', color: '#0e7490' },
  { name: '李老师', color: '#6d28d9' },
]

export type CommentStatus = 'OPEN' | 'RESOLVED'

export type DocComment = {
  id: string
  author: string
  authorRole: '教师' | '学生'
  /** 批注锚点段落。 */
  anchor: string
  content: string
  status: CommentStatus
  time: string
}

const COMMENTS: DocComment[] = [
  {
    id: 'CMT-1',
    author: '李老师',
    authorRole: '教师',
    anchor: '3.2 权限校验 · 第 9 页',
    content: '系统设计部分建议补充权限校验流程，说明越权访问的处理结果，并与评分标准对应。',
    status: 'OPEN',
    time: ago(0, 3),
  },
  {
    id: 'CMT-2',
    author: '陈同学',
    authorRole: '学生',
    anchor: '3.2 接口说明 · 第 10 页',
    content: '我更新了第 3.2 节的接口说明，请确认是否与测试部分的描述一致。',
    status: 'OPEN',
    time: ago(0, 4),
  },
  {
    id: 'CMT-3',
    author: '刘同学',
    authorRole: '学生',
    anchor: '图表题注 · 第 14 页',
    content: '版本已保存，图表题注部分已交给张同学继续处理。',
    status: 'RESOLVED',
    time: ago(0, 6),
  },
]

export type Activity = { id: string; author: string; action: string; time: string }

const ACTIVITIES: Activity[] = [
  { id: 'ACT-1', author: '张同学', action: '保存并生成版本 v6（格式修复）', time: ago(0, 2) },
  { id: 'ACT-2', author: '李老师', action: '添加了教师批注（权限校验流程）', time: ago(0, 3) },
  { id: 'ACT-3', author: '陈同学', action: '编辑了「3.2 接口说明」段落', time: ago(0, 3) },
  { id: 'ACT-4', author: '刘同学', action: '解决了批注「图表题注编号」', time: ago(0, 6) },
]

export function listComments() {
  return delay(COMMENTS.map((c) => ({ ...c })), 280)
}

export function listActivities() {
  return delay(ACTIVITIES.map((a) => ({ ...a })), 200)
}

/** 解决/重新打开批注：写回数组，并追加一条活动流记录。返回最新批注列表。 */
export function setCommentStatus(id: string, status: CommentStatus, operator: string) {
  update(COMMENTS, (c) => c.id === id, { status })
  const target = COMMENTS.find((c) => c.id === id)
  insert(ACTIVITIES, {
    id: nextId('ACT'),
    author: operator,
    action: status === 'RESOLVED' ? `将批注「${target?.anchor ?? ''}」标记为已解决` : `重新打开了批注「${target?.anchor ?? ''}」`,
    time: ago(0, 0),
  })
  return delay(COMMENTS.map((c) => ({ ...c })), 150)
}

/* ---------- 共享设置 / 协作者（学生 20/21 · 教师 19） ---------- */

export type ShareScope = 'PRIVATE' | 'COURSE' | 'LINK'

export const shareScopeOptions: { value: ShareScope; label: string; desc: string }[] = [
  { value: 'PRIVATE', label: '仅本人', desc: '仅文档所有者可以访问。' },
  { value: 'COURSE', label: '课程组', desc: '《Web开发技术》课程组成员可查看，指定协作者可编辑。' },
  { value: 'LINK', label: '共享链接', desc: '获得链接的校内账号可访问，需登录校园身份认证，链接可随时关闭。' },
]

export const linkExpiryOptions = ['24 小时', '7 天', '30 天']

export type CollabPerm = '可编辑' | '可评论' | '仅查看'

export const collabPermOptions: CollabPerm[] = ['可编辑', '可评论', '仅查看']

export type Collaborator = {
  id: string
  name: string
  account: string
  memberRole: '教师' | '学生'
  /** 所有者不参与权限调整与移除。 */
  perm: CollabPerm | '所有者'
  owner?: boolean
  time: string
}

const COLLABORATORS: Collaborator[] = [
  { id: 'CO-1', name: '张同学', account: 'zhang@campus.edu.cn', memberRole: '学生', perm: '所有者', owner: true, time: ago(6) },
  { id: 'CO-2', name: '李老师', account: 'li@campus.edu.cn', memberRole: '教师', perm: '可评论', time: ago(5) },
  { id: 'CO-3', name: '陈同学', account: 'chen@campus.edu.cn', memberRole: '学生', perm: '可编辑', time: ago(3) },
]

/** 添加协作者候选人（课程组成员，未在协作者列表中）。 */
export const COLLAB_CANDIDATES = [
  { name: '王老师', account: 'wang@campus.edu.cn', memberRole: '教师' as const },
  { name: '刘同学', account: 'liu@campus.edu.cn', memberRole: '学生' as const },
]

export type PermLog = { id: string; content: string; operator: string; time: string }

const PERM_LOGS: PermLog[] = [
  { id: 'LOG-1', content: '将共享范围调整为「课程组」', operator: '张同学', time: ago(4) },
  { id: 'LOG-2', content: '将 陈同学 的权限调整为「可编辑」', operator: '张同学', time: ago(3) },
  { id: 'LOG-3', content: '邀请 李老师 加入协作（可评论）', operator: '张同学', time: ago(5) },
]

const shareState = {
  scope: 'COURSE' as ShareScope,
  linkExpiry: '7 天',
  link: 'https://edu.example/doc/7Q4K2M',
}

export type ShareData = typeof shareState & { collaborators: Collaborator[]; logs: PermLog[] }

function pushLog(content: string, operator: string) {
  insert(PERM_LOGS, { id: nextId('LOG'), content, operator, time: ago(0, 0) })
}

export function getShare() {
  const data: ShareData = {
    ...shareState,
    collaborators: COLLABORATORS.map((c) => ({ ...c })),
    logs: PERM_LOGS.map((l) => ({ ...l })),
  }
  return delay(data, 300)
}

export function setShareScope(scope: ShareScope, operator: string) {
  shareState.scope = scope
  const label = shareScopeOptions.find((o) => o.value === scope)?.label ?? scope
  pushLog(`将共享范围调整为「${label}」`, operator)
  return delay(true, 150)
}

export function setLinkExpiry(linkExpiry: string, operator: string) {
  shareState.linkExpiry = linkExpiry
  pushLog(`将链接有效期调整为「${linkExpiry}」`, operator)
  return delay(true, 150)
}

export function addCollaborator(name: string, perm: CollabPerm, operator: string) {
  const cand = COLLAB_CANDIDATES.find((c) => c.name === name)
  insert(COLLABORATORS, {
    id: nextId('CO'),
    name,
    account: cand?.account ?? '',
    memberRole: cand?.memberRole ?? '学生',
    perm,
    time: ago(0, 0),
  })
  pushLog(`邀请 ${name} 加入协作（${perm}）`, operator)
  return delay(true, 200)
}

export function setCollabPerm(id: string, perm: CollabPerm, operator: string) {
  const target = COLLABORATORS.find((c) => c.id === id)
  update(COLLABORATORS, (c) => c.id === id, { perm })
  pushLog(`将 ${target?.name ?? ''} 的权限调整为「${perm}」`, operator)
  return delay(true, 150)
}

export function removeCollaborator(id: string, operator: string) {
  const target = COLLABORATORS.find((c) => c.id === id)
  remove(COLLABORATORS, (c) => c.id === id)
  pushLog(`移除协作者 ${target?.name ?? ''}`, operator)
  return delay(true, 150)
}
