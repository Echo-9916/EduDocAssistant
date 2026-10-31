import { describe, expect, it } from 'vitest'
import { theme } from './theme'

describe('设计令牌', () => {
  it('主色/页面底色/正文字号对齐原型', () => {
    expect(theme.token?.colorPrimary).toBe('#2563EB')
    expect(theme.token?.colorBgLayout).toBe('#F4F7FB')
    expect(theme.token?.fontSize).toBe(12)
  })
})
