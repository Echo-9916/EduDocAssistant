import { describe, expect, it } from 'vitest'
import { roleLabel, roleNav, rolePath } from './routes'

describe('三端导航路由表', () => {
  it('数量 8/9/7 且首项均为工作台', () => {
    expect(roleNav.STUDENT).toHaveLength(8)
    expect(roleNav.TEACHER).toHaveLength(9)
    expect(roleNav.OPS).toHaveLength(7)
    expect(roleNav.STUDENT[0].key).toBe('workbench')
    expect(roleNav.TEACHER[0].key).toBe('workbench')
    expect(roleNav.OPS[0].key).toBe('workbench')
  })

  it('权限点使用大写编码、路由前缀与角色标签对齐', () => {
    const all = [...roleNav.STUDENT, ...roleNav.TEACHER, ...roleNav.OPS]
    for (const item of all) {
      if (item.permission) expect(item.permission).toMatch(/^[A-Z_]+$/)
    }
    expect(rolePath.OPS).toBe('/operations')
    expect(roleLabel.TEACHER).toBe('教师')
  })
})
