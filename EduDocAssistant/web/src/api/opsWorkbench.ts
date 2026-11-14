// 运维工作台计数摘要（契约=后端 OpsWorkbenchController，路径受 hasRole("OPS") + AUDIT_READ 限制）。
import { get } from './http'

export interface WorkbenchSummary {
  userTotal: number
  /** 账号状态 L（已锁定/冻结）人数 */
  userLocked: number
  /** 不含软删除与 DELETED 状态的文档 */
  documentTotal: number
  aiTasksToday: number
  /** 今日已成功任务占比，百分比 0-100，保留一位小数 */
  aiSuccessRate: number
  storageBytes: number
  storageObjects: number
  /** 近 7 天审计高风险事件数，口径同审计页 */
  auditHighRisk7d: number
}

export const getWorkbenchSummary = () => get<WorkbenchSummary>('/ops/workbench-summary')