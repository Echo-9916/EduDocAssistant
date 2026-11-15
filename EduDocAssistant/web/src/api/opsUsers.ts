// 运维用户目录与冻结/解冻 API（契约=后端 OpsUserController，路径受 hasRole("OPS") 限制）。
import { get, post } from './http'
import type { AccountStatusValue, PageResult } from './types'

export interface OpsUserView {
  id: string
  username: string
  displayName: string
  /** 多个角色以逗号拼接；取第一个作为主角色展示 */
  role: string | null
  accountStatus: AccountStatusValue
  department: string | null
  lastLoginTime: string | null
}

export interface OpsUserQuery {
  keyword?: string
  role?: string
  status?: string
  page?: number
  pageSize?: number
}

export const listOpsUsers = (params: OpsUserQuery) =>
  get<PageResult<OpsUserView>>('/ops/users', { params })

export const freezeUser = (userId: string) => post<void>(`/ops/users/${userId}/freeze`)

export const unfreezeUser = (userId: string) => post<void>(`/ops/users/${userId}/unfreeze`)