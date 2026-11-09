// 存储与资源 fixture（运维 21–24 屏）。基准时间 2026-11-09，容量单位统一 GB。
// 回收站保留期沿用数据库设计：默认 30 天，purge_at 到期后清理版本与派生对象。
import { delay, insert, nextId, update } from '../store'

export type StorageStatus = 'NORMAL' | 'PENDING_CLEAN' | 'RECYCLE' | 'CLEANED'
export type ServiceHealth = 'NORMAL' | 'DEGRADED' | 'DOWN'
export type ChangeStatus = 'ACTIVE' | 'PENDING' | 'ROLLED_BACK'

export const storageStatusLabel: Record<StorageStatus, string> = {
  NORMAL: '正常',
  PENDING_CLEAN: '待清理',
  RECYCLE: '回收站',
  CLEANED: '已清理',
}

export const serviceHealthLabel: Record<ServiceHealth, string> = {
  NORMAL: '正常',
  DEGRADED: '降级',
  DOWN: '中断',
}

export const changeStatusLabel: Record<ChangeStatus, string> = {
  ACTIVE: '生效',
  PENDING: '待审批',
  ROLLED_BACK: '已回滚',
}

/** GB → 可读容量：小于 1 GB 显示 MB。 */
export function fmtGb(gb: number): string {
  return gb >= 1 ? `${gb.toFixed(2)} GB` : `${Math.round(gb * 1024)} MB`
}

export function fmtTb(gb: number): string {
  return `${(gb / 1024).toFixed(2)} TB`
}

export type StorageObject = {
  id: string
  name: string
  note: string
  course: string
  sizeGb: number
  status: StorageStatus
  /** 保留期：回收站对象为 30 天，正常对象为长期保留 */
  retention: string
  /** 恢复窗口说明，屏 22 清理确认展示 */
  recoverWindow: string
  lastAccess: string
}

const OBJECTS: StorageObject[] = [
  { id: 'OBJ-01', name: '旧版课程设计模板.docx', note: '模板版本 v1.5，已被 v3 取代', course: '《Web开发技术》', sizeGb: 0.041, status: 'RECYCLE', retention: '30 天（剩余 5 天）', recoverWindow: '2026-11-14 前可从回收站恢复', lastAccess: '2026-10-20 14:12' },
  { id: 'OBJ-02', name: '批量导出_1108.zip', note: '含 36 个 PDF，下载有效期已结束', course: '下载中心', sizeGb: 1.2, status: 'RECYCLE', retention: '30 天（剩余 10 天）', recoverWindow: '2026-11-19 前可从回收站恢复', lastAccess: '2026-11-08 18:26' },
  { id: 'OBJ-03', name: '旧版用户导入名单.xlsx', note: '教务名单快照，仅保留最近一次成功导入', course: '用户目录', sizeGb: 0.0024, status: 'PENDING_CLEAN', retention: '已过期（超 30 天）', recoverWindow: '超出恢复窗口，清理后不可恢复', lastAccess: '2026-10-18 09:40' },
  { id: 'OBJ-04', name: '解析中间产物（分块与向量缓存）', note: '文档解析失败后残留，可安全重建', course: '《Web开发技术》', sizeGb: 0.64, status: 'PENDING_CLEAN', retention: '已过期（超 30 天）', recoverWindow: '超出恢复窗口，清理后由解析服务重建', lastAccess: '2026-10-12 03:00' },
  { id: 'OBJ-05', name: '《数据库原理》作业 2 提交包', note: '126 份学生提交，含格式校验结果', course: '《数据库原理》', sizeGb: 0.32, status: 'NORMAL', retention: '长期保留（结课后转归档）', recoverWindow: '删除后进入回收站，保留 30 天', lastAccess: '2026-11-09 08:55' },
  { id: 'OBJ-06', name: '课程版本快照 v1–v12', note: '《操作系统》课程报告历史版本链', course: '《操作系统》', sizeGb: 1.2, status: 'NORMAL', retention: '长期保留（版本链不可裁剪）', recoverWindow: '删除后进入回收站，保留 30 天', lastAccess: '2026-11-07 16:20' },
  { id: 'OBJ-07', name: '课程模板 v2 派生 PDF', note: '按模板新建时生成的只读派生对象', course: '《编译原理》', sizeGb: 0.086, status: 'NORMAL', retention: '长期保留', recoverWindow: '删除后进入回收站，保留 30 天', lastAccess: '2026-11-06 10:02' },
  { id: 'OBJ-08', name: '失效临时预览 20 个', note: '已无关联文档的预览缓存', course: '下载中心', sizeGb: 0.08, status: 'RECYCLE', retention: '30 天（已过期）', recoverWindow: '超出恢复窗口，清理后不可恢复', lastAccess: '2026-10-08 11:30' },
]

