import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import RoleEntry from './auth/RoleEntry'
import AppShell from './layouts/AppShell'
import Login from './pages/Login'
import AccountRestricted from './pages/AccountRestricted'
import ForgotPassword from './pages/ForgotPassword'
import Forbidden from './pages/Forbidden'
import SectionPlaceholder from './pages/SectionPlaceholder'
import Demo from './pages/Demo'
import { roleNav, rolePath } from './routes'
import type { NavItem } from './routes'
import type { RoleCodeValue } from './api/types'

function Home() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  // P1-1：已登录访问根路径也统一先进角色选择页
  return <Navigate to="/roles" replace />
}

const ROLES: { role: RoleCodeValue; nav: NavItem[] }[] = [
  { role: 'STUDENT', nav: roleNav.STUDENT },
  { role: 'TEACHER', nav: roleNav.TEACHER },
  { role: 'OPS', nav: roleNav.OPS },
]

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/roles" element={<RoleEntry />} />
          <Route path="/account-restricted" element={<AccountRestricted />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/403" element={<Forbidden />} />
          <Route path="/demo" element={<Demo />} />
          <Route path="/" element={<Home />} />
          {ROLES.map(({ role, nav }) => (
            <Route
              key={role}
              path={rolePath[role]}
              element={
                <RequireAuth roles={[role]}>
                  <AppShell />
                </RequireAuth>
              }
            >
              <Route index element={<Navigate to={nav[0].key} replace />} />
              {nav.map((item) => {
                const element = <SectionPlaceholder title={item.label} />
                return (
                  <Route
                    key={item.key}
                    path={item.key}
                    element={item.permission ? <RequireAuth permission={item.permission}>{element}</RequireAuth> : element}
                  />
                )
              })}
            </Route>
          ))}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
