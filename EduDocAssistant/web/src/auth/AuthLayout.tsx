import type { ReactNode } from 'react'
import logoMark from '../assets/logo-mark.png'
import AnimatedCharacters from './AnimatedCharacters'
import './auth.css'

const BRAND_800 = '#1F3864'
const BRAND_700 = '#294A7A'
const NAV_MUTED = '#9FB2D4'

/**
 * 认证页共用画板：左侧品牌视觉区（打底色/品牌标识/趣味几何/政策链接）+ 右侧表单区（中线/表单）。
 * 对齐《04_登录与认证页面设计规范.md》：左 814 / 右 640，表单面板 530，标题 32px、说明 16px。
 */
export default function AuthLayout({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: string
  children: ReactNode
}) {
  return (
    <div className="auth-page">
      {/* 左侧品牌视觉区 */}
      <aside className="auth-left" aria-hidden>
        <div className="auth-left-glow" />
        <div className="auth-brand">
          <span className="auth-brand-mark">
            <img src={logoMark} alt="校园智能文档助手 Logo" />
          </span>
          <span className="auth-brand-copy">
            <strong>校园智能文档助手</strong>
            <span>EduDocAssistant</span>
          </span>
        </div>
        <div className="auth-characters">
          <AnimatedCharacters />
        </div>
        <div className="auth-policy">
          <a onClick={(e) => e.preventDefault()}>隐私政策</a>
          <span>·</span>
          <a onClick={(e) => e.preventDefault()}>服务条款</a>
          <span>·</span>
          <a onClick={(e) => e.preventDefault()}>联系我们</a>
        </div>
      </aside>

      {/* 右侧表单区 */}
      <main className="auth-right">
        <div className="auth-panel">
          <h1 className="auth-title">{title}</h1>
          {subtitle && <p className="auth-subtitle">{subtitle}</p>}
          {children}
        </div>
      </main>
    </div>
  )
}

export { BRAND_800, BRAND_700, NAV_MUTED }
