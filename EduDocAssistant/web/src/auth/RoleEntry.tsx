import { useState } from 'react'
import { Avatar, Button, Checkbox, Tag } from 'antd'
import { Navigate, useNavigate } from 'react-router-dom'
import AuthLayout from './AuthLayout'
import { homeOf, useAuth } from './AuthContext'
import { roleLabel, rolePath } from '../routes'
import type { RoleCodeValue } from '../api/types'

const COURSES = ['Web开发技术', 'Oracle 数据库应用', 'JavaEE 程序设计']

/** 演示角色入口：学生 / 教师 / 教学管理（原型把运维端演示入口标注为「教学管理」）。 */
const DEMO_ENTRIES: { role: RoleCodeValue; label: string }[] = [
  { role: 'STUDENT', label: '学生' },
  { role: 'TEACHER', label: '教师' },
  { role: 'OPS', label: '教学管理' },
]

/** 登录成功页（统一身份认证回调）：身份卡片 + 课程列表 + 进入工作台 / 切换账号 + 角色演示入口。 */
export default function RoleEntry() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [courses, setCourses] = useState<string[]>(COURSES)

  if (!user) return <Navigate to="/login" replace />

  const switchAccount = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <AuthLayout title="身份认证成功" subtitle="请确认身份信息与课程，选择进入的工作台。">
      {/* 身份卡片 */}
      <div className="auth-id-card">
        <Avatar size={48} style={{ background: '#2563EB', flex: '0 0 auto' }}>
          {user.username.slice(0, 1).toUpperCase()}
        </Avatar>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 16, fontWeight: 700, color: '#1F2937' }}>{user.username}</div>
          <div style={{ fontSize: 13, color: '#5B6B80', wordBreak: 'break-all' }}>学号：{user.userId}</div>
        </div>
        {user.roles.map((r) => (
          <Tag key={r} color="blue" style={{ margin: 0 }}>
            {roleLabel[r as RoleCodeValue] ?? r}
          </Tag>
        ))}
      </div>

      {/* 课程列表 */}
      <div style={{ margin: '20px 0' }}>
        <div style={{ fontSize: 15, fontWeight: 700, color: '#1F2937', marginBottom: 8 }}>我的课程</div>
        <Checkbox.Group
          value={courses}
          onChange={(v) => setCourses(v as string[])}
          style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
        >
          {COURSES.map((c) => (
            <Checkbox key={c} value={c}>
              {c}
            </Checkbox>
          ))}
        </Checkbox.Group>
      </div>

      <Button type="primary" block onClick={() => navigate(homeOf(user.roles), { replace: true })}>
        进入工作台
      </Button>
      <Button block style={{ marginTop: 12 }} onClick={switchAccount}>
        切换账号
      </Button>

      {/* 角色演示入口 */}
      <div className="auth-divider" style={{ marginTop: 20 }}>演示角色路线</div>
      <div style={{ display: 'flex', gap: 12 }}>
        {DEMO_ENTRIES.map(({ role, label }) => (
          <Button key={role} style={{ flex: 1 }} onClick={() => navigate(rolePath[role])}>
            {label}
          </Button>
        ))}
      </div>
    </AuthLayout>
  )
}
