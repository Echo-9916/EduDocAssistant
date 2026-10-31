import { useState } from 'react'
import { Button, Form, Input, message } from 'antd'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { homeOf, useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/http'

export default function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    return <Navigate to={homeOf(user.roles)} replace />
  }

  const redirect = new URLSearchParams(location.search).get('redirect')

  const onFinish = async (values: { username: string; password: string }) => {
    setSubmitting(true)
    try {
      const u = await login(values.username.trim(), values.password)
      message.success('登录成功')
      navigate(redirect || homeOf(u.roles), { replace: true })
    } catch (e) {
      message.error(e instanceof ApiError ? e.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
      <Form name="login" layout="vertical" onFinish={onFinish} style={{ width: 320 }}>
        <h2 style={{ textAlign: 'center' }}>CIDAS 校园文档辅助系统</h2>
        <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
          <Input placeholder="用户名" autoComplete="username" />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password placeholder="密码" autoComplete="current-password" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>
            登录
          </Button>
        </Form.Item>
      </Form>
    </div>
  )
}
