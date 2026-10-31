import { Button, Space } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/** 工作台骨架占位，F-06 落地三端布局与导航。 */
export default function Workbench() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div style={{ padding: 24 }}>
      <h2>工作台（骨架）</h2>
      <p>
        当前用户：{user?.username}（角色：{user?.roles.join('、')}）
      </p>
      <Space>
        <Button onClick={async () => { await logout(); navigate('/login', { replace: true }) }}>
          退出登录
        </Button>
      </Space>
    </div>
  )
}
