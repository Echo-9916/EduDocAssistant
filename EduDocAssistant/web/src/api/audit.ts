// 06 切片 1：审计查询 API（契约=后端 AuditQueryController，见 docs/tasks/06-backend-followup.md Task 1-9）。
import { get } from './http'
import type { PageResult } from './types'

export type AuditRange = '24h' | '7d' | '30d'
export type AuditResourceType = 'ACCOUNT' | 'COURSE' | 'DOCUMENT' | 'PERMISSION' | 'SYSTEM'

export interface AuditEventView {
  id: string
  occurredAt: string
  actor: string
  actorAccount: string
  actionType: string
  resourceType: AuditResourceType
  resourceRef: string | null
  /** 成功码兼容 '200' 与 'SUCCESS'（既有写入路径两种都写过） */
  result: 'SUCCESS' | 'FAIL'
  resultCode: string | null
  traceId: string | null
  sourceIp: string | null
  userAgent: string | null
  riskLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  summary: string | null
}

export interface AuditEventDetailView extends AuditEventView {
  chain: { step: number; at: string; detail: string }[]
  impacts: { type: string; name: string; detail: string }[]
  advice: string[]
}

export const listAuditEvents = (query: {
  range?: AuditRange
  resourceType?: AuditResourceType | ''
  actor?: string
  keyword?: string
  page?: number
  pageSize?: number
}) => get<PageResult<AuditEventView>>('/ops/audit-events', { params: query })

export const getAuditEvent = (id: string) => get<AuditEventDetailView>(`/ops/audit-events/${id}`)
