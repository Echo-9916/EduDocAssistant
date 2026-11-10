// 06 切片 2：个人中心接真实后端（学生 27 / 教师 27 / 运维 26+27 屏共用一套壳）。
// 资料 GET/PUT /me/profile（白名单 name/org/email/phone，学号/工号与 extra 一律只读展示，带同步字段键后端 40001）；
// 安全 GET/PUT /me/security（OPS 关双因素 40301，前端如实写明「必须开启」；sensitiveVerify 只读不给开关）；
// 通知偏好 GET/PUT /me/notification-preferences（八类，与开关组合并为一处）；
// 改密 POST /me/password（成功后令牌被撤销：清本地登录态并跳登录页）；
// 会话 GET /me/sessions（DELETE 现阶段一律 40901，如实提示、不假装下线成功）；登录记录 GET /me/login-records?range。
import { useEffect, useState } from 'react'
import { Alert, Avatar, Col, Form, Input, Row, Segmented, Space, Spin, Switch, Tabs, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { AppButton, AppModal, AppTable, requiredRule } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import { useAuth } from '../../auth/AuthContext'
import { getErrorMessage } from '../../api/http'
import { messageTypeLabel } from '../../api/messages'
import {
  changePassword,
  endSession,
  getProfile,
  getSecurity,
  listLoginRecords,
  listPreferences,
  listSessions,
  savePreferences,
  updateProfile,
  updateSecurity,
} from '../../api/me'
import type { LoginRange, LoginRecordView, PreferenceView, ProfileView, SecurityView, SessionView } from '../../api/me'
import type { RoleCodeValue } from '../../api/types'

const ROLE_ORDER: RoleCodeValue[] = ['OPS', 'TEACHER', 'STUDENT']
const RESULT_COLOR: Record<LoginRecordView['result'], string> = { SUCCESS: 'green', BLOCKED: 'red' }
const RESULT_LABEL: Record<LoginRecordView['result'], string> = { SUCCESS: '成功', BLOCKED: '已阻断' }
const ACCOUNT_LABEL: Record<RoleCodeValue, string> = { STUDENT: '学号', TEACHER: '工号', OPS: '工号' }

const ROW_STYLE = { display: 'flex', justifyContent: 'space-between', gap: 16, padding: '10px 0', borderBottom: '1px solid #F0F2F5' } as const

/** 后端 ISO 时间串（UTC 带 Z）→ 本地「YYYY-MM-DD HH:mm」；裸 slice 会早 8 小时，禁止。 */
const dt = (v: string | null) => {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString('zh-CN', { hour12: false }).slice(0, 16)
}

export default function Profile() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const role = ROLE_ORDER.find((r) => user?.roles.includes(r)) ?? 'STUDENT'
  const isOps = role === 'OPS'

  const [info, setInfo] = useState<ProfileView | null>(null)
  const [sec, setSec] = useState<SecurityView | null>(null)
  const [prefs, setPrefs] = useState<PreferenceView[]>([])
  const [sessions, setSessions] = useState<SessionView[]>([])
  // 后端登录记录没有 id，注入序号作 antd rowKey（默认取 key 字段）
  const [records, setRecords] = useState<(LoginRecordView & { key: number })[]>([])
  const [range, setRange] = useState<LoginRange>('7d')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [pwdOpen, setPwdOpen] = useState(false)
  const [form] = Form.useForm()

  const load = () => {
    setLoading(true)
    return Promise.all([getProfile(), getSecurity(), listSessions(), listPreferences()])
      .then(([p, s, ses, pr]) => { setInfo(p); setSec(s); setSessions(ses); setPrefs(pr) })
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setLoading(false))
  }
  useEffect(() => {
    load()
  }, [role])

  useEffect(() => {
    let cancelled = false
    listLoginRecords({ range })
      .then((r) => !cancelled && setRecords(r.map((x, i) => ({ ...x, key: i }))))
      .catch((e) => { if (!cancelled) { setRecords([]); message.error(getErrorMessage(e)) } })
    return () => { cancelled = true }
  }, [range])

  const name = user?.displayName || info?.name || ''

  const setSwitch = async (patch: { twoFactor?: boolean; newDeviceAlert?: boolean }, tip: string) => {
    try {
      // 后端返回更新后的完整视图，直接落状态；OPS 关双因素 40301 走 catch 如实提示
      setSec(await updateSecurity(patch))
      message.success(tip)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const toggleTwoFactor = (v: boolean) => {
    // OPS 关闭会被后端 40301 拒：不悄悄禁用开关，前置写明原因
    if (isOps && !v) {
      message.warning('运维账号必须开启双因素认证，不能关闭')
      return
    }
    void setSwitch({ twoFactor: v }, v ? '双因素认证已开启' : '双因素认证已关闭')
  }

  const togglePref = async (type: PreferenceView['type'], enabled: boolean) => {
    try {
      await savePreferences([{ type, enabled }])
      setPrefs((prev) => prev.map((p) => (p.type === type ? { ...p, enabled } : p)))
      message.success(`「${messageTypeLabel[type]}」通知${enabled ? '已开启' : '已关闭'}`)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const settingRow = (key: string, title: string, desc: string, checked: boolean, onChange: (v: boolean) => void) => (
    <div key={key} style={ROW_STYLE}>
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

  const sessionColumns: ColumnsType<SessionView> = [
    { title: '设备', dataIndex: 'device', render: (v: string | null) => v ?? '-' },
    { title: 'IP', dataIndex: 'ip', width: 140, render: (v: string | null) => v ?? '-' },
    { title: '登录时间', dataIndex: 'loginTime', width: 170, render: (v: string) => dt(v) },
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
          // 后端现阶段一律 40901（无令牌会话索引，B2）：如实弹出原因，不 message.success 假装下线，也不删行
          <AppButton
            type="link"
            size="small"
            danger
            onClick={async () => {
              try {
                await endSession(row.id)
                message.success('会话已下线')
                setSessions(await listSessions())
              } catch (e) {
                message.warning(getErrorMessage(e))
              }
            }}
          >
            下线
          </AppButton>
        ),
    },
  ]

  const recordColumns: ColumnsType<LoginRecordView> = [
    { title: '时间', dataIndex: 'time', width: 170, render: (v: string) => dt(v) },
    { title: '设备', dataIndex: 'device', render: (v: string | null) => v ?? '-' },
    // ip/location 后端可能为空（现 AUTH 审计不写 source_ip，B3）：空值如实显示「-」，不伪造
    { title: '来源 IP', dataIndex: 'ip', width: 140, render: (v: string | null) => v ?? '-' },
    {
      title: '位置',
      dataIndex: 'location',
      width: 180,
      render: (v: string | null, row) => (
        <Space size={4}>
          <span>{v ?? '-'}</span>
          {row.remote && <Tag color="warning">异地登录</Tag>}
        </Space>
      ),
    },
    {
      title: '结果',
      dataIndex: 'result',
      width: 130,
      render: (v: LoginRecordView['result']) => <Tag color={RESULT_COLOR[v] ?? 'default'}>{RESULT_LABEL[v] ?? v}</Tag>,
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
              <Typography.Text type="secondary">{info.org ?? '-'}</Typography.Text>
              <Tag color="green">{info.roleLabel}</Tag>
            </Space>
          </DemoCard>
        </Col>
        <Col span={17}>
          <DemoCard title="基本信息" extra={<Typography.Text type="secondary">实名信息由学校统一身份认证同步，只读</Typography.Text>}>
            <Form
              layout="vertical"
              initialValues={{
                name: info.name ?? '',
                account: info.account,
                org: info.org ?? '',
                email: info.email ?? '',
                phone: info.phone ?? '',
                ...Object.fromEntries(info.extra.map((e, i) => [`extra-${i}`, e.value])),
              }}
              onFinish={async (values) => {
                const v = values as Record<string, string>
                setSaving(true)
                try {
                  // 只提交白名单四项：account/extra 为同步字段，带上后端直接 40001
                  await updateProfile({ name: v.name, org: v.org, email: v.email, phone: v.phone })
                  await load()
                  message.success('个人资料已保存')
                } catch (e) {
                  message.error(getErrorMessage(e))
                } finally {
                  setSaving(false)
                }
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
                {info.extra.map((e, i) => (
                  <Col span={12} key={e.label}>
                    <Form.Item name={`extra-${i}`} label={e.label} tooltip="由学校同步，如需修改请联系教务处">
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
    sec && !loading ? (
      <>
        <DemoCard title={isOps ? '账号与安全（运维）' : '账号与安全'}>
          <div style={ROW_STYLE}>
            <div>
              <Typography.Text strong>登录密码</Typography.Text>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  上次更新：{dt(sec.passwordUpdated)} · 建议每 90 天更换一次
                </Typography.Text>
              </div>
            </div>
            <AppButton type="default" onClick={() => setPwdOpen(true)}>
              修改密码
            </AppButton>
          </div>
          {settingRow('2fa', '双因素认证', '校内身份服务 + 动态验证码。', sec.twoFactor, toggleTwoFactor)}
          {isOps && (
            // sensitiveVerify 后端只读（PUT 不接受该键）：不给开关，只展示策略状态
            <div key="sensitive" style={ROW_STYLE}>
              <div>
                <Typography.Text strong>敏感操作二次验证</Typography.Text>
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    角色变更、账号冻结和存储清理需动态验证码确认；按策略强制，不可关闭。
                  </Typography.Text>
                </div>
              </div>
              <Tag color={sec.sensitiveVerify ? 'green' : 'default'}>{sec.sensitiveVerify ? '已开启' : '未开启'}</Tag>
            </div>
          )}
          {settingRow('device', '新设备登录提醒', '通过站内消息与邮件通知本人。', sec.newDeviceAlert, (v) =>
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

        <DemoCard title="通知偏好" extra={<Typography.Text type="secondary">八类消息，关闭后该类站内消息不再投递</Typography.Text>}>
          {prefs.map((p) =>
            settingRow(p.type, messageTypeLabel[p.type] ?? p.type, '关闭后该类站内消息不再投递。', p.enabled, (v) =>
              void togglePref(p.type, v),
            ),
          )}
        </DemoCard>

        <DemoCard title="当前会话" extra={<Typography.Text type="secondary">由登录审计派生的近似列表 · 令牌 30 分钟内自然过期</Typography.Text>}>
          <AppTable<SessionView> rowKey="id" size="small" columns={sessionColumns} dataSource={sessions} pagination={false} />
        </DemoCard>

        <DemoCard
          title="登录记录"
          extra={
            <Segmented
              size="small"
              value={range}
              onChange={(v) => setRange(v as LoginRange)}
              options={[
                { label: '24 小时', value: '24h' },
                { label: '7 天', value: '7d' },
                { label: '30 天', value: '30d' },
              ]}
            />
          }
        >
          <AppTable<LoginRecordView> size="small" columns={recordColumns} dataSource={records} pagination={false} />
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
      live
      mixedNote="会话精确下线与告警投递未接入"
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
          let values: { current: string; next: string }
          try {
            // 两次一致校验在前端做；旧密码错后端回 40001（401 系会被 http 拦截器当成登录态失效硬跳 /login）
            values = await form.validateFields()
          } catch {
            return
          }
          try {
            await changePassword(values.current, values.next)
          } catch (e) {
            message.error(getErrorMessage(e))
            return
          }
          setPwdOpen(false)
          form.resetFields()
          message.success('密码已修改，请使用新密码重新登录')
          // 后端已撤销当前令牌：照 AppShell 退出写法清本地登录态并跳登录页
          await logout()
          navigate('/login', { replace: true })
        }}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="current" label="当前密码" rules={[requiredRule('请输入当前密码')]}>
            <Input.Password placeholder="请输入当前密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item name="next" label="新密码" rules={[requiredRule('请输入新密码'), { min: 8, message: '新密码至少 8 位' }]}>
            <Input.Password placeholder="至少 8 位，需同时包含字母和数字" autoComplete="new-password" />
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
