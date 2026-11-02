import { useState } from 'react'
import { Avatar, Button, Checkbox, Tag } from 'antd'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import AuthLayout from './AuthLayout'
import { homeOf, useAuth } from './AuthContext'
import { roleLabel } from '../routes'
import type { RoleCodeValue } from '../api/types'

const COURSES = ['Web开发技术', 'Oracle 数据库应用', 'JavaEE 程序设计']

/** P3-S1：仅接受站内单斜杠路径（排除 `//`、`/\` 协议相对地址），非法值回退 homeOf。 */
function safeRedirect(raw?: string): string | undefined {
  return raw && /^\/(?!\/|\\)/.test(raw) ? raw : undefined
}

/** 登录成功页（统一身份认证回调）：身份卡片 + 课程列表 + 进入工作台 / 切换账号。 */
export default function RoleEntry() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [courses, setCourses] = useState<string[]>(COURSES)

  if (!user) return <Navigate to="/login" replace />

  // P1-1：登录页透传的深链目标，「进入工作台」后回跳；无深链或非法值则按 homeOf 进入
  const redirect = safeRedirect((location.state as { redirect?: string } | null)?.redirect)

  const switchAccount = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <AuthLayout title="身份认证成功" subtitle="请确认身份信息与课程，选择进入的工作台。" confetti>
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

      {/* 课程列表 */}
      <div style={{ margin: '24px 0 28px' }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: '#1F2937', marginBottom: 10 }}>我的课程</div>
        <Checkbox.Group
          value={courses}
          onChange={(v) => setCourses(v as string[])}
          style={{ display: 'flex', flexDirection: 'column', gap: 10 }}
        >
          {COURSES.map((c) => (
            <Checkbox key={c} value={c}>
              {c}
            </Checkbox>
          ))}
        </Checkbox.Group>
      </div>

      <Button type="primary" block onClick={() => navigate(redirect || homeOf(user.roles), { replace: true })}>
        进入工作台
      </Button>
      <Button block style={{ marginTop: 12 }} onClick={switchAccount}>
        切换账号
      </Button>
    </AuthLayout>
  )
}
