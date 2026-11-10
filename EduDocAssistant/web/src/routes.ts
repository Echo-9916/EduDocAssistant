import { PermissionCode } from './api/types'
import type { RoleCodeValue } from './api/types'

export type NavItem = { key: string; label: string; permission?: string }

/** 三角色一级导航（对齐详细设计 §4.1 路由表与各端信息架构：学生 8 / 教师 9 / 运维 7 项）。 */
export const roleNav: Record<RoleCodeValue, NavItem[]> = {
  STUDENT: [
    { key: 'workbench', label: '工作台' },
    { key: 'documents', label: '我的文档', permission: PermissionCode.DOCUMENT_READ },
    { key: 'ai', label: '智能处理' },
    { key: 'format', label: '格式与模板' },
    { key: 'collab', label: '协作与版本' },
    // 06 切片 1 收口：学生作业用 SUBMISSION_SELF_READ（本人作业），不是教师/运维口径的 SUBMISSION_READ
    { key: 'courses', label: '课程与作业', permission: PermissionCode.SUBMISSION_SELF_READ },
    { key: 'profile', label: '个人中心' },
    { key: 'messages', label: '消息通知' },
  ],
  TEACHER: [
    { key: 'workbench', label: '工作台' },
    { key: 'documents', label: '我的文档', permission: PermissionCode.DOCUMENT_READ },
    { key: 'ai', label: '智能处理' },
    { key: 'format', label: '格式与模板' },
    { key: 'templates', label: '课程模板', permission: PermissionCode.TEMPLATE_MANAGE },
    { key: 'collab', label: '协作与版本' },
    { key: 'courses', label: '课程与作业', permission: PermissionCode.ASSIGNMENT_MANAGE },
    { key: 'messages', label: '消息通知' },
    { key: 'profile', label: '个人中心' },
  ],
  OPS: [
    { key: 'workbench', label: '运维工作台' },
    { key: 'courses', label: '课程治理' },
    { key: 'users', label: '用户与权限', permission: PermissionCode.ROLE_MANAGE },
    { key: 'audit', label: '审计与安全', permission: PermissionCode.AUDIT_READ },
    { key: 'storage', label: '存储与资源' },
    { key: 'messages', label: '消息与告警' },
    { key: 'profile', label: '个人中心' },
  ],
}

export const rolePath: Record<RoleCodeValue, string> = {
  STUDENT: '/student',
  TEACHER: '/teacher',
  OPS: '/operations',
}

export const roleLabel: Record<RoleCodeValue, string> = {
  STUDENT: '学生',
  TEACHER: '教师',
  OPS: '运维',
}
