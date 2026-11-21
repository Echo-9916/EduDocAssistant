import { Avatar, Button, Tag } from 'antd'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import AuthLayout from './AuthLayout'
import { homeOf, useAuth } from './AuthContext'
import { roleLabel, rolePath } from '../routes'
import type { RoleCodeValue } from '../api/types'

/** P3-S1：仅接受站内单斜杠路径（排除 `//`、`/\` 协议相对地址），非法值回退 homeOf。 */
function safeRedirect(raw?: string): string | undefined {
  return raw && /^\/(?!\/|\\)/.test(raw) ? raw : undefined
}

/** 登录成功页（统一身份认证回调）：身份卡片 + 进入工作台 / 切换账号。 */
export default function RoleEntry() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  if (!user) return <Navigate to="/login" replace />

  // P1-1：登录页透传的深链目标，「进入工作台」后回跳。
  // 深链可能来自登录前的页面，角色可能已变化；只接受当前角色前缀下的路径，否则回 homeOf，
  // 避免首次点击落到不属于当前角色的路由后被 RequireAuth 判 403。
  const requested = safeRedirect((location.state as { redirect?: string } | null)?.redirect)
  const home = homeOf(user.roles)
  const rolePaths = user.roles
    .map((role) => rolePath[role as RoleCodeValue])
    .filter((path): path is string => !!path)
  const redirect = requested && rolePaths.some((path) => requested === path || requested.startsWith(`${path}/`))
    ? requested
    : home

  const switchAccount = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <AuthLayout title="身份认证成功" subtitle="请确认身份信息，选择进入的工作台。" confetti>
      {/* 身份卡片 */}
      <div className="auth-id-card">
        <Avatar size={44} style={{ background: '#2563EB', flex: '0 0 auto', fontSize: 18 }}>
          {user.username.slice(0, 1).toUpperCase()}
        </Avatar>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: '#1F2937', lineHeight: 1.4 }}>
            {user.displayName || user.username}
          </div>
          <div style={{ fontSize: 13, color: '#5B6B80', wordBreak: 'break-all' }}>账号：{user.username}</div>
        </div>
        {user.roles.map((r) => (
          <Tag key={r} color="blue" style={{ margin: 0 }}>
            {roleLabel[r as RoleCodeValue] ?? r}
          </Tag>
        ))}
      </div>

      <Button type="primary" block style={{ marginTop: 28 }} onClick={() => navigate(redirect, { replace: true })}>
        进入工作台
      </Button>
      <Button block style={{ marginTop: 12 }} onClick={switchAccount}>
        切换账号
      </Button>
    </AuthLayout>
  )
}
