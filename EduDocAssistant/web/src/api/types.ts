// 与后端 common/api 契约对齐，见 docs/design/接口契约速查.md

/** 统一响应 envelope */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId: string
  timestamp: string
}

/** 统一分页结果 */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

/** 当前登录用户上下文（与后端 common/security/UserPrincipal 字段一致） */
export interface UserPrincipal {
  userId: string
  username: string
  /** 显示姓名（edudoc_user.display_name），可能为空 */
  displayName?: string
  roles: string[]
  permissions: string[]
  courseScope: string[]
}

/** 全局错误码（详细设计 §5.1） */
export const ErrorCode = {
  OK: 200,
  BAD_REQUEST: 40001,
  UNAUTHORIZED: 40101,
  TOKEN_REVOKED: 40102,
  FORBIDDEN: 40301,
  ACL_DENIED: 40302,
  SHARE_LINK_EXPIRED: 40303,
  NOT_FOUND: 40401,
  METHOD_NOT_ALLOWED: 40501,
  CONFLICT: 40901,
  DUPLICATE: 40902,
  FILE_TOO_LARGE: 41301,
  UNSUPPORTED_MEDIA_TYPE: 41501,
  PARSE_FAILED: 42201,
  FORMAT_BLOCKER: 42202,
  RATE_LIMITED: 42901,
  INTERNAL_ERROR: 50001,
  MODEL_FAILED: 50201,
  SERVICE_UNAVAILABLE: 50301,
  TIMEOUT: 50401,
} as const
export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

/** 账号状态（edudoc_user.account_status） */
export const AccountStatus = {
  ACTIVE: 'A',
  INACTIVE: 'I',
  LOCKED: 'L',
  DISABLED: 'D',
} as const
export type AccountStatusValue = (typeof AccountStatus)[keyof typeof AccountStatus]

/** 内置角色编码（04_seed.sql） */
export const RoleCode = {
  STUDENT: 'STUDENT',
  TEACHER: 'TEACHER',
  OPS: 'OPS',
} as const
export type RoleCodeValue = (typeof RoleCode)[keyof typeof RoleCode]

/** 文档类型（edudoc_document.doc_type） */
export const DocType = {
  COURSEWARE: 'COURSEWARE',
  HOMEWORK: 'HOMEWORK',
  THESIS: 'THESIS',
  REPORT: 'REPORT',
} as const
export type DocTypeValue = (typeof DocType)[keyof typeof DocType]

/** 文档状态（edudoc_document.status；UPLOADING 仅前端会话态，后端建档即 PARSING） */
export const DocStatus = {
  UPLOADING: 'UPLOADING',
  PARSING: 'PARSING',
  READY: 'READY',
  FAILED: 'FAILED',
  SUBMITTED: 'SUBMITTED',
  ARCHIVED: 'ARCHIVED',
  DELETED: 'DELETED',
} as const
export type DocStatusValue = (typeof DocStatus)[keyof typeof DocStatus]

/** 权限点编码（04_seed.sql） */
export const PermissionCode = {
  DOCUMENT_READ: 'DOCUMENT_READ',
  DOCUMENT_CREATE: 'DOCUMENT_CREATE',
  DOCUMENT_EDIT: 'DOCUMENT_EDIT',
  DOCUMENT_DELETE: 'DOCUMENT_DELETE',
  DOCUMENT_DOWNLOAD: 'DOCUMENT_DOWNLOAD',
  DOCUMENT_SHARE: 'DOCUMENT_SHARE',
  AI_SUMMARY: 'AI_SUMMARY',
  AI_KEYPOINT: 'AI_KEYPOINT',
  AI_QA: 'AI_QA',
  AI_POLISH: 'AI_POLISH',
  TEMPLATE_READ: 'TEMPLATE_READ',
  TEMPLATE_MANAGE: 'TEMPLATE_MANAGE',
  FORMAT_CHECK: 'FORMAT_CHECK',
  FORMAT_CONVERT: 'FORMAT_CONVERT',
  COLLAB_EDIT: 'COLLAB_EDIT',
  VERSION_RESTORE: 'VERSION_RESTORE',
  ROLE_MANAGE: 'ROLE_MANAGE',
  AUDIT_READ: 'AUDIT_READ',
  ASSIGNMENT_MANAGE: 'ASSIGNMENT_MANAGE',
  SUBMISSION_READ: 'SUBMISSION_READ',
  /** 学生查看本人作业与提交（06 切片 1）；SUBMISSION_READ 是教师/运维的提交统计，两者不可混用 */
  SUBMISSION_SELF_READ: 'SUBMISSION_SELF_READ',
} as const
export type PermissionCodeValue = (typeof PermissionCode)[keyof typeof PermissionCode]
