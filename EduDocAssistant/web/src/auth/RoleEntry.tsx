import { Button, Card, Space, Typography } from 'antd'
import { Navigate, useNavigate } from 'react-router-dom'
import { homeOf, useAuth } from './AuthContext'
import { roleLabel, rolePath } from '../routes'
import type { RoleCodeValue } from '../api/types'

const ROLE_ORDER: RoleCodeValue[] = ['STUDENT', 'TEACHER', 'OPS']

/** 多角色登录后的身份选择页：身份卡片 + 角色入口 + 切换账号。 */
export default function RoleEntry() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  if (!user) return <Navigate to="/login" replace />
  if (user.roles.length <= 1) return <Navigate to={homeOf(user.roles)} replace />

  const roles = ROLE_ORDER.filter((r) => user.roles.includes(r))

  const switchAccount = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#F4F7FB',
        padding: 24,
      }}
    >
      <Card title="选择身份进入" style={{ width: 420, borderRadius: 16 }}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div>
            <Typography.Text strong style={{ fontSize: 18 }}>{user.username}</Typography.Text>
            <div style={{ color: '#475569' }}>
              角色：{user.roles.map((r) => roleLabel[r as RoleCodeValue] ?? r).join('、')}
            </div>
            <div style={{ color: '#475569' }}>课程：暂无</div>
          </div>
          {roles.map((r) => (
            <Button key={r} block onClick={() => navigate(rolePath[r], { replace: true })}>
              进入{roleLabel[r]}工作台
            </Button>
          ))}
          <Button type="link" block onClick={switchAccount}>
            切换账号
          </Button>
        </Space>
      </Card>
    </div>
  )
}
