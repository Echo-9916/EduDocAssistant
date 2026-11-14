// 文档共享链接 API（契约=后端 ShareController）：owner 创建/列出/撤销，公开 token 免登录只读。
import { del, get, post } from './http'

export type SharePermission = 'VIEW' | 'COMMENT'
export type ShareStatus = 'A' | 'R' | 'E'

export interface CreatedShare {
  shareId: string
  token: string
  /** 前端拼 window.location.origin 得到完整链接 */
  path: string
  permission: SharePermission
  expiresAt: string
}

export interface ShareView {
  id: string
  token: string
  path: string
  permission: SharePermission
  status: ShareStatus
  expiresAt: string | null
  createTime: string
}

export interface PublicShare {
  title: string
  versionNo: number | null
  permission: SharePermission
  text: string
}

export const createShare = (documentId: string, body: { expiresInDays: number; permission: SharePermission }) =>
  post<CreatedShare>(`/documents/${documentId}/shares`, body)

export const listShares = (documentId: string) =>
  get<ShareView[]>(`/documents/${documentId}/shares`)

export const revokeShare = (shareId: string) => del<void>(`/shares/${shareId}`)

export const getPublicShare = (token: string) => get<PublicShare>(`/public/shares/${token}`)