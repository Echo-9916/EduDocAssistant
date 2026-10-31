import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, homeOf, useAuth } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import Login from './pages/Login'
import Forbidden from './pages/Forbidden'
import Workbench from './pages/Workbench'
import Demo from './pages/Demo'

function Home() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={homeOf(user.roles)} replace />
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/403" element={<Forbidden />} />
          <Route path="/demo" element={<Demo />} />
          <Route path="/" element={<Home />} />
          <Route path="/student/*" element={<RequireAuth roles={['STUDENT']}><Workbench /></RequireAuth>} />
          <Route path="/teacher/*" element={<RequireAuth roles={['TEACHER']}><Workbench /></RequireAuth>} />
          <Route path="/operations/*" element={<RequireAuth roles={['OPS']}><Workbench /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
