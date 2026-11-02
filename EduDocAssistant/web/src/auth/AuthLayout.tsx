import { useMemo, type CSSProperties, type ReactNode } from 'react'
import logoMark from '../assets/logo-mark.png'
import AnimatedCharacters from './AnimatedCharacters'
import './auth.css'

const BRAND_800 = '#1F3864'
const BRAND_700 = '#294A7A'
const NAV_MUTED = '#9FB2D4'

/** 彩带颜色：沿用角色配色 + 品牌色。 */
const CONFETTI_COLORS = ['#6C3FF5', '#FF9B6B', '#E8D754', '#2563EB', '#FFFFFF']

/** 登录成功彩带：从左右两下角斜向上喷射，越过峰值后飘落（对齐设计规范「彩带装饰（仅登录成功页）」）。 */
function Confetti({ count = 56 }: { count?: number }) {
  const pieces = useMemo(
    () =>
      Array.from({ length: count }, (_, i) => {
        const fromLeft = i % 2 === 0
        return {
          id: i,
          fromLeft,
          dx: (8 + Math.random() * 34) * (fromLeft ? 1 : -1), // vw，向内侧喷射
          peak: -(45 + Math.random() * 35), // vh，上升峰值
          rot: (540 + Math.random() * 720) * (fromLeft ? 1 : -1),
          delay: Math.random() * 0.5,
          dur: 2.8 + Math.random() * 1.6,
          w: 6 + Math.random() * 5,
          h: 12 + Math.random() * 8,
          color: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
        }
      }),
    [count]
  )

  return (
    <div className="auth-confetti">
      {pieces.map((p) => (
        <i
          key={p.id}
          className={p.fromLeft ? 'from-left' : 'from-right'}
          style={
            {
              width: p.w,
              height: p.h,
              background: p.color,
              animationDelay: `${p.delay}s`,
              animationDuration: `${p.dur}s`,
              '--dx': `${p.dx}vw`,
              '--peak': `${p.peak}vh`,
              '--rot': `${p.rot}deg`,
            } as CSSProperties
          }
        />
      ))}
    </div>
  )
}

/**
 * 认证页共用画板：左侧品牌视觉区（打底色/品牌标识/趣味几何/政策链接）+ 右侧表单区（中线/表单）。
 * 对齐《04_登录与认证页面设计规范.md》：左 814 / 右 640，表单面板 530，标题 32px、说明 16px。
 */
export default function AuthLayout({
  title,
  subtitle,
  confetti,
  children,
}: {
  title: string
  subtitle?: string
  /** 登录成功页开启：左侧视觉区播放一次彩带飘落 */
  confetti?: boolean
  children: ReactNode
}) {
  return (
    <div className="auth-page">
      {/* 左侧品牌视觉区 */}
      <aside className="auth-left" aria-hidden>
        <div className="auth-left-glow" />
        {confetti && <Confetti />}
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
