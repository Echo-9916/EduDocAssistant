import { useState } from 'react'
import { Alert, Button, Checkbox, Form, Input, message } from 'antd'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import AuthLayout from '../auth/AuthLayout'
import { signalCharacters, triggerLoginError } from '../auth/AnimatedCharacters'
import { landingPath, useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/http'

export default function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [passwordVisible, setPasswordVisible] = useState(false)

  const redirect = new URLSearchParams(location.search).get('redirect')

  // P1-1：所有认证落点统一先进角色选择页；深链目标存入路由 state，「进入工作台」后回跳
  if (user) {
    return <Navigate to="/roles" state={redirect ? { redirect } : undefined} replace />
  }

  const onFinish = async (values: { username: string; password: string }) => {
    setSubmitting(true)
    setError(null)
    try {
      const u = await login(values.username.trim(), values.password)
      navigate(landingPath(u.roles), { replace: true, state: redirect ? { redirect } : undefined })
    } catch (e) {
      // P2-2：按结构化 data.accountStatus 判定账号受限，不再依赖中文文案
      const accountStatus = e instanceof ApiError
        ? (e.data as { accountStatus?: string } | null | undefined)?.accountStatus
        : undefined
      if (e instanceof ApiError && e.code === 40101 && accountStatus) {
        navigate('/account-restricted', {
          replace: true,
          state: { username: values.username.trim(), accountStatus, reason: e.message },
        })
        return
      }
      setError(e instanceof ApiError ? e.message : '登录失败')
      triggerLoginError()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="登录工作台" subtitle="使用学校账号继续，系统会自动识别所属角色。">
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      <Form name="login" layout="vertical" onFinish={onFinish} requiredMark={false}>
        <Form.Item
          name="username"
          label="账号"
          rules={[{ required: true, whitespace: true, message: '请输入账号' }]}
        >
          <Input
            placeholder="2023001 或教师工号"
            autoComplete="username"
            onFocus={() => signalCharacters({ accountFocused: true })}
            onBlur={() => signalCharacters({ accountFocused: false })}
          />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password
            placeholder="••••••••••"
            autoComplete="current-password"
            visibilityToggle={{
              visible: passwordVisible,
              onVisibleChange: (v) => {
                setPasswordVisible(v)
                signalCharacters({ passwordVisible: v })
              },
            }}
            onFocus={() => signalCharacters({ passwordFocused: true })}
            onBlur={() => signalCharacters({ passwordFocused: false })}
            onChange={(e) => signalCharacters({ passwordLen: e.target.value.length })}
          />
        </Form.Item>
        <div className="auth-helper" style={{ marginTop: -12, marginBottom: 16 }}>密码区分大小写</div>
        <div className="auth-row-between" style={{ marginBottom: 20 }}>
          {/* ponytail: 「记住此设备」为原型元素，待后端支持长效会话/设备绑定后接线（S-3） */}
          <Form.Item name="remember" valuePropName="checked" noStyle>
            <Checkbox>记住此设备</Checkbox>
          </Form.Item>
          <Link to="/forgot-password">忘记密码？</Link>
        </div>
        <Button type="primary" htmlType="submit" loading={submitting} block>
          登录
        </Button>
        <div className="auth-divider">或</div>
        <Button block onClick={() => message.info('学校统一身份认证暂未开放')}>
          使用学校统一身份认证
        </Button>
        <p className="auth-helper" style={{ marginTop: 12 }}>
          注意: 认证成功将同步校园账号默认身份信息，本系统仅获取登录必要字段，不会额外采集隐私数据。
        </p>
      </Form>
    </AuthLayout>
  )
}
