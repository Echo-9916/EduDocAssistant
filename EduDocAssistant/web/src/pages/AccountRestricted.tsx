import { Alert, Button, message, Tag } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import AuthLayout from '../auth/AuthLayout'

/** 账号受限页：锁定/未激活/注销账号登录失败后的落点，对齐原型「账号暂时受限」。 */
export default function AccountRestricted() {
  const navigate = useNavigate()
  const location = useLocation()
  const username = (location.state as { username?: string } | null)?.username

  return (
    <AuthLayout title="账号暂时受限" subtitle="检测到您的账号存在异常，请按提示操作。">
      <div className="auth-id-card" style={{ marginBottom: 20 }}>
        <div style={{ flex: 1 }}>
          <div className="auth-helper">账号</div>
          <div style={{ fontSize: 16, fontWeight: 700, color: '#1F2937' }}>{username ?? '—'}</div>
          <div className="auth-helper" style={{ marginTop: 8 }}>状态</div>
          <Tag color="red" style={{ marginTop: 2 }}>已锁定</Tag>
          <div className="auth-helper" style={{ marginTop: 8 }}>最近失败</div>
          <div style={{ fontSize: 14, color: '#475569' }}>多次连续密码错误</div>
        </div>
      </div>
      <Button type="primary" block onClick={() => message.info('已提交解锁请求，请等待信息中心处理')}>
        联系信息中心解锁
      </Button>
      <Button block style={{ marginTop: 12 }} onClick={() => navigate('/operations/users')}>
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
