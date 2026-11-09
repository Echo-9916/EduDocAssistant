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
import Documents from './pages/Documents'
import DocumentView from './pages/DocumentView'
import FormatSection from './pages/FormatSection'
import TeacherTemplates from './pages/teacher/Templates'
import StudentWorkbench from './pages/student/Workbench'
import StudentCourses from './pages/student/Courses'
import TeacherWorkbench from './pages/teacher/Workbench'
import TeacherCourses from './pages/teacher/Courses'
import OpsWorkbench from './pages/operations/Workbench'
import OpsCourses from './pages/operations/Courses'
import OpsUsers from './pages/operations/Users'
import OpsAudit from './pages/operations/Audit'
import OpsStorage from './pages/operations/Storage'
import SmartSection from './pages/shared/Smart'
import CollabSection from './pages/shared/Collab'
import MessagesSection from './pages/shared/Messages'
import ProfileSection from './pages/shared/Profile'
import Demo from './pages/Demo'
import { roleNav, rolePath } from './routes'
import type { NavItem } from './routes'
import { PermissionCode } from './api/types'
import type { RoleCodeValue } from './api/types'

/**
 * nav key → 页面映射。已联调切片（01 认证 / 02 文档 / 03 智能处理 / 04 格式模板）走真实接口页面，
 * 其余分区先以 mock 骨架页呈现全量门面，后端与 ai-service 跟进后逐页替换。
 */
export function pageOf(role: RoleCodeValue, item: NavItem) {
  if (item.key === 'documents') return <Documents role={role} />
  // 04 切片 D-09/D-10/D-14：格式与模板分组 + 教师模板管理
  if (item.key === 'format') return <FormatSection />
  if (item.key === 'templates') return <TeacherTemplates />
  if (item.key === 'ai') return <SmartSection role={role} />
  if (item.key === 'collab') return <CollabSection role={role} />
  if (item.key === 'messages') return <MessagesSection />
  if (item.key === 'profile') return <ProfileSection />
  if (item.key === 'workbench') {
    if (role === 'TEACHER') return <TeacherWorkbench />
    if (role === 'OPS') return <OpsWorkbench />
    return <StudentWorkbench />
  }
  if (item.key === 'courses') {
    if (role === 'TEACHER') return <TeacherCourses />
    if (role === 'OPS') return <OpsCourses />
    return <StudentCourses />
  }
  if (role === 'OPS') {
    if (item.key === 'users') return <OpsUsers />
    if (item.key === 'audit') return <OpsAudit />
    if (item.key === 'storage') return <OpsStorage />
  }
  return <SectionPlaceholder title={item.label} />
}

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
              <Route
                path="documents/:docId/view"
                element={
                  <RequireAuth permission={PermissionCode.DOCUMENT_READ}>
                    <DocumentView />
                  </RequireAuth>
                }
              />
              {nav.map((item) => {
                const element = pageOf(role, item)
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
