import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import type { UserPrincipal } from '../api/types'
import { get, post } from '../api/http'
import { clearToken, getToken, setToken } from '../api/token'

type LoginResult = { token: string; user: UserPrincipal }

type AuthContextValue = {
  user: UserPrincipal | null
  loading: boolean
  login: (username: string, password: string) => Promise<UserPrincipal>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

/** 按角色优先级返回工作台首页路径。多角色暂取最高优先级，选择器后续补齐。 */
export function homeOf(roles: string[]): string {
  if (roles.includes('OPS')) return '/operations'
  if (roles.includes('TEACHER')) return '/teacher'
  return '/student'
}

/** 登录落地：进入「身份认证成功」页（统一身份认证回调），由用户确认后进入工作台。 */
export function landingPath(_roles: string[]): string {
  return '/roles'
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserPrincipal | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!getToken()) {
      setLoading(false)
      return
    }
    get<UserPrincipal>('/auth/me')
      .then(setUser)
      .catch(() => clearToken())
      .finally(() => setLoading(false))
  }, [])

  const login = async (username: string, password: string) => {
    const result = await post<LoginResult>('/auth/login', { username, password })
    setToken(result.token)
    setUser(result.user)
    return result.user
  }

  const logout = async () => {
    try {
      await post<void>('/auth/logout')
    } catch {
      // 登出尽力而为，即使失败也清理本地令牌
    }
    clearToken()
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, loading, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用')
  return ctx
}