/** 容量总览：清理后就地扣减，页面可见容量变化。 */
const CAPACITY = { quotaGb: 2048, usedGb: 1392, recycleGb: 4.8, expiredCount: 128 }

export type Capacity = typeof CAPACITY

export function getCapacity(): Capacity {
  return { ...CAPACITY }
}

/** 可释放容量：待清理 + 回收站对象之和，容量总览卡用。 */
export function reclaimableGb(): number {
  return +OBJECTS.filter((o) => o.status === 'PENDING_CLEAN' || o.status === 'RECYCLE')
    .reduce((sum, o) => sum + o.sizeGb, 0)
    .toFixed(3)
}

export function listStorageObjects(status: StorageStatus | 'ALL' = 'ALL', keyword = '') {
  const kw = keyword.trim().toLowerCase()
  const rows = OBJECTS.filter(
    (o) =>
      (status === 'ALL' || o.status === status) &&
      (!kw || [o.name, o.course, o.note].some((v) => v.toLowerCase().includes(kw))),
  )
  return delay(rows.map((o) => ({ ...o })))
}

/** 屏 22 永久清理：状态改已清理并释放容量。 */
export function cleanupObject(id: string): Promise<string> {
  const obj = OBJECTS.find((o) => o.id === id)
  if (!obj) return delay('')
  update(OBJECTS, (o) => o.id === id, { status: 'CLEANED', recoverWindow: '已永久删除，不可恢复' })
  CAPACITY.usedGb = Math.max(0, +(CAPACITY.usedGb - obj.sizeGb).toFixed(4))
  if (obj.status === 'RECYCLE') CAPACITY.recycleGb = Math.max(0, +(CAPACITY.recycleGb - obj.sizeGb).toFixed(4))
  if (obj.status === 'PENDING_CLEAN') CAPACITY.expiredCount = Math.max(0, CAPACITY.expiredCount - 1)
  return delay(fmtGb(obj.sizeGb), 200)
}

/** 从回收站恢复：状态回到正常，容量从回收站转回在用。 */
export function restoreObject(id: string): Promise<string> {
  const obj = OBJECTS.find((o) => o.id === id)
  if (!obj) return delay('')
  update(OBJECTS, (o) => o.id === id, {
    status: 'NORMAL',
    retention: '长期保留',
    recoverWindow: '删除后进入回收站，保留 30 天',
  })
  CAPACITY.recycleGb = Math.max(0, +(CAPACITY.recycleGb - obj.sizeGb).toFixed(4))
  return delay(obj.name, 200)
}

export type ServiceStatusRow = {
  id: string
  name: string
  health: ServiceHealth
  p95: string
  backlog: number
  lastCheck: string
  /** 队列积压说明，异常行的「查看积压任务」展示 */
  backlogNote: string
}

