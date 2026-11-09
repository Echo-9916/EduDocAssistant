// 个人中心（学生 27 / 教师 27 / 运维 26+27 屏）：三端共用一套壳，按当前登录角色取资料与安全项。
// 数据来自 mock/domain/profile.ts；实名信息（姓名以外的学号/工号、院系、课程）由学校同步，页面置只读。
import { useEffect, useState } from 'react'
import { Alert, Avatar, Col, Form, Input, Popconfirm, Row, Space, Spin, Switch, Tabs, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable, requiredRule } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import { useAuth } from '../../auth/AuthContext'
import {
  changePassword,
  endSession,
  getProfile,
  getSecurity,
  listLoginRecords,
  listSessions,
  loginResultLabel,
  saveProfile,
  saveSecurity,
} from '../../mock/domain/profile'
import type { LoginRecord, ProfileInfo, SecurityPref, Session } from '../../mock/domain/profile'
import type { RoleCodeValue } from '../../api/types'

const ROLE_ORDER: RoleCodeValue[] = ['OPS', 'TEACHER', 'STUDENT']
const RESULT_COLOR: Record<LoginRecord['result'], string> = { SUCCESS: 'green', VERIFY: 'blue', BLOCKED: 'red' }

const ACCOUNT_LABEL: Record<RoleCodeValue, string> = { STUDENT: '学号', TEACHER: '工号', OPS: '工号' }

