import { describe, expect, it } from 'vitest'
import { AccountStatus, ErrorCode, PermissionCode, RoleCode } from './types'

describe('契约常量与后端对齐（防漂移）', () => {
  it('错误码与后端 ErrorCode 枚举一致', () => {
    expect(ErrorCode).toEqual({
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
    })
  })

  it('权限点 21 项、角色 3 项、账号状态 4 项', () => {
    // 第 21 项 SUBMISSION_SELF_READ 随 06 切片1 落地（与后端 PermissionCode.java、04_seed.sql 同步）
    expect(Object.keys(PermissionCode)).toHaveLength(21)
    expect(Object.keys(RoleCode)).toHaveLength(3)
    expect(Object.keys(AccountStatus)).toHaveLength(4)
  })
})
