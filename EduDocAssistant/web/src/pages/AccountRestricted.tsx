import { Alert, Button, Card, Space, Typography } from 'antd'
import { Link } from 'react-router-dom'

/** 账号受限页：锁定/未激活/注销账号登录失败后的落点。 */
export default function AccountRestricted() {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#F4F7FB',
        padding: 24,
      }}
    >
      <Card style={{ width: 480, borderRadius: 16 }}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div>
            <Typography.Title level={4} style={{ marginTop: 0 }}>账号受限</Typography.Title>
            <Typography.Text type="secondary">账号状态：受限</Typography.Text>
            <div style={{ color: '#475569' }}>原因：账号被锁定、未激活或已注销。</div>
          </div>
          <Alert
            type="warning"
            showIcon
            message="安全提示"
            description="为保障账户安全，该账号已被限制登录。如需继续使用，可申请临时访问权限或联系管理员。"
          />
          <Space>
            <Link to="/access-request">
              <Button type="primary">申请临时访问权限</Button>
            </Link>
            <Link to="/login">
              <Button>返回登录</Button>
            </Link>
          </Space>
        </Space>
      </Card>
    </div>
  )
}
