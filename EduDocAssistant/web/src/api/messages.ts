// 06 切片 2：站内信箱 API（契约=后端 InboxController / InboxService 嵌套 record，
// 见 docs/design/接口契约速查.md §8）。列表过滤参数是 unread（布尔口径），不是 read。
import { get, patch } from './http'
import type { PageResult } from './types'

/** 八类消息类型，与后端 InboxServiceImpl.NOTIF_TYPES 及库约束 ck_notification_type 逐字对齐。 */
export type MessageType =
  | 'COURSE' | 'SUBMISSION' | 'FEEDBACK' | 'FORMAT'
  | 'PERMISSION' | 'ALERT' | 'APPROVAL' | 'SYSTEM'

export const messageTypeLabel: Record<MessageType, string> = {
  COURSE: '课程组更新',
  SUBMISSION: '提交提醒',
  FEEDBACK: '教师反馈',
  FORMAT: '格式异常',
  PERMISSION: '权限变更',
  ALERT: '系统告警',
  APPROVAL: '审批通知',
  SYSTEM: '系统通知',
}

/** 后端 InboxService.MessageView；actorName 系统消息为 null → 前端显示「系统」。 */
export interface MessageView {
  id: string
  type: MessageType
  title: string
  body: string | null
  /** 「前往处理」落点：关联对象类型（可空），前端按角色映射到一级导航 */
  resourceType: string | null
  resourceRef: string | null
  actorName: string | null
  /** UTC ISO（带偏移/Z 结尾），必须 new Date() 本地化解析，禁裸 slice */
  createdAt: string
  read: boolean
}

export const listMessages = (query: { type?: MessageType; unread?: boolean; page?: number; pageSize?: number }) =>
  get<PageResult<MessageView>>('/messages', { params: query })

/** 未读数单独口径（顶部 Segmented 与工作台角标），避免拉全量列表自己算。 */
export const unreadCount = () => get<number>('/messages/unread-count')

/** 批量已读/未读：越权 id 后端静默跳过，updated 只算命中自己的条数；ids 空数组 40001。 */
export const markRead = (ids: string[], read: boolean) =>
  patch<{ updated: number }>('/messages:read', { ids, read })

export const readAll = () => patch<{ updated: number }>('/messages:read-all')
