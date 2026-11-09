// 前端演示数据层：仅覆盖后端尚未实现的部分（协作版本 / 课程作业 / 个人中心 / 运维端全端）。
// 01–04 切片（认证、文档、智能处理、格式模板）仍走真实 /api/v1，本目录不参与。
// 约定：每个业务域一个 fixture 文件，导出数组 + delay() 模拟网络；写操作就地改内存数组，
// 所以刷新页面即回到种子状态，演示可反复讲同一个故事。

/** 模拟网络往返，让页面 loading 态与真实接口一致。 */
export function delay<T>(value: T, ms = 240): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

let seq = 1000

/** 演示主键：内存自增，够唯一即可。 */
export function nextId(prefix = 'ID'): string {
  seq += 1
  return `${prefix}-${seq}`
}

/** 数组集合的通用增删改：就地变更，返回副本供表格刷新。 */
export function insert<T>(rows: T[], row: T): T[] {
  rows.unshift(row)
  return [...rows]
}

export function remove<T>(rows: T[], predicate: (row: T) => boolean): T[] {
  for (let i = rows.length - 1; i >= 0; i -= 1) {
    if (predicate(rows[i])) rows.splice(i, 1)
  }
  return [...rows]
}

export function update<T>(rows: T[], predicate: (row: T) => boolean, patch: Partial<T>): T[] {
  rows.forEach((row, i) => {
    if (predicate(row)) rows[i] = { ...row, ...patch }
  })
  return [...rows]
}

/** 客户端分页：mock 数据量小，一次取全表再切片。 */
export function paginate<T>(rows: T[], page = 1, pageSize = 10): { items: T[]; total: number } {
  const start = (page - 1) * pageSize
  return { items: rows.slice(start, start + pageSize), total: rows.length }
}

/** 相对时间展示，固定基准时间避免演示时数字跳动。 */
export function ago(days: number, hours = 0): string {
  const base = new Date('2026-11-09T09:00:00+08:00')
  base.setDate(base.getDate() - days)
  base.setHours(base.getHours() - hours)
  return base.toISOString().slice(0, 16).replace('T', ' ')
}
