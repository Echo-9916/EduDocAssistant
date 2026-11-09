import { Avatar, ConfigProvider, Dropdown, Layout, Menu, Space } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { roleLabel, roleNav, rolePath } from '../routes'
import { rolePalette, roleTheme } from '../theme'
import logoMark from '../assets/logo-mark.png'
import type { RoleCodeValue } from '../api/types'

const { Sider, Header, Content } = Layout
const ROLE_ORDER: RoleCodeValue[] = ['STUDENT', 'TEACHER', 'OPS']

/** 应用壳：侧边导航按角色渲染，顶部用户/角色/退出，内容区嵌套路由。 */
export default function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const role = ROLE_ORDER.find((r) => user?.roles.includes(r)) ?? 'STUDENT'
  const palette = rolePalette[role]
  const items = roleNav[role].filter((i) => !i.permission || user?.permissions.includes(i.permission))
  const base = rolePath[role]
  const selectedKey = location.pathname.split('/').pop() || items[0]?.key

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <ConfigProvider theme={roleTheme(role)}>
      <Layout style={{ minHeight: '100vh' }}>
        <Sider width={200} theme="dark" style={{ background: palette.nav }}>
          {/* 品牌位与登录页同一套口径：Logo + 中文简称 + 英文昵称（Profile.md）；白底 chip 保证深色导航上可读 */}
          <div style={{ margin: '14px 12px', display: 'flex', alignItems: 'center', gap: 8 }}>
            <img
              src={logoMark}
              alt="校园智能文档助手 Logo"
              style={{ width: 32, height: 32, objectFit: 'contain', background: '#fff', borderRadius: 9, padding: 3 }}
            />
            <span style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.25, overflow: 'hidden' }}>
              <strong style={{ fontSize: 13, color: '#fff', letterSpacing: 0.5, whiteSpace: 'nowrap' }}>校园智能文档助手</strong>
              <span style={{ fontSize: 10, color: '#fff', whiteSpace: 'nowrap' }}>EduDocAssistant</span>
            </span>
          </div>
          <Menu
            theme="dark"
            mode="inline"
            style={{ background: 'transparent', fontSize: 13 }}
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
              borderBottom: `2px solid ${palette.primary}`,
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
                <Avatar style={{ background: palette.primary }}>{user?.username?.[0]?.toUpperCase()}</Avatar>
              </Space>
            </Dropdown>
          </Header>
          <Content style={{ margin: 16 }}>
            <Outlet />
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  )
}
