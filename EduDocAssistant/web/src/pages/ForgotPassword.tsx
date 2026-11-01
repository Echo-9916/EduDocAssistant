import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Select } from 'antd'
import { Link } from 'react-router-dom'
import { post } from '../api/http'

/** 新密码强度：至少 8 位且同时包含字母与数字。 */
function validateStrength(_: unknown, value: string) {
  if (!value) return Promise.resolve()
  if (value.length < 8 || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return Promise.reject(new Error('密码至少 8 位且包含字母和数字'))
  }
  return Promise.resolve()
}

/** 找回密码页：对接 A-04 占位接口，展示后端占位文案。 */
export default function ForgotPassword() {
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

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
      <Card title="找回密码" style={{ width: 400, borderRadius: 16 }}>
        {notice && <Alert type="info" showIcon message={notice} style={{ marginBottom: 16 }} />}
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item name="method" label="验证方式" initialValue="email" rules={[{ required: true, message: '请选择验证方式' }]}>
            <Select
              options={[
                { value: 'email', label: '邮箱验证' },
                { value: 'phone', label: '手机验证' },
              ]}
            />
          </Form.Item>
          <Form.Item name="code" label="验证码" rules={[{ required: true, message: '请输入验证码' }]}>
            <Input placeholder="验证码" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true, message: '请输入新密码' }, { validator: validateStrength }]}
          >
            <Input.Password placeholder="新密码" autoComplete="new-password" />
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
            <Input.Password placeholder="确认新密码" autoComplete="new-password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              重置密码
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'right' }}>
            <Link to="/login">返回登录</Link>
          </div>
        </Form>
      </Card>
    </div>
  )
}
