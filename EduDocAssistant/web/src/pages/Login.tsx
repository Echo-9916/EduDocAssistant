import { useState } from 'react'
import { Alert, Button, Form, Input } from 'antd'
import { ReadOutlined } from '@ant-design/icons'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { homeOf, useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/http'

export default function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (user) {
    return <Navigate to={homeOf(user.roles)} replace />
  }

  const redirect = new URLSearchParams(location.search).get('redirect')

  const onFinish = async (values: { username: string; password: string }) => {
    setSubmitting(true)
    setError(null)
    try {
      const u = await login(values.username.trim(), values.password)
      navigate(redirect || homeOf(u.roles), { replace: true })
    } catch (e) {
      // 40101 分两种：账号受限跳受限页，其余（密码错误等）按 message 表单提示
      if (e instanceof ApiError && e.code === 40101 && e.message.includes('账号受限')) {
        navigate('/account-restricted', { replace: true })
        return
      }
      setError(e instanceof ApiError ? e.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      {/* 左侧品牌区 */}
      <div
        style={{
          width: 440,
          background: 'linear-gradient(160deg, #2563EB 0%, #1D4ED8 100%)',
          color: '#fff',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          padding: 48,
        }}
      >
        {/* Logo 占位：无正式图片资产，替换 Logo 图片时仅改此块 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 30, fontWeight: 700 }}>
          <ReadOutlined />
          <span>CIDAS</span>
        </div>
        <div style={{ marginTop: 8, fontSize: 16, opacity: 0.9 }}>校园智能文档辅助系统</div>
      </div>
      {/* 右侧表单区 */}
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <Form name="login" layout="vertical" onFinish={onFinish} style={{ width: 360 }}>
          <h2 style={{ marginTop: 0 }}>登录</h2>
          {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, whitespace: true, message: '请输入用户名' }]}
          >
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
          <div style={{ textAlign: 'right' }}>
            <Link to="/forgot-password">忘记密码？</Link>
          </div>
        </Form>
      </div>
    </div>
  )
}
