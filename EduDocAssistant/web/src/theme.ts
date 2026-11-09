import type { ThemeConfig } from 'antd'
import type { RoleCodeValue } from './api/types'

/** 三端导航底色与主题色：对齐 docs/prototype/README.md 设计口径。 */
export const rolePalette: Record<RoleCodeValue, { primary: string; nav: string; label: string }> = {
  STUDENT: { primary: '#2563EB', nav: '#1F3864', label: '学生端' },
  TEACHER: { primary: '#0F766E', nav: '#193C3B', label: '教师端' },
  OPS: { primary: '#0E7490', nav: '#18344B', label: '运维端' },
}

/** 设计令牌：对齐原型视觉规范（docs/prototype/student/README.md）。 */
export const theme: ThemeConfig = {
  token: {
    colorPrimary: '#2563EB',
    colorInfo: '#2563EB',
    colorBgLayout: '#F4F7FB',
    colorBorder: '#DDE5EF',
    colorText: '#1F2937',
    colorTextSecondary: '#475569',
    colorTextTertiary: '#5B6B80',
    fontSize: 14,
    borderRadius: 8,
    fontFamily: "'Microsoft YaHei', 'PingFang SC', 'Segoe UI', sans-serif",
  },
}

/** 按角色覆盖主色：三端门面共用一个壳，靠主色与导航底色区分身份。 */
export function roleTheme(role: RoleCodeValue): ThemeConfig {
  const { primary } = rolePalette[role]
  return { ...theme, token: { ...theme.token, colorPrimary: primary, colorInfo: primary } }
}
