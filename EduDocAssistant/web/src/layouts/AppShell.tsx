import { Avatar, Dropdown, Layout, Menu, Space } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { roleLabel, roleNav, rolePath } from '../routes'
import type { RoleCodeValue } from '../api/types'

const { Sider, Header, Content } = Layout
const ROLE_ORDER: RoleCodeValue[] = ['STUDENT', 'TEACHER', 'OPS']

/** 应用壳：侧边导航按角色渲染，顶部用户/角色/退出，内容区嵌套路由。 */
export default function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const role = ROLE_ORDER.find((r) => user?.roles.includes(r)) ?? 'STUDENT'
  const items = roleNav[role].filter((i) => !i.permission || user?.permissions.includes(i.permission))
  const base = rolePath[role]
  const selectedKey = location.pathname.split('/').pop() || items[0]?.key

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={200} theme="dark">
        <div
          style={{
            height: 48,
            margin: 16,
            color: '#fff',
            fontSize: 16,
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          CIDAS
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={items.map((i) => ({ key: i.key, label: i.label }))}
          onClick={({ key }) => navigate(`${base}/${key}`)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 16px',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            borderBottom: '1px solid #DDE5EF',
          }}
        >
          <Dropdown
            menu={{
              items: [
                {
                  key: 'role',
                  label: `角色：${user?.roles.map((r) => roleLabel[r as RoleCodeValue] ?? r).join('、')}`,
                  disabled: true,
                },
                { key: 'logout', label: '退出登录', onClick: handleLogout },
              ],
            }}
          >
            <Space style={{ cursor: 'pointer' }}>
              <span>{user?.username}</span>
              <Avatar>{user?.username?.[0]?.toUpperCase()}</Avatar>
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
