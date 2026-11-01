import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from './AuthContext'

/** 路由守卫：未登录→/login(保留回跳)，角色不符或权限不足→/403。 */
export default function RequireAuth({ children, roles, permission }: {
  children: ReactElement
  roles?: string[]
  permission?: string
}) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div style={{ padding: 100, textAlign: 'center' }}>
        <Spin />
      </div>
    )
  }

  if (!user) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }

  if (roles && !roles.some((r) => user.roles.includes(r))) {
    return <Navigate to="/403" replace />
  }

  if (permission && !user.permissions.includes(permission)) {
    return <Navigate to="/403" replace />
  }

  return children
}
