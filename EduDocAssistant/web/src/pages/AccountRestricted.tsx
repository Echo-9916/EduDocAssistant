import { useState } from 'react'
import { Alert, Button, message, Tag } from 'antd'
import { useLocation } from 'react-router-dom'
import AuthLayout from '../auth/AuthLayout'
import { post } from '../api/http'

/** 账号状态码 → 状态标签（后端 40101 data.accountStatus 为稳定契约，见 LocalAuthProvider）。 */
function statusOf(accountStatus?: string): { label: string; color: string } {
  switch (accountStatus) {
    case 'L':
      return { label: '已锁定', color: 'red' }
    case 'I':
      return { label: '未激活', color: 'orange' }
    case 'D':
      return { label: '已注销', color: 'default' }
    default:
      return { label: '已受限', color: 'red' }
  }
}

/** 账号受限页：锁定/未激活/注销账号登录失败后的落点，对齐原型「账号暂时受限」。 */
export default function AccountRestricted() {
  const location = useLocation()
  const [notice, setNotice] = useState<string | null>(null)
  const state = location.state as { username?: string; accountStatus?: string; reason?: string } | null
  const username = state?.username
  const status = statusOf(state?.accountStatus)
  const reason = state?.reason?.replace(/^账号受限[：:]\s*/, '')

  // P2-3：受限用户此时未认证，/operations/users 会被 RequireAuth 拦回登录页；
  // 改为调用公开占位接口 /auth/access-request，待审批流落地后替换为公开申请页
  const requestAccess = async () => {
    try {
      await post('/auth/access-request')
      setNotice('该功能正在开发中')
    } catch {
      message.error('申请提交失败，请稍后重试')
    }
  }

  return (
    <AuthLayout title="账号暂时受限" subtitle="检测到您的账号存在异常，请按提示操作。">
      <div className="auth-id-card" style={{ display: 'block', marginBottom: 24 }}>
        <div className="auth-helper" style={{ marginTop: 0 }}>账号</div>
        <div style={{ fontSize: 15, fontWeight: 600, color: '#1F2937', lineHeight: 1.6 }}>{username ?? '—'}</div>
        <div className="auth-helper" style={{ marginTop: 10 }}>状态</div>
        <Tag color={status.color} style={{ marginTop: 2 }}>{status.label}</Tag>
        <div className="auth-helper" style={{ marginTop: 10 }}>受限原因</div>
        <div style={{ fontSize: 14, color: '#475569', lineHeight: 1.6 }}>
          {reason || '账号状态异常，请联系管理员'}
        </div>
      </div>
      {notice && <Alert type="info" showIcon message={notice} style={{ marginBottom: 16 }} />}
      <Button type="primary" block onClick={() => message.info('已提交解锁请求，请等待信息中心处理')}>
        联系信息中心解锁
      </Button>
      <Button block style={{ marginTop: 12 }} onClick={requestAccess}>
        申请临时访问权限
      </Button>
      <Alert
        type="warning"
        showIcon
        message="安全提示"
        description="为保障账户安全，该账号已被限制登录。如需继续使用，可联系信息中心或申请临时访问权限。"
        style={{ marginTop: 20 }}
      />
    </AuthLayout>
  )
}