export default function Profile() {
  const { user } = useAuth()
  const role = ROLE_ORDER.find((r) => user?.roles.includes(r)) ?? 'STUDENT'
  const isOps = role === 'OPS'

  const [info, setInfo] = useState<ProfileInfo | null>(null)
  const [pref, setPref] = useState<SecurityPref | null>(null)
  const [sessions, setSessions] = useState<Session[]>([])
  const [records, setRecords] = useState<LoginRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [pwdOpen, setPwdOpen] = useState(false)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    return Promise.all([getProfile(role), getSecurity(role), listSessions(role), listLoginRecords(role)]).then(
      ([p, s, ses, rec]) => {
        setInfo(p)
        setPref(s)
        setSessions(ses)
        setRecords(rec)
        setLoading(false)
      },
    )
  }
  useEffect(() => {
    load()
  }, [role])

  const name = user?.displayName || info?.name || ''

  const setSwitch = async (patch: Parameters<typeof saveSecurity>[1], tip: string) => {
    await saveSecurity(role, patch)
    await load()
    message.success(tip)
  }

  const toggleTwoFactor = (v: boolean) => {
    if (isOps && !v) {
      message.warning('运维管理员必须保持双因素认证开启，未关闭')
      return
    }
    void setSwitch({ twoFactor: v }, v ? '双因素认证已开启' : '双因素认证已关闭')
  }

  const settingRow = (key: string, title: string, desc: string, checked: boolean, onChange: (v: boolean) => void) => (
    <div key={key} style={{ display: 'flex', justifyContent: 'space-between', gap: 16, padding: '10px 0', borderBottom: '1px solid #F0F2F5' }}>
      <div>
        <Typography.Text strong>{title}</Typography.Text>
        <div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {desc}
          </Typography.Text>
        </div>
      </div>
      <Switch checked={checked} onChange={onChange} />
    </div>
  )

  const sessionColumns: ColumnsType<Session> = [
    { title: '设备', dataIndex: 'device' },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    { title: '登录时间', dataIndex: 'loginTime', width: 170 },
    {
      title: '状态',
      dataIndex: 'current',
      width: 110,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? '当前会话' : '远程会话'}</Tag>,
    },
    {
      title: '操作',
      width: 90,
      render: (_, row) =>
        row.current ? (
          <Typography.Text type="secondary">—</Typography.Text>
        ) : (
          <Popconfirm
            title="下线该会话？"
            description="该设备的登录状态会立即失效，需重新认证。"
            okText="下线"
            cancelText="取消"
            onConfirm={async () => {
              await endSession(row.id)
              setSessions(await listSessions(role))
              message.success('远程会话已下线')
            }}
          >
            <AppButton type="link" size="small" danger>
              下线
            </AppButton>
          </Popconfirm>
        ),
    },
  ]

  const recordColumns: ColumnsType<LoginRecord> = [
    { title: '时间', dataIndex: 'time', width: 170 },
    { title: '设备', dataIndex: 'device' },
    { title: '来源 IP', dataIndex: 'ip', width: 140 },
    {
      title: '位置',
      dataIndex: 'location',
      width: 180,
      render: (v: string, row) => (
        <Space size={4}>
          <span>{v}</span>
          {row.remote && <Tag color="warning">异地登录</Tag>}
        </Space>
      ),
    },
    {
      title: '结果',
      dataIndex: 'result',
      width: 130,
      render: (v: LoginRecord['result']) => <Tag color={RESULT_COLOR[v]}>{loginResultLabel[v]}</Tag>,
    },
  ]

  const profilePane =
    info && !loading ? (
      <Row gutter={16}>
        <Col span={7}>
          <DemoCard title="账号概览">
            <Space direction="vertical" align="center" size={8} style={{ width: '100%' }}>
              <Avatar size={64} style={{ backgroundColor: '#18344B' }}>
                {name.slice(0, 1)}
              </Avatar>
              <Typography.Title level={5} style={{ margin: 0 }}>
                {name}
              </Typography.Title>
              <Typography.Text type="secondary">{info.org}</Typography.Text>
              <Tag color="green">{info.roleLabel}</Tag>
              <Row gutter={16} style={{ marginTop: 8 }}>
                {info.stats.map((s) => (
                  <Col span={12} key={s.label} style={{ textAlign: 'center' }}>
                    <Typography.Title level={4} style={{ margin: 0, color: '#0E7490' }}>
                      {s.value}
                    </Typography.Title>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {s.label}
                    </Typography.Text>
                  </Col>
                ))}
              </Row>
            </Space>
          </DemoCard>
        </Col>
        <Col span={17}>
          <DemoCard title="基本信息" extra={<Typography.Text type="secondary">实名信息由学校统一身份认证同步</Typography.Text>}>
            <Form
              layout="vertical"
              initialValues={{
                name: user?.displayName || info.name,
                account: user?.username || info.account,
                org: info.org,
                email: info.email,
                phone: info.phone,
                ...Object.fromEntries(info.extra.map((e) => [e.name, e.value])),
              }}
              onFinish={async (values) => {
                const v = values as Record<string, string>
                setSaving(true)
                await saveProfile(role, { name: v.name, org: v.org, email: v.email, phone: v.phone })
                await load()
                setSaving(false)
                message.success('个人资料已保存')
              }}
            >
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="name" label="姓名" rules={[requiredRule('请输入姓名')]}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="account" label={ACCOUNT_LABEL[role]}>
                    <Input disabled />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="org" label="组织 / 院系">
                    <Input />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="email" label="联系邮箱" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="phone" label="联系方式">
                    <Input />
                  </Form.Item>
                </Col>
                {info.extra.map((e) => (
                  <Col span={12} key={e.name}>
                    <Form.Item name={e.name} label={e.label} tooltip="由学校同步，如需修改请联系教务处">
                      <Input disabled />
                    </Form.Item>
                  </Col>
                ))}
              </Row>
              <AppButton htmlType="submit" loading={saving}>
                保存
              </AppButton>
            </Form>
          </DemoCard>
        </Col>
      </Row>
    ) : (
      <Spin />
    )

  const securityPane =
    pref && !loading ? (
      <>
        <DemoCard title={isOps ? '账号与安全（运维）' : '账号与安全'}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, padding: '10px 0', borderBottom: '1px solid #F0F2F5' }}>
            <div>
              <Typography.Text strong>登录密码</Typography.Text>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  上次更新：{pref.passwordUpdated} · 建议每 90 天更换一次
                </Typography.Text>
              </div>
            </div>
            <AppButton type="default" onClick={() => setPwdOpen(true)}>
              修改密码
            </AppButton>
          </div>
          {settingRow('2fa', '双因素认证', '校内身份服务 + 动态验证码。', pref.twoFactor, toggleTwoFactor)}
          {isOps &&
            settingRow('sensitive', '敏感操作二次验证', '角色变更、账号冻结和存储清理需动态验证码确认。', pref.sensitiveVerify, (v) =>
              void setSwitch({ sensitiveVerify: v }, v ? '敏感操作二次验证已开启' : '敏感操作二次验证已关闭'),
            )}
          {settingRow('device', '新设备登录提醒', '通过站内消息与邮件通知本人。', pref.newDeviceAlert, (v) =>
            void setSwitch({ newDeviceAlert: v }, v ? '新设备登录提醒已开启' : '新设备登录提醒已关闭'),
          )}
          {isOps && (
            <Alert
              style={{ marginTop: 12 }}
              type="info"
              showIcon
              message="运维管理员必须保持双因素认证与敏感操作二次验证开启，关闭后角色变更、账号冻结、存储清理将被拒绝。"
            />
          )}
        </DemoCard>

        <DemoCard title="通知偏好">
          {settingRow('homework', '作业提醒', '截止前 72 小时与 24 小时提醒。', pref.notify.homework, (v) =>
            void setSwitch({ notify: { homework: v } }, v ? '作业提醒已开启' : '作业提醒已关闭'),
          )}
          {settingRow('format', '格式异常', '格式校验完成后发送站内消息。', pref.notify.format, (v) =>
            void setSwitch({ notify: { format: v } }, v ? '格式异常通知已开启' : '格式异常通知已关闭'),
          )}
          {settingRow('alert', '系统告警', isOps ? '高风险告警站内消息、邮件与即时电话。' : '课程与系统维护公告。', pref.notify.alert, (v) =>
            void setSwitch({ notify: { alert: v } }, v ? '系统告警通知已开启' : '系统告警通知已关闭'),
          )}
        </DemoCard>

        <DemoCard title="当前会话" extra={<Typography.Text type="secondary">会话空闲 30 分钟自动退出</Typography.Text>}>
          <AppTable<Session> rowKey="id" size="small" columns={sessionColumns} dataSource={sessions} pagination={false} />
        </DemoCard>

        <DemoCard title="登录记录">
          <AppTable<LoginRecord> rowKey="id" size="small" columns={recordColumns} dataSource={records} pagination={false} />
        </DemoCard>
      </>
    ) : (
      <Spin />
    )

  return (
    <DemoPage
      title="个人中心"
      screens="对应原型：学生 27、教师 27、运维 26/27"
      desc={
        isOps
          ? '维护运维账号的组织信息、值班联系方式、双因素认证、会话与登录记录。'
          : '维护账号资料、通知偏好，以及密码、双因素认证、会话与登录记录。'
      }
    >
      <Tabs
        defaultActiveKey="info"
        items={[
          { key: 'info', label: '个人资料', children: profilePane },
          { key: 'security', label: '账号与安全', children: securityPane },
        ]}
      />

      <AppModal
        open={pwdOpen}
        title="修改登录密码"
        okText="确认修改"
        cancelText="取消"
        onCancel={() => {
          setPwdOpen(false)
          form.resetFields()
        }}
        onOk={async () => {
          try {
            await form.validateFields()
          } catch {
            return
          }
          await changePassword(role)
          setPwdOpen(false)
          form.resetFields()
          await load()
          message.success('登录密码已更新，其他会话需重新登录')
        }}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="current" label="当前密码" rules={[requiredRule('请输入当前密码')]}>
            <Input.Password placeholder="请输入当前密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item name="next" label="新密码" rules={[requiredRule('请输入新密码'), { min: 8, message: '新密码至少 8 位' }]}>
            <Input.Password placeholder="至少 8 位，建议含字母与数字" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="确认新密码"
            dependencies={['next']}
            rules={[
              requiredRule('请再次输入新密码'),
              ({ getFieldValue }) => ({
                validator: (_, value: string) =>
                  !value || value === getFieldValue('next') ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')),
              }),
            ]}
          >
            <Input.Password placeholder="请再次输入新密码" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </AppModal>
    </DemoPage>
  )
}