const SERVICES: ServiceStatusRow[] = [
  { id: 'SVC-AUTH', name: '身份认证', health: 'NORMAL', p95: '86 ms', backlog: 0, lastCheck: '2026-11-09 10:55', backlogNote: '无积压任务。' },
  { id: 'SVC-PARSE', name: '文档解析', health: 'DEGRADED', p95: '8.4 s', backlog: 47, lastCheck: '2026-11-09 10:55', backlogNote: '47 条解析任务积压，平均等待 6 分 12 秒，已超过 5 分钟阈值；当前并发 8，扩缩容窗口 22:00–23:00。' },
  { id: 'SVC-VECTOR', name: '向量化', health: 'NORMAL', p95: '1.2 s', backlog: 6, lastCheck: '2026-11-09 10:54', backlogNote: '6 条向量化任务在队列中，属正常水位。' },
  { id: 'SVC-COLLAB', name: '协作同步', health: 'NORMAL', p95: '42 ms', backlog: 0, lastCheck: '2026-11-09 10:55', backlogNote: '无积压任务。' },
  { id: 'SVC-STORAGE', name: '存储', health: 'NORMAL', p95: '128 ms', backlog: 3, lastCheck: '2026-11-09 10:53', backlogNote: '3 个清理任务待执行（每月 1 日 02:00 计划任务）。' },
  { id: 'SVC-NOTIFY', name: '通知', health: 'DOWN', p95: '—', backlog: 214, lastCheck: '2026-11-09 10:50', backlogNote: '短信网关不可用，214 条通知积压；站内消息与邮件通道正常，高风险告警改由电话通道兜底。' },
]

export function listServices() {
  return delay(SERVICES.map((s) => ({ ...s })))
}

/** 刷新状态：更新最近检查时间，让按钮有可见反应。 */
export function refreshServices(): Promise<ServiceStatusRow[]> {
  update(SERVICES, () => true, { lastCheck: '2026-11-09 11:05' })
  return delay(SERVICES.map((s) => ({ ...s })), 200)
}

export type ConfigChange = {
  id: string
  time: string
  item: string
  detail: string
  scope: string
  submitter: string
  approver: string
  status: ChangeStatus
  rollbackTo: string
}

const CHANGES: ConfigChange[] = [
  { id: 'CFG-20261109-01', time: '2026-11-09 09:30', item: '文档解析并发上限调整', detail: '并发 8 → 12', scope: '文档解析服务', submitter: '陈工', approver: '赵工', status: 'ACTIVE', rollbackTo: '并发上限 8' },
  { id: 'CFG-20261108-02', time: '2026-11-08 17:20', item: '异常登录阈值调整', detail: '5 次 → 3 次 / 10 分钟', scope: '身份认证服务', submitter: '赵工', approver: '陈工', status: 'ACTIVE', rollbackTo: '阈值 5 次 / 10 分钟' },
  { id: 'CFG-20261025-03', time: '2026-10-25 11:05', item: '存储清理周期调整', detail: '自动清理 30 天 → 45 天', scope: '存储服务', submitter: '陈工', approver: '赵工', status: 'ROLLED_BACK', rollbackTo: '保留期 30 天' },
  { id: 'CFG-20261023-04', time: '2026-10-23 15:48', item: '通知发送限流', detail: '每分钟 300 → 500', scope: '通知服务', submitter: '赵工', approver: '陈工', status: 'ACTIVE', rollbackTo: '每分钟 300 条' },
  { id: 'CFG-20261109-05', time: '2026-11-09 08:12', item: '结课课程自动只读', detail: '结课 30 天后转只读', scope: '课程访问策略', submitter: '赵工', approver: '待审批', status: 'PENDING', rollbackTo: '不自动转只读' },
]

export function listConfigChanges() {
  return delay(CHANGES.map((c) => ({ ...c })))
}

/** 回滚：就地改状态，已生效项才可回滚。 */
export function rollbackChange(id: string) {
  update(CHANGES, (c) => c.id === id, { status: 'ROLLED_BACK' })
  return delay(true, 160)
}

/** 访问策略保存后追加一条待审批的变更记录（屏 13 → 屏 24 联动）。 */
export function recordPolicyChange(groups: string[]): Promise<string> {
  if (!groups.length) return delay('')
  const id = nextId('CFG-20261109')
  insert(CHANGES, {
    id,
    time: '2026-11-09 11:10',
    item: '访问策略调整',
    detail: `${groups.join(' / ')} 配置项变更`,
    scope: groups.join(' / '),
    submitter: '赵工',
    approver: '待审批',
    status: 'PENDING',
    rollbackTo: '变更前策略',
  })
  return delay(id, 200)
}
