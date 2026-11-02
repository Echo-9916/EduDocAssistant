import { useEffect, useState } from 'react'
import { Alert, Button, Form, Input } from 'antd'
import { Link } from 'react-router-dom'
import AuthLayout from '../auth/AuthLayout'
import { post } from '../api/http'

/** 新密码强度：至少 10 位且同时包含字母、数字与符号（对齐原型「至少 10 位，包含字母、数字和符号」）。 */
function validateStrength(_: unknown, value: string) {
  if (!value) return Promise.resolve()
  if (value.length < 10 || !/[A-Za-z]/.test(value) || !/\d/.test(value) || !/[^A-Za-z0-9]/.test(value)) {
    return Promise.reject(new Error('至少 10 位，包含字母、数字和符号'))
  }
  return Promise.resolve()
}

/** 找回密码页：对接 A-04 占位接口，展示后端占位文案。 */
export default function ForgotPassword() {
  const [submitting, setSubmitting] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [countdown, setCountdown] = useState(0)

  useEffect(() => {
    if (countdown <= 0) return
    const t = setTimeout(() => setCountdown(countdown - 1), 1000)
    return () => clearTimeout(t)
  }, [countdown])

  const sendCode = () => {
    // ponytail: 验证码发送为占位，真实短信/邮件接入前只做倒计时展示
    setCountdown(60)
  }

  const onFinish = async (values: Record<string, unknown>) => {
    setSubmitting(true)
    try {
      await post('/auth/reset-password', values)
      setNotice('该功能正在开发中')
    } catch {
      setNotice('重置失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="找回密码" subtitle="通过校内邮箱验证后重置密码。">
      {notice && <Alert type="info" showIcon message={notice} style={{ marginBottom: 16 }} />}
      <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
        <Form.Item label="验证方式">
          {/* ponytail: 演示占位邮箱（S-6），接真实找回流程时改为读取当前账号绑定的校内邮箱 */}
          <Input value="校内邮箱 · zhang***@campus.edu.cn" disabled />
        </Form.Item>
        <Form.Item name="code" label="验证码" rules={[{ required: true, message: '请输入验证码' }]}>
          <Input
            placeholder="6 位数字验证码"
            suffix={
              <Button
                type="link"
                size="small"
                style={{ padding: 0 }}
                onClick={sendCode}
                disabled={countdown > 0}
                title={countdown > 0 ? `${countdown} 秒后可重新发送` : undefined}
              >
                {countdown > 0 ? `${countdown}s 后重发` : '发送验证码'}
              </Button>
            }
          />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="新密码"
          rules={[{ required: true, message: '请输入新密码' }, { validator: validateStrength }]}
        >
          <Input.Password placeholder="至少 10 位，包含字母、数字和符号" autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator(_: unknown, value: string) {
                if (!value || value === getFieldValue('newPassword')) return Promise.resolve()
                return Promise.reject(new Error('两次输入的密码不一致'))
              },
            }),
          ]}
        >
          <Input.Password placeholder="再次输入新密码" autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={submitting} block>
          确认重置
        </Button>
        <div style={{ textAlign: 'right', marginTop: 12 }}>
          <Link to="/login">返回登录</Link>
        </div>
        <Alert
          type="warning"
          showIcon
          message="安全提示"
          description="请勿将验证码告知他人。系统仅通过校内邮箱向本人发送验证码。"
          style={{ marginTop: 20 }}
        />
      </Form>
    </AuthLayout>
  )
}
