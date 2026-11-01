import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, landingPath, useAuth } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import RoleEntry from './auth/RoleEntry'
import AppShell from './layouts/AppShell'
import Login from './pages/Login'
import Forbidden from './pages/Forbidden'
import SectionPlaceholder from './pages/SectionPlaceholder'
import Demo from './pages/Demo'
import { roleNav, rolePath } from './routes'
import type { NavItem } from './routes'
import type { RoleCodeValue } from './api/types'

function Home() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={landingPath(user.roles)} replace />
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
