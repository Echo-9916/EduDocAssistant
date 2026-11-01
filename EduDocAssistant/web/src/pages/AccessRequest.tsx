import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Select } from 'antd'
import { Link } from 'react-router-dom'
import { post } from '../api/http'

/** 权限申请页：对接 A-04 占位接口，展示后端占位文案。 */
export default function AccessRequest() {
  const [submitting, setSubmitting] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const onFinish = async (values: Record<string, unknown>) => {
    setSubmitting(true)
    try {
      await post('/auth/access-request', values)
      setNotice('该功能正在开发中')
    } catch {
      setNotice('提交失败，请稍后重试')
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
      <Card title="申请临时访问权限" style={{ width: 400, borderRadius: 16 }}>
        {notice && <Alert type="info" showIcon message={notice} style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item
            name="scope"
            label="期望权限范围"
            initialValue="DOCUMENT_READ"
            rules={[{ required: true, message: '请选择期望权限范围' }]}
          >
            <Select
              options={[
                { value: 'DOCUMENT_READ', label: '文档查看' },
                { value: 'DOCUMENT_EDIT', label: '文档编辑' },
                { value: 'DOCUMENT_DOWNLOAD', label: '文档下载' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="reason"
            label="申请原因"
            rules={[{ required: true, message: '请填写申请原因' }]}
          >
            <Input.TextArea rows={4} placeholder="请说明申请临时访问权限的原因" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              提交申请
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
